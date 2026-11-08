package com.classforge.activity;

import com.classforge.project.application.ProjectNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestControllerAdvice(basePackages = {"com.classforge.activity", "com.classforge.session", "com.classforge.continuity", "com.classforge.notification"})
public class ProjectActivityErrorHandler {
    @ExceptionHandler(ProjectNotFoundException.class) @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String,String> notFound(ProjectNotFoundException exception) { return Map.of("error", "PROJECT_NOT_FOUND", "message", exception.getMessage()); }
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String,String> invalid(IllegalArgumentException exception) { return Map.of("error", "INVALID_ACTIVITY_QUERY", "message", exception.getMessage()); }
}
