package com.classforge.project.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:classforge-validation-web-test;DB_CLOSE_DELAY=-1",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "classforge.security.jwt.secret=test-secret-long-enough-for-classforge-validation-tests-2026"
        }
)
class ProjectValidationControllerIntegrationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void returnsFriendly400WithViolationList() throws Exception {
        String token = register();
        String projectId = createProject(token);
        String classA = UUID.randomUUID().toString();
        String classB = UUID.randomUUID().toString();

        HttpResponse<String> response = send(
                "PUT",
                "/api/projects/" + projectId + "/document",
                """
                {
                  "baseRevision": 0,
                  "document": {
                    "schemaVersion": "1.0",
                    "umlModel": {
                      "classes": [
                        {"id": "%s", "name": "Animal", "attributes": []},
                        {"id": "%s", "name": "Animal", "attributes": []}
                      ],
                      "relationships": []
                    },
                    "layout": {"nodes": {}}
                  }
                }
                """.formatted(classA, classB),
                token
        );

        assertEquals(400, response.statusCode());

        JsonNode body = jsonMapper.readTree(response.body());
        assertEquals("VALIDATION_ERROR", body.get("error").asString());
        assertTrue(body.get("violations").isArray());

        boolean duplicateFound = false;
        for (JsonNode violation : body.get("violations")) {
            if ("DUPLICATE_CLASS_NAME".equals(violation.get("code").asString())) {
                duplicateFound = true;
                assertTrue(violation.get("message").asString().contains("Animal"));
            }
        }
        assertTrue(duplicateFound);
    }

    private String register() throws Exception {
        HttpResponse<String> response = send(
                "POST",
                "/api/auth/register",
                """
                {
                  "displayName":"Validation User",
                  "email":"validation@classforge.test",
                  "password":"password123"
                }
                """,
                null
        );

        assertEquals(201, response.statusCode());
        return jsonMapper.readTree(response.body()).get("accessToken").asString();
    }

    private String createProject(String token) throws Exception {
        HttpResponse<String> response = send(
                "POST",
                "/api/projects",
                """
                {"name":"ValidationProject"}
                """,
                token
        );

        assertEquals(201, response.statusCode());
        return jsonMapper.readTree(response.body()).get("id").asString();
    }

    private HttpResponse<String> send(
            String method,
            String path,
            String body,
            String token
    ) throws Exception {
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

        return httpClient.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }
}