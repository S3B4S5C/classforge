package ${model.basePackage}.controller;

import ${model.basePackage}.dto.${auth.className}Request;
import ${model.basePackage}.security.JwtService;
import ${model.basePackage}.security.LoginRequest;
import ${model.basePackage}.security.LoginResponse;
import ${model.basePackage}.service.${auth.className}Service;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final ${auth.className}Service authService;
    private final JwtService jwtService;

    public AuthController(${auth.className}Service authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        authService.authenticate(request.username(), request.password());
        return jwtService.issue(request.username());
    }

    @PostMapping("/bootstrap")
    public LoginResponse bootstrap(@Valid @RequestBody ${auth.className}Request request) {
        authService.bootstrap(request);
        return jwtService.issue(request.${auth.usernameField().fieldName}());
    }
}
