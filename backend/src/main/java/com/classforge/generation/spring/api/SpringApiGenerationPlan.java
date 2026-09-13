package com.classforge.generation.spring.api;

import com.classforge.generation.spring.application.SpringBootGenerationMode;
import java.util.List;

public record SpringApiGenerationPlan(
        SpringBootGenerationMode mode,
        List<SpringApiEntityModel> entities
) {
    public SpringApiGenerationPlan {
        entities = List.copyOf(entities == null ? List.of() : entities);
    }

    public static SpringApiGenerationPlan none() {
        return new SpringApiGenerationPlan(null, List.of());
    }

    public boolean enabled() {
        return mode != null;
    }

    public boolean authEnabled() {
        return mode == SpringBootGenerationMode.AUTH_INFORMATION_SYSTEM;
    }

    public SpringApiEntityModel authEntity() {
        return entities.stream().filter(SpringApiEntityModel::authEntity).findFirst().orElse(null);
    }
}
