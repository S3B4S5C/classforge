package com.classforge.integration.xmi;

import com.classforge.project.access.ProjectAccessRole;
import com.classforge.project.application.AccessibleProject;
import com.classforge.project.application.ProjectService;
import com.classforge.project.domain.Project;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.validation.ProjectDocumentValidator;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class XmiInterchangeService {

    static final Duration PREVIEW_TTL = Duration.ofMinutes(10);

    private final EnterpriseArchitectXmiImporter importer;
    private final EnterpriseArchitectXmiExporter exporter;
    private final ProjectService projectService;
    private final ProjectDocumentValidator validator;
    private final Map<UUID, PendingImport> pending = new ConcurrentHashMap<>();

    public XmiInterchangeService(
            EnterpriseArchitectXmiImporter importer,
            EnterpriseArchitectXmiExporter exporter,
            ProjectService projectService,
            ProjectDocumentValidator validator
    ) {
        this.importer = importer;
        this.exporter = exporter;
        this.projectService = projectService;
        this.validator = validator;
    }

    public XmiImportPreviewResponse preview(UUID userId, UUID projectId, MultipartFile file) {
        Project project = projectService.get(userId, projectId);
        byte[] bytes = read(file);
        XmiImportResult imported = importer.importXmi(bytes);
        validator.validate(imported.document());

        UUID token = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(PREVIEW_TTL);
        pending.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(Instant.now()));
        pending.put(token, new PendingImport(
                userId,
                projectId,
                project.revision(),
                imported.document(),
                expiresAt
        ));

        return new XmiImportPreviewResponse(
                token,
                project.revision(),
                expiresAt,
                imported.packageCount(),
                imported.classCount(),
                imported.attributeCount(),
                imported.relationshipCount(),
                imported.diagnostics()
        );
    }

    public AppliedImport apply(UUID userId, UUID projectId, UUID previewToken) {
        if (previewToken == null) {
            throw new XmiInterchangeException("XMI_PREVIEW_TOKEN_REQUIRED", "previewToken es obligatorio.");
        }
        PendingImport preview = pending.remove(previewToken);
        if (preview == null) {
            throw new XmiInterchangeException("XMI_PREVIEW_NOT_FOUND", "El preview XMI no existe o ya fue consumido.");
        }
        if (preview.expiresAt().isBefore(Instant.now())) {
            throw new XmiInterchangeException("XMI_PREVIEW_EXPIRED", "El preview XMI expiro; vuelve a seleccionar el archivo.");
        }
        if (!preview.userId().equals(userId) || !preview.projectId().equals(projectId)) {
            throw new XmiInterchangeException("XMI_PREVIEW_SCOPE_MISMATCH", "El preview XMI pertenece a otra sesion o proyecto.");
        }
        ProjectAccessRole accessRole = projectService.accessRole(userId, projectId);
        Project saved = projectService.saveDocument(
                userId,
                projectId,
                preview.baseRevision(),
                preview.document()
        );
        return new AppliedImport(saved, accessRole);
    }

    public XmiExport export(UUID userId, UUID projectId) {
        AccessibleProject accessible = projectService.getAccessible(userId, projectId);
        Project project = accessible.project();
        validator.validate(project.document());
        byte[] bytes = exporter.export(project.id(), project.name(), project.document());
        return new XmiExport(filename(project.name()), bytes, accessible.accessRole());
    }

    private byte[] read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new XmiInterchangeException("XMI_FILE_REQUIRED", "Selecciona un archivo XMI.");
        }
        if (file.getSize() > EnterpriseArchitectXmiImporter.MAX_XMI_BYTES) {
            throw new XmiInterchangeException("XMI_TOO_LARGE", "El archivo XMI supera el limite de 5 MiB.");
        }
        String filename = file.getOriginalFilename();
        if (filename != null && !filename.isBlank()) {
            String lower = filename.toLowerCase();
            if (!lower.endsWith(".xmi") && !lower.endsWith(".xml")) {
                throw new XmiInterchangeException("XMI_EXTENSION_INVALID", "El archivo debe usar extension .xmi o .xml.");
            }
        }
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new XmiInterchangeException("XMI_READ_FAILED", "No se pudo leer el archivo XMI.", exception);
        }
    }

    private String filename(String projectName) {
        String safe = (projectName == null ? "classforge" : projectName.trim())
                .replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (safe.isBlank()) {
            safe = "classforge";
        }
        return safe + "-enterprise-architect.xmi";
    }

    private record PendingImport(
            UUID userId,
            UUID projectId,
            long baseRevision,
            ProjectDocument document,
            Instant expiresAt
    ) {
    }

    public record AppliedImport(Project project, ProjectAccessRole accessRole) {
    }

    public record XmiExport(
            String filename,
            byte[] bytes,
            ProjectAccessRole accessRole
    ) {
    }
}
