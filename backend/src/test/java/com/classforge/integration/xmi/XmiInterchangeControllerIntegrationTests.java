package com.classforge.integration.xmi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:classforge-xmi-web-test;DB_CLOSE_DELAY=-1",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "classforge.security.jwt.secret=test-secret-long-enough-for-classforge-xmi-integration-tests-2026"
        }
)
class XmiInterchangeControllerIntegrationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void previewApplyExportAndReimportAreRevisionSafe() throws Exception {
        String token = register("XMI User", "xmi@classforge.test");
        String projectId = createProject(token, "Veterinaria XMI");

        HttpResponse<String> previewResponse = multipart(
                "/api/projects/" + projectId + "/xmi/import/preview",
                token,
                fixture()
        );
        assertEquals(200, previewResponse.statusCode());
        JsonNode preview = jsonMapper.readTree(previewResponse.body());
        assertEquals(5, preview.get("classCount").asInt());
        assertEquals(4, preview.get("relationshipCount").asInt());
        assertEquals(0L, preview.get("baseRevision").asLong());
        String previewToken = preview.get("previewToken").asString();

        HttpResponse<String> beforeApply = send("GET", "/api/projects/" + projectId, null, token);
        assertEquals(0, jsonMapper.readTree(beforeApply.body()).at("/document/umlModel/classes").size());

        HttpResponse<String> applied = send(
                "POST",
                "/api/projects/" + projectId + "/xmi/import/apply",
                "{\"previewToken\":\"" + previewToken + "\"}",
                token
        );
        assertEquals(200, applied.statusCode());
        JsonNode appliedJson = jsonMapper.readTree(applied.body());
        assertEquals(1L, appliedJson.get("revision").asLong());
        assertEquals(5, appliedJson.at("/document/umlModel/classes").size());

        HttpResponse<byte[]> exported = export(projectId, token);
        assertEquals(200, exported.statusCode());
        assertTrue(exported.headers().firstValue("Content-Disposition").orElse("").contains("enterprise-architect.xmi"));
        String xmi = new String(exported.body(), StandardCharsets.UTF_8);
        assertTrue(xmi.contains("xmi:version=\"2.1\""));
        assertTrue(xmi.contains("exporter=\"ClassForge\""));
        assertTrue(xmi.contains("aggregation=\"composite\""));

        HttpResponse<String> rePreviewResponse = multipart(
                "/api/projects/" + projectId + "/xmi/import/preview",
                token,
                exported.body()
        );
        assertEquals(200, rePreviewResponse.statusCode());
        JsonNode rePreview = jsonMapper.readTree(rePreviewResponse.body());
        assertEquals(1L, rePreview.get("baseRevision").asLong());
        assertEquals(5, rePreview.get("classCount").asInt());
        assertEquals(4, rePreview.get("relationshipCount").asInt());

        HttpResponse<String> reApplied = send(
                "POST",
                "/api/projects/" + projectId + "/xmi/import/apply",
                "{\"previewToken\":\"" + rePreview.get("previewToken").asString() + "\"}",
                token
        );
        assertEquals(200, reApplied.statusCode());
        assertEquals(2L, jsonMapper.readTree(reApplied.body()).get("revision").asLong());
    }

    @Test
    void previewTokenIsOneShotAndStaleRevisionFailsClosed() throws Exception {
        String token = register("Stale User", "xmi-stale@classforge.test");
        String projectId = createProject(token, "Stale XMI");
        JsonNode preview = jsonMapper.readTree(multipart(
                "/api/projects/" + projectId + "/xmi/import/preview",
                token,
                fixture()
        ).body());

        HttpResponse<String> save = send(
                "PUT",
                "/api/projects/" + projectId + "/document",
                """
                {"baseRevision":0,"document":{"schemaVersion":"1.0","umlModel":{"classes":[],"relationships":[]},"layout":{"nodes":{}}}}
                """,
                token
        );
        assertEquals(200, save.statusCode());

        String body = "{\"previewToken\":\"" + preview.get("previewToken").asString() + "\"}";
        HttpResponse<String> stale = send("POST", "/api/projects/" + projectId + "/xmi/import/apply", body, token);
        assertEquals(409, stale.statusCode());
        assertEquals("PROJECT_REVISION_CONFLICT", jsonMapper.readTree(stale.body()).get("error").asString());

        HttpResponse<String> consumed = send("POST", "/api/projects/" + projectId + "/xmi/import/apply", body, token);
        assertEquals(400, consumed.statusCode());
        assertEquals("XMI_PREVIEW_NOT_FOUND", jsonMapper.readTree(consumed.body()).get("error").asString());
    }

    private String register(String displayName, String email) throws Exception {
        HttpResponse<String> response = send(
                "POST",
                "/api/auth/register",
                "{\"displayName\":\"" + displayName + "\",\"email\":\"" + email + "\",\"password\":\"password123\"}",
                null
        );
        assertEquals(201, response.statusCode());
        return jsonMapper.readTree(response.body()).get("accessToken").asString();
    }

    private String createProject(String token, String name) throws Exception {
        HttpResponse<String> response = send("POST", "/api/projects", "{\"name\":\"" + name + "\"}", token);
        assertEquals(201, response.statusCode());
        return jsonMapper.readTree(response.body()).get("id").asString();
    }

    private byte[] fixture() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/xmi/enterprise-architect-veterinaria.xmi")) {
            assertNotNull(input);
            return input.readAllBytes();
        }
    }

    private HttpResponse<String> multipart(String path, String token, byte[] file) throws Exception {
        String boundary = "----ClassForgeXmi" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.write("Content-Disposition: form-data; name=\"file\"; filename=\"veterinaria.xmi\"\r\n".getBytes(StandardCharsets.UTF_8));
        body.write("Content-Type: application/xml\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        body.write(file);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<byte[]> export(String projectId, String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/projects/" + projectId + "/xmi/export"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<String> send(String method, String path, String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
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
}
