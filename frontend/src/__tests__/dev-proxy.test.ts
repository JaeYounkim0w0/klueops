import { describe, expect, it } from 'vitest';

import viteConfig from '../../vite.config';

describe('development authentication proxy', () => {
  it('keeps the SPA login route in Vite and proxies only the OAuth callback', () => {
    const proxy = viteConfig.server?.proxy;

    expect(proxy).not.toHaveProperty('/login');
    expect(proxy).toHaveProperty('^/login/oauth2/code/');
    expect(proxy?.['/oauth2']).toMatchObject({
      target: 'http://localhost:8080',
      changeOrigin: false,
      xfwd: true,
    });
    expect(proxy?.['/ws']).toMatchObject({
      target: 'http://localhost:8080',
      changeOrigin: true,
      rewriteWsOrigin: true,
      ws: true,
    });
  });
});
