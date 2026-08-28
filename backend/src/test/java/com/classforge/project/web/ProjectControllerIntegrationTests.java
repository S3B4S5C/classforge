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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:classforge-project-web-test;DB_CLOSE_DELAY=-1",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "classforge.security.jwt.secret=test-secret-long-enough-for-classforge-integration-tests-2026"
        }
)
class ProjectControllerIntegrationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void authenticatedUsersOnlySeeTheirOwnProjects() throws Exception {
        String tokenA = register("Ana", "ana@classforge.test");
        String tokenB = register("Bruno", "bruno@classforge.test");

        HttpResponse<String> createResponse = send(
                "POST",
                "/api/projects",
                """
                {"name":"Veterinaria"}
                """,
                tokenA
        );

        assertEquals(201, createResponse.statusCode());
        JsonNode created = jsonMapper.readTree(createResponse.body());
        String projectId = created.get("id").asString();

        assertEquals("1.0", created.at("/document/schemaVersion").asString());
        assertEquals(0, created.at("/document/umlModel/classes").size());

        HttpResponse<String> listA = send(
                "GET",
                "/api/projects",
                null,
                tokenA
        );
        assertEquals(1, jsonMapper.readTree(listA.body()).size());

        HttpResponse<String> listB = send(
                "GET",
                "/api/projects",
                null,
                tokenB
        );
        assertEquals(0, jsonMapper.readTree(listB.body()).size());

        HttpResponse<String> foreignRead = send(
                "GET",
                "/api/projects/" + projectId,
                null,
                tokenB
        );
        assertEquals(404, foreignRead.statusCode());
    }

    @Test
    void renamesAndSavesDocumentUsingRevision() throws Exception {
        String token = register("Sebas", "sebas@classforge.test");

        HttpResponse<String> createdResponse = send(
                "POST",
                "/api/projects",
                """
                {"name":"Veterinaria"}
                """,
                token
        );

        String projectId = jsonMapper
                .readTree(createdResponse.body())
                .get("id")
                .asString();

        HttpResponse<String> renamedResponse = send(
                "PATCH",
                "/api/projects/" + projectId,
                """
                {"name":"Veterinaria Central"}
                """,
                token
        );

        assertEquals(200, renamedResponse.statusCode());
        JsonNode renamed = jsonMapper.readTree(renamedResponse.body());
        assertEquals("Veterinaria Central", renamed.get("name").asString());
        assertEquals(0L, renamed.get("revision").asLong());

        String savePayload = """
                {
                  "baseRevision":0,
                  "document":{
                    "schemaVersion":"1.0",
                    "umlModel":{
                      "classes":[],
                      "relationships":[]
                    },
                    "layout":{
                      "nodes":{}
                    }
                  }
                }
                """;

        HttpResponse<String> savedResponse = send(
                "PUT",
                "/api/projects/" + projectId + "/document",
                savePayload,
                token
        );

        assertEquals(200, savedResponse.statusCode());
        assertEquals(
                1L,
                jsonMapper.readTree(savedResponse.body())
                        .get("revision")
                        .asLong()
        );

        HttpResponse<String> staleResponse = send(
                "PUT",
                "/api/projects/" + projectId + "/document",
                savePayload,
                token
        );

        assertEquals(409, staleResponse.statusCode());

        JsonNode conflict = jsonMapper.readTree(staleResponse.body());
        assertEquals(
                "PROJECT_REVISION_CONFLICT",
                conflict.get("error").asString()
        );
        assertEquals(1L, conflict.get("currentRevision").asLong());
    }

    @Test
    void projectEndpointsRequireAuthentication() throws Exception {
        HttpResponse<String> response = send(
                "GET",
                "/api/projects",
                null,
                null
        );

        assertEquals(401, response.statusCode());
    }

    private String register(
            String displayName,
            String email
    ) throws Exception {
        HttpResponse<String> response = send(
                "POST",
                "/api/auth/register",
                """
                {
                  "displayName":"%s",
                  "email":"%s",
                  "password":"password123"
                }
                """.formatted(displayName, email),
                null
        );

        assertEquals(201, response.statusCode());

        return jsonMapper
                .readTree(response.body())
                .get("accessToken")
                .asString();
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
            builder.method(
                    method,
                    HttpRequest.BodyPublishers.noBody()
            );
        } else {
            builder.header("Content-Type", "application/json");
            builder.method(
                    method,
                    HttpRequest.BodyPublishers.ofString(body)
            );
        }

        return httpClient.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }
}