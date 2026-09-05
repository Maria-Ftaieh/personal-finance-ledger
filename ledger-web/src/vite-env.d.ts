/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** `tr` (default) or `en`; see src/i18n.ts. */
  readonly VITE_LOCALE?: string;
  /** "true" shows the fictional-data banner required by SPEC §8.3. */
  readonly VITE_DEMO_MODE?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
