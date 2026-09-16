import { readonly, ref } from 'vue';

export interface OperationsFeedEvent {
  type: string;
  receivedAt: string;
}

const connected = ref(false);
const lastEvent = ref<OperationsFeedEvent | null>(null);
const eventCount = ref(0);
const subscribers = new Set<(event: OperationsFeedEvent) => void>();
let source: EventSource | null = null;

const eventNames = [
  'triage',
  'incident',
  'watch-signal',
  'watch-status',
  'watch-continuity',
  'noise-policy',
  'remediation',
  'ai-release-gate',
  'postmortem',
  'heartbeat'
];

/** ensureConnected 처리에 필요한 화면 또는 업무 로직을 수행한다. */
function ensureConnected() {
  if (source || typeof EventSource === 'undefined') return;
  source = new EventSource('/api/operations/events');
  source.onopen = () => { connected.value = true; };
  source.onerror = () => { connected.value = false; };
  for (const type of eventNames) {
    source.addEventListener(type, () => {
      const event = { type, receivedAt: new Date().toISOString() };
      lastEvent.value = event;
      eventCount.value += 1;
      subscribers.forEach((subscriber) => subscriber(event));
    });
  }
}

/** subscribeOperationsFeed 처리에 필요한 화면 또는 업무 로직을 수행한다. */
export function subscribeOperationsFeed(subscriber: (event: OperationsFeedEvent) => void) {
  subscribers.add(subscriber);
  ensureConnected();
  return () => subscribers.delete(subscriber);
}

/** useOperationsFeedState 처리에 필요한 화면 또는 업무 로직을 수행한다. */
export function useOperationsFeedState() {
  ensureConnected();
  return {
    connected: readonly(connected),
    lastEvent: readonly(lastEvent),
    eventCount: readonly(eventCount)
  };
}
