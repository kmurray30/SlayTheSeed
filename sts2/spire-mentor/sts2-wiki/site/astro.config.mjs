// @ts-check
import { fileURLToPath } from 'node:url';
import { defineConfig } from 'astro/config';
import { loadEnv } from 'vite';

// Load .env / .env.local from this directory. Empty prefix lets us read
// non-VITE_ vars like ASTRO_BASE and ASTRO_ALLOWED_HOSTS.
const env = {
  ...loadEnv(process.env.NODE_ENV ?? 'development', fileURLToPath(new URL('.', import.meta.url)), ''),
  ...process.env,
};

// https://astro.build/config
export default defineConfig({
  output: 'static',
  site: 'https://drmaciver.github.io',
  base: env.ASTRO_BASE || '/sts2-wiki/',
  vite: {
    server: {
      allowedHosts: env.ASTRO_ALLOWED_HOSTS?.split(',').filter(Boolean) ?? [],
    },
  },
});
