package com.classforge.integration.xmi;

import com.classforge.auth.security.CurrentUser;
import com.classforge.project.application.ProjectNotFoundException;
import com.classforge.project.application.ProjectRevisionConflictException;
import com.classforge.project.web.ProjectResponse;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/xmi")
public class XmiInterchangeController {

    private final XmiInterchangeService interchangeService;
    private final CurrentUser currentUser;

    public XmiInterchangeController(
            XmiInterchangeService interchangeService,
            CurrentUser currentUser
    ) {
        this.interchangeService = interchangeService;
        this.currentUser = currentUser;
    }

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public XmiImportPreviewResponse previewImport(
            @PathVariable UUID projectId,
            @RequestPart("file") MultipartFile file
    ) {
        return interchangeService.preview(currentUser.id(), projectId, file);
    }

    @PostMapping("/import/apply")
    public ProjectResponse applyImport(
            @PathVariable UUID projectId,
            @Valid @RequestBody XmiImportApplyRequest request
    ) {
        XmiInterchangeService.AppliedImport applied = interchangeService.apply(currentUser.id(), projectId, request.previewToken());
        return ProjectResponse.from(applied.project(), applied.accessRole());
    }

    @GetMapping(value = "/export", produces = "application/xml")
    public ResponseEntity<byte[]> export(@PathVariable UUID projectId) {
        XmiInterchangeService.XmiExport exported = interchangeService.export(currentUser.id(), projectId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        headers.set(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(exported.filename()).build().toString()
        );
        return new ResponseEntity<>(exported.bytes(), headers, HttpStatus.OK);
    }

    @ExceptionHandler(XmiInterchangeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleXmi(XmiInterchangeException exception) {
        return Map.of("error", exception.code(), "message", exception.getMessage());
    }

    @ExceptionHandler(ProjectRevisionConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> handleRevisionConflict(ProjectRevisionConflictException exception) {
        return Map.of(
                "error", "PROJECT_REVISION_CONFLICT",
                "message", exception.getMessage(),
                "requestedRevision", exception.getRequestedRevision(),
                "currentRevision", exception.getCurrentRevision()
        );
    }

    @ExceptionHandler(ProjectNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(ProjectNotFoundException exception) {
        return Map.of("error", "PROJECT_NOT_FOUND", "message", exception.getMessage());
    }
}
