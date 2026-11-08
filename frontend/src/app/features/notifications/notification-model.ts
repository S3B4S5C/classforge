export interface ProjectNotification {
  id: string; projectId: string; recipientUserId: string; type: string; title: string; message: string;
  activityId: string | null; sessionId: string | null; read: boolean; createdAt: string; readAt: string | null;
}
export interface NotificationEvent { recipientUserId: string; type: 'NOTIFICATION_CREATED' | 'NOTIFICATIONS_CHANGED'; notification: ProjectNotification | null; }

export function mergeNotifications(current: ProjectNotification[], incoming: ProjectNotification[], userId: string): ProjectNotification[] {
  const records = new Map(current.filter(n => n.recipientUserId === userId).map(n => [n.id, n]));
  incoming.filter(n => n.recipientUserId === userId).forEach(n => records.set(n.id, n));
  const stamp = (value: string) => value.replace(/(?:\.(\d+))?Z$/, (_, fraction: string | undefined) => `.${(fraction ?? '').padEnd(9, '0')}Z`);
  return [...records.values()].sort((a, b) => stamp(b.createdAt).localeCompare(stamp(a.createdAt)) || b.id.localeCompare(a.id));
}
