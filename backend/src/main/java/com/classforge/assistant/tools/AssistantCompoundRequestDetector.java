package com.classforge.assistant.tools;

import com.classforge.assistant.AssistantActionType;
import com.classforge.assistant.AssistantPlanAction;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class AssistantCompoundRequestDetector {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern CREATE_NAMED = Pattern.compile("\\bcrea(?:r)?\\s+(?:una?\\s+)?(?:clase\\s+)?([a-z_][a-z0-9_]*)");

    public boolean isCompound(String userText) {
        return requiredFamilies(userText).size() >= 2;
    }

    public Set<AssistantActionType> requiredFamilies(String userText) {
        String text = normalize(userText);
        Set<AssistantActionType> required = new LinkedHashSet<>();

        if (isAssociationClassCreationRequest(text)) {
            required.add(AssistantActionType.CREATE_ASSOCIATION_CLASS);
            return Set.copyOf(required);
        }

        boolean classCreation =
                containsAny(text, "crea clase", "crear clase", "nueva clase", "clase llamada", "necesito una clase")
                        || createsNamedClass(text);
        if (classCreation) {
            required.add(AssistantActionType.CREATE_CLASS);
        }
        if (requestsAttributeAddition(text, classCreation)) {
            required.add(AssistantActionType.ADD_ATTRIBUTES);
        }
        String relationshipIntentText = withoutAssociationClassPhrases(text);
        if (containsAny(relationshipIntentText, "relacion", "relaciona", "relacional", "conecta", "asocia", "asocial", "hereda", "agrupa", "compuesta")) {
            required.add(AssistantActionType.CREATE_RELATIONSHIP);
        }
        if (containsAny(text, "renombra", "cambia el nombre", "ahora se llama")) {
            required.add(AssistantActionType.RENAME_CLASS);
        }
        return Set.copyOf(required);
    }

    public boolean complete(String userText, List<AssistantPlanAction> actions) {
        Set<AssistantActionType> required = requiredFamilies(userText);
        if (required.isEmpty()) {
            return !actions.isEmpty();
        }
        Set<AssistantActionType> actual = actions.stream()
                .map(AssistantPlanAction::type)
                .collect(java.util.stream.Collectors.toSet());
        return actual.containsAll(required);
    }


    private boolean isAssociationClassCreationRequest(String text) {
        String associationClass =
                "(?:clase de asociacion|clase asociativa|clase intermedia|association class|associationclass)";
        if (!Pattern.compile("\\b" + associationClass + "\\b").matcher(text).find()) {
            return false;
        }
        return Pattern.compile(
                "(?:"
                        + "\\b(?:convierte|convertir|transforma|transformar)\\b.*\\b(?:en|como)\\b.*\\b" + associationClass + "\\b"
                        + "|\\b(?:crea|crear|agrega|anade)\\s+(?:una\\s+)?(?:nueva\\s+)?" + associationClass + "\\b"
                        + "|\\bcrea\\b.*\\bcomo\\s+(?:una\\s+)?" + associationClass + "\\b"
                        + "|\\b(?:haz|hacer)\\s+que\\b.*\\b(?:sea|como)\\b.*\\b" + associationClass + "\\b"
                        + "|\\bvuelve\\b.*\\b" + associationClass + "\\b"
                        + ")"
        ).matcher(text).find();
    }

    private String withoutAssociationClassPhrases(String text) {
        return text
                .replace("clase de asociacion", " ")
                .replace("clase asociativa", " ")
                .replace("clase intermedia", " ")
                .replace("association class", " ")
                .replace("associationclass", " ");
    }

    private boolean requestsAttributeAddition(String text, boolean classCreation) {
        if (containsAny(text, "atributo", "campo", "agregale", "anadele", "ponle")) {
            return true;
        }

        boolean genericAdd = containsAny(text, "agrega", "anade");
        if (!genericAdd) {
            return false;
        }
        if (!classCreation) {
            return true;
        }

        // "Anade al modelo una nueva clase ..." uses the add verb to create
        // the class itself; it must not fabricate a second ADD_ATTRIBUTES step.
        // A second add verb still means a genuine compound request, e.g.
        // "Agrega una nueva clase Cliente y agrega email".
        int addVerbCount = countToken(text, "agrega") + countToken(text, "anade");
        boolean addVerbGovernsNewClass = Pattern.compile(
                "\\b(?:agrega|anade)\\b.{0,64}\\b(?:una\\s+)?nueva\\s+clase\\b"
        ).matcher(text).find();
        return !addVerbGovernsNewClass || addVerbCount > 1;
    }

    private int countToken(String text, String token) {
        var matcher = Pattern.compile("\\b" + Pattern.quote(token) + "\\b").matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private boolean createsNamedClass(String text) {
        var matcher = CREATE_NAMED.matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (!Set.of(
                    "el", "la", "los", "las", "un", "una",
                    "atributo", "campo",
                    "asociacion", "relacion", "agregacion", "composicion", "generalizacion", "vinculo"
            ).contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) return true;
        }
        return false;
    }

    private String normalize(String value) {
        String decomposed = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD);
        return DIACRITICS.matcher(decomposed).replaceAll("")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_*\\s.]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
