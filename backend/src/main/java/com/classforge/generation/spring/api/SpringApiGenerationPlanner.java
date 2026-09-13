package com.classforge.generation.spring.api;

import com.classforge.generation.spring.application.SpringBootGenerationMode;
import com.classforge.generation.spring.application.SpringBootGenerationOptions;
import com.classforge.generation.spring.model.SpringDirectRelationModel;
import com.classforge.generation.spring.model.SpringEntityModel;
import com.classforge.generation.spring.model.SpringGenerationModel;
import com.classforge.generation.spring.model.SpringManyToManyRelationModel;
import com.classforge.generation.spring.model.SpringRepositoryModel;
import com.classforge.generation.spring.model.SpringScalarFieldModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class SpringApiGenerationPlanner {
    public SpringApiGenerationPlan plan(SpringGenerationModel model, SpringBootGenerationOptions options) {
        Objects.requireNonNull(model, "model is required");
        SpringBootGenerationOptions effectiveOptions =
                options == null ? SpringBootGenerationOptions.strict() : options;
        if (!effectiveOptions.apiEnabled()) {
            return SpringApiGenerationPlan.none();
        }

        Map<String, SpringEntityModel> entityByClass = new HashMap<>();
        model.entities().forEach(entity -> entityByClass.put(entity.className(), entity));
        Map<String, SpringRepositoryModel> repositoryByEntity = new HashMap<>();
        model.repositories().forEach(repository -> repositoryByEntity.put(repository.entityClassName(), repository));

        List<SpringApiGenerationDiagnostic> diagnostics = new ArrayList<>();
        SpringEntityModel authEntity = null;
        SpringScalarFieldModel username = null;
        SpringScalarFieldModel password = null;

        if (effectiveOptions.mode() == SpringBootGenerationMode.AUTH_INFORMATION_SYSTEM) {
            if (effectiveOptions.authClassId() == null || effectiveOptions.usernameAttributeId() == null || effectiveOptions.passwordAttributeId() == null) {
                diagnostics.add(new SpringApiGenerationDiagnostic(
                        SpringApiGenerationDiagnosticCode.AUTH_SELECTION_REQUIRED,
                        null,
                        "auth",
                        "Auth mode requires an authentication class, username attribute and password attribute."
                ));
            } else {
                authEntity = model.entities().stream()
                        .filter(entity -> effectiveOptions.authClassId().equals(entity.sourceClassId()))
                        .findFirst()
                        .orElse(null);
                if (authEntity == null) {
                    diagnostics.add(new SpringApiGenerationDiagnostic(
                            SpringApiGenerationDiagnosticCode.AUTH_ENTITY_NOT_FOUND,
                            effectiveOptions.authClassId(),
                            "authClassId",
                            "The selected authentication class is not present in the generated entity model."
                    ));
                } else {
                    username = authEntity.scalarFields().stream()
                            .filter(field -> effectiveOptions.usernameAttributeId().equals(field.sourceAttributeId()))
                            .findFirst().orElse(null);
                    password = authEntity.scalarFields().stream()
                            .filter(field -> effectiveOptions.passwordAttributeId().equals(field.sourceAttributeId()))
                            .findFirst().orElse(null);
                    if (username == null) {
                        diagnostics.add(new SpringApiGenerationDiagnostic(
                                SpringApiGenerationDiagnosticCode.AUTH_USERNAME_ATTRIBUTE_NOT_FOUND,
                                effectiveOptions.usernameAttributeId(),
                                "usernameAttributeId",
                                "The selected username attribute must be declared by the selected authentication class."
                        ));
                    }
                    if (password == null) {
                        diagnostics.add(new SpringApiGenerationDiagnostic(
                                SpringApiGenerationDiagnosticCode.AUTH_PASSWORD_ATTRIBUTE_NOT_FOUND,
                                effectiveOptions.passwordAttributeId(),
                                "passwordAttributeId",
                                "The selected password attribute must be declared by the selected authentication class."
                        ));
                    }
                    if (effectiveOptions.usernameAttributeId().equals(effectiveOptions.passwordAttributeId())) {
                        diagnostics.add(new SpringApiGenerationDiagnostic(
                                SpringApiGenerationDiagnosticCode.AUTH_ATTRIBUTES_MUST_DIFFER,
                                effectiveOptions.usernameAttributeId(),
                                "auth",
                                "Username and password must be different attributes."
                        ));
                    }
                    if ((username != null && !"String".equals(username.javaType().simpleName()))
                            || (password != null && !"String".equals(password.javaType().simpleName()))) {
                        diagnostics.add(new SpringApiGenerationDiagnostic(
                                SpringApiGenerationDiagnosticCode.AUTH_ATTRIBUTES_MUST_BE_STRING,
                                authEntity.sourceClassId(),
                                "auth",
                                "Username and password attributes must both use UML STRING."
                        ));
                    }
                }
            }
        }

        if (!diagnostics.isEmpty()) {
            throw new SpringApiGenerationException(diagnostics);
        }

        List<SpringApiEntityModel> apiEntities = new ArrayList<>();
        for (SpringEntityModel entity : model.entities()) {
            SpringRepositoryModel repository = repositoryByEntity.get(entity.className());
            if (repository == null) {
                diagnostics.add(new SpringApiGenerationDiagnostic(
                        SpringApiGenerationDiagnosticCode.API_MODEL_INVALID,
                        entity.sourceClassId(),
                        "repositories",
                        "No repository was generated for entity " + entity.className() + "."
                ));
                continue;
            }
            List<SpringScalarFieldModel> fields = effectiveFields(entity, entityByClass);
            List<SpringDirectRelationModel> direct = effectiveDirectRelations(entity, entityByClass);
            List<SpringManyToManyRelationModel> many = effectiveManyRelations(entity, entityByClass);
            List<SpringApiDirectRelationModel> apiDirect = direct.stream().map(relation -> {
                SpringEntityModel target = entityByClass.get(relation.targetEntityClassName());
                SpringRepositoryModel targetRepository = repositoryByEntity.get(relation.targetEntityClassName());
                if (target == null || targetRepository == null) {
                    diagnostics.add(new SpringApiGenerationDiagnostic(
                            SpringApiGenerationDiagnosticCode.API_MODEL_INVALID,
                            relation.sourceRelationshipId(),
                            "relations",
                            "Relation target cannot be exposed through CRUD: " + relation.targetEntityClassName()
                    ));
                    return null;
                }
                return new SpringApiDirectRelationModel(
                        relation.fieldName(),
                        target.className(),
                        targetRepository.interfaceName(),
                        target.id(),
                        relation.optional()
                );
            }).filter(Objects::nonNull).toList();
            List<SpringApiManyRelationModel> apiMany = many.stream().map(relation -> {
                SpringEntityModel target = entityByClass.get(relation.targetEntityClassName());
                SpringRepositoryModel targetRepository = repositoryByEntity.get(relation.targetEntityClassName());
                if (target == null || targetRepository == null) {
                    diagnostics.add(new SpringApiGenerationDiagnostic(
                            SpringApiGenerationDiagnosticCode.API_MODEL_INVALID,
                            relation.sourceRelationshipId(),
                            "relations",
                            "Many-to-many target cannot be exposed through CRUD: " + relation.targetEntityClassName()
                    ));
                    return null;
                }
                return new SpringApiManyRelationModel(
                        relation.fieldName(),
                        target.className(),
                        targetRepository.interfaceName(),
                        target.id()
                );
            }).filter(Objects::nonNull).toList();
            boolean isAuth = authEntity != null && authEntity.sourceClassId().equals(entity.sourceClassId());
            apiEntities.add(new SpringApiEntityModel(
                    entity.sourceClassId(),
                    entity.className(),
                    entity.tableName(),
                    repository.interfaceName(),
                    entity.id(),
                    fields,
                    apiDirect,
                    apiMany,
                    isAuth,
                    isAuth ? effectiveOptions.usernameAttributeId() : null,
                    isAuth ? effectiveOptions.passwordAttributeId() : null
            ));
        }
        if (!diagnostics.isEmpty()) {
            throw new SpringApiGenerationException(diagnostics);
        }
        apiEntities.sort(Comparator.comparing(SpringApiEntityModel::className));
        return new SpringApiGenerationPlan(effectiveOptions.mode(), apiEntities);
    }

    private List<SpringScalarFieldModel> effectiveFields(
            SpringEntityModel entity,
            Map<String, SpringEntityModel> entityByClass
    ) {
        Map<UUID, SpringScalarFieldModel> fields = new LinkedHashMap<>();
        collectFields(entity, entityByClass, fields);
        return fields.values().stream()
                .sorted(Comparator.comparing(SpringScalarFieldModel::fieldName))
                .toList();
    }

    private void collectFields(
            SpringEntityModel entity,
            Map<String, SpringEntityModel> entityByClass,
            Map<UUID, SpringScalarFieldModel> fields
    ) {
        if (entity.inheritance().superClassName() != null) {
            SpringEntityModel parent = entityByClass.get(entity.inheritance().superClassName());
            if (parent != null) {
                collectFields(parent, entityByClass, fields);
            }
        }
        entity.scalarFields().forEach(field -> fields.put(field.sourceAttributeId(), field));
    }

    private List<SpringDirectRelationModel> effectiveDirectRelations(
            SpringEntityModel entity,
            Map<String, SpringEntityModel> entityByClass
    ) {
        List<SpringDirectRelationModel> result = new ArrayList<>();
        collectDirect(entity, entityByClass, result);
        return result.stream().sorted(Comparator.comparing(SpringDirectRelationModel::fieldName)).toList();
    }

    private void collectDirect(SpringEntityModel entity, Map<String, SpringEntityModel> entityByClass, List<SpringDirectRelationModel> result) {
        if (entity.inheritance().superClassName() != null) {
            SpringEntityModel parent = entityByClass.get(entity.inheritance().superClassName());
            if (parent != null) collectDirect(parent, entityByClass, result);
        }
        result.addAll(entity.directRelations());
    }

    private List<SpringManyToManyRelationModel> effectiveManyRelations(
            SpringEntityModel entity,
            Map<String, SpringEntityModel> entityByClass
    ) {
        List<SpringManyToManyRelationModel> result = new ArrayList<>();
        collectMany(entity, entityByClass, result);
        return result.stream().sorted(Comparator.comparing(SpringManyToManyRelationModel::fieldName)).toList();
    }

    private void collectMany(SpringEntityModel entity, Map<String, SpringEntityModel> entityByClass, List<SpringManyToManyRelationModel> result) {
        if (entity.inheritance().superClassName() != null) {
            SpringEntityModel parent = entityByClass.get(entity.inheritance().superClassName());
            if (parent != null) collectMany(parent, entityByClass, result);
        }
        result.addAll(entity.manyToManyRelations());
    }
}
