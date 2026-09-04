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
                Recibes una hoja de evidencia formada por paneles E1, E2, ...; cada panel corresponde a un PAR FIJO de clases.
                Java/OpenCV ya fijo los dos endpoints candidatos. Tu tarea NO es elegir otras clases: decide solamente si el panel muestra una relacion directa entre A y B y, si existe, anota tipo y multiplicidades.

                Reglas de topologia:
                - Emite un edge solamente cuando una MISMA linea/conector visible une directamente las dos cajas etiquetadas A y B del panel.
                - Una linea que pasa cerca, cruza el panel o conecta una de las cajas con una tercera caja NO cuenta.
                - No inventes relaciones por proximidad, significado del dominio o porque sean habituales.
                - Si el panel es ambiguo, omite ese edge. Precision antes que recall.

                Marcadores:
                - linea simple => ASSOCIATION, markerAt=NONE.
                - rombo hueco/blanco => AGGREGATION; markerAt indica A o B segun la caja tocada por el rombo.
                - rombo relleno/negro => COMPOSITION; markerAt indica A o B.
                - triangulo hueco => GENERALIZATION; markerAt indica A o B segun la SUPERCLASE tocada por el triangulo.
                - No conviertas una linea simple en agregacion/composicion por conocimiento del dominio.

                Multiplicidades:
                - Lee exclusivamente los rotulos pequenos junto a los extremos A y B del conector confirmado.
                - 1 => lower=1 upper=1 unbounded=false.
                - 0..1 => lower=0 upper=1 unbounded=false.
                - * o 0..* => lower=0 upper=null unbounded=true.
                - 1..* => lower=1 upper=null unbounded=true.
                - Si no es legible, usa null. Nunca inventes 0..1 o 1:1 por defecto.

                Evidence:
                - evidenceLabel debe describir brevemente lo visible en ese panel (nombres/marker/multiplicidades), no conocimiento inferido.
                - Solo puedes usar edgeId incluidos por el usuario.
                Devuelve exclusivamente JSON conforme al schema.
                """;
    }

    public String relationshipUserPrompt(
            List<VisionGeometryEdgeCandidate> candidates,
            List<VisionClassProposal> classes
    ) {
        Map<String, VisionClassProposal> byRef = classes.stream()
                .collect(Collectors.toMap(VisionClassProposal::ref, Function.identity()));
        StringBuilder out = new StringBuilder();
        out.append("Paneles y pares cerrados:\n");
        for (VisionGeometryEdgeCandidate edge : candidates) {
            VisionClassProposal a = byRef.get(edge.aClassRef());
            VisionClassProposal b = byRef.get(edge.bClassRef());
            out.append("- ").append(edge.edgeId())
                    .append(": A=").append(edge.aClassRef()).append(' ')
                    .append(quote(a == null ? edge.aClassRef() : a.name()))
                    .append("; B=").append(edge.bClassRef()).append(' ')
                    .append(quote(b == null ? edge.bClassRef() : b.name()))
                    .append("; geometryScore=")
                    .append(String.format(java.util.Locale.ROOT, "%.3f", edge.geometryScore()))
                    .append('\n');
        }
        out.append("geometryScore es solo una pista CV; la evidencia visual del panel manda. ");
        out.append("Omitir un edge ambiguo es correcto. No cambies endpoints.");
        return out.toString();
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
