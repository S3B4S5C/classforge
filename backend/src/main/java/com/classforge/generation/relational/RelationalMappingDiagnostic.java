package com.classforge.generation.relational;

import java.util.UUID;

public record RelationalMappingDiagnostic(RelationalMappingDiagnosticCode code, UUID elementId,
                                          String path, String message) { }
