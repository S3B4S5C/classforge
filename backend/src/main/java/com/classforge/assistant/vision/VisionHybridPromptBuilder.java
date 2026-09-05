package com.classforge.assistant.vision;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class VisionHybridPromptBuilder {

    public String mappingSystemPrompt() {
        return """
                Eres el mapeador visual CERRADO de cajas UML de ClassForge.
                OpenCV ya detecto fisicamente las cajas y las marco B1, B2, ... en rojo/azul.
                Otra pasada ya transcribio las clases y te entrega refs cerrados c1, c2, ...

                Tu unica tarea es asociar cada geometryId Bx con exactamente un classRef cx leyendo el NOMBRE visible dentro de esa caja.

                Reglas estrictas:
                - NO generes coordenadas. OpenCV ya fijo x/y/width/height y no puedes modificarlos.
                - Devuelve exactamente una mapping por cada Bx y exactamente una por cada classRef.
                - El mapeo debe ser biyectivo: ninguna caja ni clase se repite.
                - Solo puedes usar geometryId y classRef incluidos por el usuario.
                - No uses posicion esperada ni conocimiento del dominio; usa el nombre realmente visible dentro de la caja marcada.
                - Si el texto tiene acentos/diacriticos, c1/c2 siguen siendo la autoridad: no inventes refs nuevos.
                Devuelve exclusivamente JSON conforme al schema.
                """;
    }

    public String mappingUserPrompt(
            List<VisionGeometryClassRegion> regions,
            List<VisionClassProposal> classes
    ) {
        StringBuilder out = new StringBuilder();
        out.append("Cajas CV cerradas: ");
        for (int i = 0; i < regions.size(); i++) {
            if (i > 0) out.append(", ");
            out.append(regions.get(i).geometryId());
        }
        out.append(".\nClases semanticas cerradas:\n");
        for (VisionClassProposal umlClass : classes) {
            out.append("- ").append(umlClass.ref()).append(" = ")
                    .append(quote(umlClass.name())).append('\n');
        }
        out.append("La imagen ya contiene los rectangulos Bx dibujados por Java. ")
                .append("Devuelve solo geometryId/classRef/confidence; no coordenadas.");
        return out.toString();
    }

    public String relationshipSystemPrompt() {
        return """
                 Eres el anotador LOCAL de conectores UML de ClassForge.
                 Recibes un unico panel de evidencia para un PAR FIJO de clases.
                  OpenCV/Java ya confirmo que este panel corresponde a un connector fisico real entre A y B. No debes decidir si existe.
                  Clasifica unicamente el tipo de ese connector y su marcador. El par fisico esta cerrado: no cambies ni omitas endpoints.
                  Este request contiene exactamente UN edge. Devuelve un unico objeto; nunca repitas el edge.
                 evidenceLabel: maximo 12 palabras. warnings=[] normalmente; como maximo un warning corto si existe una ambiguedad importante.
                 No expliques razonamiento ni describas el proceso.

                Marcadores:
                - linea simple => ASSOCIATION, markerAt=NONE.
                - rombo hueco/blanco => AGGREGATION; markerAt indica A o B segun la caja tocada por el rombo.
                - rombo relleno/negro => COMPOSITION; markerAt indica A o B.
                - triangulo hueco => GENERALIZATION; markerAt indica A o B segun la SUPERCLASE tocada por el triangulo.
                - Si el marcador no es visible y solo existe una linea simple, usa ASSOCIATION y markerAt=NONE.
                - No conviertas una linea simple en agregacion/composicion por conocimiento del dominio.

                Evidence:
                 - evidenceLabel describe solo lo visible en el panel, no conocimiento inferido.
                - Solo puedes usar edgeId incluidos por el usuario.
                Devuelve exclusivamente JSON conforme al schema.
                """;
    }

    public String relationshipUserPrompt(VisionGeometryEdgeCandidate edge, List<VisionClassProposal> classes) {
        Map<String, VisionClassProposal> byRef = classes.stream()
                .collect(Collectors.toMap(VisionClassProposal::ref, Function.identity()));
        StringBuilder out = new StringBuilder();
        VisionClassProposal a = byRef.get(edge.aClassRef());
        VisionClassProposal b = byRef.get(edge.bClassRef());
        out.append("Panel y par cerrado:\n- ").append(edge.edgeId())
                .append(": A=").append(edge.aClassRef()).append(' ')
                .append(quote(a == null ? edge.aClassRef() : a.name()))
                .append("; B=").append(edge.bClassRef()).append(' ')
                .append(quote(b == null ? edge.bClassRef() : b.name()))
                .append("; geometryScore=")
                .append(String.format(java.util.Locale.ROOT, "%.3f", edge.geometryScore()))
                .append('\n');
        out.append("geometryScore es solo una pista CV; la evidencia visual del panel manda. ");
        out.append("El par fisico esta cerrado. No cambies ni omitas endpoints.");
        return out.toString();
    }

    public String multiplicityTranscriptionSystemPrompt() {
        return """
                Ves un crop limpio centrado alrededor de un endpoint UML. Tu unica tarea es LEER y transcribir cualquier multiplicidad UML claramente legible proxima al centro.
                No decidas a que connector pertenece. No infieras por dominio, tipo de relacion o convenciones UML.
                Los formatos permitidos incluyen 1, *, 0..*, 1..*, 0..1 y N..M. Usa rawLabel=null solo si no hay texto de multiplicidad legible.
                Devuelve unicamente JSON.
                """;
    }

    public String multiplicityAttributionSystemPrompt() {
        return """
                 candidateRawLabel ya fue transcrito por otra etapa. NO lo retranscribas ni corrijas.
                 LABEL SOURCE muestra donde se observo ese texto. CLASS CONTEXT muestra los connectors fisicos de la clase.
                 RED y BLUE son ayudas visuales; cada connector tiene su edge ID. Devuelve el edge ID propietario del texto.
                 Si no pertenece a ninguno usa NONE. Si no puedes distinguirlo usa AMBIGUOUS.
                 No infieras por dominio, tipo UML ni endpoint opuesto. Devuelve unicamente JSON.
                """;
    }

    public String multiplicityUserPrompt(
            VisionGeometryEdgeCandidate edge,
            VisionHybridEndpoint endpoint,
            VisionClassProposal endpointClass
    ) {
        return edge.edgeId() + "\nendpoint=" + endpoint.name()
                + "\nclassRef=" + endpointClass.ref()
                + "\nclassName=" + quote(endpointClass.name());
    }

    public String multiplicityAttributionUserPrompt(
            VisionGeometryEdgeCandidate edge,
            VisionHybridEndpoint endpoint,
            VisionClassProposal endpointClass,
            String candidateRawLabel,
            List<String> visibleCompetingEdgeIds
    ) {
        return multiplicityUserPrompt(edge, endpoint, endpointClass)
                + "\ncandidateRawLabel=" + quote(candidateRawLabel)
                + "\nallowedOwnerIds=" + String.join(",", java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(edge.edgeId()), visibleCompetingEdgeIds.stream().sorted()
                ).toList());
    }

    @Deprecated
    public String multiplicityOwnershipSystemPrompt() {
        return multiplicityAttributionSystemPrompt();
    }

    @Deprecated
    public String multiplicityOwnershipUserPrompt(
            VisionGeometryEdgeCandidate edge, VisionHybridEndpoint endpoint,
            VisionClassProposal endpointClass, String candidateRawLabel
    ) {
        return multiplicityAttributionUserPrompt(edge, endpoint, endpointClass, candidateRawLabel, List.of());
    }

    private String quote(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", " ")
                .replace("\n", " ") + "\"";
    }
}
