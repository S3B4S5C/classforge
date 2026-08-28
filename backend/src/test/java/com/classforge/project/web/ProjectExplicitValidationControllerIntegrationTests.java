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
                "spring.datasource.url=jdbc:h2:mem:classforge-explicit-validation-web-test;DB_CLOSE_DELAY=-1",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "classforge.security.jwt.secret=test-secret-long-enough-for-cu04-validation-tests-2026"
        }
)
class ProjectExplicitValidationControllerIntegrationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    private final HttpClient httpClient =
            HttpClient.newHttpClient();

    @Test
    void invalidDraftReturns200WithDiagnosticsAndDoesNotSave()
            throws Exception {
        String token = register();
        String projectId = createProject(token);

        String firstId =
                UUID.randomUUID().toString();

        String secondId =
                UUID.randomUUID().toString();

        HttpResponse<String> validation =
                send(
                        "POST",
                        "/api/projects/"
                                + projectId
                                + "/validate",
                        """
                        {
                          "document": {
                            "schemaVersion": "1.0",
                            "umlModel": {
                              "classes": [
                                {
                                  "id": "%s",
                                  "name": "Animal",
                                  "attributes": []
                                },
                                {
                                  "id": "%s",
                                  "name": "Animal",
                                  "attributes": []
                                }
                              ],
                              "relationships": []
                            },
                            "layout": {
                              "nodes": {}
                            }
                          }
                        }
                        """.formatted(
                                firstId,
                                secondId
                        ),
                        token
                );

        assertEquals(
                200,
                validation.statusCode()
        );

        JsonNode report =
                jsonMapper.readTree(
                        validation.body()
                );

        assertTrue(
                !report.get("valid")
                        .asBoolean()
        );

        assertTrue(
                report.get("errors")
                        .asInt() > 0
        );

        boolean duplicateFound = false;
        boolean navigable = false;

        for (
                JsonNode diagnostic
                : report.get("diagnostics")
        ) {
            if (
                    "DUPLICATE_CLASS_NAME"
                            .equals(
                                    diagnostic
                                            .get("code")
                                            .asString()
                            )
            ) {
                duplicateFound = true;

                navigable =
                        secondId.equals(
                                diagnostic
                                        .get("elementId")
                                        .asString()
                        );
            }
        }

        assertTrue(duplicateFound);
        assertTrue(navigable);

        HttpResponse<String> project =
                send(
                        "GET",
                        "/api/projects/"
                                + projectId,
                        null,
                        token
                );

        JsonNode persisted =
                jsonMapper.readTree(
                        project.body()
                );

        assertEquals(
                0,
                persisted.get("revision")
                        .asInt()
        );

        assertEquals(
                0,
                persisted.get("document")
                        .get("umlModel")
                        .get("classes")
                        .size()
        );
    }

    private String register()
            throws Exception {
        HttpResponse<String> response =
                send(
                        "POST",
                        "/api/auth/register",
                        """
                        {
                          "displayName":"CU04 User",
                          "email":"cu04-validation@classforge.test",
                          "password":"password123"
                        }
                        """,
                        null
                );

        assertEquals(
                201,
                response.statusCode()
        );

        return jsonMapper
                .readTree(response.body())
                .get("accessToken")
                .asString();
    }

    private String createProject(
            String token
    ) throws Exception {
        HttpResponse<String> response =
                send(
                        "POST",
                        "/api/projects",
                        """
                        {"name":"CU04Project"}
                        """,
                        token
                );

        assertEquals(
                201,
                response.statusCode()
        );

        return jsonMapper
                .readTree(response.body())
                .get("id")
                .asString();
    }

    private HttpResponse<String> send(
            String method,
            String path,
            String body,
            String token
    ) throws Exception {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        "http://localhost:"
                                                + port
                                                + path
                                )
                        )
                        .header(
                                "Accept",
                                "application/json"
                        );

        if (token != null) {
            builder.header(
                    "Authorization",
                    "Bearer " + token
            );
        }

        if (body == null) {
            builder.method(
                    method,
                    HttpRequest.BodyPublishers
                            .noBody()
            );
        } else {
            builder.header(
                    "Content-Type",
                    "application/json"
            );

            builder.method(
                    method,
                    HttpRequest.BodyPublishers
                            .ofString(body)
            );
        }

        return httpClient.send(
                builder.build(),
                HttpResponse.BodyHandlers
                        .ofString()
        );
    }
}