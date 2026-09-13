package com.classforge.generation.spring.api.contract;

import static org.junit.jupiter.api.Assertions.*;

import com.classforge.generation.spring.api.SpringApiEntityModel;
import com.classforge.generation.spring.api.SpringApiGenerationPlan;
import com.classforge.generation.spring.application.SpringBootGenerationMode;
import com.classforge.generation.spring.model.SpringEntityIdModel;
import com.classforge.generation.spring.model.SpringIdFieldModel;
import com.classforge.generation.spring.model.SpringIdKind;
import com.classforge.generation.spring.model.SpringJavaType;
import com.classforge.generation.spring.model.SpringScalarFieldModel;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpringApiContractPlannerTests {
    @Test
    void derivesCrudAndAuthOperationsFromOneCanonicalPlan() {
        UUID classId = UUID.randomUUID();
        UUID idId = UUID.randomUUID();
        UUID usernameId = UUID.randomUUID();
        UUID passwordId = UUID.randomUUID();
        SpringEntityIdModel id = new SpringEntityIdModel(
                SpringIdKind.SIMPLE,
                SpringJavaType.UUID,
                null,
                List.of(new SpringIdFieldModel(idId, "id", "id", SpringJavaType.UUID)),
                true
        );
        SpringApiEntityModel entity = new SpringApiEntityModel(
                classId,
                "Usuario",
                "usuario",
                "UsuarioRepository",
                id,
                List.of(
                        new SpringScalarFieldModel(idId, "id", "id", "id", SpringJavaType.UUID, false, true),
                        new SpringScalarFieldModel(usernameId, "username", "username", "username", SpringJavaType.STRING, false, false),
                        new SpringScalarFieldModel(passwordId, "password", "password", "password", SpringJavaType.STRING, false, false)
                ),
                List.of(),
                List.of(),
                true,
                usernameId,
                passwordId
        );
        SpringApiContract contract = new SpringApiContractPlanner().plan(new SpringApiGenerationPlan(
                SpringBootGenerationMode.AUTH_INFORMATION_SYSTEM,
                List.of(entity)
        ));

        assertTrue(contract.authEnabled());
        assertEquals(8, contract.operations().size());
        assertEquals(List.of(
                "bootstrapAuthentication",
                "loginAuthentication",
                "listUsuario",
                "countUsuario",
                "createUsuario",
                "getUsuario",
                "updateUsuario",
                "deleteUsuario"
        ), contract.operations().stream().map(SpringApiContractOperation::operationId).toList());
        assertFalse(contract.operations().getFirst().authenticationRequired());
        assertTrue(contract.operations().stream()
                .filter(operation -> operation.operationId().equals("listUsuario"))
                .findFirst().orElseThrow().authenticationRequired());
        SpringApiContractOperation list = contract.operations().stream()
                .filter(operation -> operation.operationId().equals("listUsuario"))
                .findFirst().orElseThrow();
        assertTrue(list.parameters().stream().anyMatch(parameter -> parameter.name().equals("filter.username")));
        assertTrue(list.parameters().stream().noneMatch(parameter -> parameter.name().equals("filter.password")));
    }
}
