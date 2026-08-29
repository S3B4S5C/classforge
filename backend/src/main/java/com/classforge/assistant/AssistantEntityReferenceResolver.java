package com.classforge.assistant;

import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlAttribute;
import com.classforge.project.domain.document.UmlClass;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class AssistantEntityReferenceResolver {

    private static final Pattern DIACRITICS =
            Pattern.compile("\\p{M}+");

    private static final Pattern NON_WORD =
            Pattern.compile("[^a-z0-9]+");

    private static final Pattern CAMEL_CASE =
            Pattern.compile("(?<=[a-z0-9])(?=[A-Z])");

    private static final double MIN_MATCH_SCORE = 0.82d;
    private static final double MIN_DOMINANCE_MARGIN = 0.07d;

    public List<ResolvedClassReference> resolveMentions(
            String userText,
            ProjectDocument document
    ) {
        if (
                userText == null
                        || userText.isBlank()
                        || document == null
                        || document.umlModel() == null
                        || document.umlModel().classes().isEmpty()
        ) {
            return List.of();
        }

        List<String> tokens =
                tokenize(
                        normalizeForMatch(
                                userText
                        )
                );

        if (tokens.isEmpty()) {
            return List.of();
        }

        List<WindowCandidate> candidates =
                new ArrayList<>();

        for (
                UmlClass umlClass
                : document.umlModel().classes()
        ) {
            String canonical =
                    normalizeForMatch(
                            splitCamelCase(
                                    umlClass.name()
                            )
                    );

            List<String> classTokens =
                    tokenize(
                            canonical
                    );

            if (classTokens.isEmpty()) {
                continue;
            }

            int preferredWindow =
                    classTokens.size();

            int minWindow =
                    Math.max(
                            1,
                            preferredWindow - 1
                    );

            int maxWindow =
                    Math.min(
                            tokens.size(),
                            preferredWindow + 1
                    );

            for (
                    int windowSize = minWindow;
                    windowSize <= maxWindow;
                    windowSize++
            ) {
                for (
                        int start = 0;
                        start + windowSize <= tokens.size();
                        start++
                ) {
                    String observed =
                            String.join(
                                    " ",
                                    tokens.subList(
                                            start,
                                            start + windowSize
                                    )
                            );

                    double score =
                            similarity(
                                    observed,
                                    canonical
                            );

                    if (
                            score >= thresholdFor(
                                    canonical
                            )
                    ) {
                        candidates.add(
                                new WindowCandidate(
                                        start,
                                        start + windowSize,
                                        observed,
                                        umlClass,
                                        score
                                )
                        );
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            return List.of();
        }

        Map<String, List<WindowCandidate>> byWindow =
                new LinkedHashMap<>();

        for (
                WindowCandidate candidate
                : candidates
        ) {
            String key =
                    candidate.start()
                            + ":"
                            + candidate.end();

            byWindow.computeIfAbsent(
                            key,
                            ignored ->
                                    new ArrayList<>()
                    )
                    .add(
                            candidate
                    );
        }

        List<WindowCandidate> winners =
                new ArrayList<>();

        for (
                List<WindowCandidate> windowCandidates
                : byWindow.values()
        ) {
            windowCandidates.sort(
                    Comparator.comparingDouble(
                                    WindowCandidate::score
                            )
                            .reversed()
                            .thenComparing(
                                    candidate ->
                                            candidate.umlClass()
                                                    .name()
                            )
            );

            WindowCandidate best =
                    windowCandidates.getFirst();

            double secondScore =
                    windowCandidates.size() > 1
                            ? windowCandidates.get(1)
                                    .score()
                            : 0.0d;

            if (
                    best.score() >= 0.999d
                            || best.score() - secondScore
                            >= MIN_DOMINANCE_MARGIN
            ) {
                winners.add(
                        best
                );
            }
        }

        winners.sort(
                Comparator.comparingInt(
                                WindowCandidate::start
                        )
                        .thenComparing(
                                Comparator.comparingDouble(
                                                WindowCandidate::score
                                        )
                                        .reversed()
                        )
        );

        Set<UUID> emittedIds =
                new LinkedHashSet<>();

        List<ResolvedClassReference> resolved =
                new ArrayList<>();

        int lastEnd = -1;

        for (
                WindowCandidate winner
                : winners
        ) {
            if (
                    winner.start() < lastEnd
                            || emittedIds.contains(
                            winner.umlClass()
                                    .id()
                    )
            ) {
                continue;
            }

            emittedIds.add(
                    winner.umlClass()
                            .id()
            );

            resolved.add(
                    new ResolvedClassReference(
                            winner.umlClass()
                                    .id(),
                            winner.umlClass()
                                    .name(),
                            winner.observed(),
                            winner.score(),
                            winner.start()
                    )
            );

            lastEnd =
                    winner.end();
        }

        return List.copyOf(
                resolved
        );
    }

    public Optional<ResolvedClassReference> resolveExistingClass(
            String candidate,
            ProjectDocument document
    ) {
        if (
                candidate == null
                        || candidate.isBlank()
                        || document == null
                        || document.umlModel() == null
        ) {
            return Optional.empty();
        }

        String normalizedCandidate =
                normalizeForMatch(
                        splitCamelCase(
                                candidate
                        )
                );

        List<ResolvedClassReference> matches =
                document.umlModel()
                        .classes()
                        .stream()
                        .map(
                                umlClass -> {
                                    String normalizedClass =
                                            normalizeForMatch(
                                                    splitCamelCase(
                                                            umlClass.name()
                                                    )
                                            );

                                    return new ResolvedClassReference(
                                            umlClass.id(),
                                            umlClass.name(),
                                            candidate,
                                            similarity(
                                                    normalizedCandidate,
                                                    normalizedClass
                                            ),
                                            0
                                    );
                                }
                        )
                        .sorted(
                                Comparator.comparingDouble(
                                                ResolvedClassReference::score
                                        )
                                        .reversed()
                        )
                        .toList();

        if (matches.isEmpty()) {
            return Optional.empty();
        }

        ResolvedClassReference best =
                matches.getFirst();

        double second =
                matches.size() > 1
                        ? matches.get(1)
                                .score()
                        : 0.0d;

        if (
                best.score() < thresholdFor(
                        normalizedCandidate
                )
                        || (
                        best.score() < 0.999d
                                && best.score() - second
                                < MIN_DOMINANCE_MARGIN
                )
        ) {
            return Optional.empty();
        }

        return Optional.of(
                best
        );
    }

    public Optional<ResolvedAttributeReference> resolveExistingAttribute(
            String classCandidate,
            String attributeCandidate,
            ProjectDocument document
    ) {
        if (
                attributeCandidate == null
                        || attributeCandidate.isBlank()
        ) {
            return Optional.empty();
        }

        Optional<ResolvedClassReference> resolvedClass =
                resolveExistingClass(
                        classCandidate,
                        document
                );

        if (resolvedClass.isEmpty()) {
            return Optional.empty();
        }

        UmlClass umlClass =
                document.umlModel()
                        .classes()
                        .stream()
                        .filter(
                                candidate ->
                                        candidate.id().equals(
                                                resolvedClass.get().classId()
                                        )
                        )
                        .findFirst()
                        .orElse(null);

        if (umlClass == null) {
            return Optional.empty();
        }

        String normalizedCandidate =
                normalizeForMatch(
                        splitCamelCase(
                                attributeCandidate
                        )
                );

        List<ResolvedAttributeReference> matches =
                umlClass.attributes()
                        .stream()
                        .filter(Objects::nonNull)
                        .map(
                                attribute -> {
                                    String normalizedAttribute =
                                            normalizeForMatch(
                                                    splitCamelCase(
                                                            attribute.name()
                                                    )
                                            );

                                    return new ResolvedAttributeReference(
                                            umlClass.id(),
                                            attribute.id(),
                                            attribute.name(),
                                            attributeCandidate,
                                            similarity(
                                                    normalizedCandidate,
                                                    normalizedAttribute
                                            )
                                    );
                                }
                        )
                        .sorted(
                                Comparator.comparingDouble(
                                                ResolvedAttributeReference::score
                                        )
                                        .reversed()
                        )
                        .toList();

        if (matches.isEmpty()) {
            return Optional.empty();
        }

        ResolvedAttributeReference best =
                matches.getFirst();

        double second =
                matches.size() > 1
                        ? matches.get(1).score()
                        : 0.0d;

        double threshold =
                attributeThresholdFor(
                        normalizedCandidate
                );

        double dominanceMargin =
                attributeDominanceMarginFor(
                        normalizedCandidate
                );

        if (
                best.score() < threshold
                        || (
                        best.score() < 0.999d
                                && best.score() - second
                                < dominanceMargin
                )
        ) {
            return Optional.empty();
        }

        return Optional.of(best);
    }

    private double attributeThresholdFor(
            String normalized
    ) {
        int compactLength =
                normalized.replace(
                                " ",
                                ""
                        )
                        .length();

        if (compactLength <= 3) {
            return 1.0d;
        }

        // Attribute lookup is scoped to an already resolved UML class.
        // For short names, accept exactly one edit/transposition while
        // keeping a stronger dominance requirement to avoid guessing.
        if (compactLength == 4) {
            return 0.75d;
        }

        if (compactLength == 5) {
            return 0.80d;
        }

        return MIN_MATCH_SCORE;
    }

    private double attributeDominanceMarginFor(
            String normalized
    ) {
        int compactLength =
                normalized.replace(
                                " ",
                                ""
                        )
                        .length();

        return compactLength <= 5
                ? 0.15d
                : MIN_DOMINANCE_MARGIN;
    }

    public boolean fuzzyMentionsExistingAttribute(
            String userText,
            String className,
            String attributeName,
            ProjectDocument document
    ) {
        if (
                userText == null
                        || userText.isBlank()
                        || attributeName == null
                        || attributeName.isBlank()
                        || resolveExistingClass(className, document).isEmpty()
        ) {
            return false;
        }

        String normalizedAttribute =
                normalizeForMatch(
                        splitCamelCase(attributeName)
                );

        List<String> textTokens =
                tokenize(
                        normalizeForMatch(userText)
                );

        List<String> attributeTokens =
                tokenize(normalizedAttribute);

        if (textTokens.isEmpty() || attributeTokens.isEmpty()) {
            return false;
        }

        int preferredWindow = attributeTokens.size();
        int minWindow = Math.max(1, preferredWindow - 1);
        int maxWindow = Math.min(textTokens.size(), preferredWindow + 1);
        double threshold = attributeThresholdFor(normalizedAttribute);

        for (int windowSize = minWindow; windowSize <= maxWindow; windowSize++) {
            for (int start = 0; start + windowSize <= textTokens.size(); start++) {
                String observed = String.join(
                        " ",
                        textTokens.subList(start, start + windowSize)
                );
                if (similarity(observed, normalizedAttribute) >= threshold) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean fuzzyMentions(
            String userText,
            String entity
    ) {
        if (
                userText == null
                        || userText.isBlank()
                        || entity == null
                        || entity.isBlank()
        ) {
            return false;
        }

        String normalizedEntity =
                normalizeForMatch(
                        splitCamelCase(
                                entity
                        )
                );

        List<String> textTokens =
                tokenize(
                        normalizeForMatch(
                                userText
                        )
                );

        List<String> entityTokens =
                tokenize(
                        normalizedEntity
                );

        if (
                textTokens.isEmpty()
                        || entityTokens.isEmpty()
        ) {
            return false;
        }

        int preferredWindow =
                entityTokens.size();

        int minWindow =
                Math.max(
                        1,
                        preferredWindow - 1
                );

        int maxWindow =
                Math.min(
                        textTokens.size(),
                        preferredWindow + 1
                );

        for (
                int windowSize = minWindow;
                windowSize <= maxWindow;
                windowSize++
        ) {
            for (
                    int start = 0;
                    start + windowSize <= textTokens.size();
                    start++
            ) {
                String observed =
                        String.join(
                                " ",
                                textTokens.subList(
                                        start,
                                        start + windowSize
                                )
                        );

                if (
                        similarity(
                                observed,
                                normalizedEntity
                        ) >= thresholdFor(
                                normalizedEntity
                        )
                ) {
                    return true;
                }
            }
        }

        return false;
    }

    private double thresholdFor(
            String normalized
    ) {
        int compactLength =
                normalized.replace(
                                " ",
                                ""
                        )
                        .length();

        if (compactLength <= 3) {
            return 1.0d;
        }

        if (compactLength == 4) {
            return 0.85d;
        }

        return MIN_MATCH_SCORE;
    }

    private double similarity(
            String left,
            String right
    ) {
        String normalizedLeft =
                leetNormalize(
                        left
                );

        String normalizedRight =
                leetNormalize(
                        right
                );

        if (
                normalizedLeft.equals(
                        normalizedRight
                )
        ) {
            return 1.0d;
        }

        String compactLeft =
                normalizedLeft.replace(
                        " ",
                        ""
                );

        String compactRight =
                normalizedRight.replace(
                        " ",
                        ""
                );

        if (
                compactLeft.isBlank()
                        || compactRight.isBlank()
        ) {
            return 0.0d;
        }

        double direct = normalizedEditSimilarity(compactLeft, compactRight);

        String singularLeft = simpleSingular(compactLeft);
        String singularRight = simpleSingular(compactRight);

        return Math.max(
                direct,
                Math.max(
                        normalizedEditSimilarity(singularLeft, compactRight),
                        Math.max(
                                normalizedEditSimilarity(compactLeft, singularRight),
                                normalizedEditSimilarity(singularLeft, singularRight)
                        )
                )
        );
    }

    private double normalizedEditSimilarity(String left, String right) {
        if (left == null || right == null || left.isBlank() || right.isBlank()) {
            return 0.0d;
        }
        if (left.equals(right)) {
            return 1.0d;
        }
        int distance = damerauLevenshtein(left, right);
        int maxLength = Math.max(left.length(), right.length());
        return 1.0d - ((double) distance / (double) maxLength);
    }

    private String simpleSingular(String value) {
        if (value == null || value.length() <= 4) {
            return value;
        }
        if (value.endsWith("iones") && value.length() > 6) {
            return value.substring(0, value.length() - 2);
        }
        if (value.endsWith("es") && value.length() > 6) {
            return value.substring(0, value.length() - 2);
        }
        if (value.endsWith("s")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private int damerauLevenshtein(
            String left,
            String right
    ) {
        int[][] distance =
                new int[left.length() + 1][right.length() + 1];

        for (
                int i = 0;
                i <= left.length();
                i++
        ) {
            distance[i][0] =
                    i;
        }

        for (
                int j = 0;
                j <= right.length();
                j++
        ) {
            distance[0][j] =
                    j;
        }

        for (
                int i = 1;
                i <= left.length();
                i++
        ) {
            for (
                    int j = 1;
                    j <= right.length();
                    j++
            ) {
                int substitutionCost =
                        left.charAt(
                                i - 1
                        )
                                == right.charAt(
                                j - 1
                        )
                                ? 0
                                : 1;

                distance[i][j] =
                        Math.min(
                                Math.min(
                                        distance[i - 1][j]
                                                + 1,
                                        distance[i][j - 1]
                                                + 1
                                ),
                                distance[i - 1][j - 1]
                                        + substitutionCost
                        );

                if (
                        i > 1
                                && j > 1
                                && left.charAt(
                                i - 1
                        )
                                == right.charAt(
                                j - 2
                        )
                                && left.charAt(
                                i - 2
                        )
                                == right.charAt(
                                j - 1
                        )
                ) {
                    distance[i][j] =
                            Math.min(
                                    distance[i][j],
                                    distance[i - 2][j - 2]
                                            + 1
                            );
                }
            }
        }

        return distance[left.length()][right.length()];
    }

    private String normalizeForMatch(
            String value
    ) {
        String decomposed =
                Normalizer.normalize(
                        value == null
                                ? ""
                                : value,
                        Normalizer.Form.NFD
                );

        String withoutMarks =
                DIACRITICS.matcher(
                                decomposed
                        )
                        .replaceAll(
                                ""
                        )
                        .toLowerCase(
                                Locale.ROOT
                        );

        return NON_WORD.matcher(
                        withoutMarks
                )
                .replaceAll(
                        " "
                )
                .trim()
                .replaceAll(
                        "\\s+",
                        " "
                );
    }

    private String leetNormalize(
            String value
    ) {
        return value
                .replace('4', 'a')
                .replace('3', 'e')
                .replace('0', 'o')
                .replace('5', 's')
                .replace('7', 't');
    }

    private String splitCamelCase(
            String value
    ) {
        return CAMEL_CASE.matcher(
                        value == null
                                ? ""
                                : value
                )
                .replaceAll(
                        " "
                );
    }

    private List<String> tokenize(
            String normalized
    ) {
        if (
                normalized == null
                        || normalized.isBlank()
        ) {
            return List.of();
        }

        return List.of(
                normalized.split(
                        " "
                )
        );
    }

    public record ResolvedClassReference(
            UUID classId,
            String canonicalName,
            String observedText,
            double score,
            int tokenIndex
    ) {
    }

    public record ResolvedAttributeReference(
            UUID classId,
            UUID attributeId,
            String canonicalName,
            String observedText,
            double score
    ) {
    }

    private record WindowCandidate(
            int start,
            int end,
            String observed,
            UmlClass umlClass,
            double score
    ) {
    }
}
