import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [react()],
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
