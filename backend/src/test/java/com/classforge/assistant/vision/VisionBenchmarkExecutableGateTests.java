package com.classforge.assistant.vision;

import com.classforge.assistant.AssistantActionType;
import com.classforge.assistant.AssistantAttributePlan;
import com.classforge.assistant.AssistantPlanAction;
import com.classforge.assistant.AssistantSemanticPlan;
import com.classforge.assistant.AssistantTypeSource;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlDataType;
import com.classforge.project.domain.document.UmlVisibility;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisionBenchmarkExecutableGateTests {

    private final VisionBenchmarkExecutableGate gate = new VisionBenchmarkExecutableGate();

    @Test
    void acceptsCanonicalPlan() {
        assertTrue(gate.evaluate(createClass("Cliente", "email"), ProjectDocument.empty()).accepted());
    }

    @Test
    void rejectsInvalidManualClassName() {
        assertFalse(gate.evaluate(createClass("Categoría", "email"), ProjectDocument.empty()).accepted());
    }

    @Test
    void rejectsInvalidManualAttributeName() {
        assertFalse(gate.evaluate(createClass("Libro", "añoPublicacion"), ProjectDocument.empty()).accepted());
    }

    private AssistantSemanticPlan createClass(String className, String attributeName) {
        return new AssistantSemanticPlan(
                "test",
                List.of(new AssistantPlanAction(
                        AssistantActionType.CREATE_CLASS,
                        className,
                        null,
                        List.of(new AssistantAttributePlan(
                                attributeName,
                                UmlDataType.STRING,
                                null,
                                UmlVisibility.PRIVATE,
                                true,
                                false,
                                AssistantTypeSource.EXPLICIT
                        )),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ))
        );
    }
}
