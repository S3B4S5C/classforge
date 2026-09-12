spring:
  datasource:
    url: <#noparse>${DB_URL:jdbc:postgresql://localhost:5432/</#noparse>${model.artifactName}<#noparse>}</#noparse>
    username: <#noparse>${DB_USERNAME:postgres}</#noparse>
    password: <#noparse>${DB_PASSWORD:}</#noparse>
  jpa:
    hibernate:
      ddl-auto: create-drop
    open-in-view: false
