package com.classforge.assistant.vision;

import com.classforge.assistant.AssistantPlanningException;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.regex.Pattern;

@Component
public class VisionCodeIdentifierCanonicalizer {

    private static final Pattern CODE_NAME =
            Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final Pattern COMBINING_MARKS =
            Pattern.compile("\\p{M}+");

    public String canonicalize(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw new AssistantPlanningException(field + " es obligatorio.");
        }

        String value = raw.trim();
        if (CODE_NAME.matcher(value).matches()) {
            return value;
        }

        String decomposed = COMBINING_MARKS.matcher(
                Normalizer.normalize(value, Normalizer.Form.NFD)
        ).replaceAll("");
        StringBuilder result = new StringBuilder();
        boolean previousWasReplacement = false;
        boolean meaningful = false;

        for (int index = 0; index < decomposed.length(); index++) {
            char character = decomposed.charAt(index);
            if ((character >= 'A' && character <= 'Z')
                    || (character >= 'a' && character <= 'z')
                    || (character >= '0' && character <= '9')
                    || character == '_') {
                result.append(character);
                previousWasReplacement = false;
                if (character != '_') {
                    meaningful = true;
                }
            } else if (!previousWasReplacement) {
                result.append('_');
                previousWasReplacement = true;
            }
        }

        if (!meaningful) {
            throw new AssistantPlanningException(
                    field + " no puede adaptarse a un identificador compatible con codigo."
            );
        }

        if (result.charAt(0) >= '0' && result.charAt(0) <= '9') {
            result.insert(0, '_');
        }

        String canonical = result.toString();
        if (!CODE_NAME.matcher(canonical).matches()) {
            throw new AssistantPlanningException(
                    field + " no puede adaptarse a un identificador compatible con codigo."
            );
        }
        return canonical;
    }
}
