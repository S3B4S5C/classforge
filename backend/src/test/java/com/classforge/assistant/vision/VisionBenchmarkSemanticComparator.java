package com.classforge.assistant.vision;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class VisionBenchmarkSemanticComparator {

    private VisionBenchmarkSemanticComparator() {
    }

    static Comparison compare(Set<String> actual, Set<String> expected) {
        Set<String> actualClasses = classElements(actual);
        Set<String> expectedClasses = classElements(expected);
        Set<String> actualAttributes = attributeElements(actual);
        Set<String> expectedAttributes = attributeElements(expected);
        Set<String> actualRelationships = relationshipElements(actual);
        Set<String> expectedRelationships = relationshipElements(expected);
        Set<String> actualMultiplicities = multiplicityElements(actual);
        Set<String> expectedMultiplicities = multiplicityElements(expected);

        return new Comparison(
                stats(actualClasses, expectedClasses),
                stats(actualAttributes, expectedAttributes),
                stats(actualRelationships, expectedRelationships),
                stats(actualMultiplicities, expectedMultiplicities),
                canonicalSignatures(actual).equals(canonicalSignatures(expected))
        );
    }

    private static ElementStats stats(Set<String> actual, Set<String> expected) {
        Set<String> matched = new LinkedHashSet<>(actual);
        matched.retainAll(expected);

        Set<String> unexpected = new LinkedHashSet<>(actual);
        unexpected.removeAll(expected);

        return new ElementStats(matched.size(), expected.size(), unexpected.size());
    }

    private static Set<String> canonicalSignatures(Set<String> signatures) {
        Set<String> result = new LinkedHashSet<>();
        for (String signature : signatures) {
            String[] parts = signature.split("\\|", -1);
            if (parts.length >= 3
                    && ("CREATE_CLASS".equals(parts[0]) || "ADD_ATTRIBUTES".equals(parts[0]))) {
                List<String> attributes = new ArrayList<>();
                if (!parts[2].isBlank()) {
                    for (String attribute : parts[2].split(",")) {
                        attributes.add(canonicalAttribute(attribute));
                    }
                }
                Collections.sort(attributes);
                result.add(parts[0] + "|" + normalize(parts[1]) + "|" + String.join(",", attributes));
                continue;
            }

            Relationship relationship = relationship(parts);
            if (relationship != null) {
                result.add(relationship.fullKey());
                continue;
            }

            result.add(normalize(signature));
        }
        return sortedSet(result);
    }

    private static Set<String> classElements(Set<String> signatures) {
        Set<String> result = new LinkedHashSet<>();
        for (String signature : signatures) {
            String[] parts = signature.split("\\|", -1);
            if (parts.length >= 2
                    && ("CREATE_CLASS".equals(parts[0]) || "ADD_ATTRIBUTES".equals(parts[0]))) {
                result.add(parts[0] + "|" + normalize(parts[1]));
            }
        }
        return sortedSet(result);
    }

    private static Set<String> attributeElements(Set<String> signatures) {
        Set<String> result = new LinkedHashSet<>();
        for (String signature : signatures) {
            String[] parts = signature.split("\\|", -1);
            if (parts.length >= 3
                    && ("CREATE_CLASS".equals(parts[0]) || "ADD_ATTRIBUTES".equals(parts[0]))
                    && !parts[2].isBlank()) {
                String className = normalize(parts[1]);
                for (String attribute : parts[2].split(",")) {
                    result.add(className + "|" + canonicalAttribute(attribute));
                }
            }
        }
        return sortedSet(result);
    }

    private static Set<String> relationshipElements(Set<String> signatures) {
        Set<String> result = new LinkedHashSet<>();
        for (String signature : signatures) {
            Relationship relationship = relationship(signature.split("\\|", -1));
            if (relationship != null) {
                result.add(relationship.topologyKey());
            }
        }
        return sortedSet(result);
    }

    private static Set<String> multiplicityElements(Set<String> signatures) {
        Set<String> result = new LinkedHashSet<>();
        for (String signature : signatures) {
            Relationship relationship = relationship(signature.split("\\|", -1));
            if (relationship != null) {
                result.addAll(relationship.multiplicityKeys());
            }
        }
        return sortedSet(result);
    }

    private static Relationship relationship(String[] parts) {
        if (parts.length < 6 || !"CREATE_RELATIONSHIP".equals(parts[0])) {
            return null;
        }

        String source = normalize(parts[1]);
        String target = normalize(parts[2]);
        String type = parts[3].trim().toUpperCase(Locale.ROOT);
        String sourceMultiplicity = parts[4].trim();
        String targetMultiplicity = parts[5].trim();

        if ("ASSOCIATION".equals(type) && source.compareTo(target) > 0) {
            return new Relationship(
                    target,
                    source,
                    type,
                    targetMultiplicity,
                    sourceMultiplicity,
                    true
            );
        }

        return new Relationship(
                source,
                target,
                type,
                sourceMultiplicity,
                targetMultiplicity,
                "ASSOCIATION".equals(type)
        );
    }

    private static String canonicalAttribute(String attribute) {
        String trimmed = attribute == null ? "" : attribute.trim();
        int separator = trimmed.lastIndexOf(':');
        if (separator < 0) {
            return normalize(trimmed);
        }
        String name = normalize(trimmed.substring(0, separator));
        String type = trimmed.substring(separator + 1).trim().toUpperCase(Locale.ROOT);
        return name + ":" + type;
    }

    private static String normalize(String value) {
        String decomposed = Normalizer.normalize(
                value == null ? "" : value,
                Normalizer.Form.NFD
        );
        return decomposed
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static Set<String> sortedSet(Set<String> input) {
        List<String> sorted = new ArrayList<>(input);
        Collections.sort(sorted);
        return new LinkedHashSet<>(sorted);
    }

    record ElementStats(int matched, int expected, int unexpected) {
        boolean exact() {
            return matched == expected && unexpected == 0;
        }
    }

    record Comparison(
            ElementStats classes,
            ElementStats attributes,
            ElementStats relationships,
            ElementStats multiplicities,
            boolean semanticExact
    ) {
    }

    private record Relationship(
            String source,
            String target,
            String type,
            String sourceMultiplicity,
            String targetMultiplicity,
            boolean undirected
    ) {
        String topologyKey() {
            return "CREATE_RELATIONSHIP|" + source + "|" + target + "|" + type;
        }

        String fullKey() {
            return topologyKey() + "|" + sourceMultiplicity + "|" + targetMultiplicity;
        }

        Set<String> multiplicityKeys() {
            Set<String> result = new LinkedHashSet<>();
            if (!"null:null".equals(sourceMultiplicity)) {
                result.add(endpointMultiplicityKey("source", source, sourceMultiplicity));
            }
            if (!"null:null".equals(targetMultiplicity)) {
                result.add(endpointMultiplicityKey("target", target, targetMultiplicity));
            }
            return result;
        }

        private String endpointMultiplicityKey(String role, String endpoint, String multiplicity) {
            if (undirected) {
                return topologyKey() + "|endpoint=" + endpoint + "|multiplicity=" + multiplicity;
            }
            return topologyKey() + "|" + role + "=" + endpoint + "|multiplicity=" + multiplicity;
        }
    }
}
