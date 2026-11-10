package com.classforge.notification.push;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.UUID;
@Document("push_deliveries")
public record PushDelivery(@Id UUID id,String subscriptionId,UUID generation,UUID userId,UUID projectId,
        String payload,Instant createdAt,Instant expiresAt,Instant dueAt,String status,int attempts,Instant leaseUntil,
        UUID claimToken,Integer lastStatus) { }
