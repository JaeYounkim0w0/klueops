import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';

const appSource = readFileSync(new URL('../App.vue', import.meta.url), 'utf8');
const routerSource = readFileSync(new URL('../router/index.ts', import.meta.url), 'utf8');
const preferencesSource = readFileSync(new URL('../views/PreferencesView.vue', import.meta.url), 'utf8');
const jobDockSource = readFileSync(new URL('../components/JobDock.vue', import.meta.url), 'utf8');
const notificationSource = readFileSync(new URL('../components/NotificationCenter.vue', import.meta.url), 'utf8');
const trustCenterSource = readFileSync(new URL('../views/AiTrustCenterView.vue', import.meta.url), 'utf8');

describe('frontend smoke test', () => {
  it('runs vitest', () => {
    expect('k8s-aiops-platform').toContain('aiops');
  });

  it('exposes localized shell navigation and a preferences route', () => {
    expect(appSource).toContain("t('shell.dashboard')");
    expect(appSource).toContain('to="/settings/preferences"');
    expect(routerSource).toContain("path: '/settings/preferences'");
  });

  it('provides an accessible Korean and English language selector', () => {
    expect(preferencesSource).toContain("setLocale('ko-KR')");
    expect(preferencesSource).toContain("setLocale('en-US')");
    expect(preferencesSource).toContain('aria-pressed');
  });

  it('localizes persistent job and notification surfaces', () => {
    expect(jobDockSource).toContain("t('jobs.title')");
    expect(notificationSource).toContain("t('notifications.title')");
  });

  it('provides login, access denied, and identity administration routes', () => {
    expect(routerSource).toContain("path: '/login'");
    expect(routerSource).toContain("path: '/access-denied'");
    expect(routerSource).toContain("path: '/settings/access'");
    expect(appSource).toContain("auth.user?.displayName");
    expect(appSource).toContain("auth.hasCapability('identity:manage')");
  });

  it('provides a dedicated AI trust center with release evidence', () => {
    expect(routerSource).toContain("path: '/ai/trust'");
    expect(appSource).toContain('to="/ai/trust"');
    expect(trustCenterSource).toContain('getAiTrustSnapshot');
  });
});
