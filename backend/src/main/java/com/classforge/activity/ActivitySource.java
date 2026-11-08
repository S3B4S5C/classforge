package com.classforge.activity;

/** Origin is descriptive metadata, never an authorization decision. */
public enum ActivitySource {
    MANUAL, ASSISTANT_TEXT, ASSISTANT_VOICE, VISION, XMI, SYSTEM;

    public static ActivitySource orManual(ActivitySource source) {
        return source == null ? MANUAL : source;
    }
}
