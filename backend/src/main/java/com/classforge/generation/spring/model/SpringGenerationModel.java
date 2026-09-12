package com.classforge.generation.spring.model;

import java.util.Comparator;
import java.util.List;

public record SpringGenerationModel(String generatorSchemaVersion, String artifactName, String basePackage,
                                    String applicationClassName, String javaVersion, String springBootVersion,
                                    String gradleVersion, List<SpringEntityModel> entities,
                                    List<SpringRepositoryModel> repositories) {
    public static final String GENERATOR_SCHEMA_VERSION = "1.0";
    public static final String JAVA_VERSION = "21";
    public static final String SPRING_BOOT_VERSION = "4.0.8";
    public static final String GRADLE_VERSION = "9.2.0";
    public SpringGenerationModel {
        entities = List.copyOf((entities == null ? List.<SpringEntityModel>of() : entities).stream()
                .sorted(Comparator.comparing(SpringEntityModel::className).thenComparing(SpringEntityModel::tableName).thenComparing(e -> e.sourceClassId().toString())).toList());
        repositories = List.copyOf((repositories == null ? List.<SpringRepositoryModel>of() : repositories).stream()
                .sorted(Comparator.comparing(SpringRepositoryModel::interfaceName).thenComparing(SpringRepositoryModel::entityClassName)).toList());
    }
}
