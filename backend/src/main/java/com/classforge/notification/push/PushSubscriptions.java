package com.classforge.notification.push;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

@Service
public class PushSubscriptions {
    private final MongoTemplate mongo;private final PushProperties properties;private final Clock clock;
    public PushSubscriptions(MongoTemplate mongo,PushProperties properties,@Qualifier("pushClock")Clock clock){this.mongo=mongo;this.properties=properties;this.clock=clock;}
    public void requireEnabled(){if(!properties.enabled())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Push no está habilitado.");}
    @Transactional public void register(UUID user,String endpoint,String point,String auth){
        requireEnabled();PushValidation.endpoint(endpoint);PushValidation.keys(point,auth);
        String id=PushValidation.hash(endpoint);var existing=mongo.findById(id,PushSubscription.class);Instant now=clock.instant();
        if(existing!=null){
            if(!existing.userId().equals(user))throw new ResponseStatusException(HttpStatus.CONFLICT,"Este dispositivo está vinculado a otra cuenta; desactiva y vuelve a activar push.");
            mongo.updateFirst(Query.query(Criteria.where("id").is(id).and("userId").is(user)),new Update().set("p256dh",point).set("auth",auth).set("confirmedAt",now),PushSubscription.class);return;
        }
        mongo.upsert(Query.query(Criteria.where("id").is(user)),new Update().setOnInsert("count",0),PushQuota.class);
        var quota=mongo.findAndModify(Query.query(Criteria.where("id").is(user).and("count").lt(10)),new Update().inc("count",1),FindAndModifyOptions.options().returnNew(true),PushQuota.class);
        if(quota==null)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Se alcanzó el límite de dispositivos push.");
        mongo.insert(new PushSubscription(id,UUID.randomUUID(),user,endpoint,point,auth,now,now,null));
    }
    public boolean owns(UUID user,String endpoint){PushValidation.endpoint(endpoint);return mongo.exists(Query.query(Criteria.where("id").is(PushValidation.hash(endpoint)).and("userId").is(user)),PushSubscription.class);}
    @Transactional public void remove(UUID user,String endpoint){
        PushValidation.endpoint(endpoint);var s=mongo.findOne(Query.query(Criteria.where("id").is(PushValidation.hash(endpoint)).and("userId").is(user)),PushSubscription.class);
        if(s!=null)invalidate(s.id(),s.generation());
    }
    @Transactional public void invalidate(String id,UUID generation){
        var s=mongo.findAndRemove(Query.query(Criteria.where("id").is(id).and("generation").is(generation)),PushSubscription.class);
        if(s!=null)mongo.updateFirst(Query.query(Criteria.where("id").is(s.userId()).and("count").gt(0)),new Update().inc("count",-1),PushQuota.class);
    }
    @Transactional public PushSubscription claimTest(UUID user,String endpoint){
        requireEnabled();PushValidation.endpoint(endpoint);String id=PushValidation.hash(endpoint);
        if(!owns(user,endpoint))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Activa push en este dispositivo primero.");
        var criteria=Criteria.where("id").is(id).and("userId").is(user).orOperator(Criteria.where("lastTestAt").is(null),Criteria.where("lastTestAt").lte(clock.instant().minusSeconds(60)));
        var result=mongo.findAndModify(Query.query(criteria),new Update().set("lastTestAt",clock.instant()),FindAndModifyOptions.options().returnNew(true),PushSubscription.class);
        if(result==null)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Espera un minuto antes de otra prueba.");return result;
    }
}
