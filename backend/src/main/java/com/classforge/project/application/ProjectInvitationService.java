package com.classforge.project.application;

import com.classforge.auth.persistence.UserEntity;
import com.classforge.auth.persistence.UserRepository;
import com.classforge.project.access.ProjectAccessRole;
import com.classforge.project.access.ProjectAccessService;
import com.classforge.project.domain.Project;
import com.classforge.project.persistence.ProjectInvitationEntity;
import com.classforge.project.persistence.ProjectInvitationRepository;
import com.classforge.project.persistence.ProjectInvitationStatus;
import com.classforge.project.persistence.ProjectMembershipEntity;
import com.classforge.project.persistence.ProjectMembershipRepository;
import com.classforge.project.persistence.ProjectRepository;
import com.classforge.project.persistence.ProjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class ProjectInvitationService {

    private final ProjectAccessService projectAccessService;
    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;
    private final ProjectMembershipRepository projectMembershipRepository;
    private final ProjectInvitationRepository projectInvitationRepository;
    private final UserRepository userRepository;

    public ProjectInvitationService(
            ProjectAccessService projectAccessService,
            ProjectRepository projectRepository,
            ProjectMapper projectMapper,
            ProjectMembershipRepository projectMembershipRepository,
            ProjectInvitationRepository projectInvitationRepository,
            UserRepository userRepository
    ) {
        this.projectAccessService = projectAccessService;
        this.projectRepository = projectRepository;
        this.projectMapper = projectMapper;
        this.projectMembershipRepository = projectMembershipRepository;
        this.projectInvitationRepository = projectInvitationRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ProjectInvitationView invite(
            UUID ownerUserId,
            UUID projectId,
            String requestedEmail
    ) {
        projectAccessService.requireOwner(ownerUserId, projectId);
        requireLockedProject(projectId);

        String invitedEmail = normalizeEmail(requestedEmail);
        UserEntity owner = requireUser(ownerUserId);

        if (normalizeEmail(owner.getEmail()).equals(invitedEmail)) {
            throw conflict(
                    "OWNER_CANNOT_BE_INVITED",
                    "El propietario no puede invitarse a si mismo."
            );
        }

        userRepository.findByEmailIgnoreCase(invitedEmail)
                .ifPresent(existingUser -> {
                    if (projectMembershipRepository.existsByProjectIdAndUserId(
                            projectId,
                            existingUser.getId()
                    )) {
                        throw conflict(
                                "USER_ALREADY_MEMBER",
                                "Ese usuario ya es editor del proyecto."
                        );
                    }
                });

        if (projectInvitationRepository
                .existsByProjectIdAndInvitedEmailIgnoreCaseAndStatus(
                        projectId,
                        invitedEmail,
                        ProjectInvitationStatus.PENDING
                )) {
            throw conflict(
                    "INVITATION_ALREADY_PENDING",
                    "Ya existe una invitacion pendiente para ese correo."
            );
        }

        ProjectInvitationEntity saved = projectInvitationRepository.save(
                ProjectInvitationEntity.pending(
                        projectId,
                        invitedEmail,
                        ownerUserId
                )
        );

        return toView(saved);
    }

    @Transactional(readOnly = true)
    public List<ProjectInvitationView> pendingForUser(UUID userId) {
        UserEntity user = requireUser(userId);
        String email = normalizeEmail(user.getEmail());

        return projectInvitationRepository
                .findAllByInvitedEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(
                        email,
                        ProjectInvitationStatus.PENDING
                )
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public ProjectInvitationView accept(
            UUID userId,
            UUID invitationId
    ) {
        UserEntity user = requireUser(userId);
        UUID projectId = requireInvitationProjectId(invitationId);
        Project project = requireLockedProject(projectId);
        ProjectInvitationEntity invitation = requireLockedInvitation(invitationId);

        requireInvitationForEmail(invitation, user.getEmail());
        requirePending(invitation);

        if (project.ownerId().equals(userId)) {
            throw conflict(
                    "OWNER_CANNOT_ACCEPT_INVITATION",
                    "El propietario no puede aceptar una invitacion a su propio proyecto."
            );
        }

        if (projectMembershipRepository.existsByProjectIdAndUserId(
                project.id(),
                userId
        )) {
            throw conflict(
                    "USER_ALREADY_MEMBER",
                    "El usuario ya es editor del proyecto."
            );
        }

        projectMembershipRepository.save(
                ProjectMembershipEntity.editor(project.id(), userId)
        );
        invitation.accept();

        return toView(invitation);
    }

    @Transactional
    public ProjectInvitationView decline(
            UUID userId,
            UUID invitationId
    ) {
        UserEntity user = requireUser(userId);
        UUID projectId = requireInvitationProjectId(invitationId);
        requireLockedProject(projectId);
        ProjectInvitationEntity invitation = requireLockedInvitation(invitationId);

        requireInvitationForEmail(invitation, user.getEmail());
        requirePending(invitation);

        invitation.decline();
        return toView(invitation);
    }

    @Transactional
    public void cancel(
            UUID ownerUserId,
            UUID projectId,
            UUID invitationId
    ) {
        projectAccessService.requireOwner(ownerUserId, projectId);
        requireLockedProject(projectId);

        ProjectInvitationEntity invitation = requireLockedInvitation(invitationId);

        if (!invitation.getProjectId().equals(projectId)) {
            throw new ProjectInvitationNotFoundException(invitationId);
        }

        requirePending(invitation);
        invitation.cancel();
    }

    @Transactional(readOnly = true)
    public List<ProjectInvitationView> pendingForProject(
            UUID ownerUserId,
            UUID projectId
    ) {
        projectAccessService.requireOwner(ownerUserId, projectId);

        return projectInvitationRepository
                .findAllByProjectIdAndStatusOrderByCreatedAtAsc(
                        projectId,
                        ProjectInvitationStatus.PENDING
                )
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectCollaboratorsView collaborators(
            UUID viewerUserId,
            UUID projectId
    ) {
        ProjectAccessRole viewerRole = projectAccessService.requireRead(
                viewerUserId,
                projectId
        );

        Project project = projectRepository
                .findById(projectId)
                .map(projectMapper::toDomain)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        UserEntity owner = requireUser(project.ownerId());
        ProjectCollaboratorView ownerView = new ProjectCollaboratorView(
                owner.getId(),
                owner.getDisplayName(),
                owner.getEmail(),
                ProjectAccessRole.OWNER,
                project.createdAt()
        );

        List<ProjectCollaboratorView> editors = new ArrayList<>();

        for (ProjectMembershipEntity membership :
                projectMembershipRepository.findAllByProjectIdOrderByCreatedAtAsc(projectId)) {
            userRepository.findById(membership.getUserId())
                    .ifPresent(user -> editors.add(
                            new ProjectCollaboratorView(
                                    user.getId(),
                                    user.getDisplayName(),
                                    user.getEmail(),
                                    membership.getRole(),
                                    membership.getCreatedAt()
                            )
                    ));
        }

        editors.sort(
                Comparator.comparing(
                        ProjectCollaboratorView::displayName,
                        String.CASE_INSENSITIVE_ORDER
                )
        );

        List<ProjectInvitationView> pendingInvitations =
                viewerRole == ProjectAccessRole.OWNER
                        ? projectInvitationRepository
                                .findAllByProjectIdAndStatusOrderByCreatedAtAsc(
                                        projectId,
                                        ProjectInvitationStatus.PENDING
                                )
                                .stream()
                                .map(this::toView)
                                .toList()
                        : List.of();

        return new ProjectCollaboratorsView(
                viewerRole,
                ownerView,
                editors,
                pendingInvitations
        );
    }

    private UUID requireInvitationProjectId(UUID invitationId) {
        return projectInvitationRepository
                .findProjectIdById(invitationId)
                .orElseThrow(() -> new ProjectInvitationNotFoundException(invitationId));
    }

    private ProjectInvitationEntity requireLockedInvitation(UUID invitationId) {
        return projectInvitationRepository
                .findForUpdateById(invitationId)
                .orElseThrow(() -> new ProjectInvitationNotFoundException(invitationId));
    }

    private Project requireLockedProject(UUID projectId) {
        return projectRepository
                .findForUpdateById(projectId)
                .map(projectMapper::toDomain)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
    }

    private void requireInvitationForEmail(
            ProjectInvitationEntity invitation,
            String authenticatedEmail
    ) {
        if (!invitation.getInvitedEmail().equals(normalizeEmail(authenticatedEmail))) {
            throw new ProjectInvitationNotFoundException(invitation.getId());
        }
    }

    private void requirePending(ProjectInvitationEntity invitation) {
        if (invitation.getStatus() != ProjectInvitationStatus.PENDING) {
            throw conflict(
                    "INVITATION_NOT_PENDING",
                    "La invitacion ya fue respondida o cancelada."
            );
        }
    }

    private ProjectInvitationView toView(ProjectInvitationEntity invitation) {
        Project project = projectRepository
                .findById(invitation.getProjectId())
                .map(projectMapper::toDomain)
                .orElseThrow(() -> new ProjectNotFoundException(invitation.getProjectId()));

        UserEntity inviter = requireUser(invitation.getInvitedByUserId());

        return new ProjectInvitationView(
                invitation.getId(),
                invitation.getProjectId(),
                project.name(),
                invitation.getInvitedEmail(),
                invitation.getInvitedByUserId(),
                inviter.getDisplayName(),
                invitation.getStatus(),
                invitation.getCreatedAt(),
                invitation.getRespondedAt()
        );
    }

    private UserEntity requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "ClassForge user does not exist: " + userId
                ));
    }

    private String normalizeEmail(String email) {
        Objects.requireNonNull(email, "email is required");
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private ProjectInvitationConflictException conflict(
            String code,
            String message
    ) {
        return new ProjectInvitationConflictException(code, message);
    }
}
