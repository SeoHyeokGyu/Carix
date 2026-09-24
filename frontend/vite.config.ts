import { fileURLToPath } from "node:url";
import { reactRouter } from "@react-router/dev/vite";
import { defineConfig, loadEnv } from "vite";
import tailwindcss from "@tailwindcss/vite";

/**
 * 개발 서버에서 쓰는 `/api` 프록시.
 *
 * 1. 기본값: OCI 원격 배포 서버 (https://carix.144.24.90.88.sslip.io)
 * 2. 로컬 게이트웨이 연동: CARIX_API_TARGET=http://localhost:8080
 * 3. 로컬 collector 단독 연동: CARIX_COLLECTOR_TARGET=http://localhost:8083 (Gateway 없이 자체 rewrite)
 * 4. 로컬 core-api 단독 연동: CARIX_API_TARGET=http://localhost:8082
 *
 * .env 또는 .env.local 파일에서 설정하거나 환경변수로 주입할 수 있습니다.
 */
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const API_TARGET =
    env.CARIX_API_TARGET ||
    process.env.CARIX_API_TARGET ||
    "https://carix.144.24.90.88.sslip.io";

  const COLLECTOR_TARGET =
    env.CARIX_COLLECTOR_TARGET || process.env.CARIX_COLLECTOR_TARGET;

  return {
    plugins: [tailwindcss(), reactRouter()],
    resolve: {
      alias: { "@": fileURLToPath(new URL("./app", import.meta.url)) },
    },
    server: {
      proxy: {
        ...(COLLECTOR_TARGET
          ? {
              "/api/v1/admin/collector": {
                target: COLLECTOR_TARGET,
                changeOrigin: true,
                rewrite: (path: string) =>
                  path.replace(
                    /^\/api\/v1\/admin\/collector/,
                    "/internal/pipeline",
                  ),
              },
              "/api/v1/admin/pipeline": {
                target: COLLECTOR_TARGET,
                changeOrigin: true,
                rewrite: (path: string) =>
                  path.replace(
                    /^\/api\/v1\/admin\/pipeline/,
                    "/internal/pipeline",
                  ),
              },
            }
          : {}),
        "/api": {
          target: API_TARGET,
          changeOrigin: true,
          secure: false,
        },
      },
    },
  };
});
