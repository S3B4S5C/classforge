package com.classforge.demo;

import com.classforge.integration.xmi.EnterpriseArchitectXmiImporter;
import com.classforge.integration.xmi.XmiImportResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ActiveProfiles("demo")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:classforge-cu27-demo;DB_CLOSE_DELAY=-1",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        }
)
class DemoScenarioAcceptanceIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private EnterpriseArchitectXmiImporter xmiImporter;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void deterministicVeterinaryDemoIsReadyForOwnerEditorAndXmiTransition() throws Exception {
        String ownerToken = login(DemoScenario.OWNER_EMAIL);
        String editorToken = login(DemoScenario.EDITOR_EMAIL);

        JsonNode ownerProject = projectFromList(ownerToken);
        assertEquals(DemoScenario.PROJECT_ID.toString(), ownerProject.get("id").asString());
        assertEquals(DemoScenario.PROJECT_NAME, ownerProject.get("name").asString());
        assertEquals("OWNER", ownerProject.get("accessRole").asString());
        assertEquals(1L, ownerProject.get("revision").asLong());
        verifyDocument(ownerProject.get("document"));

        JsonNode editorProject = projectFromList(editorToken);
        assertEquals(DemoScenario.PROJECT_ID.toString(), editorProject.get("id").asString());
        assertEquals("EDITOR", editorProject.get("accessRole").asString());
        verifyDocument(editorProject.get("document"));

        HttpResponse<byte[]> exported = export(ownerToken);
        assertEquals(200, exported.statusCode());
        String xmi = new String(exported.body(), StandardCharsets.UTF_8);
        assertTrue(xmi.contains("xmi:version=\"2.1\""));
        assertTrue(xmi.contains("name=\"Veterinaria CU-27\""));
        assertTrue(xmi.contains("name=\"Animal\""));
        assertTrue(xmi.contains("name=\"Usuario\""));

        XmiImportResult roundTrip = xmiImporter.importXmi(exported.body());
        assertEquals(6, roundTrip.classCount());
        assertEquals(5, roundTrip.relationshipCount());
        assertTrue(roundTrip.document().umlModel().classes().stream()
                .anyMatch(item -> item.id().toString().equals("27000000-0000-0000-0000-000000000102")
                        && item.name().equals("Animal")));
        assertTrue(roundTrip.document().umlModel().classes().stream()
                .anyMatch(item -> item.name().equals("Usuario")
                        && item.attributes().stream().anyMatch(attribute -> attribute.name().equals("username"))
                        && item.attributes().stream().anyMatch(attribute -> attribute.name().equals("password"))));
    }

    private String login(String email) throws Exception {
        HttpResponse<String> response = send(
                "POST",
                "/api/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"" + DemoScenario.PASSWORD + "\"}",
                null
        );
        assertEquals(200, response.statusCode(), response.body());
        String token = jsonMapper.readTree(response.body()).get("accessToken").asString();
        assertFalse(token.isBlank());
        return token;
    }

    private JsonNode projectFromList(String token) throws Exception {
        HttpResponse<String> response = send("GET", "/api/projects", null, token);
        assertEquals(200, response.statusCode(), response.body());
        JsonNode projects = jsonMapper.readTree(response.body());
        assertEquals(1, projects.size());
        return projects.get(0);
    }

    private void verifyDocument(JsonNode document) {
        assertEquals("1.0", document.get("schemaVersion").asString());
        JsonNode classes = document.at("/umlModel/classes");
        JsonNode relationships = document.at("/umlModel/relationships");
        assertEquals(6, classes.size());
        assertEquals(5, relationships.size());
        assertTrue(hasClass(classes, "Propietario"));
        assertTrue(hasClass(classes, "Animal"));
        assertTrue(hasClass(classes, "Veterinario"));
        assertTrue(hasClass(classes, "Cita"));
        assertTrue(hasClass(classes, "Tratamiento"));
        assertTrue(hasClass(classes, "Usuario"));
    }

    private boolean hasClass(JsonNode classes, String name) {
        for (JsonNode item : classes) {
            if (name.equals(item.get("name").asString())) {
                return true;
            }
        }
        return false;
    }

    private HttpResponse<byte[]> export(String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri("/api/projects/" + DemoScenario.PROJECT_ID + "/xmi/export"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<String> send(String method, String path, String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri(path))
                .header("Accept", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
