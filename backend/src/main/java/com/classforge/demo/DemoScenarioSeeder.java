package com.classforge.demo;

import com.classforge.auth.persistence.UserEntity;
import com.classforge.auth.persistence.UserRepository;
import com.classforge.integration.xmi.EnterpriseArchitectXmiImporter;
import com.classforge.project.access.ProjectAccessRole;
import com.classforge.project.domain.Project;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.persistence.ProjectMapper;
import com.classforge.project.persistence.ProjectMembershipEntity;
import com.classforge.project.persistence.ProjectMembershipRepository;
import com.classforge.project.persistence.ProjectRepository;
import com.classforge.project.validation.ProjectDocumentValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.io.IOException;
import java.time.Instant;

@Component
@Profile({"demo", "aws-demo"})
public class DemoScenarioSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoScenarioSeeder.class);
    private static final Instant CREATED_AT = Instant.parse("2026-09-01T12:00:00Z");

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;
    private final ProjectMembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final EnterpriseArchitectXmiImporter xmiImporter;
    private final ProjectDocumentValidator validator;
    private final MongoTemplate mongo;

    public DemoScenarioSeeder(
            UserRepository userRepository,
            ProjectRepository projectRepository,
            ProjectMapper projectMapper,
            ProjectMembershipRepository membershipRepository,
            PasswordEncoder passwordEncoder,
            EnterpriseArchitectXmiImporter xmiImporter,
            ProjectDocumentValidator validator,
            MongoTemplate mongo
    ) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.projectMapper = projectMapper;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
        this.xmiImporter = xmiImporter;
        this.validator = validator;
        this.mongo = mongo;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        resetDeterministicRows();

        UserEntity owner = new UserEntity(
                DemoScenario.OWNER_ID,
                "Modelador Demo",
                DemoScenario.OWNER_EMAIL,
                passwordEncoder.encode(DemoScenario.PASSWORD),
                CREATED_AT
        );
        UserEntity editor = new UserEntity(
                DemoScenario.EDITOR_ID,
                "Colaborador Demo",
                DemoScenario.EDITOR_EMAIL,
                passwordEncoder.encode(DemoScenario.PASSWORD),
                CREATED_AT
        );
        userRepository.save(owner);
        userRepository.save(editor);

        ProjectDocument document = loadDocument();
        validator.validate(document);

        Project project = new Project(
                DemoScenario.PROJECT_ID,
                DemoScenario.OWNER_ID,
                DemoScenario.PROJECT_NAME,
                1L,
                document,
                CREATED_AT,
                CREATED_AT
        );
        projectRepository.save(projectMapper.toEntity(project));
        membershipRepository.save(new ProjectMembershipEntity(
                DemoScenario.MEMBERSHIP_ID,
                DemoScenario.PROJECT_ID,
                DemoScenario.EDITOR_ID,
                ProjectAccessRole.EDITOR,
                CREATED_AT
        ));

        log.info("CU-27 demo ready: project={} owner={} editor={}",
                DemoScenario.PROJECT_ID,
                DemoScenario.OWNER_EMAIL,
                DemoScenario.EDITOR_EMAIL);
    }

    private void resetDeterministicRows() {
        var oldSessions = mongo.find(Query.query(Criteria.where("projectId").is(DemoScenario.PROJECT_ID)), com.classforge.session.ProjectSessionEntity.class);
        var oldSessionIds = oldSessions.stream().map(com.classforge.session.ProjectSessionEntity::getId).toList();
        if (!oldSessionIds.isEmpty()) mongo.remove(Query.query(Criteria.where("sessionId").in(oldSessionIds)), "project_session_participants");
        for (String collection : java.util.List.of("project_activities", "project_sessions", "project_notifications", "project_user_visits", "project_ai_messages")) {
            mongo.remove(Query.query(Criteria.where("projectId").is(DemoScenario.PROJECT_ID)), collection);
        }
        membershipRepository.findByProjectIdAndUserId(DemoScenario.PROJECT_ID, DemoScenario.EDITOR_ID)
                .ifPresent(membershipRepository::delete);
        projectRepository.findById(DemoScenario.PROJECT_ID).ifPresent(projectRepository::delete);
        userRepository.findById(DemoScenario.OWNER_ID).ifPresent(userRepository::delete);
        userRepository.findById(DemoScenario.EDITOR_ID).ifPresent(userRepository::delete);
    }

    private ProjectDocument loadDocument() throws IOException {
        ClassPathResource resource = new ClassPathResource(DemoScenario.XMI_RESOURCE.substring(1));
        return xmiImporter.importXmi(resource.getInputStream().readAllBytes()).document();
    }
}
