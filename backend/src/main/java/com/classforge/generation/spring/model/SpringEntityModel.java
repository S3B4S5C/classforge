package com.classforge.generation.spring.model;

import java.util.List;
import java.util.UUID;

public record SpringEntityModel(UUID sourceClassId, String logicalName, String className, String tableName,
                                SpringInheritanceModel inheritance, SpringEntityIdModel id,
                                List<SpringScalarFieldModel> scalarFields,
                                List<SpringDirectRelationModel> directRelations,
                                List<SpringManyToManyRelationModel> manyToManyRelations,
                                List<SpringUniqueConstraintModel> uniqueConstraints,
                                List<SpringIndexModel> indexes) {
    public SpringEntityModel {
        scalarFields = List.copyOf(scalarFields == null ? List.of() : scalarFields);
        directRelations = List.copyOf(directRelations == null ? List.of() : directRelations);
        manyToManyRelations = List.copyOf(manyToManyRelations == null ? List.of() : manyToManyRelations);
        uniqueConstraints = List.copyOf(uniqueConstraints == null ? List.of() : uniqueConstraints);
        indexes = List.copyOf(indexes == null ? List.of() : indexes);
    }
}
