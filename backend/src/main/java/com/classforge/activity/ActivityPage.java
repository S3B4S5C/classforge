package com.classforge.activity;

import org.springframework.data.domain.Page;
import java.util.List;
import java.util.function.Function;

public record ActivityPage<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
    public static <E,T> ActivityPage<T> from(Page<E> page, Function<E,T> convert) {
        return new ActivityPage<>(page.stream().map(convert).toList(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    public static void validate(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page debe ser >= 0; size debe estar entre 1 y 100.");
        }
    }
}
