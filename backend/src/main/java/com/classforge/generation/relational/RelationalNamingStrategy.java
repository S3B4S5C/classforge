package com.classforge.generation.relational;

import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class RelationalNamingStrategy {
    public String toSnakeCase(String value) {
        if (value == null || value.isEmpty()) return value;
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            char previous = index == 0 ? 0 : value.charAt(index - 1);
            char next = index + 1 == value.length() ? 0 : value.charAt(index + 1);
            boolean boundary = index > 0 && current != '_' && previous != '_'
                    && (Character.isUpperCase(current) && (Character.isLowerCase(previous)
                    || Character.isDigit(previous) || (Character.isUpperCase(previous) && Character.isLowerCase(next))));
            if (boundary) result.append('_');
            result.append(Character.toString(current).toLowerCase(Locale.ROOT));
        }
        return result.toString();
    }
}
