package com.classforge.generation.spring.model;

import java.util.UUID;

public record SpringIdFieldModel(UUID sourceAttributeId, String fieldName, String columnName, SpringJavaType javaType) { }
