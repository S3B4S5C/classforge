package com.classforge.generation.spring.validation;

import com.classforge.generation.spring.model.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SpringGenerationModelValidator {
    public void validate(SpringGenerationModel model) {
        List<SpringGenerationDiagnostic> diagnostics = new ArrayList<>();
        if (model == null) fail(diagnostics, "model", "Spring model is required.");
        else {
            validConfig(model, diagnostics);
            Set<String> entityNames = new HashSet<>(), tables = new HashSet<>(), entityIds = new HashSet<>();
            for (SpringEntityModel entity : model.entities()) {
                if (entity == null) { fail(diagnostics, "entities", "Entity is required."); continue; }
                unique(entityNames, entity.className(), diagnostics, "entities.className", "Duplicate entity class name.");
                unique(tables, entity.tableName(), diagnostics, "entities.tableName", "Duplicate entity table name.");
                unique(entityIds, String.valueOf(entity.sourceClassId()), diagnostics, "entities.sourceClassId", "Duplicate source class id.");
                validateEntity(entity, diagnostics);
            }
            validateInheritance(model, diagnostics);
            validateRelations(model, diagnostics);
            validateRepositories(model, diagnostics);
        }
        if (!diagnostics.isEmpty()) throw new SpringGenerationException(diagnostics);
    }

    private void validConfig(SpringGenerationModel model, List<SpringGenerationDiagnostic> diagnostics) {
        try { new SpringGenerationConfig(model.artifactName(), model.basePackage()); }
        catch (SpringGenerationException exception) { diagnostics.addAll(exception.diagnostics()); }
        if (!validIdentifier(model.applicationClassName())) fail(diagnostics, "applicationClassName", "Application class name is invalid.");
    }
    private void validateEntity(SpringEntityModel entity, List<SpringGenerationDiagnostic> diagnostics) {
        Set<String> fields = new HashSet<>();
        for (SpringScalarFieldModel field : entity.scalarFields()) unique(fields, field.fieldName(), diagnostics, "entities.scalarFields", "Duplicate scalar or relation field name.");
        for (SpringDirectRelationModel relation : entity.directRelations()) unique(fields, relation.fieldName(), diagnostics, "entities.directRelations", "Duplicate scalar or relation field name.");
        for (SpringManyToManyRelationModel relation : entity.manyToManyRelations()) unique(fields, relation.fieldName(), diagnostics, "entities.manyToManyRelations", "Duplicate scalar or relation field name.");
        SpringEntityIdModel id = entity.id();
        if (id == null || id.fields().isEmpty()) { fail(diagnostics, "entities.id", "Entity id is required."); return; }
        if (id.kind() == SpringIdKind.SIMPLE && id.fields().size() != 1) fail(diagnostics, "entities.id", "Simple id must contain exactly one field.");
        if (id.kind() == SpringIdKind.COMPOSITE && (id.fields().size() < 2 || !validIdentifier(id.idClassName()))) fail(diagnostics, "entities.id", "Composite id must contain at least two fields and a valid id class name.");
        if (entity.inheritance() == null) fail(diagnostics, "entities.inheritance", "Inheritance is required.");
    }
    private void validateInheritance(SpringGenerationModel model, List<SpringGenerationDiagnostic> diagnostics) {
        Set<String> names = model.entities().stream().map(SpringEntityModel::className).collect(java.util.stream.Collectors.toSet());
        for (SpringEntityModel entity : model.entities()) {
            SpringInheritanceModel inheritance = entity.inheritance(); if (inheritance == null) continue;
            if (entity.id() == null) continue;
            if (inheritance.kind() == SpringInheritanceKind.JOINED_SUBCLASS) {
                if (!names.contains(inheritance.superClassName())) fail(diagnostics, "entities.inheritance.superClassName", "Subclass parent entity is missing.");
                if (inheritance.primaryKeyJoinColumns().isEmpty()) fail(diagnostics, "entities.inheritance.primaryKeyJoinColumns", "Subclass primary-key join is required.");
                if (entity.id().declaredByEntity()) fail(diagnostics, "entities.id", "Subclass id must be inherited.");
            } else if (!entity.id().declaredByEntity()) fail(diagnostics, "entities.id", "Root/non-inherited id must be declared by entity.");
        }
        java.util.Map<String, SpringEntityModel> entitiesByName = model.entities().stream().collect(java.util.stream.Collectors.toMap(SpringEntityModel::className, entity -> entity, (left, right) -> left));
        for (SpringEntityModel entity : model.entities()) {
            Set<String> seen = new HashSet<>(); String current = entity.className();
            while (current != null && seen.add(current)) {
                SpringEntityModel currentEntity = entitiesByName.get(current);
                current = currentEntity == null ? null : currentEntity.inheritance().superClassName();
            }
            if (current != null) fail(diagnostics, "entities.inheritance", "Inheritance contains a cycle.");
        }
    }
    private void validateRelations(SpringGenerationModel model, List<SpringGenerationDiagnostic> diagnostics) {
        Set<String> names = model.entities().stream().map(SpringEntityModel::className).collect(java.util.stream.Collectors.toSet());
        for (SpringEntityModel entity : model.entities()) {
            for (SpringDirectRelationModel relation : entity.directRelations()) {
                if (!names.contains(relation.targetEntityClassName()) || relation.joinColumns().isEmpty()) fail(diagnostics, "entities.directRelations", "Direct relation target and join columns are required.");
            }
            for (SpringManyToManyRelationModel relation : entity.manyToManyRelations()) {
                if (!names.contains(relation.targetEntityClassName()) || relation.joinTableName() == null || relation.joinTableName().isBlank() || relation.joinColumns().isEmpty() || relation.inverseJoinColumns().isEmpty()) fail(diagnostics, "entities.manyToManyRelations", "Many-to-many target, join table and columns are required.");
            }
        }
    }
    private void validateRepositories(SpringGenerationModel model, List<SpringGenerationDiagnostic> diagnostics) {
        if (model.repositories().size() != model.entities().size()) fail(diagnostics, "repositories", "Repository count must equal entity count.");
        Set<String> names = new HashSet<>();
        Map<String, Integer> repositoriesByEntity = new HashMap<>();
        for (SpringRepositoryModel repository : model.repositories()) {
            unique(names, repository.interfaceName(), diagnostics, "repositories.interfaceName", "Duplicate repository interface name.");
            SpringEntityModel entity = model.entities().stream().filter(e -> e.className().equals(repository.entityClassName())).findFirst().orElse(null);
            if (entity == null) { fail(diagnostics, "repositories.entityClassName", "Repository entity is missing."); continue; }
            repositoriesByEntity.merge(repository.entityClassName(), 1, Integer::sum);
            if (!Objects.equals(repository.idTypeSimpleName(), entity.id().typeSimpleName()) || !Objects.equals(repository.idTypeQualifiedName(), entity.id().typeQualifiedName()) || repository.generatedIdType() != (entity.id().kind() == SpringIdKind.COMPOSITE)) fail(diagnostics, "repositories.idType", "Repository id type does not match entity id.");
        }
        for (SpringEntityModel entity : model.entities()) if (repositoriesByEntity.getOrDefault(entity.className(), 0) != 1) fail(diagnostics, "repositories.entityClassName", "Each entity must have exactly one repository.");
    }
    private boolean validIdentifier(String value) { return value != null && !value.isEmpty() && Character.isJavaIdentifierStart(value.charAt(0)) && value.chars().skip(1).allMatch(c -> Character.isJavaIdentifierPart((char) c)); }
    private void unique(Set<String> values, String value, List<SpringGenerationDiagnostic> diagnostics, String path, String message) { if (value == null || !values.add(value)) fail(diagnostics, path, message); }
    private void fail(List<SpringGenerationDiagnostic> diagnostics, String path, String message) { diagnostics.add(new SpringGenerationDiagnostic(SpringGenerationDiagnosticCode.SPRING_MODEL_INVALID, null, path, message)); }
}
