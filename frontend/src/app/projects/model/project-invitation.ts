import { ProjectAccessRole } from './project';

export type ProjectInvitationStatus =
  | 'PENDING'
  | 'ACCEPTED'
  | 'DECLINED'
  | 'CANCELLED';

export interface ProjectInvitation {
  id: string;
  projectId: string;
  projectName: string;
  invitedEmail: string;
  invitedByUserId: string;
  invitedByDisplayName: string;
  status: ProjectInvitationStatus;
  createdAt: string;
  respondedAt: string | null;
}

export interface ProjectCollaborator {
  userId: string;
  displayName: string;
  email: string;
  accessRole: ProjectAccessRole;
  joinedAt: string;
}

export interface ProjectCollaborators {
  viewerRole: ProjectAccessRole;
  owner: ProjectCollaborator;
  editors: ProjectCollaborator[];
  pendingInvitations: ProjectInvitation[];
}
