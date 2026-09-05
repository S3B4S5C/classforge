package com.classforge.assistant.vision;

import com.classforge.assistant.AssistantPlanningException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VisionCodeIdentifierCanonicalizerTests {

    private final VisionCodeIdentifierCanonicalizer canonicalizer =
            new VisionCodeIdentifierCanonicalizer();

    @Test
    void canonicalizesSpecifiedVisualIdentifiers() {
        assertEquals("Categoria", canonicalizer.canonicalize("Categoría", "field"));
        assertEquals("Prestamo", canonicalizer.canonicalize("Préstamo", "field"));
        assertEquals("anoPublicacion", canonicalizer.canonicalize("añoPublicacion", "field"));
        assertEquals("Linea_Pedido", canonicalizer.canonicalize("Línea Pedido", "field"));
        assertEquals("fecha_devolucion", canonicalizer.canonicalize("fecha-devolución", "field"));
        assertEquals("_2FA", canonicalizer.canonicalize("2FA", "field"));
    }

    @Test
    void preservesSpecifiedValidIdentifiers() {
        assertEquals("_private", canonicalizer.canonicalize("_private", "field"));
        assertEquals("Linea__Pedido", canonicalizer.canonicalize("Linea__Pedido", "field"));
    }

    @Test
    void rejectsInvalidOnlyAndMissingIdentifiers() {
        assertThrows(AssistantPlanningException.class, () -> canonicalizer.canonicalize("---", "field"));
        assertThrows(AssistantPlanningException.class, () -> canonicalizer.canonicalize("用户", "field"));
        assertThrows(AssistantPlanningException.class, () -> canonicalizer.canonicalize(" ", "field"));
        assertThrows(AssistantPlanningException.class, () -> canonicalizer.canonicalize(null, "field"));
    }
}
