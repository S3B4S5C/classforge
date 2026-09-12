package com.classforge.generation.spring.validation;

import java.util.UUID;

public record SpringGenerationDiagnostic(SpringGenerationDiagnosticCode code, UUID sourceElementId,
                                         String path, String message) { }
