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
                "spring.datasource.url=jdbc:h2:mem:classforge-project-invitation-test;DB_CLOSE_DELAY=-1",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "classforge.security.jwt.secret=test-secret-long-enough-for-classforge-invitation-tests-2026"
        }
)
class ProjectInvitationIntegrationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void invitationCanPrecedeRegistrationAndAcceptCreatesPersistentEditorWithoutChangingRevision()
            throws Exception {
        String ownerToken = register(
                "Sebastian",
                "owner-invite@classforge.test"
        );

        JsonNode project = createProject(ownerToken, "Veterinaria");
        String projectId = project.get("id").asString();
        assertEquals(0L, project.get("revision").asLong());

        HttpResponse<String> invitationResponse = send(
                "POST",
                "/api/projects/" + projectId + "/invitations",
                """
                {"email":"FUTURE.Editor@ClassForge.Test"}
                """,
                ownerToken
        );

        assertEquals(201, invitationResponse.statusCode());
        JsonNode invitation = jsonMapper.readTree(invitationResponse.body());
        String invitationId = invitation.get("id").asString();
        assertEquals(
                "future.editor@classforge.test",
                invitation.get("invitedEmail").asString()
        );
        assertEquals("PENDING", invitation.get("status").asString());

        JsonNode ownerCollaborators = jsonMapper.readTree(
                send(
                        "GET",
                        "/api/projects/" + projectId + "/collaborators",
                        null,
                        ownerToken
                ).body()
        );
        assertEquals("OWNER", ownerCollaborators.get("viewerRole").asString());
        assertEquals(1, ownerCollaborators.get("pendingInvitations").size());

        String editorToken = register(
                "Maria",
                "future.editor@classforge.test"
        );

        JsonNode beforeAcceptProjects = jsonMapper.readTree(
                send("GET", "/api/projects", null, editorToken).body()
        );
        assertEquals(0, beforeAcceptProjects.size());

        JsonNode inbox = jsonMapper.readTree(
                send(
                        "GET",
                        "/api/project-invitations",
                        null,
                        editorToken
                ).body()
        );
        assertEquals(1, inbox.size());
        assertEquals(projectId, inbox.get(0).get("projectId").asString());
        assertEquals("Sebastian", inbox.get(0).get("invitedByDisplayName").asString());

        HttpResponse<String> acceptResponse = send(
                "POST",
                "/api/project-invitations/" + invitationId + "/accept",
                null,
                editorToken
        );
        assertEquals(200, acceptResponse.statusCode());
        assertEquals(
                "ACCEPTED",
                jsonMapper.readTree(acceptResponse.body()).get("status").asString()
        );

        JsonNode afterAcceptProjects = jsonMapper.readTree(
                send("GET", "/api/projects", null, editorToken).body()
        );
        assertEquals(1, afterAcceptProjects.size());
        assertEquals("EDITOR", afterAcceptProjects.get(0).get("accessRole").asString());
        assertEquals(0L, afterAcceptProjects.get(0).get("revision").asLong());

        JsonNode editorCollaborators = jsonMapper.readTree(
                send(
                        "GET",
                        "/api/projects/" + projectId + "/collaborators",
                        null,
                        editorToken
                ).body()
        );
        assertEquals("EDITOR", editorCollaborators.get("viewerRole").asString());
        assertEquals("Sebastian", editorCollaborators.at("/owner/displayName").asString());
        assertEquals(1, editorCollaborators.get("editors").size());
        assertEquals("Maria", editorCollaborators.at("/editors/0/displayName").asString());
        assertEquals(0, editorCollaborators.get("pendingInvitations").size());

        JsonNode ownerProject = jsonMapper.readTree(
                send(
                        "GET",
                        "/api/projects/" + projectId,
                        null,
                        ownerToken
                ).body()
        );
        assertEquals(0L, ownerProject.get("revision").asLong());
    }

    @Test
    void invitationRulesProtectOwnerPendingAndAuthenticatedEmail()
            throws Exception {
        String ownerToken = register(
                "Owner Rules",
                "owner-rules@classforge.test"
        );
        String editorToken = register(
                "Editor Rules",
                "editor-rules@classforge.test"
        );
        String strangerToken = register(
                "Stranger Rules",
                "stranger-rules@classforge.test"
        );

        String projectId = createProject(ownerToken, "Clinica")
                .get("id")
                .asString();

        HttpResponse<String> selfInvite = invite(
                projectId,
                "OWNER-RULES@classforge.test",
                ownerToken
        );
        assertEquals(409, selfInvite.statusCode());
        assertEquals(
                "OWNER_CANNOT_BE_INVITED",
                jsonMapper.readTree(selfInvite.body()).get("error").asString()
        );

        HttpResponse<String> firstInvite = invite(
                projectId,
                "editor-rules@classforge.test",
                ownerToken
        );
        assertEquals(201, firstInvite.statusCode());
        String firstInvitationId = jsonMapper
                .readTree(firstInvite.body())
                .get("id")
                .asString();

        HttpResponse<String> duplicateInvite = invite(
                projectId,
                "EDITOR-RULES@classforge.test",
                ownerToken
        );
        assertEquals(409, duplicateInvite.statusCode());
        assertEquals(
                "INVITATION_ALREADY_PENDING",
                jsonMapper.readTree(duplicateInvite.body()).get("error").asString()
        );

        HttpResponse<String> foreignAccept = send(
                "POST",
                "/api/project-invitations/" + firstInvitationId + "/accept",
                null,
                strangerToken
        );
        assertEquals(404, foreignAccept.statusCode());

        HttpResponse<String> decline = send(
                "POST",
                "/api/project-invitations/" + firstInvitationId + "/decline",
                null,
                editorToken
        );
        assertEquals(200, decline.statusCode());
        assertEquals(
                "DECLINED",
                jsonMapper.readTree(decline.body()).get("status").asString()
        );

        assertEquals(
                0,
                jsonMapper.readTree(
                        send("GET", "/api/projects", null, editorToken).body()
                ).size()
        );

        HttpResponse<String> secondInvite = invite(
                projectId,
                "editor-rules@classforge.test",
                ownerToken
        );
        assertEquals(201, secondInvite.statusCode());
        String secondInvitationId = jsonMapper
                .readTree(secondInvite.body())
                .get("id")
                .asString();

        assertEquals(
                200,
                send(
                        "POST",
                        "/api/project-invitations/" + secondInvitationId + "/accept",
                        null,
                        editorToken
                ).statusCode()
        );

        HttpResponse<String> alreadyMemberInvite = invite(
                projectId,
                "editor-rules@classforge.test",
                ownerToken
        );
        assertEquals(409, alreadyMemberInvite.statusCode());
        assertEquals(
                "USER_ALREADY_MEMBER",
                jsonMapper.readTree(alreadyMemberInvite.body()).get("error").asString()
        );

        HttpResponse<String> editorInviteAttempt = invite(
                projectId,
                "someone@classforge.test",
                editorToken
        );
        assertEquals(404, editorInviteAttempt.statusCode());

        HttpResponse<String> ownerPending = invite(
                projectId,
                "cancel-me@classforge.test",
                ownerToken
        );
        assertEquals(201, ownerPending.statusCode());
        String cancelInvitationId = jsonMapper
                .readTree(ownerPending.body())
                .get("id")
                .asString();

        HttpResponse<String> editorPendingList = send(
                "GET",
                "/api/projects/" + projectId + "/invitations",
                null,
                editorToken
        );
        assertEquals(404, editorPendingList.statusCode());

        HttpResponse<String> editorCancel = send(
                "DELETE",
                "/api/projects/" + projectId + "/invitations/" + cancelInvitationId,
                null,
                editorToken
        );
        assertEquals(404, editorCancel.statusCode());

        HttpResponse<String> ownerCancel = send(
                "DELETE",
                "/api/projects/" + projectId + "/invitations/" + cancelInvitationId,
                null,
                ownerToken
        );
        assertEquals(204, ownerCancel.statusCode());
    }

    private JsonNode createProject(
            String token,
            String name
    ) throws Exception {
        HttpResponse<String> response = send(
                "POST",
                "/api/projects",
                """
                {"name":"%s"}
                """.formatted(name),
                token
        );
        assertEquals(201, response.statusCode());
        return jsonMapper.readTree(response.body());
    }

    private HttpResponse<String> invite(
            String projectId,
            String email,
            String token
    ) throws Exception {
        return send(
                "POST",
                "/api/projects/" + projectId + "/invitations",
                """
                {"email":"%s"}
                """.formatted(email),
                token
        );
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
            builder.method(method, HttpRequest.BodyPublishers.noBody());
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
