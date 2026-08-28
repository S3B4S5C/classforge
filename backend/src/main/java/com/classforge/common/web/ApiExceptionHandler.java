package com.classforge.common.web;

import com.classforge.project.validation.ProjectDocumentValidationException;
import com.classforge.project.validation.ValidationViolation;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ProjectDocumentValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiValidationErrorResponse handleProjectDocumentValidation(
            ProjectDocumentValidationException exception
    ) {
        return new ApiValidationErrorResponse(
                "VALIDATION_ERROR",
                "Hay datos del modelo UML que necesitan correccion.",
                exception.getViolations()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiValidationErrorResponse handleBeanValidation(
            MethodArgumentNotValidException exception
    ) {
        List<ValidationViolation> violations =
                exception.getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .map(this::toViolation)
                        .toList();

        return new ApiValidationErrorResponse(
                "VALIDATION_ERROR",
                "Revisa los campos indicados e intenta nuevamente.",
                violations
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiValidationErrorResponse handleMalformedJson(
            HttpMessageNotReadableException exception
    ) {
        return new ApiValidationErrorResponse(
                "INVALID_REQUEST_BODY",
                "No pudimos interpretar los datos enviados.",
                List.of(
                        new ValidationViolation(
                                "body",
                                "INVALID_JSON",
                                "Verifica el formato JSON y los valores de enums como tipo y visibilidad."
                        )
                )
        );
    }

    private ValidationViolation toViolation(FieldError error) {
        return new ValidationViolation(
                error.getField(),
                "INVALID_FIELD",
                error.getDefaultMessage() == null
                        ? "El valor no es valido."
                        : error.getDefaultMessage()
        );
    }
}