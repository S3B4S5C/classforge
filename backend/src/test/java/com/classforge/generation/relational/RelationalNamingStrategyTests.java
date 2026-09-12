package com.classforge.generation.relational;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class RelationalNamingStrategyTests {
    private final RelationalNamingStrategy naming = new RelationalNamingStrategy();
    @Test void convertsCodeReadyNames() {
        assertEquals("cliente", naming.toSnakeCase("Cliente"));
        assertEquals("linea_pedido", naming.toSnakeCase("LineaPedido"));
        assertEquals("fecha_prestamo", naming.toSnakeCase("fechaPrestamo"));
        assertEquals("url_externa", naming.toSnakeCase("URLExterna"));
        assertEquals("cliente2_fa", naming.toSnakeCase("Cliente2FA"));
        assertEquals("already_snake", naming.toSnakeCase("already_snake"));
    }
}
