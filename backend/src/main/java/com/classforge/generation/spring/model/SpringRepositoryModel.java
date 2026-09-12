package com.classforge.generation.spring.model;

public record SpringRepositoryModel(String interfaceName, String entityClassName, String idTypeSimpleName,
                                    String idTypeQualifiedName, boolean generatedIdType) { }
