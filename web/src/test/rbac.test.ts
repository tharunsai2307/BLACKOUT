import { describe, it, expect } from 'vitest';
import { AppRole } from '../types/database';

function checkRolePermissions(role: AppRole) {
  return {
    canVerify: role === 'SUPER_ADMIN' || role === 'CONTROL_ROOM_OPERATOR',
    canDispatch: role === 'SUPER_ADMIN' || role === 'CONTROL_ROOM_OPERATOR' || role === 'FIELD_RESPONSE_COORDINATOR',
    canPublishWarnings: role === 'SUPER_ADMIN' || role === 'PUBLIC_WARNING_PUBLISHER',
    canViewAudit: role === 'SUPER_ADMIN' || role === 'CONTROL_ROOM_OPERATOR',
    isSuperAdmin: role === 'SUPER_ADMIN',
  };
}

describe('Role-Based Access Control (RBAC) Perms', () => {
  it('SUPER_ADMIN has full permissions across all operations', () => {
    const perms = checkRolePermissions('SUPER_ADMIN');
    expect(perms.canVerify).toBe(true);
    expect(perms.canDispatch).toBe(true);
    expect(perms.canPublishWarnings).toBe(true);
    expect(perms.canViewAudit).toBe(true);
    expect(perms.isSuperAdmin).toBe(true);
  });

  it('CONTROL_ROOM_OPERATOR can verify, dispatch, and view audit, but cannot publish public warnings', () => {
    const perms = checkRolePermissions('CONTROL_ROOM_OPERATOR');
    expect(perms.canVerify).toBe(true);
    expect(perms.canDispatch).toBe(true);
    expect(perms.canPublishWarnings).toBe(false);
    expect(perms.canViewAudit).toBe(true);
    expect(perms.isSuperAdmin).toBe(false);
  });

  it('FIELD_RESPONSE_COORDINATOR can dispatch response teams, but cannot verify incidents or publish warnings', () => {
    const perms = checkRolePermissions('FIELD_RESPONSE_COORDINATOR');
    expect(perms.canVerify).toBe(false);
    expect(perms.canDispatch).toBe(true);
    expect(perms.canPublishWarnings).toBe(false);
    expect(perms.canViewAudit).toBe(false);
  });

  it('PUBLIC_WARNING_PUBLISHER can approve and publish warnings, but cannot verify incidents or dispatch units', () => {
    const perms = checkRolePermissions('PUBLIC_WARNING_PUBLISHER');
    expect(perms.canVerify).toBe(false);
    expect(perms.canDispatch).toBe(false);
    expect(perms.canPublishWarnings).toBe(true);
    expect(perms.canViewAudit).toBe(false);
  });

  it('VIEWER has read-only access with no privileged actions', () => {
    const perms = checkRolePermissions('VIEWER');
    expect(perms.canVerify).toBe(false);
    expect(perms.canDispatch).toBe(false);
    expect(perms.canPublishWarnings).toBe(false);
    expect(perms.canViewAudit).toBe(false);
    expect(perms.isSuperAdmin).toBe(false);
  });

  it('CITIZEN role has zero administrative or operational permissions', () => {
    const perms = checkRolePermissions('CITIZEN');
    expect(perms.canVerify).toBe(false);
    expect(perms.canDispatch).toBe(false);
    expect(perms.canPublishWarnings).toBe(false);
    expect(perms.canViewAudit).toBe(false);
    expect(perms.isSuperAdmin).toBe(false);
  });
});
