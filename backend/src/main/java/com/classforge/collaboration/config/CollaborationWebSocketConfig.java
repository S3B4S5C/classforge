package com.classforge.collaboration.config;

import com.classforge.collaboration.security.ProjectStompAuthorizationInterceptor;
import com.classforge.collaboration.security.StompJwtAuthenticationInterceptor;
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

    public CollaborationWebSocketConfig(
            StompJwtAuthenticationInterceptor authenticationInterceptor,
            ProjectStompAuthorizationInterceptor authorizationInterceptor
    ) {
        this.authenticationInterceptor =
                authenticationInterceptor;

        this.authorizationInterceptor =
                authorizationInterceptor;
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
                .setAllowedOriginPatterns("*");
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