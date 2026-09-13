package ${model.basePackage}.api;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CrudSupport {
    private CrudSupport() { }

    public static <T> PageResponse<T> query(
            List<T> source,
            String q,
            Map<String, String> filters,
            String sort,
            String direction,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        List<T> filtered = new ArrayList<>(source == null ? List.of() : source);
        String normalizedQ = normalize(q);
        if (!normalizedQ.isEmpty()) {
            filtered.removeIf(item -> values(item).values().stream()
                    .map(CrudSupport::normalizeObject)
                    .noneMatch(value -> value.contains(normalizedQ)));
        }
        if (filters != null && !filters.isEmpty()) {
            filtered.removeIf(item -> {
                Map<String, Object> values = values(item);
                for (Map.Entry<String, String> filter : filters.entrySet()) {
                    Object actual = values.get(filter.getKey());
                    if (actual == null || !normalizeObject(actual).contains(normalize(filter.getValue()))) {
                        return true;
                    }
                }
                return false;
            });
        }
        if (sort != null && !sort.isBlank()) {
            Comparator<T> comparator = Comparator.comparing(
                    item -> normalizeObject(values(item).get(sort)),
                    Comparator.nullsFirst(String::compareTo)
            );
            if ("desc".equalsIgnoreCase(direction)) comparator = comparator.reversed();
            filtered.sort(comparator);
        }
        long total = filtered.size();
        int from = Math.min(safePage * safeSize, filtered.size());
        int to = Math.min(from + safeSize, filtered.size());
        int pages = total == 0 ? 0 : (int) Math.ceil((double) total / safeSize);
        return new PageResponse<>(List.copyOf(filtered.subList(from, to)), safePage, safeSize, total, pages);
    }

    public static Map<String, String> extractFilters(Map<String, String> params) {
        Map<String, String> filters = new LinkedHashMap<>();
        if (params == null) return filters;
        params.forEach((key, value) -> {
            if (key.startsWith("filter.") && key.length() > 7) {
                filters.put(key.substring(7), value);
            }
        });
        return filters;
    }

    private static Map<String, Object> values(Object record) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (record == null || !record.getClass().isRecord()) return values;
        for (RecordComponent component : record.getClass().getRecordComponents()) {
            try {
                values.put(component.getName(), component.getAccessor().invoke(record));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Could not inspect generated DTO field " + component.getName(), exception);
            }
        }
        return values;
    }

    private static String normalizeObject(Object value) {
        return value == null ? "" : normalize(String.valueOf(value));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
