spring:
  datasource:
    url: jdbc:h2:mem:${model.artifactName};DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
    open-in-view: false
<#if api.authEnabled()>app:
  security:
    jwt-secret: ${r'${JWT_SECRET:classforge-change-me-before-production}'}
    jwt-expiration-seconds: 3600
</#if>
