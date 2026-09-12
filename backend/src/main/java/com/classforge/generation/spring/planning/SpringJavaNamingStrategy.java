package com.classforge.generation.spring.planning;

import com.classforge.generation.spring.validation.SpringGenerationDiagnostic;
import com.classforge.generation.spring.validation.SpringGenerationDiagnosticCode;
import com.classforge.generation.spring.validation.SpringGenerationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SpringJavaNamingStrategy {
    private static final Set<String> JAVA_KEYWORDS = Set.of("abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile", "while", "_", "exports", "module", "non-sealed", "open", "opens", "permits", "provides", "record", "requires", "sealed", "to", "transitive", "uses", "var", "with", "yield", "true", "false", "null");

    public String entityClassName(String logicalName) { return pascal(logicalName, "entityClassName"); }
    public String fieldName(String logicalName) {
        List<String> words = words(logicalName);
        String result = words.isEmpty() ? "" : words.getFirst().toLowerCase(Locale.ROOT) + words.stream().skip(1).map(this::capitalized).reduce("", String::concat);
        validate(result, "fieldName");
        return result;
    }
    public String repositoryName(String entityClassName) { return validate(entityClassName + "Repository", "repositoryName"); }
    public String idClassName(String entityClassName) { return validate(entityClassName + "Id", "idClassName"); }
    public String applicationClassName(String artifactName) { return pascal(artifactName.replace('-', '_'), "applicationClassName") + "Application"; }

    private String pascal(String value, String path) {
        String result = words(value).stream().map(this::capitalized).reduce("", String::concat);
        return validate(result, path);
    }
    private List<String> words(String value) {
        List<String> result = new ArrayList<>();
        if (value == null) return result;
        StringBuilder word = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            char previous = index == 0 ? 0 : value.charAt(index - 1);
            char next = index + 1 == value.length() ? 0 : value.charAt(index + 1);
            boolean boundary = current == '_' || current == '-'
                    || (index > 0 && Character.isUpperCase(current) && (Character.isLowerCase(previous)
                    || Character.isDigit(previous) || (Character.isUpperCase(previous) && Character.isLowerCase(next))));
            if (boundary && !word.isEmpty()) { result.add(word.toString()); word.setLength(0); }
            if (current != '_' && current != '-') word.append(current);
        }
        if (!word.isEmpty()) result.add(word.toString());
        return result;
    }
    private String capitalized(String word) {
        String normalized = word.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }
    private String validate(String value, String path) {
        boolean valid = value != null && !value.isEmpty() && Character.isJavaIdentifierStart(value.charAt(0));
        for (int index = 1; valid && index < value.length(); index++) valid = Character.isJavaIdentifierPart(value.charAt(index));
        if (!valid || JAVA_KEYWORDS.contains(value)) {
            throw new SpringGenerationException(List.of(new SpringGenerationDiagnostic(SpringGenerationDiagnosticCode.JAVA_IDENTIFIER_INVALID, null, path, "Generated Java identifier is invalid: " + value)));
        }
        return value;
    }
}
