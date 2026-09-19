package com.classforge.project.validation;

import com.classforge.project.domain.document.*;
import java.util.*;
import java.util.regex.Pattern;

final class UmlClassValidator {

    private static final Pattern CODE_NAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final int MAX_CLASS_NAME = 80;
    private static final int MAX_ATTRIBUTE_NAME = 80;
    private static final int MAX_CUSTOM_TYPE_NAME = 120;

    void validateClasses(
            List<UmlClass> classes,
            ProjectValidationCollector collector
    ) {
        Set<UUID> classIds = new HashSet<>();
        Set<String> classNames = new HashSet<>();
        Set<UUID> attributeIds = new HashSet<>();

        for (
                int classIndex = 0;
                classIndex < classes.size();
                classIndex++
        ) {
            UmlClass umlClass =
                    classes.get(classIndex);

            String classPath =
                    "document.umlModel.classes["
                            + classIndex
                            + "]";

            if (umlClass == null) {
                collector.error(
                        classPath,
                        "CLASS_REQUIRED",
                        null,
                        "La clase no puede ser nula."
                );
                continue;
            }

            UUID classId = umlClass.id();

            if (classId == null) {
                collector.error(
                        classPath + ".id",
                        "CLASS_ID_REQUIRED",
                        null,
                        "La clase necesita un identificador."
                );
            } else if (!classIds.add(classId)) {
                collector.error(
                        classPath + ".id",
                        "DUPLICATE_CLASS_ID",
                        classId,
                        "El identificador de la clase esta repetido."
                );
            }

            validateCodeName(
                    umlClass.name(),
                    classPath + ".name",
                    "CLASS_NAME",
                    "clase",
                    MAX_CLASS_NAME,
                    classId,
                    collector
            );

            if (hasText(umlClass.name())) {
                String normalized =
                        umlClass.name()
                                .toLowerCase(Locale.ROOT);

                if (!classNames.add(normalized)) {
                    collector.error(
                            classPath + ".name",
                            "DUPLICATE_CLASS_NAME",
                            classId,
                            "Ya existe otra clase con el nombre '"
                                    + umlClass.name()
                                    + "'."
                    );
                }
            }

            validateAttributes(
                    umlClass,
                    classPath,
                    attributeIds,
                    collector
            );
        }
    }

    void validateAttributes(
            UmlClass umlClass,
            String classPath,
            Set<UUID> attributeIds,
            ProjectValidationCollector collector
    ) {
        Set<String> attributeNames =
                new HashSet<>();

        for (
                int attributeIndex = 0;
                attributeIndex
                        < umlClass.attributes().size();
                attributeIndex++
        ) {
            UmlAttribute attribute =
                    umlClass.attributes()
                            .get(attributeIndex);

            String attributePath =
                    classPath
                            + ".attributes["
                            + attributeIndex
                            + "]";

            if (attribute == null) {
                collector.error(
                        attributePath,
                        "ATTRIBUTE_REQUIRED",
                        umlClass.id(),
                        "El atributo no puede ser nulo."
                );
                continue;
            }

            if (attribute.id() == null) {
                collector.error(
                        attributePath + ".id",
                        "ATTRIBUTE_ID_REQUIRED",
                        umlClass.id(),
                        "El atributo necesita un identificador."
                );
            } else if (!attributeIds.add(attribute.id())) {
                collector.error(
                        attributePath + ".id",
                        "DUPLICATE_ATTRIBUTE_ID",
                        umlClass.id(),
                        "El identificador del atributo esta repetido."
                );
            }

            validateCodeName(
                    attribute.name(),
                    attributePath + ".name",
                    "ATTRIBUTE_NAME",
                    "atributo",
                    MAX_ATTRIBUTE_NAME,
                    umlClass.id(),
                    collector
            );

            if (hasText(attribute.name())) {
                String normalized =
                        attribute.name()
                                .toLowerCase(Locale.ROOT);

                if (!attributeNames.add(normalized)) {
                    collector.error(
                            attributePath + ".name",
                            "DUPLICATE_ATTRIBUTE_NAME",
                            umlClass.id(),
                            "La clase '"
                                    + umlClass.name()
                                    + "' ya contiene un atributo llamado '"
                                    + attribute.name()
                                    + "'."
                    );
                }
            }

            if (attribute.dataType() == null) {
                collector.error(
                        attributePath + ".dataType",
                        "ATTRIBUTE_TYPE_REQUIRED",
                        umlClass.id(),
                        "Selecciona un tipo para el atributo."
                );
            }

            if (attribute.visibility() == null) {
                collector.error(
                        attributePath + ".visibility",
                        "ATTRIBUTE_VISIBILITY_REQUIRED",
                        umlClass.id(),
                        "Selecciona una visibilidad UML."
                );
            }

            if (attribute.dataType() == UmlDataType.CUSTOM) {
                validateCodeName(
                        attribute.customTypeName(),
                        attributePath + ".customTypeName",
                        "CUSTOM_TYPE_NAME",
                        "tipo personalizado",
                        MAX_CUSTOM_TYPE_NAME,
                        umlClass.id(),
                        collector
                );
            }

            if (
                    attribute.identifier()
                            && attribute.nullable()
            ) {
                collector.error(
                        attributePath + ".nullable",
                        "IDENTIFIER_CANNOT_BE_NULLABLE",
                        umlClass.id(),
                        "Un atributo identificador no puede ser nullable."
                );
            }
        }
    }

    void validateCodeName(
            String value,
            String field,
            String codePrefix,
            String label,
            int maxLength,
            UUID elementId,
            ProjectValidationCollector collector
    ) {
        if (!hasText(value)) {
            collector.error(
                    field,
                    codePrefix + "_REQUIRED",
                    elementId,
                    "El nombre del "
                            + label
                            + " es obligatorio."
            );
            return;
        }

        if (value.length() > maxLength) {
            collector.error(
                    field,
                    codePrefix + "_TOO_LONG",
                    elementId,
                    "El nombre del "
                            + label
                            + " no puede superar "
                            + maxLength
                            + " caracteres."
            );
        }

        if (!CODE_NAME.matcher(value).matches()) {
            collector.error(
                    field,
                    codePrefix + "_INVALID",
                    elementId,
                    "Usa un nombre compatible con codigo: letras, numeros y '_', sin espacios, y no empieces con un numero."
            );
        }
    }

    boolean hasText(
            String value
    ) {
        return value != null
                && !value.isBlank();
    }
}
