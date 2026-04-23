import type { GroupItem, PendingInvitationItem } from '../../api/group'

export type WorkspaceNodeType = 'invitation' | 'ownedGroup' | 'joinedGroup'

export interface WorkspaceSelection {
  type: WorkspaceNodeType
  id: number
}

export interface WorkspaceCollections {
  pendingInvitations: PendingInvitationItem[]
  ownedGroups: GroupItem[]
  joinedGroups: GroupItem[]
}

export function resolveWorkspaceSelection(
  collections: WorkspaceCollections,
  preferred: WorkspaceSelection | null,
): WorkspaceSelection | null {
  if (preferred && selectionExists(collections, preferred)) {
    return preferred
  }

  return (
    firstInvitationSelection(collections.pendingInvitations) ??
    firstGroupSelection(collections.ownedGroups, 'ownedGroup') ??
    firstGroupSelection(collections.joinedGroups, 'joinedGroup') ??
    null
  )
}

export function selectFirstByFocus(
  collections: WorkspaceCollections,
  focus: WorkspaceNodeType,
): WorkspaceSelection | null {
  if (focus === 'invitation') {
    return firstInvitationSelection(collections.pendingInvitations)
  }

  if (focus === 'ownedGroup') {
    return firstGroupSelection(collections.ownedGroups, 'ownedGroup')
  }

  return firstGroupSelection(collections.joinedGroups, 'joinedGroup')
}

export function selectionExists(
  collections: WorkspaceCollections,
  selection: WorkspaceSelection,
): boolean {
  if (selection.type === 'invitation') {
    return collections.pendingInvitations.some((item) => item.invitationId === selection.id)
  }

  if (selection.type === 'ownedGroup') {
    return collections.ownedGroups.some((item) => item.groupId === selection.id)
  }

  return collections.joinedGroups.some((item) => item.groupId === selection.id)
}

export function hasWorkspaceItems(collections: WorkspaceCollections) {
  return (
    collections.pendingInvitations.length > 0 ||
    collections.ownedGroups.length > 0 ||
    collections.joinedGroups.length > 0
  )
}

export function isSameSelection(
  left: WorkspaceSelection | null,
  right: WorkspaceSelection | null,
) {
  return left?.type === right?.type && left?.id === right?.id
}

function firstInvitationSelection(invitations: PendingInvitationItem[]): WorkspaceSelection | null {
  const first = invitations[0]
  return first ? { type: 'invitation', id: first.invitationId } : null
}

function firstGroupSelection(
  groups: GroupItem[],
  type: Extract<WorkspaceNodeType, 'ownedGroup' | 'joinedGroup'>,
): WorkspaceSelection | null {
  const first = groups[0]
  return first ? { type, id: first.groupId } : null
}
