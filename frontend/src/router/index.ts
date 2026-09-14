import { createRouter, createWebHistory } from 'vue-router';
import DashboardView from '@/views/DashboardView.vue';
import { reportRouteFailure } from './routeFailure';
import { useAuthStore } from '@/stores/auth';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
    { path: '/access-denied', name: 'access-denied', component: () => import('@/views/AccessDeniedView.vue'), meta: { public: true } },
    { path: '/', name: 'dashboard', component: DashboardView, meta: { capability: 'cluster:read' } },
    { path: '/clusters', name: 'clusters', component: () => import('@/views/ClustersView.vue'), meta: { capability: 'cluster:read' } },
    { path: '/clusters/:clusterId', name: 'cluster-detail', component: () => import('@/views/ClusterDetailView.vue'), meta: { capability: 'cluster:read' } },
    { path: '/clusters/:clusterId/console', name: 'cluster-console', component: () => import('@/views/KubernetesConsoleView.vue'), meta: { capability: 'operation:execute' } },
    { path: '/applications', name: 'applications', component: () => import('@/views/ApplicationsView.vue'), meta: { capability: 'cluster:read' } },
    { path: '/incidents', name: 'incidents', component: () => import('@/views/IncidentsView.vue'), meta: { capability: 'analysis:read' } },
    { path: '/triage', name: 'triage', component: () => import('@/views/TriageWorkbenchView.vue'), meta: { capability: 'analysis:read' } },
    { path: '/operations/fleet', name: 'fleet-command-center', component: () => import('@/views/FleetCommandCenterView.vue'), meta: { capability: 'cluster:read' } },
    { path: '/incidents/:incidentId', name: 'incident-detail', component: () => import('@/views/IncidentDetailView.vue'), meta: { capability: 'analysis:read' } },
    { path: '/policies', name: 'policies', component: () => import('@/views/PoliciesView.vue'), meta: { capability: 'cluster:read' } },
    { path: '/runbooks', name: 'runbooks', component: () => import('@/views/RunbooksView.vue'), meta: { capability: 'analysis:read' } },
    { path: '/audit', name: 'audit', component: () => import('@/views/AuditView.vue'), meta: { capability: 'audit:read' } },
    { path: '/settings/operations', name: 'operations-settings', component: () => import('@/views/OperationsSettingsView.vue'), meta: { capability: 'platform:admin' } },
    { path: '/settings/reliability', name: 'operations-reliability', component: () => import('@/views/OperationsReliabilityView.vue'), meta: { capability: 'audit:read' } },
    { path: '/settings/preferences', name: 'preferences', component: () => import('@/views/PreferencesView.vue') },
    { path: '/settings/access', name: 'access-management', component: () => import('@/views/AccessManagementView.vue'), meta: { capability: 'identity:manage' } },
    { path: '/settings/tenancy', name: 'tenancy-management', component: () => import('@/views/TenantManagementView.vue'), meta: { capability: 'platform:admin' } },
    { path: '/analysis', name: 'analysis', component: () => import('@/views/AnalysisView.vue'), meta: { capability: 'analysis:read' } },
    { path: '/ai/trust', name: 'ai-trust-center', component: () => import('@/views/AiTrustCenterView.vue'), meta: { capability: 'analysis:read' } },
    { path: '/ai-chat', name: 'ai-chat', component: () => import('@/views/AiChatView.vue'), meta: { capability: 'analysis:read' } }
  ]
});

router.onError(reportRouteFailure);

router.beforeEach(async (to) => {
  if (to.meta.public === true) return true;
  const auth = useAuthStore();
  try {
    await auth.load();
  } catch {
    return { name: 'login', query: { returnTo: to.fullPath, reason: 'session-check-failed' } };
  }
  if (!auth.authenticated) {
    return { name: 'login', query: { returnTo: to.fullPath } };
  }
  if (to.path === '/') {
    const returnTo = auth.consumeReturnTo();
    if (returnTo && returnTo !== to.fullPath) return returnTo;
  }
  const capability = typeof to.meta.capability === 'string' ? to.meta.capability : null;
  if (capability && !auth.hasCapability(capability)) {
    return { name: 'access-denied', query: { capability } };
  }
  return true;
});

export default router;
