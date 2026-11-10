package com.classforge.auth.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            @Value("${classforge.web.spa.enabled:false}") boolean spaEnabled
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                            .requestMatchers("/api/health", "/error", "/ws", "/ws/**").permitAll()
                            .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                            // Keep API paths ahead of public SPA/asset rules.
                            .requestMatchers("/api", "/api/**").authenticated();
                    if (spaEnabled) {
                        authorize.requestMatchers(HttpMethod.GET,
                                "/", "/index.html", "/login", "/register", "/projects",
                                "/projects/{projectId}", "/projects/{projectId}/assistant",
                                "/projects/{projectId}/activity", "/*.js", "/*.css", "/*.ico",
                                "/*.png", "/*.svg", "/assets/**", "/media/**").permitAll();
                    }
                    authorize.anyRequest().authenticated();
                })
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(
                                    "{\"error\":\"UNAUTHORIZED\",\"message\":\"Authentication is required\"}"
                            );
                        })
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
