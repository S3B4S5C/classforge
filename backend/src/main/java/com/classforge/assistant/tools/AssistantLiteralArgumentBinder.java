package com.classforge.assistant.tools;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Preserves user-authored identifiers for NEW UML values.
 * Existing references are canonicalized elsewhere; this binder only prevents
 * the LLM from silently autocorrecting newly requested class/attribute names.
 */
@Component
public class AssistantLiteralArgumentBinder {

    private static final Pattern IDENTIFIER = Pattern.compile("[\\p{L}_][\\p{L}\\p{N}_]*");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    public String bindNewIdentifier(String userText, String proposed) {
        if (proposed == null || proposed.isBlank() || userText == null || userText.isBlank()) {
            return proposed;
        }

        String proposedBase = proposed.trim();
        int dot = proposedBase.lastIndexOf('.');
        if (dot >= 0 && dot < proposedBase.length() - 1) {
            proposedBase = proposedBase.substring(dot + 1);
        }

        String target = normalize(proposedBase);
        if (target.isBlank()) {
            return proposed.trim();
        }

        List<Candidate> candidates = new ArrayList<>();
        Matcher matcher = IDENTIFIER.matcher(userText);
        while (matcher.find()) {
            String literal = matcher.group();
            String normalized = normalize(literal);
            if (normalized.isBlank()) {
                continue;
            }
            candidates.add(new Candidate(literal, normalized, similarity(normalized, target)));
        }

        return candidates.stream()
                .filter(candidate -> candidate.normalized().equals(target)
                        || (target.length() >= 5 && editDistance(candidate.normalized(), target) <= 1))
                .max(Comparator.comparingDouble(Candidate::score))
                .map(Candidate::literal)
                .orElse(proposedBase);
    }

    private double similarity(String left, String right) {
        int distance = editDistance(left, right);
        int max = Math.max(left.length(), right.length());
        return max == 0 ? 1.0d : 1.0d - ((double) distance / (double) max);
    }

    private int editDistance(String left, String right) {
        int[][] distance = new int[left.length() + 1][right.length() + 1];
        for (int i = 0; i <= left.length(); i++) {
            distance[i][0] = i;
        }
        for (int j = 0; j <= right.length(); j++) {
            distance[0][j] = j;
        }

        for (int i = 1; i <= left.length(); i++) {
            for (int j = 1; j <= right.length(); j++) {
                int substitution = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                distance[i][j] = Math.min(
                        Math.min(distance[i - 1][j] + 1, distance[i][j - 1] + 1),
                        distance[i - 1][j - 1] + substitution
                );
                if (i > 1
                        && j > 1
                        && left.charAt(i - 1) == right.charAt(j - 2)
                        && left.charAt(i - 2) == right.charAt(j - 1)) {
                    distance[i][j] = Math.min(distance[i][j], distance[i - 2][j - 2] + 1);
                }
            }
        }
        return distance[left.length()][right.length()];
    }

    private String normalize(String value) {
        String decomposed = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD);
        return DIACRITICS.matcher(decomposed)
                .replaceAll("")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]", "");
    }

    private record Candidate(String literal, String normalized, double score) {
    }
}
