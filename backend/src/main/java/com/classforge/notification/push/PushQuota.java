package com.classforge.notification.push;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.UUID;
@Document("push_quotas")
public record PushQuota(@Id UUID id,int count) { }
