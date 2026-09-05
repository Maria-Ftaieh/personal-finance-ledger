import react from "@vitejs/plugin-react";
import { defineConfig, type Plugin } from "vite";

/** Title and language of the HTML shell, per locale. The app strings live in src/i18n.ts. */
const SHELL = {
  tr: { title: "Finans Defteri", description: "Harcama ve enflasyon" },
  en: { title: "Finance Ledger", description: "Spending and inflation" },
} as const;

/**
 * Stamps the shell with the build's locale.
 *
 * The application sets `document.title` and `lang` from the dictionary once it loads, but
 * that is too late for two things that matter on a public demo: the tab title flashes the
 * wrong language, and anything that reads the HTML without running JavaScript — a link
 * preview, a crawler — only ever sees the static value.
 */
function localiseShell(): Plugin {
  const locale = process.env.VITE_LOCALE === "en" ? "en" : "tr";
  const { title, description } = SHELL[locale];
  return {
    name: "localise-shell",
    transformIndexHtml: (html) =>
      html
        .replace(/<html lang="[^"]*">/, `<html lang="${locale}">`)
        .replace(/<title>[^<]*<\/title>/, `<title>${title}</title>`)
        .replace(
          "</head>",
          `  <meta name="description" content="${description}" />\n  </head>`,
        ),
  };
}

export default defineConfig({
  plugins: [react(), localiseShell()],
  server: {
    // Same-origin in development, so nothing needs CORS and the production
    // deployment (nginx in front of both) behaves the same way.
    proxy: {
      "/api": {
        target: process.env.LEDGER_API ?? "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
});
