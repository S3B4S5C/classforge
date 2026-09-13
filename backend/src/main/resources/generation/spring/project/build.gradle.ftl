plugins {
    id 'java'
    id 'org.springframework.boot' version '${model.springBootVersion}'
}

repositories { mavenCentral() }

java { toolchain { languageVersion = JavaLanguageVersion.of(${model.javaVersion}) } }

dependencies {
    implementation platform('org.springframework.boot:spring-boot-dependencies:${model.springBootVersion}')

    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    runtimeOnly 'com.h2database:h2'
    runtimeOnly 'org.postgresql:postgresql'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') { useJUnitPlatform() }
