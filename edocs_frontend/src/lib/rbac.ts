import type { Role } from '../data/types'

/**
 * Role-based access control. The backend must enforce the same matrix; the UI
 * uses it to hide routes and disable actions a role can't perform.
 */
export type Permission =
  | 'document:create'
  | 'document:edit'
  | 'document:sign'
  | 'document:share'
  | 'document:delete'
  | 'workflow:manage'
  | 'audit:read'
  | 'audit:export'
  | 'compliance:run'
  | 'template:manage'
  | 'members:manage'
  | 'settings:manage'

export const roleLabels: Record<Role, string> = {
  admin: 'Administrator',
  legal: 'Legal counsel',
  signer: 'Signer',
  auditor: 'Auditor',
}

export const roleDescriptions: Record<Role, string> = {
  admin: 'Manage members, settings, retention policies and every document',
  legal: 'Draft, redline, share and send contracts; manage templates and workflows',
  signer: 'Review and sign documents assigned to them',
  auditor: 'Read-only access to archives and audit logs; export evidence',
}

const matrix: Record<Role, Permission[]> = {
  admin: [
    'document:create', 'document:edit', 'document:sign', 'document:share', 'document:delete',
    'workflow:manage', 'audit:read', 'audit:export', 'compliance:run', 'template:manage',
    'members:manage', 'settings:manage',
  ],
  legal: ['document:create', 'document:edit', 'document:sign', 'document:share', 'workflow:manage', 'audit:read', 'template:manage'],
  signer: ['document:sign'],
  auditor: ['audit:read', 'audit:export'],
}

export function can(role: Role | undefined, permission: Permission): boolean {
  return !!role && matrix[role].includes(permission)
}

export function permissionsFor(role: Role): Permission[] {
  return [...matrix[role]]
}

/** Explains a disabled control in plain words. */
export function deniedReason(permission: Permission): string {
  const needs: Role[] = (Object.keys(matrix) as Role[]).filter((r) => matrix[r].includes(permission))
  const names = needs.map((r) => roleLabels[r].toLowerCase())
  const list = names.length > 1 ? `${names.slice(0, -1).join(', ')} or ${names.at(-1)}` : names[0]
  return `Only ${list} accounts can do this.`
}
