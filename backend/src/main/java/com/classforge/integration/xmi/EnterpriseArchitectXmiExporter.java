package com.classforge.integration.xmi;

import com.classforge.project.domain.document.Multiplicity;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlAttribute;
import com.classforge.project.domain.document.UmlClass;
import com.classforge.project.domain.document.UmlDataType;
import com.classforge.project.domain.document.UmlRelationship;
import com.classforge.project.domain.document.UmlRelationshipType;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
public class EnterpriseArchitectXmiExporter {

    public byte[] export(UUID projectId, String projectName, ProjectDocument document) {
        if (projectId == null || document == null) {
            throw new IllegalArgumentException("projectId and document are required");
        }

        List<UmlClass> classes = document.umlModel().classes();
        List<UmlRelationship> relationships = document.umlModel().relationships();

        Map<String, String> customTypes = customTypes(classes);
        StringBuilder xml = new StringBuilder(16_384);
        line(xml, 0, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        line(xml, 0, "<xmi:XMI xmi:version=\"2.1\" xmlns:xmi=\"http://schema.omg.org/spec/XMI/2.1\" xmlns:uml=\"http://schema.omg.org/spec/UML/2.1\">");
        line(xml, 1, "<xmi:Documentation exporter=\"ClassForge\" exporterVersion=\"0.1\"/>");
        line(xml, 1, "<uml:Model xmi:type=\"uml:Model\" xmi:id=\"" + xmiId("CF_MODEL", projectId) + "\" name=\"EA_Model\" visibility=\"public\">");
        line(xml, 2, "<packagedElement xmi:type=\"uml:Package\" xmi:id=\"" + xmiId("EAPK", projectId) + "\" name=\"" + escape(projectName == null || projectName.isBlank() ? "ClassForge" : projectName) + "\" visibility=\"public\">");

        for (UmlDataType dataType : UmlDataType.values()) {
            if (dataType == UmlDataType.CUSTOM) {
                continue;
            }
            line(xml, 3, "<packagedElement xmi:type=\"uml:PrimitiveType\" xmi:id=\"" + primitiveTypeId(dataType) + "\" name=\"" + eaTypeName(dataType) + "\" visibility=\"public\"/>");
        }
        for (Map.Entry<String, String> customType : customTypes.entrySet()) {
            line(xml, 3, "<packagedElement xmi:type=\"uml:DataType\" xmi:id=\"" + customType.getValue() + "\" name=\"" + escape(customType.getKey()) + "\" visibility=\"public\"/>");
        }

        Map<UUID, List<UmlRelationship>> generalizationsBySource = new LinkedHashMap<>();
        for (UmlRelationship relationship : relationships) {
            if (relationship.type() == UmlRelationshipType.GENERALIZATION) {
                generalizationsBySource.computeIfAbsent(relationship.sourceClassId(), ignored -> new ArrayList<>()).add(relationship);
            }
        }

        for (UmlClass umlClass : classes) {
            line(xml, 3, "<packagedElement xmi:type=\"uml:Class\" xmi:id=\"" + xmiId("EAID", umlClass.id()) + "\" name=\"" + escape(umlClass.name()) + "\" visibility=\"public\">");
            for (UmlAttribute attribute : umlClass.attributes()) {
                String typeId = attribute.dataType() == UmlDataType.CUSTOM
                        ? customTypes.get(attribute.customTypeName())
                        : primitiveTypeId(attribute.dataType());
                String isId = attribute.identifier() ? " isID=\"true\"" : "";
                line(xml, 4, "<ownedAttribute xmi:type=\"uml:Property\" xmi:id=\"" + xmiId("EAID", attribute.id()) + "\" name=\"" + escape(attribute.name()) + "\" visibility=\"" + visibility(attribute.visibility().name()) + "\" type=\"" + typeId + "\"" + isId + ">");
                multiplicity(xml, 5, attribute.nullable() ? new Multiplicity(0, 1) : Multiplicity.one(), attribute.id(), "attribute");
                line(xml, 4, "</ownedAttribute>");
            }
            for (UmlRelationship relationship : generalizationsBySource.getOrDefault(umlClass.id(), List.of())) {
                line(xml, 4, "<generalization xmi:type=\"uml:Generalization\" xmi:id=\"" + xmiId("EAID", relationship.id()) + "\" general=\"" + xmiId("EAID", relationship.targetClassId()) + "\"/>");
            }
            line(xml, 3, "</packagedElement>");
        }

        for (UmlRelationship relationship : relationships) {
            if (relationship.type() == UmlRelationshipType.GENERALIZATION) {
                continue;
            }
            String relationshipId = xmiId("EAID", relationship.id());
            String sourceEndId = derivedId("EAID", "association-source:" + relationship.id());
            String targetEndId = derivedId("EAID", "association-target:" + relationship.id());
            line(xml, 3, "<packagedElement xmi:type=\"uml:Association\" xmi:id=\"" + relationshipId + "\" visibility=\"public\" memberEnd=\"" + sourceEndId + " " + targetEndId + "\">");
            line(xml, 4, "<ownedEnd xmi:type=\"uml:Property\" xmi:id=\"" + sourceEndId + "\" type=\"" + xmiId("EAID", relationship.sourceClassId()) + "\" association=\"" + relationshipId + "\" aggregation=\"" + aggregation(relationship.type()) + "\">");
            multiplicity(xml, 5, normalizeMultiplicity(relationship.sourceMultiplicity()), relationship.id(), "source");
            line(xml, 4, "</ownedEnd>");
            line(xml, 4, "<ownedEnd xmi:type=\"uml:Property\" xmi:id=\"" + targetEndId + "\" type=\"" + xmiId("EAID", relationship.targetClassId()) + "\" association=\"" + relationshipId + "\" aggregation=\"none\">");
            multiplicity(xml, 5, normalizeMultiplicity(relationship.targetMultiplicity()), relationship.id(), "target");
            line(xml, 4, "</ownedEnd>");
            line(xml, 3, "</packagedElement>");
        }

        line(xml, 2, "</packagedElement>");
        line(xml, 1, "</uml:Model>");
        line(xml, 0, "</xmi:XMI>");
        return xml.toString().getBytes(StandardCharsets.UTF_8);
    }

    private Map<String, String> customTypes(List<UmlClass> classes) {
        Map<String, String> result = new LinkedHashMap<>();
        classes.stream()
                .flatMap(item -> item.attributes().stream())
                .filter(item -> item.dataType() == UmlDataType.CUSTOM)
                .map(UmlAttribute::customTypeName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(name -> result.put(name, derivedId("CFDT", "custom-type:" + name)));
        return result;
    }

    private void multiplicity(StringBuilder xml, int indent, Multiplicity multiplicity, UUID seed, String end) {
        Multiplicity effective = normalizeMultiplicity(multiplicity);
        line(xml, indent, "<lowerValue xmi:type=\"uml:LiteralInteger\" xmi:id=\"" + derivedId("CFM", seed + ":" + end + ":lower") + "\" value=\"" + effective.lower() + "\"/>");
        line(xml, indent, "<upperValue xmi:type=\"uml:LiteralUnlimitedNatural\" xmi:id=\"" + derivedId("CFM", seed + ":" + end + ":upper") + "\" value=\"" + (effective.upper() == null ? "*" : effective.upper()) + "\"/>");
    }

    private Multiplicity normalizeMultiplicity(Multiplicity multiplicity) {
        return multiplicity == null ? Multiplicity.one() : multiplicity;
    }

    private String primitiveTypeId(UmlDataType dataType) {
        return "CFDT_" + dataType.name();
    }

    private String eaTypeName(UmlDataType type) {
        return switch (type) {
            case STRING -> "String";
            case INTEGER -> "Integer";
            case LONG -> "Long";
            case DECIMAL -> "Decimal";
            case BOOLEAN -> "Boolean";
            case DATE -> "Date";
            case DATETIME -> "DateTime";
            case UUID -> "UUID";
            case CUSTOM -> throw new IllegalArgumentException("CUSTOM requires customTypeName");
        };
    }

    private String visibility(String visibility) {
        return visibility.toLowerCase(Locale.ROOT);
    }

    private String aggregation(UmlRelationshipType type) {
        return switch (type) {
            case AGGREGATION -> "shared";
            case COMPOSITION -> "composite";
            default -> "none";
        };
    }

    static String xmiId(String prefix, UUID id) {
        return prefix + "_" + id.toString().replace("-", "").toUpperCase(Locale.ROOT);
    }

    static String derivedId(String prefix, String seed) {
        UUID uuid = UUID.nameUUIDFromBytes(("classforge:xmi-export:" + seed).getBytes(StandardCharsets.UTF_8));
        return xmiId(prefix, uuid);
    }

    private void line(StringBuilder xml, int indent, String value) {
        xml.append("  ".repeat(Math.max(0, indent))).append(value).append('\n');
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
