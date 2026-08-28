package com.classforge.auth;

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
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:classforge-auth-test;DB_CLOSE_DELAY=-1",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "classforge.security.jwt.secret=test-secret-long-enough-for-classforge-auth-tests-2026"
        }
)
class AuthControllerIntegrationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void registersLogsInAndReadsCurrentUser() throws Exception {
        HttpResponse<String> register = post(
                "/api/auth/register",
                """
                {
                  "displayName":"Sebas",
                  "email":"SEBAS@example.com",
                  "password":"password123"
                }
                """
        );

        assertEquals(201, register.statusCode());

        JsonNode registered = jsonMapper.readTree(register.body());
        String token = registered.get("accessToken").asString();

        assertFalse(token.isBlank());
        assertEquals("sebas@example.com", registered.at("/user/email").asString());

        HttpResponse<String> me = get("/api/auth/me", token);
        assertEquals(200, me.statusCode());
        assertEquals("Sebas", jsonMapper.readTree(me.body()).get("displayName").asString());

        HttpResponse<String> login = post(
                "/api/auth/login",
                """
                {
                  "email":"sebas@example.com",
                  "password":"password123"
                }
                """
        );

        assertEquals(200, login.statusCode());
        assertFalse(jsonMapper.readTree(login.body()).get("accessToken").asString().isBlank());
    }

    @Test
    void rejectsDuplicateEmailAndWrongPassword() throws Exception {
        String registration = """
                {
                  "displayName":"Ana",
                  "email":"ana@example.com",
                  "password":"password123"
                }
                """;

        assertEquals(201, post("/api/auth/register", registration).statusCode());
        assertEquals(409, post("/api/auth/register", registration).statusCode());

        HttpResponse<String> login = post(
                "/api/auth/login",
                """
                {
                  "email":"ana@example.com",
                  "password":"wrong-password"
                }
                """
        );

        assertEquals(401, login.statusCode());
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}