package com.classforge.assistant.vision;

import com.classforge.assistant.AssistantPlanningException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class VisionClassLocalizationValidator {

    public void validate(
            VisionClassLocalizationProposal proposal,
            List<VisionClassProposal> classes,
            int imageWidth,
            int imageHeight
    ) {
        if (proposal == null) {
            throw new AssistantPlanningException("La localizacion visual de clases esta vacia.");
        }
        Set<String> expected = new LinkedHashSet<>();
        for (VisionClassProposal umlClass : classes) {
            expected.add(normalize(required(umlClass.ref(), "class.ref")));
        }
        Set<String> actual = new LinkedHashSet<>();
        for (VisionClassLocalization mapping : proposal.safeMappings()) {
            String ref = normalize(required(mapping.classRef(), "localization.classRef"));
            if (!expected.contains(ref)) {
                throw new AssistantPlanningException("La localizacion invento un classRef: " + mapping.classRef());
            }
            if (!actual.add(ref)) {
                throw new AssistantPlanningException("La localizacion repitio classRef: " + mapping.classRef());
            }
            if (mapping.x() == null || mapping.y() == null || mapping.width() == null || mapping.height() == null
                    || mapping.x() < 0 || mapping.y() < 0 || mapping.width() <= 0 || mapping.height() <= 0
                    || mapping.x() + mapping.width() > imageWidth
                    || mapping.y() + mapping.height() > imageHeight) {
                throw new AssistantPlanningException("Bounding box de clase fuera de la imagen: " + mapping.classRef());
            }
            if (mapping.confidence() != null && (mapping.confidence() < 0 || mapping.confidence() > 1)) {
                throw new AssistantPlanningException("Confidence de localizacion fuera de [0,1].");
            }
        }
        if (!actual.equals(expected)) {
            Set<String> missing = new LinkedHashSet<>(expected);
            missing.removeAll(actual);
            throw new AssistantPlanningException("La localizacion no cubre todas las clases: " + missing);
        }

        List<VisionClassLocalization> mappings = proposal.safeMappings();
        for (int i = 0; i < mappings.size(); i++) {
            for (int j = i + 1; j < mappings.size(); j++) {
                if (intersectionOverUnion(mappings.get(i), mappings.get(j)) > 0.35) {
                    throw new AssistantPlanningException(
                            "La localizacion superpone de forma ambigua las clases "
                                    + mappings.get(i).classRef() + " y " + mappings.get(j).classRef() + "."
                    );
                }
            }
        }
    }

    private double intersectionOverUnion(VisionClassLocalization a, VisionClassLocalization b) {
        long ax2 = (long) a.x() + a.width();
        long ay2 = (long) a.y() + a.height();
        long bx2 = (long) b.x() + b.width();
        long by2 = (long) b.y() + b.height();
        long left = Math.max(a.x(), b.x());
        long top = Math.max(a.y(), b.y());
        long right = Math.min(ax2, bx2);
        long bottom = Math.min(ay2, by2);
        if (right <= left || bottom <= top) {
            return 0.0;
        }
        long intersection = (right - left) * (bottom - top);
        long areaA = (long) a.width() * a.height();
        long areaB = (long) b.width() * b.height();
        long union = areaA + areaB - intersection;
        return union <= 0 ? 0.0 : intersection / (double) union;
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new AssistantPlanningException(field + " es obligatorio.");
        }
        return value.trim();
    }
}
