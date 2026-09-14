import { describe, expect, it } from 'vitest';
import { commandCookbookItems, commandCookbookProcedures, commandPlaceholders, filterCommandCookbook, resolveCookbookCommand } from './commandCookbook';

describe('command cookbook', () => {
  it('extracts and deduplicates resource placeholders', () => {
    expect(commandPlaceholders('kubectl logs POD_NAME -c CONTAINER_NAME POD_NAME'))
      .toEqual(['POD_NAME', 'CONTAINER_NAME']);
  });

  it('fills known resource names while retaining unresolved placeholders', () => {
    expect(resolveCookbookCommand('kubectl logs POD_NAME -c CONTAINER_NAME', { POD_NAME: 'api-0', CONTAINER_NAME: '' }))
      .toBe('kubectl logs api-0 -c CONTAINER_NAME');
  });

  it('keeps every guided procedure linked to known commands', () => {
    const ids = new Set(commandCookbookItems.map((item) => item.id));
    expect(commandCookbookProcedures.length).toBeGreaterThanOrEqual(4);
    commandCookbookProcedures.forEach((procedure) => procedure.commandIds.forEach((id) => expect(ids.has(id)).toBe(true)));
  });

  it('filters by category and localized searchable text', () => {
    const result = filterCommandCookbook(commandCookbookItems, 'services', 'endpoint',
      (item) => item.id === 'serviceEndpoints' ? 'Endpoint 상태' : '');
    expect(result.map((item) => item.id)).toContain('serviceEndpoints');
    expect(result.every((item) => item.category === 'services')).toBe(true);
  });

  it('keeps cookbook commands compatible with the single-command console contract', () => {
    expect(commandCookbookItems.length).toBeGreaterThan(30);
    for (const item of commandCookbookItems) {
      expect(item.command).toMatch(/^kubectl /);
      expect(item.command).not.toMatch(/[;|`\n\r]/);
      expect(item.command).not.toContain('base64 -d');
    }
  });
});
