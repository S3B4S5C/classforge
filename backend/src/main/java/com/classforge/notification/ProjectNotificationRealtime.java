package com.classforge.notification;

import org.slf4j.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component
public class ProjectNotificationRealtime {
    private static final Logger log = LoggerFactory.getLogger(ProjectNotificationRealtime.class);
    private final SimpMessagingTemplate messaging;
    public ProjectNotificationRealtime(SimpMessagingTemplate messaging) { this.messaging = messaging; }
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deliver(ProjectNotificationEvent event) {
        try { messaging.convertAndSendToUser(event.recipientUserId().toString(), "/queue/notifications", event); }
        catch (RuntimeException failure) { log.warn("La notificación quedó persistida; se recuperará por HTTP para {}", event.recipientUserId(), failure); }
    }
}
