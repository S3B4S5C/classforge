package com.classforge.assistant;

import com.classforge.project.domain.document.ProjectDocument;

public interface LanguageModelGateway {

    AssistantSemanticPlan plan(
            String userText,
            ProjectDocument document
    );
}