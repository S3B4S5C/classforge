package com.classforge.assistant.tools;

import com.classforge.project.domain.document.ProjectDocument;

import java.util.List;

public interface AssistantToolCallingGateway {

    default List<AssistantToolInvocation> call(
            String userText,
            ProjectDocument document,
            AssistantToolCatalog catalog
    ) {
        return call(userText, document, catalog, List.of());
    }

    List<AssistantToolInvocation> call(
            String userText,
            ProjectDocument document,
            AssistantToolCatalog catalog,
            List<AssistantToolConversationTurn> history
    );
}
