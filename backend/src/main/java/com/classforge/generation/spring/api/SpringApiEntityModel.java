package com.classforge.generation.spring.api;

import com.classforge.generation.spring.model.SpringEntityIdModel;
import com.classforge.generation.spring.model.SpringScalarFieldModel;
import java.util.List;
import java.util.UUID;

public record SpringApiEntityModel(
        UUID sourceClassId,
        String className,
        String tableName,
        String repositoryName,
        SpringEntityIdModel id,
        List<SpringScalarFieldModel> scalarFields,
        List<SpringApiDirectRelationModel> directRelations,
        List<SpringApiManyRelationModel> manyToManyRelations,
        boolean authEntity,
        UUID usernameAttributeId,
        UUID passwordAttributeId
) {
    public SpringApiEntityModel {
        scalarFields = List.copyOf(scalarFields == null ? List.of() : scalarFields);
        directRelations = List.copyOf(directRelations == null ? List.of() : directRelations);
        manyToManyRelations = List.copyOf(manyToManyRelations == null ? List.of() : manyToManyRelations);
    }

    public List<SpringScalarFieldModel> responseFields() {
        if (!authEntity || passwordAttributeId == null) {
            return scalarFields;
        }
        return scalarFields.stream()
                .filter(field -> !passwordAttributeId.equals(field.sourceAttributeId()))
                .toList();
    }

    public boolean isUsernameAttribute(UUID attributeId) {
        return authEntity && usernameAttributeId != null && usernameAttributeId.equals(attributeId);
    }

    public boolean isPasswordAttribute(UUID attributeId) {
        return authEntity && passwordAttributeId != null && passwordAttributeId.equals(attributeId);
    }

    public SpringScalarFieldModel usernameField() {
        return scalarFields.stream()
                .filter(field -> usernameAttributeId != null && usernameAttributeId.equals(field.sourceAttributeId()))
                .findFirst()
                .orElse(null);
    }

    public SpringScalarFieldModel passwordField() {
        return scalarFields.stream()
                .filter(field -> passwordAttributeId != null && passwordAttributeId.equals(field.sourceAttributeId()))
                .findFirst()
                .orElse(null);
    }
}
