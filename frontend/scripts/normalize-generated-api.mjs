import { readFile, writeFile } from 'node:fs/promises';

const generatedFile = new URL('../src/api/generated/klueops.ts', import.meta.url);
const source = await readFile(generatedFile, 'utf8');

// Orval 출력의 의미 없는 줄 끝 공백을 제거해 재생성과 Git 검증 결과를 결정적으로 유지한다.
const normalized = source.replace(/[ \t]+$/gm, '');
if (normalized !== source) {
  await writeFile(generatedFile, normalized, 'utf8');
}
