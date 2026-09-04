package com.classforge.assistant.vision;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class VisionBenchmarkTileSignatureMerger {

    private VisionBenchmarkTileSignatureMerger() {
    }

    static Set<String> merge(Set<String> raw) {
        Map<String, TileClassSignature> classes = new LinkedHashMap<>();
        Map<String, String> relationships = new LinkedHashMap<>();
        Set<String> other = new LinkedHashSet<>();

        for (String signature : raw) {
            String[] parts = signature.split("\\|", -1);
            if (parts.length >= 3 && "CREATE_CLASS".equals(parts[0])) {
                String key = canonicalLabel(parts[1]);
                TileClassSignature value = classes.computeIfAbsent(
                        key,
                        ignored -> new TileClassSignature(parts[1])
                );
                if (!parts[2].isBlank()) {
                    for (String attribute : parts[2].split(",")) {
                        if (!attribute.isBlank()) {
                            value.attributes.add(attribute);
                        }
                    }
                }
                continue;
            }
            if (parts.length >= 6 && "CREATE_RELATIONSHIP".equals(parts[0])) {
                String normalized = canonicalRelationship(parts);
                String key = relationshipKey(normalized);
                String previous = relationships.get(key);
                if (previous == null || multiplicityScore(normalized) > multiplicityScore(previous)) {
                    relationships.put(key, normalized);
                }
                continue;
            }
            other.add(signature);
        }

        Set<String> merged = new LinkedHashSet<>();
        for (TileClassSignature value : classes.values()) {
            List<String> attributes = new ArrayList<>(value.attributes);
            Collections.sort(attributes);
            merged.add("CREATE_CLASS|" + value.name + "|" + String.join(",", attributes));
        }
        merged.addAll(relationships.values());
        merged.addAll(other);
        return sortedSet(merged);
    }

    private static String canonicalRelationship(String[] parts) {
        String source = parts[1];
        String target = parts[2];
        String type = parts[3];
        String sourceMultiplicity = parts[4];
        String targetMultiplicity = parts[5];

        if ("ASSOCIATION".equals(type)
                && canonicalLabel(source).compareTo(canonicalLabel(target)) > 0) {
            String swapName = source;
            source = target;
            target = swapName;
            String swapMultiplicity = sourceMultiplicity;
            sourceMultiplicity = targetMultiplicity;
            targetMultiplicity = swapMultiplicity;
        }
        return String.join(
                "|",
                "CREATE_RELATIONSHIP",
                source,
                target,
                type,
                sourceMultiplicity,
                targetMultiplicity
        );
    }

    private static String relationshipKey(String signature) {
        String[] parts = signature.split("\\|", -1);
        return parts[3]
                + "|" + canonicalLabel(parts[1])
                + "|" + canonicalLabel(parts[2]);
    }

    private static int multiplicityScore(String signature) {
        String[] parts = signature.split("\\|", -1);
        int score = 0;
        if (parts.length > 4 && !"null:null".equals(parts[4])) {
            score++;
        }
        if (parts.length > 5 && !"null:null".equals(parts[5])) {
            score++;
        }
        return score;
    }

    private static String canonicalLabel(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return normalized.trim().toLowerCase(Locale.ROOT);
    }

    private static Set<String> sortedSet(Set<String> input) {
        List<String> sorted = new ArrayList<>(input);
        Collections.sort(sorted);
        return new LinkedHashSet<>(sorted);
    }

    private static final class TileClassSignature {
        private final String name;
        private final Set<String> attributes = new LinkedHashSet<>();

        private TileClassSignature(String name) {
            this.name = name;
        }
    }
}
