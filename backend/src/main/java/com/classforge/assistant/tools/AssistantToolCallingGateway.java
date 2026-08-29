package com.classforge.assistant.tools;

import com.classforge.project.domain.document.ProjectDocument;

import java.util.List;

public interface AssistantToolCallingGateway {

    List<AssistantToolInvocation> call(
            String userText,
            ProjectDocument document,
            AssistantToolCatalog catalog
    );
}
