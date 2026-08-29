package com.classforge.assistant.tools;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class AssistantNativeToolSpringWiringTests {

    @Autowired
    private DynamicUmlToolCatalog dynamicUmlToolCatalog;

    @Autowired
    private AssistantNativeToolPlanner nativeToolPlanner;

    @Autowired
    private AssistantLiteralArgumentBinder literalArgumentBinder;

    @Autowired
    private AssistantToolRouteAdjudicator routeAdjudicator;

    @Test
    void nativeToolFoundationIsRegisteredInSpringContext() {
        assertNotNull(dynamicUmlToolCatalog);
        assertNotNull(nativeToolPlanner);
        assertNotNull(literalArgumentBinder);
        assertNotNull(routeAdjudicator);
    }
}
