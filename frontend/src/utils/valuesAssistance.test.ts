import { describe, expect, it } from 'vitest';
import { valuesDigest, previewValuesChanges } from './valuesAssistance';

describe('Values 생성 원본 지문', () => {
  /** 실제 변경만 보여주고 신규 object 안의 민감값도 가린다. */
  it('compares changes without exposing nested credentials', () => {
    expect(previewValuesChanges('replicas: 1', 'replicas: 2')).toEqual([{path: 'replicas', before: '1', after: '2'}]);
    expect(previewValuesChanges('{}', 'auth:\n  password: private-value')[0].after).toBe('보호된 설정');
    expect(previewValuesChanges('replicas: 1', 'replicas: 1')).toEqual([]);
    expect(previewValuesChanges('{}', 'server:\n  service:\n    type: NodePort')[0].path).toBe('server.service.type');
  });
  /** 빈 Values는 서버와 동일한 mapping으로 정규화한다. */
  it('normalizes blank overrides and detects changed content', async () => {
    expect(await valuesDigest('  \n')).toBe(await valuesDigest('{}'));
    expect(await valuesDigest('replicas: 1')).not.toBe(await valuesDigest('replicas: 2'));
    expect(await valuesDigest('{}')).toBe('44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a');
  });
});
