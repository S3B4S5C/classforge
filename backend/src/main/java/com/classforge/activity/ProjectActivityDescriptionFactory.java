package com.classforge.activity;

import com.classforge.collaboration.application.ProjectCommandExecutor;
import com.classforge.collaboration.protocol.UmlCommandPayload;
import com.classforge.project.domain.document.*;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ProjectActivityDescriptionFactory {
    private final ProjectCommandExecutor executor;

    public ProjectActivityDescriptionFactory(ProjectCommandExecutor executor) { this.executor = executor; }

    public List<ActivityChange> changes(ProjectOperationPersistedEvent event) {
        if (event.command() == null) return documentChanges(event.beforeDocument(), event.afterDocument());
        List<ActivityChange> changes = new ArrayList<>();
        describe(event.beforeDocument(), event.command(), changes);
        return List.copyOf(changes);
    }

    /** Replay accepted batches in order so a rename followed by a delete retains both facts. */
    private ProjectDocument describe(ProjectDocument before, UmlCommandPayload command, List<ActivityChange> result) {
        if (command.type().name().equals("BATCH")) {
            ProjectDocument current = before;
            for (var child : command.safeCommands()) current = describe(current, child, result);
            return current;
        }
        var after = executor.execute(before, command);
        String description;
        UUID element;
        switch (command.type()) {
            case CREATE_CLASS, RESTORE_CLASS -> {
                element = command.umlClass().id();
                description = (command.type().name().equals("RESTORE_CLASS") ? "restauró" : "creó")
                        + " la clase " + command.umlClass().name();
            }
            case RENAME_CLASS -> {
                element = command.classId();
                description = "renombró la clase " + className(before, element) + " a " + command.name();
            }
            case DELETE_CLASS -> {
                element = command.classId(); description = "eliminó la clase " + className(before, element);
            }
            case MOVE_CLASS -> {
                element = command.classId(); description = "movió la clase " + className(before, element);
            }
            case ADD_ATTRIBUTE, UPDATE_ATTRIBUTE -> {
                element = command.attribute().id();
                description = (command.type().name().equals("ADD_ATTRIBUTE") ? "añadió " : "actualizó ")
                        + attributeLabel(command.attribute()) + " en " + className(before, command.classId());
            }
            case DELETE_ATTRIBUTE -> {
                element = command.attributeId();
                var owner = before.umlModel().classes().stream().filter(c -> c.id().equals(command.classId())).findFirst().orElseThrow();
                var attribute = owner.attributes().stream().filter(a -> a.id().equals(element)).findFirst().orElseThrow();
                description = "eliminó " + attributeLabel(attribute) + " de " + owner.name();
            }
            case CREATE_RELATIONSHIP, UPDATE_RELATIONSHIP -> {
                element = command.relationship().id();
                description = (command.type().name().equals("CREATE_RELATIONSHIP") ? "creó" : "actualizó")
                        + " la relación " + relationshipLabel(after, command.relationship());
            }
            case DELETE_RELATIONSHIP -> {
                element = command.relationshipId();
                var relation = before.umlModel().relationships().stream().filter(r -> r.id().equals(element)).findFirst().orElseThrow();
                description = "eliminó la relación " + relationshipLabel(before, relation);
            }
            default -> throw new IllegalArgumentException("Tipo de comando no soportado: " + command.type());
        }
        result.add(new ActivityChange(ActivityOperationType.valueOf(command.type().name()), element, description));
        return after;
    }

    public String description(ActivityOperationType type, List<ActivityChange> changes) {
        if (type == ActivityOperationType.BATCH) return "aplicó un lote de " + changes.size()
                + " operaciones: " + changes.stream().map(ActivityChange::description).collect(Collectors.joining("; "));
        if (type == ActivityOperationType.SAVE_DOCUMENT) return "guardó el documento (" + changes.size()
                + " cambios): " + (changes.isEmpty() ? "sin diferencias de contenido" :
                changes.stream().map(ActivityChange::description).collect(Collectors.joining("; ")));
        return changes.getFirst().description();
    }

    private List<ActivityChange> documentChanges(ProjectDocument before, ProjectDocument after) {
        List<ActivityChange> result = new ArrayList<>();
        var oldClasses = index(before.umlModel().classes(), UmlClass::id);
        var newClasses = index(after.umlModel().classes(), UmlClass::id);
        for (var oldClass : before.umlModel().classes()) {
            if (!newClasses.containsKey(oldClass.id())) add(result, ActivityOperationType.DELETE_CLASS, oldClass.id(), "eliminó la clase " + oldClass.name());
        }
        for (var newClass : after.umlModel().classes()) {
            var oldClass = oldClasses.get(newClass.id());
            if (oldClass == null) add(result, ActivityOperationType.CREATE_CLASS, newClass.id(), "creó la clase " + newClass.name());
            else if (!oldClass.name().equals(newClass.name())) add(result, ActivityOperationType.RENAME_CLASS, newClass.id(), "renombró la clase " + oldClass.name() + " a " + newClass.name());
            var oldAttrs = index(oldClass == null ? List.<UmlAttribute>of() : oldClass.attributes(), UmlAttribute::id);
            var newAttrs = index(newClass.attributes(), UmlAttribute::id);
            for (var oldAttr : oldAttrs.values()) {
                if (!newAttrs.containsKey(oldAttr.id())) add(result, ActivityOperationType.DELETE_ATTRIBUTE, oldAttr.id(), "eliminó " + attributeLabel(oldAttr) + " de " + newClass.name());
            }
            for (var attr : newClass.attributes()) {
                if (!oldAttrs.containsKey(attr.id())) add(result, ActivityOperationType.ADD_ATTRIBUTE, attr.id(), "añadió " + attributeLabel(attr) + " en " + newClass.name());
                else if (!oldAttrs.get(attr.id()).equals(attr)) add(result, ActivityOperationType.UPDATE_ATTRIBUTE, attr.id(), "actualizó " + attributeLabel(attr) + " en " + newClass.name());
            }
            if (oldClass != null && !Objects.equals(before.layout().nodes().get(newClass.id()), after.layout().nodes().get(newClass.id()))) {
                add(result, ActivityOperationType.MOVE_CLASS, newClass.id(), "actualizó la posición o tamaño de " + newClass.name());
            }
        }
        var oldRelations = index(before.umlModel().relationships(), UmlRelationship::id);
        var newRelations = index(after.umlModel().relationships(), UmlRelationship::id);
        for (var relation : before.umlModel().relationships()) {
            if (!newRelations.containsKey(relation.id())) add(result, ActivityOperationType.DELETE_RELATIONSHIP, relation.id(), "eliminó la relación " + relationshipLabel(before, relation));
        }
        for (var relation : after.umlModel().relationships()) {
            if (!oldRelations.containsKey(relation.id())) add(result, ActivityOperationType.CREATE_RELATIONSHIP, relation.id(), "creó la relación " + relationshipLabel(after, relation));
            else if (!oldRelations.get(relation.id()).equals(relation)) add(result, ActivityOperationType.UPDATE_RELATIONSHIP, relation.id(), "actualizó la relación " + relationshipLabel(after, relation));
        }
        return List.copyOf(result);
    }

    private <T> Map<UUID,T> index(List<T> elements, Function<T,UUID> id) {
        return elements.stream().collect(Collectors.toMap(id, Function.identity(), (a,b) -> b, LinkedHashMap::new));
    }
    private void add(List<ActivityChange> result, ActivityOperationType type, UUID id, String description) { result.add(new ActivityChange(type, id, description)); }
    private String className(ProjectDocument document, UUID id) {
        return document.umlModel().classes().stream().filter(c -> c.id().equals(id)).map(UmlClass::name).findFirst().orElse(id.toString());
    }
    private String attributeLabel(UmlAttribute attribute) {
        return attribute.name() + ": " + (attribute.customTypeName() != null && !attribute.customTypeName().isBlank() ? attribute.customTypeName() : attribute.dataType());
    }
    private String relationshipLabel(ProjectDocument document, UmlRelationship relation) {
        return className(document, relation.sourceClassId()) + " → " + className(document, relation.targetClassId()) + " (" + relation.type() + ")";
    }
}
