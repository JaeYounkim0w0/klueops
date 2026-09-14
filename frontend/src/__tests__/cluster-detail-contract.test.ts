import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const viewSource = readFileSync(resolve(process.cwd(), 'src/views/ClusterDetailView.vue'), 'utf8');
const analysisSource = readFileSync(resolve(process.cwd(), 'src/views/AnalysisView.vue'), 'utf8');
const styleSource = [
  readFileSync(resolve(process.cwd(), 'src/styles/main.css'), 'utf8'),
  readFileSync(resolve(process.cwd(), 'src/styles/components/cluster-detail.css'), 'utf8')
].join('\n');

describe('cluster detail operations UX contract', () => {
  it('does not block initial detail rendering on live namespace/node lookups', () => {
    const loadAllBody = viewSource.slice(
      viewSource.indexOf('async function loadAll()'),
      viewSource.indexOf('async function loadRuntimeInfo')
    );
    expect(loadAllBody).not.toContain('loadRuntimeInfo(id)');
    expect(loadAllBody).toContain('loadOperationsInfo(id)');
    expect(viewSource).toContain('Live 상태 새로고침');
    expect(viewSource).toContain('displayedNodes');
  });

  it('shows resource intelligence before raw YAML evidence', () => {
    const intelligenceIndex = viewSource.indexOf('RESOURCE INTELLIGENCE');
    const manifestIndex = viewSource.indexOf('LIVE MANIFEST');
    expect(intelligenceIndex).toBeGreaterThan(-1);
    expect(manifestIndex).toBeGreaterThan(-1);
    expect(intelligenceIndex).toBeLessThan(manifestIndex);
  });

  it('preserves analysis navigation context from cluster detail', () => {
    expect(viewSource).toContain("mode: namespace ? 'namespace' : 'cluster'");
    expect(analysisSource).toContain("const namespace = typeof route.query.namespace === 'string' ? route.query.namespace : '';");
    expect(analysisSource).toContain("analysisMode.value = 'namespace'");
    expect(analysisSource).toContain('selectedNamespace.value = namespace');
  });

  it('keeps cluster detail grids inside the available content width', () => {
    expect(styleSource).toContain('.cluster-detail-page *');
    expect(styleSource).toContain('box-sizing: border-box');
    expect(styleSource).toMatch(/\.cluster-detail-sidebar,[\s\S]*?\.cluster-detail-main[\s\S]*?min-width: 0/);
    expect(styleSource).toMatch(/@media \(max-width: 1400px\) and \(min-width: 801px\)/);
  });

  it('uses one resource type control instead of duplicating it in the heading', () => {
    expect(viewSource).not.toContain('<select v-model="selectedResourceType"');
    expect(viewSource).toContain('class="resource-type-grid"');
    expect(viewSource).toContain("@click=\"selectResourceType('__ALL__')\"");
  });

  it('provides recent and streaming logs inside workload details', () => {
    expect(viewSource).toContain("resourceDetailTab = ref<'overview' | 'logs' | 'yaml'>('overview')");
    expect(viewSource).toContain("value=\"recent\"");
    expect(viewSource).toContain("value=\"stream\"");
    expect(viewSource).toContain('loadResourceLogTargets');
    expect(viewSource).toContain('loadRecentResourceLogs');
    expect(viewSource).toContain('startResourceLogStream');
    expect(viewSource).toContain('stopResourceLogStream');
    expect(viewSource).toContain('resourceLogAutoScroll');
    expect(viewSource).toContain('resourceLogTailOptions = [50, 100, 300, 500, 1000]');
    expect(viewSource).toContain("t('clusterLogs.tailLines', { count })");
    expect(styleSource).toContain('.resource-log-viewer');
  });

  it('provides evidence-based capability, credential, and upgrade readiness views', () => {
    expect(viewSource).toContain('loadClusterReadiness');
    expect(viewSource).toContain("'capabilities' | 'credential' | 'upgrade'");
    expect(viewSource).toContain('readiness.capabilities.checks');
    expect(viewSource).toContain('readiness.credential.secretValueExposed');
    expect(viewSource).toContain('readiness.upgrade.findings');
  });
});
