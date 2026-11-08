package com.classforge.notification;

import java.util.UUID;

public record ProjectNotificationEvent(UUID recipientUserId, String type, ProjectNotification notification) { }
