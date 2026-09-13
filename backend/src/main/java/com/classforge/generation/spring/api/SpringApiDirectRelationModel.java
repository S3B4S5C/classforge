package com.classforge.generation.spring.api;

import com.classforge.generation.spring.model.SpringEntityIdModel;

public record SpringApiDirectRelationModel(
        String fieldName,
        String targetEntityClassName,
        String targetRepositoryName,
        SpringEntityIdModel targetId,
        boolean optional
) { }
