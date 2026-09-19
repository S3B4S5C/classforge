package com.classforge.project.validation;

import com.classforge.project.domain.document.*;
import java.util.*;
import java.util.regex.Pattern;

final class DiagramLayoutValidator {

    void validateLayout(
            Map<UUID, DiagramNodeLayout> nodes,
            List<UmlClass> classes,
            ProjectValidationCollector collector
    ) {
        Set<UUID> classIds =
                new HashSet<>();

        for (UmlClass umlClass : classes) {
            if (
                    umlClass != null
                            && umlClass.id() != null
            ) {
                classIds.add(
                        umlClass.id()
                );
            }
        }

        for (
                Map.Entry<
                        UUID,
                        DiagramNodeLayout
                        > entry
                : nodes.entrySet()
        ) {
            UUID classId =
                    entry.getKey();

            DiagramNodeLayout layout =
                    entry.getValue();

            String path =
                    "document.layout.nodes["
                            + classId
                            + "]";

            if (
                    classId == null
                            || !classIds.contains(
                                    classId
                            )
            ) {
                collector.error(
                        path,
                        "LAYOUT_CLASS_NOT_FOUND",
                        classId,
                        "El layout contiene una posicion para una clase inexistente."
                );
            }

            if (layout == null) {
                collector.error(
                        path,
                        "LAYOUT_REQUIRED",
                        classId,
                        "La informacion visual de la clase no puede ser nula."
                );
                continue;
            }

            if (
                    !Double.isFinite(layout.x())
                            || !Double.isFinite(
                                    layout.y()
                            )
            ) {
                collector.error(
                        path,
                        "LAYOUT_POSITION_INVALID",
                        classId,
                        "La posicion de la clase debe contener valores finitos."
                );
            }

            if (
                    !Double.isFinite(
                            layout.width()
                    )
                            || !Double.isFinite(
                                    layout.height()
                            )
                            || layout.width() <= 0
                            || layout.height() <= 0
            ) {
                collector.error(
                        path,
                        "LAYOUT_SIZE_INVALID",
                        classId,
                        "El ancho y alto de la clase deben ser mayores que cero."
                );
            }
        }
    }
}
