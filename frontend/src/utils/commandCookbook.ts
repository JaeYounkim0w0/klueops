export type CommandCookbookScope = 'cluster' | 'namespace';
export type CommandCookbookSafety = 'readOnly' | 'diagnostic' | 'interactive';

export interface CommandCookbookItem {
  id: string;
  category: string;
  command: string;
  scope: CommandCookbookScope;
  safety: CommandCookbookSafety;
  requirements?: string[];
  expectedResult?: string;
  nextCommandId?: string;
}

export interface CommandCookbookProcedure {
  id: string;
  commandIds: string[];
}

export const commandCookbookCategories = [
  'overview', 'pods', 'logs', 'services', 'workloads', 'resources', 'nodes', 'storage', 'network', 'security'
] as const;

export const commandCookbookItems: CommandCookbookItem[] = [
  { id: 'clusterInfo', category: 'overview', command: 'kubectl cluster-info', scope: 'cluster', safety: 'readOnly' },
  { id: 'apiResources', category: 'overview', command: 'kubectl api-resources', scope: 'cluster', safety: 'readOnly' },
  { id: 'namespaceInventory', category: 'overview', command: 'kubectl get pod,svc,deploy,rs,sts,ds,job,cronjob,ingress -o wide', scope: 'namespace', safety: 'readOnly' },
  { id: 'warningEvents', category: 'overview', command: 'kubectl get events --field-selector type=Warning --sort-by=.lastTimestamp', scope: 'namespace', safety: 'readOnly', expectedResult: 'warningEvents', nextCommandId: 'podDetails' },
  { id: 'unhealthyPods', category: 'pods', command: 'kubectl get pods --field-selector=status.phase!=Running,status.phase!=Succeeded -o wide', scope: 'namespace', safety: 'readOnly' },
  { id: 'podDetails', category: 'pods', command: 'kubectl describe pod POD_NAME', scope: 'namespace', safety: 'readOnly', expectedResult: 'podDetails', nextCommandId: 'previousLogs' },
  { id: 'podContainerStates', category: 'pods', command: 'kubectl get pod POD_NAME -o jsonpath={range .status.containerStatuses[*]}{.name}{" ready="}{.ready}{" restarts="}{.restartCount}{" state="}{.state}{"\\n"}{end}', scope: 'namespace', safety: 'readOnly' },
  { id: 'podLabels', category: 'pods', command: 'kubectl get pods --show-labels', scope: 'namespace', safety: 'readOnly' },
  { id: 'podLogs', category: 'logs', command: 'kubectl logs POD_NAME --tail=200 --timestamps', scope: 'namespace', safety: 'diagnostic' },
  { id: 'containerLogs', category: 'logs', command: 'kubectl logs POD_NAME -c CONTAINER_NAME --tail=200 --timestamps', scope: 'namespace', safety: 'diagnostic' },
  { id: 'previousLogs', category: 'logs', command: 'kubectl logs POD_NAME --previous --tail=200 --timestamps', scope: 'namespace', safety: 'diagnostic' },
  { id: 'labelLogs', category: 'logs', command: 'kubectl logs -l app=APP_LABEL --all-containers=true --tail=100 --timestamps', scope: 'namespace', safety: 'diagnostic' },
  { id: 'serviceDetails', category: 'services', command: 'kubectl describe service SERVICE_NAME', scope: 'namespace', safety: 'readOnly', expectedResult: 'serviceDetails', nextCommandId: 'serviceEndpoints' },
  { id: 'serviceEndpoints', category: 'services', command: 'kubectl get endpoints SERVICE_NAME -o wide', scope: 'namespace', safety: 'readOnly', expectedResult: 'serviceEndpoints', nextCommandId: 'endpointSlices' },
  { id: 'endpointSlices', category: 'services', command: 'kubectl get endpointslice -l kubernetes.io/service-name=SERVICE_NAME -o wide', scope: 'namespace', safety: 'readOnly' },
  { id: 'serviceSelector', category: 'services', command: 'kubectl get service SERVICE_NAME -o jsonpath={.spec.selector}', scope: 'namespace', safety: 'readOnly' },
  { id: 'deploymentStatus', category: 'workloads', command: 'kubectl rollout status deployment/DEPLOYMENT_NAME --timeout=30s', scope: 'namespace', safety: 'readOnly' },
  { id: 'deploymentHistory', category: 'workloads', command: 'kubectl rollout history deployment/DEPLOYMENT_NAME', scope: 'namespace', safety: 'readOnly' },
  { id: 'workloadConditions', category: 'workloads', command: 'kubectl get deploy,sts,ds -o wide', scope: 'namespace', safety: 'readOnly' },
  { id: 'hpa', category: 'resources', command: 'kubectl get hpa -o wide', scope: 'namespace', safety: 'readOnly' },
  { id: 'pdb', category: 'resources', command: 'kubectl get pdb -o wide', scope: 'namespace', safety: 'readOnly' },
  { id: 'quotaAndLimits', category: 'resources', command: 'kubectl get resourcequota,limitrange', scope: 'namespace', safety: 'readOnly' },
  { id: 'podUsage', category: 'resources', command: 'kubectl top pods --containers --sort-by=memory', scope: 'namespace', safety: 'readOnly', requirements: ['metricsServer'] },
  { id: 'nodes', category: 'nodes', command: 'kubectl get nodes -o wide', scope: 'cluster', safety: 'readOnly' },
  { id: 'nodeConditions', category: 'nodes', command: 'kubectl get nodes -o custom-columns=NAME:.metadata.name,READY:.status.conditions[?(@.type=="Ready")].status,MEMORY:.status.conditions[?(@.type=="MemoryPressure")].status,DISK:.status.conditions[?(@.type=="DiskPressure")].status,PID:.status.conditions[?(@.type=="PIDPressure")].status', scope: 'cluster', safety: 'readOnly' },
  { id: 'nodePods', category: 'nodes', command: 'kubectl get pods -A -o wide --field-selector spec.nodeName=NODE_NAME', scope: 'cluster', safety: 'readOnly' },
  { id: 'nodeUsage', category: 'nodes', command: 'kubectl top nodes', scope: 'cluster', safety: 'readOnly', requirements: ['metricsServer'] },
  { id: 'pvc', category: 'storage', command: 'kubectl get pvc -o wide', scope: 'namespace', safety: 'readOnly', expectedResult: 'pvc', nextCommandId: 'pvcDetails' },
  { id: 'pvcDetails', category: 'storage', command: 'kubectl describe pvc PVC_NAME', scope: 'namespace', safety: 'readOnly' },
  { id: 'storageClasses', category: 'storage', command: 'kubectl get storageclass', scope: 'cluster', safety: 'readOnly' },
  { id: 'ingress', category: 'network', command: 'kubectl get ingress -o wide', scope: 'namespace', safety: 'readOnly' },
  { id: 'ingressDetails', category: 'network', command: 'kubectl describe ingress INGRESS_NAME', scope: 'namespace', safety: 'readOnly' },
  { id: 'networkPolicies', category: 'network', command: 'kubectl get networkpolicy -o wide', scope: 'namespace', safety: 'readOnly' },
  { id: 'coreDnsPods', category: 'network', command: 'kubectl get pods -n kube-system -l k8s-app=kube-dns -o wide', scope: 'cluster', safety: 'readOnly' },
  { id: 'coreDnsLogs', category: 'network', command: 'kubectl logs -n kube-system -l k8s-app=kube-dns --tail=100 --timestamps', scope: 'cluster', safety: 'diagnostic' },
  { id: 'canIListPods', category: 'security', command: 'kubectl auth can-i list pods', scope: 'namespace', safety: 'readOnly' },
  { id: 'serviceAccounts', category: 'security', command: 'kubectl get serviceaccount', scope: 'namespace', safety: 'readOnly' },
  { id: 'roleBindings', category: 'security', command: 'kubectl get rolebinding -o wide', scope: 'namespace', safety: 'readOnly' },
  { id: 'secretMetadata', category: 'security', command: 'kubectl get secret -o custom-columns=NAME:.metadata.name,TYPE:.type,AGE:.metadata.creationTimestamp', scope: 'namespace', safety: 'readOnly' }
];

export const commandCookbookProcedures: CommandCookbookProcedure[] = [
  { id: 'podFailure', commandIds: ['unhealthyPods', 'podDetails', 'previousLogs', 'warningEvents'] },
  { id: 'serviceTraffic', commandIds: ['serviceDetails', 'serviceEndpoints', 'endpointSlices', 'networkPolicies'] },
  { id: 'storagePending', commandIds: ['pvc', 'pvcDetails', 'storageClasses', 'warningEvents'] },
  { id: 'nodePressure', commandIds: ['nodeConditions', 'nodeUsage', 'nodePods', 'warningEvents'] }
];

export function commandPlaceholders(command: string): string[] {
  return [...new Set(command.match(/\b[A-Z][A-Z0-9]*_(?:NAME|LABEL)\b/g) ?? [])];
}

export function resolveCookbookCommand(command: string, values: Record<string, string>): string {
  return commandPlaceholders(command).reduce((resolved, placeholder) => {
    const value = values[placeholder]?.trim();
    return value ? resolved.split(placeholder).join(value) : resolved;
  }, command);
}

export function filterCommandCookbook(items: CommandCookbookItem[], category: string, query: string,
                                      searchableText: (item: CommandCookbookItem) => string): CommandCookbookItem[] {
  const normalized = query.trim().toLocaleLowerCase();
  return items.filter((item) => (category === 'all' || item.category === category)
    && (!normalized || `${item.command} ${searchableText(item)}`.toLocaleLowerCase().includes(normalized)));
}
