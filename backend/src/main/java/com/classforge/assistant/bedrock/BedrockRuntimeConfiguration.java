package com.classforge.assistant.bedrock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

import java.time.Duration;

@Configuration
@ConditionalOnProperty(name = "classforge.assistant.bedrock.enabled", havingValue = "true")
public class BedrockRuntimeConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(BedrockRuntimeClient.class)
    BedrockRuntimeClient bedrockRuntimeClient(
            @Value("${classforge.assistant.bedrock.region:us-east-1}") String region,
            @Value("${classforge.assistant.bedrock.request-timeout-seconds:300}") long timeoutSeconds
    ) {
        Duration timeout = Duration.ofSeconds(Math.max(5L, timeoutSeconds));
        return BedrockRuntimeClient.builder()
                .credentialsProvider(DefaultCredentialsProvider.create())
                .region(Region.of(region))
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(3))
                        .socketTimeout(timeout))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallAttemptTimeout(timeout)
                        .apiCallTimeout(timeout.plusSeconds(15))
                        .build())
                .build();
    }
}
