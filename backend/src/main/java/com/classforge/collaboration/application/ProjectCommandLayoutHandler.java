package com.classforge.collaboration.application;

import com.classforge.collaboration.protocol.UmlCommandPayload;
import com.classforge.project.domain.document.*;
import java.util.*;

final class ProjectCommandLayoutHandler {
    private final ProjectCommandExecutionSupport support;

    ProjectCommandLayoutHandler(ProjectCommandExecutionSupport support) {
        this.support = support;
    }

    ProjectDocument moveClass(
            ProjectDocument current,
            UmlCommandPayload command
    ) {
        UUID classId =
                support.requireId(
                        command.classId(),
                        "CLASS_ID_REQUIRED",
                        "MOVE_CLASS requires classId"
                );

        support.require(
                support.findClass(
                        current,
                        classId
                ) != null,
                "CLASS_NOT_FOUND",
                "The class no longer exists"
        );

        DiagramNodeLayout layout =
                command.layout();

        support.require(
                layout != null,
                "LAYOUT_REQUIRED",
                "MOVE_CLASS requires layout"
        );

        HashMap<UUID, DiagramNodeLayout> nodes =
                support.nodes(current);

        nodes.put(
                classId,
                layout
        );

        return support.document(
                current,
                support.classes(current),
                support.relationships(current),
                nodes
        );
    }
}
