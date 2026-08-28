package com.classforge.project.persistence;

import com.classforge.project.domain.Project;
import com.classforge.project.domain.UmlModelSnapshot;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
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
                serialize(project.umlModel()),
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
                deserialize(entity.getUmlModel()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String serialize(UmlModelSnapshot umlModel) {
        try {
            return jsonMapper.writeValueAsString(umlModel);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize UML model", exception);
        }
    }

    private UmlModelSnapshot deserialize(String umlModel) {
        try {
            return jsonMapper.readValue(umlModel, UmlModelSnapshot.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not deserialize UML model", exception);
        }
    }
}