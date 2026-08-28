import { reactRouter } from "@react-router/dev/vite";
import { defineConfig } from "vite";
import tailwindcss from "@tailwindcss/vite";

/**
 * 개발 서버에서만 쓰는 `/api` 프록시.
 *
 * 로컬에 백엔드를 띄우지 않으므로 배포된 게이트웨이로 넘긴다(README 개발 환경 절).
 * 운영에서는 Caddy 가 같은 출처에서 `/api` 를 게이트웨이로 보내므로 프록시가 필요 없다.
 *
 * `secure: false` 는 서버 인증서가 자체 서명이라서다. 세션 쿠키는 `Secure` 지만
 * 브라우저가 `localhost` 를 보안 컨텍스트로 취급해 http 로도 저장된다.
 */
const API_TARGET =
  process.env.CARIX_API_TARGET ?? "https://carix.144.24.90.88.sslip.io";

export default defineConfig({
  plugins: [tailwindcss(), reactRouter()],
  server: {
    proxy: {
      "/api": {
        target: API_TARGET,
        changeOrigin: true,
        secure: false,
      },
    },
  },
});
