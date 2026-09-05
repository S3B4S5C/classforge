package com.classforge.assistant.vision;

import com.classforge.assistant.AssistantPlanningException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VisionMultiplicityParser {

    private static final Pattern MULTIPLICITY = Pattern.compile("(\\d+|\\*)(?:\\s*\\.\\.\\s*(\\d+|\\*))?");

    public VisionMultiplicityProposal parse(String rawLabel) {
        if (rawLabel == null || rawLabel.isBlank()) {
            return null;
        }
        Matcher matcher = MULTIPLICITY.matcher(rawLabel.trim());
        if (!matcher.matches()) {
            throw invalid(rawLabel);
        }
        String lowerText = matcher.group(1);
        String upperText = matcher.group(2);
        if ("*".equals(lowerText)) {
            if (upperText != null) {
                throw invalid(rawLabel);
            }
            return new VisionMultiplicityProposal(0, null, true);
        }
        int lower = integer(lowerText, rawLabel);
        if (upperText == null) {
            return new VisionMultiplicityProposal(lower, lower, false);
        }
        if ("*".equals(upperText)) {
            return new VisionMultiplicityProposal(lower, null, true);
        }
        int upper = integer(upperText, rawLabel);
        if (upper < lower) {
            throw invalid(rawLabel);
        }
        return new VisionMultiplicityProposal(lower, upper, false);
    }

    private int integer(String value, String rawLabel) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw invalid(rawLabel);
        }
    }

    private AssistantPlanningException invalid(String rawLabel) {
        return new AssistantPlanningException("Etiqueta de multiplicidad invalida: " + rawLabel);
    }
}
