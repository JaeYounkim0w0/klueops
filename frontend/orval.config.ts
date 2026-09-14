import { defineConfig } from 'orval';

export default defineConfig({
  klueOps: {
    input: {
      target: './openapi/klueops.json'
    },
    output: {
      target: './src/api/generated/klueops.ts',
      client: 'fetch',
      mode: 'single',
      clean: true
    }
  }
});
