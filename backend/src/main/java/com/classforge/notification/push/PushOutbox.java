package com.classforge.notification.push;

import com.classforge.notification.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.event.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service
public class PushOutbox {
    private final MongoTemplate mongo;private final PushProperties properties;private final Clock clock;private final JsonMapper json;private final PushSubscriptions subscriptions;
    public PushOutbox(MongoTemplate mongo,PushProperties properties,@Qualifier("pushClock")Clock clock,JsonMapper json,PushSubscriptions subscriptions){this.mongo=mongo;this.properties=properties;this.clock=clock;this.json=json;this.subscriptions=subscriptions;}
    @TransactionalEventListener(phase=TransactionPhase.BEFORE_COMMIT)
    @Transactional(propagation=Propagation.MANDATORY)
    public void enqueue(ProjectNotificationEvent event){
        if(!properties.enabled()||!"NOTIFICATION_CREATED".equals(event.type())||event.notification()==null)return;
        var notification=event.notification();if(!notification.recipientUserId().equals(event.recipientUserId()))return;
        String url="/projects/"+notification.projectId()+"/activity";
        if(notification.type()==ProjectNotification.Type.SESSION_CLOSED&&notification.sessionId()!=null)url+="?sessionId="+notification.sessionId();
        String body=notification.type()==ProjectNotification.Type.SESSION_CLOSED?"Se cerró una sesión de trabajo en un proyecto compartido.":"Hay nueva actividad en un proyecto compartido.";
        for(var subscription:mongo.find(Query.query(Criteria.where("userId").is(event.recipientUserId())),PushSubscription.class))queue(subscription,notification.id(),notification.projectId(),body,url);
    }
    @Transactional public void test(UUID user,String endpoint){
        var subscription=subscriptions.claimTest(user,endpoint);
        queue(subscription,UUID.randomUUID(),null,"Las notificaciones de este dispositivo están activadas.","/projects");
    }
    private void queue(PushSubscription subscription,UUID notification,UUID project,String body,String url){
        UUID id=UUID.nameUUIDFromBytes((notification+":"+subscription.generation()).getBytes(StandardCharsets.UTF_8));Instant now=clock.instant();
        String payload=json.writeValueAsString(Map.of("title","ClassForge","body",body,"url",url,"ownerId",subscription.userId().toString(),"tag","classforge:"+notification));
        mongo.upsert(Query.query(Criteria.where("id").is(id)),new Update().setOnInsert("subscriptionId",subscription.id()).setOnInsert("generation",subscription.generation())
                .setOnInsert("userId",subscription.userId()).setOnInsert("projectId",project).setOnInsert("payload",payload).setOnInsert("createdAt",now)
                .setOnInsert("expiresAt",now.plusSeconds(86400)).setOnInsert("dueAt",now).setOnInsert("status","QUEUED").setOnInsert("attempts",0),PushDelivery.class);
    }
}
