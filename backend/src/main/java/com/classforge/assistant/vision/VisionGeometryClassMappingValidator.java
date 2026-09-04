package com.classforge.assistant.vision;

import com.classforge.assistant.AssistantPlanningException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class VisionGeometryClassMappingValidator {

    public List<VisionGeometryClassRegion> validateAndBind(
            VisionGeometryClassMappingProposal proposal,
            List<VisionGeometryClassRegion> regions,
            List<VisionClassProposal> classes
    ) {
        if (proposal == null) {
            throw new AssistantPlanningException("El mapeo Bx->classRef esta vacio.");
        }
        Map<String, VisionGeometryClassRegion> regionById = new LinkedHashMap<>();
        for (VisionGeometryClassRegion region : regions) {
            String id = normalize(required(region.geometryId(), "geometryId"));
            if (regionById.putIfAbsent(id, region) != null) {
                throw new AssistantPlanningException("OpenCV repitio geometryId: " + region.geometryId());
            }
        }
        Map<String, VisionClassProposal> classByRef = new LinkedHashMap<>();
        for (VisionClassProposal umlClass : classes) {
            String ref = normalize(required(umlClass.ref(), "class.ref"));
            classByRef.put(ref, umlClass);
        }
        if (regionById.size() != classByRef.size()) {
            throw new AssistantPlanningException(
                    "El numero de cajas CV (" + regionById.size() + ") no coincide con las clases semanticas ("
                            + classByRef.size() + ")."
            );
        }

        Set<String> usedRegions = new LinkedHashSet<>();
        Set<String> usedClasses = new LinkedHashSet<>();
        Map<String, String> classRefByRegion = new LinkedHashMap<>();
        for (VisionGeometryClassMapping mapping : proposal.safeMappings()) {
            String geometryId = normalize(required(mapping.geometryId(), "mapping.geometryId"));
            String classRef = normalize(required(mapping.classRef(), "mapping.classRef"));
            if (!regionById.containsKey(geometryId)) {
                throw new AssistantPlanningException("El VLM invento geometryId: " + mapping.geometryId());
            }
            if (!classByRef.containsKey(classRef)) {
                throw new AssistantPlanningException("El VLM invento classRef: " + mapping.classRef());
            }
            if (!usedRegions.add(geometryId)) {
                throw new AssistantPlanningException("El VLM repitio geometryId: " + mapping.geometryId());
            }
            if (!usedClasses.add(classRef)) {
                throw new AssistantPlanningException("El VLM asigno una clase a mas de una caja: " + mapping.classRef());
            }
            if (mapping.confidence() != null && (mapping.confidence() < 0 || mapping.confidence() > 1)) {
                throw new AssistantPlanningException("Confidence del mapeo fuera de [0,1].");
            }
            classRefByRegion.put(geometryId, classByRef.get(classRef).ref());
        }

        if (!usedRegions.equals(regionById.keySet()) || !usedClasses.equals(classByRef.keySet())) {
            throw new AssistantPlanningException(
                    "El mapeo de cajas debe ser biyectivo y cubrir exactamente todas las cajas y clases."
            );
        }

        return regions.stream().map(region -> new VisionGeometryClassRegion(
                region.geometryId(),
                classRefByRegion.get(normalize(region.geometryId())),
                region.x(), region.y(), region.width(), region.height(), region.confidence()
        )).toList();
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
