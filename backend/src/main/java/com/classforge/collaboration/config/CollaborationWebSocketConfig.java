package com.classforge.collaboration.config;

import com.classforge.collaboration.security.ProjectStompAuthorizationInterceptor;
import com.classforge.collaboration.security.StompJwtAuthenticationInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class CollaborationWebSocketConfig
        implements WebSocketMessageBrokerConfigurer {

    private final StompJwtAuthenticationInterceptor
            authenticationInterceptor;

    private final ProjectStompAuthorizationInterceptor
            authorizationInterceptor;

    private final String[] allowedOriginPatterns;

    public CollaborationWebSocketConfig(
            StompJwtAuthenticationInterceptor authenticationInterceptor,
            ProjectStompAuthorizationInterceptor authorizationInterceptor,
            @Value("${classforge.security.websocket.allowed-origin-patterns:*}")
            String[] allowedOriginPatterns
    ) {
        this.authenticationInterceptor =
                authenticationInterceptor;

        this.authorizationInterceptor =
                authorizationInterceptor;

        this.allowedOriginPatterns =
                allowedOriginPatterns == null || allowedOriginPatterns.length == 0
                        ? new String[]{"*"}
                        : allowedOriginPatterns;
    }

    @Override
    public void registerStompEndpoints(
            StompEndpointRegistry registry
    ) {
        registry
                .addEndpoint("/ws")
                /*
                 * CU06-001 usa JWT en STOMP CONNECT, no cookies.
                 * En desarrollo/LAN permitimos cualquier Origin.
                 * Para despliegue publico se restringira mediante
                 * configuracion de entorno.
                 */
                .setAllowedOriginPatterns(allowedOriginPatterns);
    }

    @Override
    public void configureMessageBroker(
            MessageBrokerRegistry registry
    ) {
        registry.setApplicationDestinationPrefixes(
                "/app"
        );

        registry.enableSimpleBroker(
                "/topic",
                "/queue"
        );

        registry.setUserDestinationPrefix(
                "/user"
        );
    }

    @Override
    public void configureClientInboundChannel(
            ChannelRegistration registration
    ) {
        registration.interceptors(
                authenticationInterceptor,
                authorizationInterceptor
        );
    }
}