spring:
  datasource:
    url: jdbc:h2:mem:${model.artifactName};DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
    open-in-view: false
app:
  assistant:
    llama-url: ${r'${LLAMA_URL:http://127.0.0.1:8092}'}
    llama-model: ${r'${LLAMA_MODEL:local-model}'}
    whisper-url: ${r'${WHISPER_URL:http://127.0.0.1:8093}'}
    whisper-language: ${r'${WHISPER_LANGUAGE:es}'}
    api-base-url: ${r'${APP_API_BASE_URL:http://127.0.0.1:8080}'}
<#if api.authEnabled()>  security:
    jwt-secret: ${r'${JWT_SECRET:classforge-change-me-before-production}'}
    jwt-expiration-seconds: 3600
</#if>
