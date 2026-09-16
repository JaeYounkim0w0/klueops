const ANSI_ESCAPE_PATTERN = /(?:\u001B\[[0-?]*[ -/]*[@-~]|\[[0-9;]*m)/g;

/** stripAnsi 처리에 필요한 화면 또는 업무 로직을 수행한다. */
export function stripAnsi(value: string) {
  return value.replace(ANSI_ESCAPE_PATTERN, '');
}

/** displayText 처리에 필요한 화면 또는 업무 로직을 수행한다. */
export function displayText(value: unknown, fallback = '-') {
  if (typeof value !== 'string') {
    return fallback;
  }
  const cleaned = stripAnsi(value).trim();
  return cleaned || fallback;
}
