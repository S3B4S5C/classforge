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
                "POST", "/api/projects", """
                {"name":"Veterinaria"}
                """, tokenA
        );

        assertEquals(201, createResponse.statusCode());
        String projectId = jsonMapper.readTree(createResponse.body()).get("id").asString();

        HttpResponse<String> listA = send("GET", "/api/projects", null, tokenA);
        assertEquals(1, jsonMapper.readTree(listA.body()).size());

        HttpResponse<String> listB = send("GET", "/api/projects", null, tokenB);
        assertEquals(0, jsonMapper.readTree(listB.body()).size());

        HttpResponse<String> foreignRead = send("GET", "/api/projects/" + projectId, null, tokenB);
        assertEquals(404, foreignRead.statusCode());
    }

    @Test
    void projectEndpointsRequireAuthentication() throws Exception {
        HttpResponse<String> response = send("GET", "/api/projects", null, null);
        assertEquals(401, response.statusCode());
    }

    private String register(String displayName, String email) throws Exception {
        HttpResponse<String> response = send(
                "POST", "/api/auth/register", """
                {
                  "displayName":"%s",
                  "email":"%s",
                  "password":"password123"
                }
                """.formatted(displayName, email), null
        );

        assertEquals(201, response.statusCode());
        return jsonMapper.readTree(response.body()).get("accessToken").asString();
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