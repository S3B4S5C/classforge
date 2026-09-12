package com.classforge.generation.spring.model;

import java.util.UUID;

public record SpringScalarFieldModel(UUID sourceAttributeId, String logicalName, String fieldName,
                                     String columnName, SpringJavaType javaType, boolean nullable,
                                     boolean identifier) { }
