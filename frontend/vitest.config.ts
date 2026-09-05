import { defineConfig } from "vite";
import { configDefaults } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  test: {
    environment: "jsdom",
    setupFiles: ["./test-setup.ts"],
    globals: true,
    exclude: [...configDefaults.exclude, "e2e/**", "server.test.js"],
    coverage: {
      provider: "v8",
      reporter: ["text", "json-summary", "json", "html", "lcov"],
      reportsDirectory: "./coverage",
      include: ["app/**"],
      exclude: [
        ...(configDefaults.coverage?.exclude ?? []),
        "**/*.d.ts",
        "**/*.test.{ts,tsx}",
        "**/*.spec.{ts,tsx}",
        "e2e/**",
        "server.test.js",
      ],
    },
  },
});
