package com.classforge.collaboration.security;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.classforge.auth.security.JwtService;
import com.classforge.auth.security.UserPrincipal;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Component
public class StompJwtAuthenticationInterceptor
        implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public StompJwtAuthenticationInterceptor(
            JwtService jwtService
    ) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(
            Message<?> message,
            MessageChannel channel
    ) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(
                        message,
                        StompHeaderAccessor.class
                );

        if (accessor == null) {
            throw new MessagingException(
                    "STOMP header accessor is required"
            );
        }

        if (accessor.getCommand() != StompCommand.CONNECT) {
            return message;
        }

        String authorization =
                firstAuthorizationHeader(accessor);

        if (
                authorization == null
                        || !authorization.startsWith(BEARER_PREFIX)
        ) {
            throw new MessagingException(
                    "STOMP authentication requires Authorization: Bearer <token>"
            );
        }

        String token =
                authorization.substring(
                        BEARER_PREFIX.length()
                ).trim();

        if (token.isEmpty()) {
            throw new MessagingException(
                    "STOMP bearer token is empty"
            );
        }

        try {
            UserPrincipal user =
                    jwtService.verify(token);

            accessor.setUser(
                    new CollaborationPrincipal(
                            user.id(),
                            user.email()
                    )
            );

            return message;
        } catch (
                JWTVerificationException
                        | IllegalArgumentException exception
        ) {
            throw new MessagingException(
                    "Invalid or expired STOMP bearer token",
                    exception
            );
        }
    }

    private String firstAuthorizationHeader(
            StompHeaderAccessor accessor
    ) {
        String header =
                accessor.getFirstNativeHeader(
                        "Authorization"
                );

        if (header != null) {
            return header;
        }

        return accessor.getFirstNativeHeader(
                "authorization"
        );
    }
}