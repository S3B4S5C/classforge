package com.classforge.notification.push;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import java.time.*;
@Configuration
@EnableConfigurationProperties(PushProperties.class)
public class PushConfiguration {
    @Bean("pushClock") Clock clock(){return Clock.systemUTC();}
    @Bean(defaultCandidate=false) org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler pushScheduler(){
        var scheduler=new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);scheduler.setThreadNamePrefix("classforge-push-");
        // Interrupt on shutdown; leased deliveries are safely retried by the next process.
        scheduler.setWaitForTasksToCompleteOnShutdown(false);scheduler.setAwaitTerminationSeconds(10);return scheduler;
    }
    @Bean org.springframework.beans.factory.InitializingBean pushIndexes(MongoTemplate mongo,PushProperties properties){
        return ()->{if(properties.enabled()){
            mongo.indexOps(PushSubscription.class).ensureIndex(new Index().on("userId",Sort.Direction.ASC));
            mongo.indexOps(PushDelivery.class).ensureIndex(new Index().on("status",Sort.Direction.ASC).on("dueAt",Sort.Direction.ASC));
            mongo.indexOps(PushDelivery.class).ensureIndex(new Index().on("expiresAt",Sort.Direction.ASC).expire(Duration.ZERO));
        }};
    }
}
