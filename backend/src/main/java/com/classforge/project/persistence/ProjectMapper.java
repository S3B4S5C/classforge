package com.classforge.project.persistence;

import com.classforge.project.domain.Project;
import com.classforge.project.domain.document.ProjectDocument;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ProjectMapper {

    private final JsonMapper jsonMapper;

    public ProjectMapper(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public ProjectEntity toEntity(Project project) {
        return new ProjectEntity(
                project.id(),
                project.ownerId(),
                project.name(),
                project.revision(),
                serialize(project.document()),
                project.createdAt(),
                project.updatedAt()
        );
    }

    public Project toDomain(ProjectEntity entity) {
        return new Project(
                entity.getId(),
                entity.getOwnerId(),
                entity.getName(),
                entity.getRevision(),
                deserializeDocument(entity.getDocumentJson()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String serialize(ProjectDocument document) {
        try {
            return jsonMapper.writeValueAsString(document);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize project document", exception);
        }
    }

    private ProjectDocument deserializeDocument(String json) {
        try {
            JsonNode root = jsonMapper.readTree(json);

            if (root.has("umlModel") && root.has("layout")) {
                return jsonMapper.readValue(json, ProjectDocument.class);
            }

            /*
             * CU-01 persistia:
             * {"schemaVersion":"1.0","elements":[]}
             *
             * CU-01 nunca permitio crear elementos, por lo que un snapshot
             * legacy vacio puede migrarse de forma determinista.
             */
            if (root.has("elements") && root.get("elements").isArray()) {
                if (root.get("elements").size() > 0) {
                    throw new IllegalStateException(
                            "Legacy UML snapshots with elements cannot be migrated automatically"
                    );
                }

                return ProjectDocument.empty();
            }

            throw new IllegalStateException("Unsupported project document format");
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not deserialize project document", exception);
        }
    }
}