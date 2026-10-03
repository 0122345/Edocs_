import { describe, expect, it } from 'vitest'
import { can, deniedReason, permissionsFor } from '../lib/rbac'

describe('RBAC matrix', () => {
  it('gives administrators every permission', () => {
    expect(permissionsFor('admin')).toHaveLength(12)
  })

  it('limits signers to signing', () => {
    expect(permissionsFor('signer')).toEqual(['document:sign'])
    expect(can('signer', 'document:edit')).toBe(false)
    expect(can('signer', 'audit:read')).toBe(false)
  })

  it('keeps auditors read-only', () => {
    expect(can('auditor', 'audit:export')).toBe(true)
    expect(can('auditor', 'document:create')).toBe(false)
    expect(can('auditor', 'document:sign')).toBe(false)
  })

  it('denies everything without a role', () => {
    expect(can(undefined, 'document:sign')).toBe(false)
  })

  it('lists several allowed roles in plain English', () => {
    expect(deniedReason('audit:read')).toBe('Only administrator, legal counsel or auditor accounts can do this.')
  })

  it('explains denials by naming the allowed roles', () => {
    expect(deniedReason('members:manage')).toBe('Only administrator accounts can do this.')
  })
})
