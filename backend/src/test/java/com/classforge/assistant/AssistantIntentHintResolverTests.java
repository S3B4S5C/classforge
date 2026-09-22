package com.classforge.assistant;

import com.classforge.project.domain.document.DiagramLayout;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlAttribute;
import com.classforge.project.domain.document.UmlClass;
import com.classforge.project.domain.document.UmlDataType;
import com.classforge.project.domain.document.UmlModel;
import com.classforge.project.domain.document.UmlVisibility;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantIntentHintResolverTests {

    private final AssistantIntentHintResolver resolver =
            new AssistantIntentHintResolver();

    @Test
    void detectsCleanRenameWithoutReadingModelState() {
        assertHint(
                "Renombra Veterinario a MedicoVeterinario",
                AssistantActionType.RENAME_CLASS
        );
    }

    @Test
    void toleratesTypoInRenameVerb() {
        assertHint(
                "renonbra Veterinário a MedicoVeterinario",
                AssistantActionType.RENAME_CLASS
        );
    }

    @Test
    void distinguishesAttributeRenameFromClassRename() {
        assertHint(
                "En Mascota renombra el atributo peso a pesoKg",
                AssistantActionType.UPDATE_ATTRIBUTE
        );
    }


    @Test
    void detectsAssociationClassWithoutMisroutingItAsPlainRelationship() {
        assertHint(
                "Crea una clase de asociacion DetallePedido entre Pedido y Producto",
                AssistantActionType.CREATE_ASSOCIATION_CLASS
        );
        assertHint(
                "Convierte Pedido Producto en la clase intermedia DetallePedido",
                AssistantActionType.CREATE_ASSOCIATION_CLASS
        );
    }

    @Test
    void detectsMultiplicityUpdateAsRelationshipUpdate() {
        assertHint(
                "En la relacion entre Propietario y Mascota cambia la multiplicidad de Mascota a 0..*",
                AssistantActionType.UPDATE_RELATIONSHIP
        );
    }

    @Test
    void detectsUnknownRelationshipRequestWithoutMakingItSafe() {
        assertHint(
                "Relaciona Mascota con FantasmaQueNoExiste",
                AssistantActionType.CREATE_RELATIONSHIP
        );
    }

    @Test
    void keepsAmbiguousGenericChangeUnconstrained() {
        assertTrue(
                resolver.resolve("Cambia esto para que quede mejor").isEmpty()
        );
    }


    @Test
    void infersAttributeUpdateFromExistingScopedAttributeEvenWithoutWordAttribute() {
        ProjectDocument document = documentWithMascotaPeso();

        AssistantIntentHintResolver.IntentHint hint = resolver.resolve(
                "En Masctoa cambia pseo por pesoKg",
                document
        ).orElseThrow();

        assertEquals(AssistantActionType.UPDATE_ATTRIBUTE, hint.actionType());
    }

    @Test
    void infersDeleteAttributeFromScopedExistingAttribute() {
        ProjectDocument document = documentWithMascotaPeso();

        AssistantIntentHintResolver.IntentHint hint = resolver.resolve(
                "Borra pseo de Masctoa",
                document
        ).orElseThrow();

        assertEquals(AssistantActionType.DELETE_ATTRIBUTE, hint.actionType());
    }

    private ProjectDocument documentWithMascotaPeso() {
        UmlClass mascota = new UmlClass(
                UUID.randomUUID(),
                "Mascota",
                List.of(
                        new UmlAttribute(
                                UUID.randomUUID(),
                                "peso",
                                UmlDataType.DECIMAL,
                                null,
                                UmlVisibility.PRIVATE,
                                true,
                                false
                        )
                )
        );

        return new ProjectDocument(
                ProjectDocument.CURRENT_SCHEMA_VERSION,
                new UmlModel(List.of(mascota), List.of()),
                DiagramLayout.empty()
        );
    }

    private void assertHint(
            String text,
            AssistantActionType expected
    ) {
        AssistantIntentHintResolver.IntentHint hint =
                resolver.resolve(text).orElseThrow();

        assertEquals(expected, hint.actionType());
        assertEquals(1.0d, hint.confidence());
    }
}
