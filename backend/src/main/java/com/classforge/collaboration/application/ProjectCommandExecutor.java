package com.classforge.collaboration.application;

import com.classforge.collaboration.protocol.UmlCommandPayload;
import com.classforge.collaboration.protocol.UmlCommandType;
import com.classforge.project.domain.document.ProjectDocument;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Dispatcher/facade for the authoritative UML command engine. Individual command
 * families live in focused handlers; public semantics and command ordering stay frozen.
 */
@Component
public class ProjectCommandExecutor {
    private final ProjectCommandExecutionSupport support = new ProjectCommandExecutionSupport();
    private final ProjectCommandClassHandler classHandler = new ProjectCommandClassHandler(support);
    private final ProjectCommandAttributeHandler attributeHandler = new ProjectCommandAttributeHandler(support);
    private final ProjectCommandRelationshipHandler relationshipHandler = new ProjectCommandRelationshipHandler(support);
    private final ProjectCommandLayoutHandler layoutHandler = new ProjectCommandLayoutHandler(support);

    public ProjectDocument execute(ProjectDocument current, UmlCommandPayload command) {
        support.require(current != null, "DOCUMENT_REQUIRED", "The current project document is required");
        support.require(command != null, "COMMAND_REQUIRED", "The UML command is required");
        support.require(command.commandId() != null, "COMMAND_ID_REQUIRED", "commandId is required");
        support.require(command.type() != null, "COMMAND_TYPE_REQUIRED", "command type is required");

        return switch (command.type()) {
            case CREATE_CLASS -> classHandler.createClass(current, command);
            case RENAME_CLASS -> classHandler.renameClass(current, command);
            case DELETE_CLASS -> classHandler.deleteClass(current, command);
            case RESTORE_CLASS -> classHandler.restoreClass(current, command);
            case ADD_ATTRIBUTE -> attributeHandler.addAttribute(current, command);
            case UPDATE_ATTRIBUTE -> attributeHandler.updateAttribute(current, command);
            case DELETE_ATTRIBUTE -> attributeHandler.deleteAttribute(current, command);
            case CREATE_RELATIONSHIP -> relationshipHandler.createRelationship(current, command);
            case UPDATE_RELATIONSHIP -> relationshipHandler.updateRelationship(current, command);
            case DELETE_RELATIONSHIP -> relationshipHandler.deleteRelationship(current, command);
            case MOVE_CLASS -> layoutHandler.moveClass(current, command);
            case BATCH -> executeBatch(current, command);
        };
    }

    private ProjectDocument executeBatch(ProjectDocument current, UmlCommandPayload command) {
        List<UmlCommandPayload> commands = command.safeCommands();
        support.require(!commands.isEmpty() && commands.size() <= 50,
                "BATCH_SIZE_INVALID", "A BATCH must contain between 1 and 50 commands");
        ProjectDocument next = current;
        for (UmlCommandPayload child : commands) {
            support.require(child != null && child.type() != UmlCommandType.BATCH,
                    "NESTED_BATCH_NOT_ALLOWED", "Nested BATCH commands are not allowed");
            next = execute(next, child);
        }
        return next;
    }
}
