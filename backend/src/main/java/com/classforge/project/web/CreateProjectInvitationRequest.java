package com.classforge.project.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProjectInvitationRequest(
        @NotBlank(message = "El correo es obligatorio.")
        @Email(message = "Usa un correo valido.")
        @Size(max = 160, message = "El correo no puede superar 160 caracteres.")
        String email
) {
}
