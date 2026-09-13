package com.classforge.generation.spring.api.contract;

public record SpringApiContractParameter(
        String name,
        SpringApiParameterLocation location,
        boolean required,
        String schemaType,
        String format,
        String example,
        String description
) { }
