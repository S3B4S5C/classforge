package com.classforge.projectassistant.persistence;
import java.util.*;
public interface ProjectAiRequestWriteAccess { Optional<ProjectAiMessageEntity> lockRequest(UUID project, UUID request); }
