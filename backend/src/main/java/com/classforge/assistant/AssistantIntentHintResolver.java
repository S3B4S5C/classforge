package com.classforge.assistant;

import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlAttribute;
import com.classforge.project.domain.document.UmlClass;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Produces high-confidence action-family hints from explicit wording.
 *
 * The hint never creates UML data and is deliberately conservative: when the
 * request looks compound or ambiguous, it returns empty and the LLM keeps the
 * full action schema. Its purpose is to stop small local models from confusing
 * read-only model context with the requested operation.
 */
@Component
public class AssistantIntentHintResolver {

    private final AssistantEntityReferenceResolver entityResolver =
            new AssistantEntityReferenceResolver();

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    public Optional<IntentHint> resolve(String userText) {
        return resolve(userText, null);
    }

    public Optional<IntentHint> resolve(
            String userText,
            ProjectDocument document
    ) {
        if (userText == null || userText.isBlank()) {
            return Optional.empty();
        }

        String normalized = normalize(userText);
        List<String> tokens = tokens(normalized);

        boolean associationClassContext =
                containsPhrase(normalized, "clase de asociacion")
                        || containsPhrase(normalized, "clase asociativa")
                        || containsPhrase(normalized, "clase intermedia")
                        || containsPhrase(normalized, "association class")
                        || normalized.contains("associationclass");

        boolean relationshipContext =
                !associationClassContext
                        && (containsApprox(tokens, "relacion")
                        || containsApprox(tokens, "asociacion")
                        || containsApprox(tokens, "multiplicidad")
                        || containsApprox(tokens, "relaciona")
                        || containsApprox(tokens, "conecta")
                        || containsApprox(tokens, "hereda")
                        || containsApprox(tokens, "compuesta")
                        || containsApprox(tokens, "agrupar")
                        || containsApprox(tokens, "agrupa")
                        || normalized.contains("0..")
                        || normalized.contains("1..")
                        || normalized.contains("..*"));

        boolean attributeContext =
                containsApprox(tokens, "atributo")
                        || containsApprox(tokens, "campo");

        boolean classContext =
                !associationClassContext && containsApprox(tokens, "clase");

        boolean deleteCue =
                containsApprox(tokens, "elimina")
                        || containsApprox(tokens, "eliminar")
                        || containsApprox(tokens, "borra")
                        || containsApprox(tokens, "borrar")
                        || containsApprox(tokens, "quita")
                        || containsApprox(tokens, "quitar")
                        || containsApprox(tokens, "saca")
                        || containsApprox(tokens, "sacalo")
                        || containsStem(tokens, "elimin")
                        || containsStem(tokens, "borr")
                        || containsStem(tokens, "quit")
                        || containsStem(tokens, "sac");

        boolean renameCue =
                containsApprox(tokens, "renombra")
                        || containsApprox(tokens, "renombrar")
                        || containsStem(tokens, "renombr")
                        || containsStem(tokens, "renonbr")
                        || containsPhrase(normalized, "cambia el nombre")
                        || containsPhrase(normalized, "cambiale el nombre")
                        || containsPhrase(normalized, "ahora se llama")
                        || containsPhrase(normalized, "se llama");

        boolean classRenamePhrase =
                containsPhrase(normalized, "cambia el nombre")
                        || containsPhrase(normalized, "cambiale el nombre")
                        || containsPhrase(normalized, "cambiar el nombre")
                        || containsPhrase(normalized, "cambiarle el nombre");

        boolean updateCue =
                containsApprox(tokens, "actualiza")
                        || containsApprox(tokens, "actualizar")
                        || containsApprox(tokens, "cambia")
                        || containsApprox(tokens, "cambiar")
                        || containsStem(tokens, "actualiz")
                        || containsStem(tokens, "cambi")
                        || renameCue;

        boolean createCue =
                containsApprox(tokens, "crea")
                        || containsApprox(tokens, "crear")
                        || containsApprox(tokens, "agrega")
                        || containsApprox(tokens, "agregar")
                        || containsApprox(tokens, "anade")
                        || containsApprox(tokens, "anadir")
                        || containsApprox(tokens, "ponle")
                        || containsApprox(tokens, "conecta")
                        || containsApprox(tokens, "relaciona")
                        || containsApprox(tokens, "hereda")
                        || containsApprox(tokens, "compuesta")
                        || containsApprox(tokens, "agrupa")
                        || containsApprox(tokens, "nueva")
                        || containsApprox(tokens, "nuevo")
                        || containsStem(tokens, "crea")
                        || containsStem(tokens, "agreg")
                        || containsStem(tokens, "anad")
                        || containsStem(tokens, "pon")
                        || containsStem(tokens, "conect")
                        || containsStem(tokens, "relacion")
                        || containsStem(tokens, "hered")
                        || containsStem(tokens, "agrup");

        boolean multiplicityCue =
                containsApprox(tokens, "multiplicidad")
                        || normalized.contains("0..")
                        || normalized.contains("1..")
                        || normalized.contains("..*")
                        || containsApprox(tokens, "muchas")
                        || containsApprox(tokens, "muchos")
                        || containsApprox(tokens, "cero");

        List<AssistantEntityReferenceResolver.ResolvedClassReference> resolvedClasses =
                document == null
                        ? List.of()
                        : entityResolver.resolveMentions(userText, document);

        List<ResolvedAttributeMention> resolvedAttributes =
                resolveAttributeMentions(
                        tokens,
                        resolvedClasses,
                        document
                );

        boolean scopedMemberSyntax =
                document != null
                        && !classContext
                        && hasScopedMemberSyntax(tokens, resolvedClasses);

        Set<AssistantActionType> candidates = new LinkedHashSet<>();
        List<String> evidence = new ArrayList<>();

        if (associationClassContext && isAssociationClassCreationRequest(normalized)) {
            candidates.add(AssistantActionType.CREATE_ASSOCIATION_CLASS);
            evidence.add("association-class+create");
        } else if (relationshipContext) {
            if (deleteCue) {
                candidates.add(AssistantActionType.DELETE_RELATIONSHIP);
                evidence.add("relationship+delete");
            } else if (multiplicityCue || containsApprox(tokens, "existente") || updateCue) {
                candidates.add(AssistantActionType.UPDATE_RELATIONSHIP);
                evidence.add("relationship+update");
            } else if (createCue) {
                candidates.add(AssistantActionType.CREATE_RELATIONSHIP);
                evidence.add("relationship+create");
            }
        } else if (attributeContext) {
            if (deleteCue) {
                candidates.add(AssistantActionType.DELETE_ATTRIBUTE);
                evidence.add("attribute+delete");
            } else if (renameCue || updateCue) {
                candidates.add(AssistantActionType.UPDATE_ATTRIBUTE);
                evidence.add("attribute+update");
            } else if (createCue) {
                candidates.add(AssistantActionType.ADD_ATTRIBUTES);
                evidence.add("attribute+create");
            }
        } else {
            if (renameCue && deleteCue) {
                return Optional.empty();
            }

            if (renameCue) {
                if (classRenamePhrase && resolvedClasses.size() == 1) {
                    candidates.add(AssistantActionType.RENAME_CLASS);
                    evidence.add("rename+class-name-phrase");
                } else if (resolvedAttributes.size() == 1) {
                    candidates.add(AssistantActionType.UPDATE_ATTRIBUTE);
                    evidence.add("rename+scoped-attribute");
                } else {
                    candidates.add(AssistantActionType.RENAME_CLASS);
                    evidence.add("rename-existing-symbol");
                }
            } else if (deleteCue) {
                if (resolvedAttributes.size() == 1 || scopedMemberSyntax) {
                    candidates.add(AssistantActionType.DELETE_ATTRIBUTE);
                    evidence.add(scopedMemberSyntax ? "delete+scoped-member-syntax" : "delete+scoped-attribute");
                } else if (classContext || resolvedClasses.size() == 1) {
                    candidates.add(AssistantActionType.DELETE_CLASS);
                    evidence.add("delete+existing-class");
                }
            } else if (classContext && createCue) {
                candidates.add(AssistantActionType.CREATE_CLASS);
                evidence.add("class+create");
            } else if (updateCue && resolvedAttributes.size() == 1) {
                candidates.add(AssistantActionType.UPDATE_ATTRIBUTE);
                evidence.add("update+scoped-attribute");
            } else if (
                    updateCue
                            && resolvedClasses.size() == 1
                            && containsApprox(tokens, "por")
            ) {
                candidates.add(AssistantActionType.RENAME_CLASS);
                evidence.add("replace+existing-class");
            } else if (
                    createCue
                            && resolvedClasses.size() == 1
                            && (
                            containsApprox(tokens, "ponle")
                                    || containsApprox(tokens, "agrega")
                                    || containsApprox(tokens, "anade")
                                    || containsStem(tokens, "pon")
                                    || containsStem(tokens, "agreg")
                                    || containsStem(tokens, "anad")
                    )
            ) {
                candidates.add(AssistantActionType.ADD_ATTRIBUTES);
                evidence.add("add-property+existing-class");
            }
        }

        if (candidates.size() != 1) {
            return Optional.empty();
        }

        AssistantActionType type = candidates.iterator().next();
        return Optional.of(
                new IntentHint(
                        type,
                        1.0d,
                        String.join("+", evidence)
                )
        );
    }

    private List<ResolvedAttributeMention> resolveAttributeMentions(
            List<String> tokens,
            List<AssistantEntityReferenceResolver.ResolvedClassReference> resolvedClasses,
            ProjectDocument document
    ) {
        if (
                document == null
                        || document.umlModel() == null
                        || resolvedClasses.isEmpty()
        ) {
            return List.of();
        }

        List<ResolvedAttributeMention> matches = new ArrayList<>();

        for (AssistantEntityReferenceResolver.ResolvedClassReference classReference : resolvedClasses) {
            UmlClass umlClass = document.umlModel().classes().stream()
                    .filter(candidate -> candidate.id().equals(classReference.classId()))
                    .findFirst()
                    .orElse(null);

            if (umlClass == null) {
                continue;
            }

            List<UmlAttribute> classMatches = umlClass.attributes().stream()
                    .filter(attribute -> tokenListMentionsScoped(tokens, attribute.name()))
                    .toList();

            if (classMatches.size() == 1) {
                matches.add(
                        new ResolvedAttributeMention(
                                classReference.canonicalName(),
                                classMatches.getFirst().name()
                        )
                );
            }
        }

        return matches.stream().distinct().toList();
    }

    private boolean hasScopedMemberSyntax(
            List<String> tokens,
            List<AssistantEntityReferenceResolver.ResolvedClassReference> resolvedClasses
    ) {
        if (resolvedClasses.size() != 1) {
            return false;
        }
        AssistantEntityReferenceResolver.ResolvedClassReference reference = resolvedClasses.getFirst();
        String observed = normalize(reference.observedText());
        if (observed.startsWith("de ") || observed.startsWith("del ")) {
            return true;
        }
        int startIndex = reference.tokenIndex();
        if (startIndex <= 0 || startIndex > tokens.size()) {
            return false;
        }
        String previous = tokens.get(startIndex - 1);
        return previous.equals("de") || previous.equals("del");
    }

    private boolean tokenListMentionsScoped(
            List<String> tokens,
            String expected
    ) {
        String normalizedExpected = normalize(expected).replace(" ", "");
        if (normalizedExpected.isBlank()) {
            return false;
        }

        for (String token : tokens) {
            String compact = token.replace(" ", "");
            if (compact.equals(normalizedExpected)) {
                return true;
            }

            if (normalizedExpected.length() >= 4 && compact.length() >= 4) {
                int distance = damerauLevenshtein(compact, normalizedExpected);
                int allowed = normalizedExpected.length() <= 5 ? 1 : 1;
                if (distance <= allowed) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean containsPhrase(String normalized, String phrase) {
        return (" " + normalized + " ").contains(" " + phrase + " ");
    }

    private boolean containsStem(List<String> tokens, String stem) {
        if (stem == null || stem.length() < 3) {
            return false;
        }
        for (String token : tokens) {
            if (token.startsWith(stem)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsApprox(List<String> tokens, String expected) {
        for (String token : tokens) {
            if (token.equals(expected)) {
                return true;
            }

            if (expected.length() >= 5 && token.length() >= 4) {
                int distance = damerauLevenshtein(token, expected);
                if (distance <= 1) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isAssociationClassCreationRequest(String text) {
        String associationClass =
                "(?:clase de asociacion|clase asociativa|clase intermedia|association class|associationclass)";
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

    private String normalize(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD);
        normalized = DIACRITICS.matcher(normalized).replaceAll("");
        normalized = normalized.toLowerCase(Locale.ROOT)
                .replace('ñ', 'n');
        normalized = normalized
                .replaceAll("[^a-z0-9.*]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
        return normalized;
    }

    private List<String> tokens(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.asList(value.split(" "));
    }

    private int damerauLevenshtein(String left, String right) {
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

                if (
                        i > 1
                                && j > 1
                                && left.charAt(i - 1) == right.charAt(j - 2)
                                && left.charAt(i - 2) == right.charAt(j - 1)
                ) {
                    distance[i][j] = Math.min(
                            distance[i][j],
                            distance[i - 2][j - 2] + 1
                    );
                }
            }
        }

        return distance[left.length()][right.length()];
    }

    private record ResolvedAttributeMention(
            String className,
            String attributeName
    ) {
    }

    public record IntentHint(
            AssistantActionType actionType,
            double confidence,
            String evidence
    ) {
    }
}
