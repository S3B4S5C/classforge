package com.classforge.project.access;

public enum ProjectAccessRole {
    OWNER,
    EDITOR,
    NONE;

    public boolean canRead() {
        return this != NONE;
    }

    public boolean canEdit() {
        return this == OWNER || this == EDITOR;
    }

    public boolean isOwner() {
        return this == OWNER;
    }
}
