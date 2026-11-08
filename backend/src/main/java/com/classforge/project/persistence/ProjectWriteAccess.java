package com.classforge.project.persistence;
import java.util.*;
public interface ProjectWriteAccess { Optional<ProjectEntity> acquireWrite(UUID id); }
