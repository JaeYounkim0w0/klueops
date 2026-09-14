export function formatElapsedDuration(startedAt?: string, completedAt?: string, now = new Date()) {
  if (!startedAt) {
    return '-';
  }

  const startedTime = Date.parse(startedAt);
  if (Number.isNaN(startedTime)) {
    return '-';
  }

  const completedTime = completedAt ? Date.parse(completedAt) : now.getTime();
  if (Number.isNaN(completedTime)) {
    return '-';
  }

  const elapsedSeconds = Math.max(0, Math.floor((completedTime - startedTime) / 1000));
  const hours = Math.floor(elapsedSeconds / 3600);
  const minutes = Math.floor((elapsedSeconds % 3600) / 60);
  const seconds = elapsedSeconds % 60;

  if (hours > 0) {
    return `${hours}시간 ${minutes}분`;
  }

  if (minutes > 0) {
    return `${minutes}분 ${seconds.toString().padStart(2, '0')}초`;
  }

  return `${seconds}초`;
}

export function formatDurationMs(durationMs?: number) {
  if (durationMs == null || !Number.isFinite(durationMs) || durationMs < 0) {
    return '-';
  }
  const elapsedSeconds = Math.floor(durationMs / 1000);
  const hours = Math.floor(elapsedSeconds / 3600);
  const minutes = Math.floor((elapsedSeconds % 3600) / 60);
  const seconds = elapsedSeconds % 60;
  if (hours > 0) return `${hours}시간 ${minutes}분`;
  if (minutes > 0) return `${minutes}분 ${seconds.toString().padStart(2, '0')}초`;
  return `${seconds}초`;
}

export function formatTimestamp(value?: string) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '-';
  return new Intl.DateTimeFormat('ko-KR', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).format(date);
}
