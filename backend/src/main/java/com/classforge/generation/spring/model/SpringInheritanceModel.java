package com.classforge.generation.spring.model;

import java.util.List;

public record SpringInheritanceModel(SpringInheritanceKind kind, String superClassName,
                                    List<SpringJoinColumnModel> primaryKeyJoinColumns) {
    public SpringInheritanceModel { primaryKeyJoinColumns = List.copyOf(primaryKeyJoinColumns == null ? List.of() : primaryKeyJoinColumns); }
}
