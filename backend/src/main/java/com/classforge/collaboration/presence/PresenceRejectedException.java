package com.classforge.collaboration.presence;

public class PresenceRejectedException
        extends RuntimeException {

    public PresenceRejectedException(
            String message
    ) {
        super(message);
    }
}