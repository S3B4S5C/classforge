package com.classforge.notification.push;

import com.classforge.project.access.ProjectAccessService;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.UUID;

@Component
public class PushWorker {
    private static final Logger log=LoggerFactory.getLogger(PushWorker.class);
    private final MongoTemplate mongo;private final PushProperties properties;private final Clock clock;private final PushTransport transport;private final PushSubscriptions subscriptions;private final ProjectAccessService access;
    public PushWorker(MongoTemplate mongo,PushProperties properties,@Qualifier("pushClock")Clock clock,PushTransport transport,PushSubscriptions subscriptions,ProjectAccessService access){this.mongo=mongo;this.properties=properties;this.clock=clock;this.transport=transport;this.subscriptions=subscriptions;this.access=access;}
    @Scheduled(fixedDelayString="${classforge.push.poll-interval-millis:2000}",initialDelay=2000,scheduler="pushScheduler")
    public void scheduled(){if(properties.enabled()&&properties.workerEnabled())for(int i=0;i<10&&drainOne();i++){}}
    public boolean drainOne(){
        if(!properties.enabled())return false;Instant now=clock.instant();UUID token=UUID.randomUUID();
        mongo.updateMulti(Query.query(Criteria.where("status").is("PROCESSING").and("leaseUntil").lte(now).and("attempts").gte(5)),new Update().set("status","FAILED").unset("claimToken").unset("leaseUntil"),PushDelivery.class);
        var eligible=new Criteria().orOperator(Criteria.where("status").is("QUEUED").and("dueAt").lte(now),Criteria.where("status").is("PROCESSING").and("leaseUntil").lte(now));
        var query=Query.query(Criteria.where("expiresAt").gt(now).and("attempts").lt(5).andOperator(eligible)).with(Sort.by("dueAt"));
        var job=mongo.findAndModify(query,new Update().set("status","PROCESSING").set("leaseUntil",now.plusSeconds(30)).set("claimToken",token).inc("attempts",1),FindAndModifyOptions.options().returnNew(true),PushDelivery.class);
        if(job==null)return false;
        var subscription=mongo.findById(job.subscriptionId(),PushSubscription.class);
        if(subscription==null||!subscription.generation().equals(job.generation())||!subscription.userId().equals(job.userId())){finish(job,"DROPPED",0,0);return true;}
        if(job.projectId()!=null&&!access.role(job.userId(),job.projectId()).canRead()){finish(job,"DROPPED",0,0);return true;}
        try {
            var response=transport.send(subscription,job.payload());int status=response.status();
            if(status>=200&&status<300)finish(job,"SENT",status,0);
            else if(status==404||status==410){subscriptions.invalidate(subscription.id(),subscription.generation());finish(job,"DROPPED",status,0);}
            else if(status==429||status>=500)finish(job,"QUEUED",status,response.retryAfterSeconds());
            else finish(job,"FAILED",status,0);
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();finish(job,"QUEUED",0,0);}
        catch(Exception failure){log.warn("Push delivery deferred; attempt={} errorType={}",job.attempts(),failure.getClass().getSimpleName());finish(job,"QUEUED",0,0);}
        return true;
    }
    private void finish(PushDelivery job,String status,int response,int retryAfter){
        if("QUEUED".equals(status)&&job.attempts()>=5)status="FAILED";
        long delay=Math.max(Math.min(3600,retryAfter),Math.min(600,10L<<(Math.min(6,job.attempts()-1))));
        mongo.updateFirst(Query.query(Criteria.where("id").is(job.id()).and("claimToken").is(job.claimToken())),new Update().set("status",status).set("lastStatus",response)
                .set("dueAt",clock.instant().plusSeconds(delay)).unset("leaseUntil").unset("claimToken"),PushDelivery.class);
    }
}
