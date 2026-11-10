package com.classforge.notification.push;
public interface PushTransport {
    record Result(int status,int retryAfterSeconds) { }
    Result send(PushSubscription subscription,String payload) throws Exception;
}
