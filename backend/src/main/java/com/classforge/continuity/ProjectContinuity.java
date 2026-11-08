package com.classforge.continuity;

import com.classforge.activity.ProjectActivity;
import com.classforge.activity.ProjectActivitySummary;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProjectContinuity(UUID projectId, boolean firstVisit, Instant lastSeenAt,
        long lastSeenRevision, long currentRevision, long newActivityCount,
        List<ProjectActivity> activities, List<ProjectActivitySummary.Participant> participants,
        int page, int size, boolean hasMore) { }
