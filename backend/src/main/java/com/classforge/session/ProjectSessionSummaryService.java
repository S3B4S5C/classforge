package com.classforge.session;

import com.classforge.activity.ActivityChange;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;

@Service
public class ProjectSessionSummaryService {
    private final JsonMapper json;
    public ProjectSessionSummaryService(JsonMapper json) { this.json = json; }

    public Map<String,Long> counts(ProjectSessionEntity session) {
        return new TreeMap<>(json.readValue(session.getOperationCountsJson(), new TypeReference<Map<String,Long>>() { }));
    }

    public String increment(ProjectSessionEntity session, List<ActivityChange> changes) {
        Map<String,Long> counts = counts(session);
        changes.forEach(change -> counts.merge(change.operationType().name(), 1L, Long::sum));
        return json.writeValueAsString(counts);
    }

    public void refresh(ProjectSessionEntity session, List<ProjectSession.Participant> participants) {
        StringBuilder text = new StringBuilder("Sesión iniciada: ").append(session.getStartedAt())
                .append("\nEstado: ").append(session.getStatus());
        if (session.getEndedAt() != null) text.append("\nFin: ").append(session.getEndedAt()).append(" (").append(session.getCloseReason()).append(")");
        text.append("\nParticipantes:");
        participants.forEach(p -> text.append("\n- ").append(p.displayName()).append(": ")
                .append(p.operations()).append(" operaciones, ").append(p.changes()).append(" cambios"));
        text.append("\nCambios:");
        counts(session).forEach((type, count) -> text.append("\n- ").append(label(type)).append(": ").append(count));
        text.append("\nTotal: ").append(session.getTotalOperations()).append(" operaciones aceptadas; ")
                .append(session.getTotalChanges()).append(" cambios detallados")
                .append("\nRevisión: ").append(session.getStartRevision()).append(" → ").append(session.getEndRevision());
        session.summarize(text.toString());
    }

    private String label(String type) {
        return switch (type) {
            case "CREATE_CLASS" -> "Clases creadas";
            case "RENAME_CLASS" -> "Clases renombradas";
            case "DELETE_CLASS" -> "Clases eliminadas";
            case "RESTORE_CLASS" -> "Clases restauradas";
            case "ADD_ATTRIBUTE" -> "Atributos añadidos";
            case "UPDATE_ATTRIBUTE" -> "Atributos actualizados";
            case "DELETE_ATTRIBUTE" -> "Atributos eliminados";
            case "CREATE_RELATIONSHIP" -> "Relaciones creadas";
            case "UPDATE_RELATIONSHIP" -> "Relaciones actualizadas";
            case "DELETE_RELATIONSHIP" -> "Relaciones eliminadas";
            case "MOVE_CLASS" -> "Posiciones o tamaños actualizados";
            default -> type;
        };
    }
}
