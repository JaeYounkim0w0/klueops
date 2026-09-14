const ANSI_ESCAPE_PATTERN = /(?:\u001B\[[0-?]*[ -/]*[@-~]|\[[0-9;]*m)/g;

export function stripAnsi(value: string) {
  return value.replace(ANSI_ESCAPE_PATTERN, '');
}

export function displayText(value: unknown, fallback = '-') {
  if (typeof value !== 'string') {
    return fallback;
  }
  const cleaned = stripAnsi(value).trim();
  return cleaned || fallback;
}
