package com.classforge.notification.push;

import com.classforge.auth.persistence.*;
import com.classforge.auth.security.JwtService;
import com.classforge.project.application.ProjectService;
import com.classforge.notification.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"classforge.push.enabled=true","classforge.push.worker-enabled=false","classforge.push.public-key=test-public-key"})
class PushIntegrationTests {
    @LocalServerPort int port;
    @Autowired MongoTemplate mongo;
    @Autowired UserRepository users;
    @Autowired ProjectService projects;
    @Autowired JwtService jwt;
    @Autowired JsonMapper json;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ApplicationEventPublisher events;
    @Autowired PushWorker worker;
    @Autowired org.springframework.context.ApplicationContext context;
    @MockitoBean PushTransport transport;
    UserEntity owner, other;
    UUID project;
    String point, auth, endpoint;
    final HttpClient http=HttpClient.newHttpClient();
    @BeforeEach void setup() throws Exception {
        mongo.remove(new Query(),PushDelivery.class);mongo.remove(new Query(),PushSubscription.class);mongo.remove(new Query(),PushQuota.class);
        owner=user();other=user();project=projects.create(owner.getId(),"Modelo confidencial").id();
        var generator=java.security.KeyPairGenerator.getInstance("EC");generator.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
        var key=(java.security.interfaces.ECPublicKey)generator.generateKeyPair().getPublic();
        byte[] raw=new byte[65];raw[0]=4;System.arraycopy(bytes(key.getW().getAffineX()),0,raw,1,32);System.arraycopy(bytes(key.getW().getAffineY()),0,raw,33,32);
        point=Base64.getUrlEncoder().withoutPadding().encodeToString(raw);auth=Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);
        endpoint="https://fcm.googleapis.com/fcm/send/"+UUID.randomUUID();
        when(transport.send(any(),anyString())).thenReturn(new PushTransport.Result(201,0));
    }
    byte[] bytes(java.math.BigInteger n){byte[] b=n.toByteArray(),r=new byte[32];int l=Math.min(32,b.length);System.arraycopy(b,b.length-l,r,32-l,l);return r;}
    UserEntity user(){UUID id=UUID.randomUUID();return users.save(new UserEntity(id,"Prueba",id+"@test.invalid","unused",Instant.now()));}
    HttpResponse<String> req(UserEntity user,String method,String path,Object body)throws Exception{
        var b=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/push/"+path)).header("Content-Type","application/json");
        if(user!=null)b.header("Authorization","Bearer "+jwt.issue(user));
        return http.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
    Map<String,Object> registration(String url){var body=new LinkedHashMap<String,Object>();body.put("endpoint",url);body.put("expirationTime",null);body.put("keys",Map.of("p256dh",point,"auth",auth));return body;}
    void subscribe()throws Exception{assertEquals(204,req(owner,"POST","subscriptions",registration(endpoint)).statusCode());}
    ProjectNotification event(){return new ProjectNotification(UUID.randomUUID(),project,owner.getId(),ProjectNotification.Type.PROJECT_ACTIVITY,"Privado","No mostrar estos detalles",null,null,"test:"+UUID.randomUUID(),false,Instant.now(),null);}
    void enqueue(ProjectNotification n){new TransactionTemplate(transactions).executeWithoutResult(s->{mongo.insert(n);events.publishEvent(new ProjectNotificationEvent(owner.getId(),"NOTIFICATION_CREATED",n));});}

    @Test void authenticationValidationAndOwnershipProtectCapabilityEndpoints()throws Exception{
        assertEquals(401,req(null,"POST","subscriptions",registration(endpoint)).statusCode());
        assertEquals(400,req(owner,"POST","subscriptions",registration("https://127.0.0.1/private")).statusCode());
        subscribe();subscribe();assertEquals(1,mongo.count(new Query(),PushSubscription.class));
        assertEquals(409,req(other,"POST","subscriptions",registration(endpoint)).statusCode());
        assertTrue(req(owner,"POST","subscriptions/status",Map.of("endpoint",endpoint)).body().contains("true"));
        assertTrue(req(other,"POST","subscriptions/status",Map.of("endpoint",endpoint)).body().contains("false"));
        assertEquals(204,req(other,"DELETE","subscriptions",Map.of("endpoint",endpoint)).statusCode());
        assertEquals(1,mongo.count(new Query(),PushSubscription.class));
        assertEquals(204,req(owner,"DELETE","subscriptions",Map.of("endpoint",endpoint)).statusCode());
        assertEquals(0,mongo.count(new Query(),PushSubscription.class));
    }
    @Test void commitQueuesExactlyOnceAndRollbackQueuesNothing()throws Exception{
        subscribe();ProjectNotification n=event();
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(transactions).executeWithoutResult(s->{mongo.insert(n);events.publishEvent(new ProjectNotificationEvent(owner.getId(),"NOTIFICATION_CREATED",n));throw new IllegalStateException("rollback");}));
        assertEquals(0,mongo.count(new Query(),PushDelivery.class));
        enqueue(n);
        new TransactionTemplate(transactions).executeWithoutResult(s->events.publishEvent(new ProjectNotificationEvent(owner.getId(),"NOTIFICATION_CREATED",n)));
        assertEquals(1,mongo.count(new Query(),PushDelivery.class));verifyNoInteractions(transport);
        assertTrue(worker.drainOne());
        verify(transport).send(any(),argThat(payload->!payload.contains("confidencial")&&!payload.contains("No mostrar")&&payload.contains(owner.getId().toString())));
        assertEquals("SENT",mongo.findAll(PushDelivery.class).getFirst().status());
    }
    @Test void vanishedSubscriptionIsRemovedWhileTransientProviderFailureIsRetried()throws Exception{
        subscribe();enqueue(event());when(transport.send(any(),anyString())).thenReturn(new PushTransport.Result(503,30));
        worker.drainOne();var job=mongo.findAll(PushDelivery.class).getFirst();assertEquals("QUEUED",job.status());assertEquals(1,job.attempts());assertTrue(job.dueAt().isAfter(Instant.now().plusSeconds(20)));
        mongo.updateFirst(Query.query(Criteria.where("id").is(job.id())),new Update().set("dueAt",Instant.now().minusSeconds(1)),PushDelivery.class);
        when(transport.send(any(),anyString())).thenReturn(new PushTransport.Result(410,0));worker.drainOne();
        assertEquals(0,mongo.count(new Query(),PushSubscription.class));assertEquals("DROPPED",mongo.findAll(PushDelivery.class).getFirst().status());
    }
    @Test void lostProjectAccessAndUnsubscribePreventQueuedDelivery()throws Exception{
        subscribe();enqueue(event());mongo.remove(Query.query(Criteria.where("id").is(project)),com.classforge.project.persistence.ProjectEntity.class);
        worker.drainOne();verifyNoInteractions(transport);assertEquals("DROPPED",mongo.findAll(PushDelivery.class).getFirst().status());
    }
    @Test void ownTestIsRateLimitedAndQuotaBoundsDeviceFanout()throws Exception{
        subscribe();assertEquals(202,req(owner,"POST","subscriptions/test",Map.of("endpoint",endpoint)).statusCode());
        assertEquals(429,req(owner,"POST","subscriptions/test",Map.of("endpoint",endpoint)).statusCode());
        assertEquals(404,req(other,"POST","subscriptions/test",Map.of("endpoint",endpoint)).statusCode());
        for(int i=1;i<10;i++)assertEquals(204,req(owner,"POST","subscriptions",registration(endpoint+i)).statusCode());
        assertEquals(429,req(owner,"POST","subscriptions",registration(endpoint+"extra")).statusCode());
    }
    @Test void eventsOutsideTransactionsAndUserUnsubscribeCannotSendQueuedMessages()throws Exception{
        assertDoesNotThrow(()->events.publishEvent(new ProjectNotificationEvent(owner.getId(),"NOTIFICATIONS_CHANGED",null)));
        subscribe();enqueue(event());req(owner,"DELETE","subscriptions",Map.of("endpoint",endpoint));
        worker.drainOne();verifyNoInteractions(transport);assertEquals("DROPPED",mongo.findAll(PushDelivery.class).getFirst().status());
    }
    @Test void expiredLeaseAtAttemptLimitIsTerminalWithoutSixthSend()throws Exception{
        subscribe();enqueue(event());var job=mongo.findAll(PushDelivery.class).getFirst();
        mongo.updateFirst(Query.query(Criteria.where("id").is(job.id())),new Update().set("status","PROCESSING").set("attempts",5).set("leaseUntil",Instant.now().minusSeconds(60)),PushDelivery.class);
        assertFalse(worker.drainOne());verifyNoInteractions(transport);assertEquals("FAILED",mongo.findAll(PushDelivery.class).getFirst().status());
    }
    @Test void pushSchedulerNeverBecomesTheWebsocketExecutor(){
        assertNotSame(context.getBean("pushScheduler"),context.getBean("clientOutboundChannelExecutor"));
        assertNotSame(context.getBean("pushScheduler"),context.getBean("clientInboundChannelExecutor"));
    }
}
