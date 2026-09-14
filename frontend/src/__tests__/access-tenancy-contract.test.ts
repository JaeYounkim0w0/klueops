import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

describe('tenant scoped role binding UI', () => {
  it('supports tenant and workspace scopes without replacing Keycloak groups', () => {
    const source = readFileSync(new URL('../views/AccessManagementView.vue', import.meta.url), 'utf8');

    expect(source).toContain('<option value="TENANT">');
    expect(source).toContain('<option value="WORKSPACE">');
    expect(source).toContain('tenantId:');
    expect(source).toContain('workspaceId:');
    expect(source).toContain("principalType: 'USER' as 'USER' | 'GROUP'");
    expect(source).toContain('<option value="GROUP">');
  });
});
