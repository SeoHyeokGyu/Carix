import { createServer } from "node:http";
import { readFile, stat } from "node:fs/promises";
import { extname, join, normalize } from "node:path";
import { fileURLToPath } from "node:url";

const PORT = Number(process.env.PORT ?? 3000);
const CLIENT_DIR = fileURLToPath(new URL("./build/client/", import.meta.url));

// 빌드가 내보내는 SPA 셸. index.html 은 사전 렌더링된 랜딩이라 셸로 쓰면
// 다른 경로에서 랜딩 내용이 먼저 그려진다.
const SPA_FALLBACK = "/__spa-fallback.html";

/** @type {Record<string, string>} */
const CONTENT_TYPES = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".svg": "image/svg+xml",
  ".json": "application/json; charset=utf-8",
  ".woff2": "font/woff2",
  ".png": "image/png",
  ".ico": "image/x-icon",
};

/** @param {string} pathname */
async function resolveFile(pathname) {
  const safePath = normalize(join(CLIENT_DIR, pathname)).replace(/\\/g, "/");
  if (!safePath.startsWith(CLIENT_DIR.replace(/\\/g, "/"))) {
    return null;
  }
  try {
    const fileStat = await stat(safePath);
    if (fileStat.isDirectory()) {
      return resolveFile(join(pathname, "index.html"));
    }
    return safePath;
  } catch {
    return null;
  }
}

/**
 * 애플리케이션 라우트는 클라이언트에서만 해석된다 (`ssr: false`, `prerender: ["/"]`).
 * 사전 렌더링된 파일이 없는 경로는 SPA 셸을 돌려줘야 브라우저가 라우팅을 이어받는다.
 * 확장자가 붙은 요청은 에셋이므로 그대로 404 로 둔다 — 셸을 돌려주면 깨진 스크립트를
 * text/html 로 받아 원인을 찾기 어려워진다.
 *
 * @param {string} pathname
 */
function isAssetRequest(pathname) {
  return extname(pathname) !== "";
}

createServer(async (req, res) => {
  const url = new URL(req.url ?? "/", `http://${req.headers.host}`);
  let filePath = await resolveFile(url.pathname);

  if (!filePath && !isAssetRequest(url.pathname)) {
    filePath = await resolveFile(SPA_FALLBACK);
  }

  if (!filePath) {
    res.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
    res.end("Not Found\n");
    return;
  }

  const contentType =
    CONTENT_TYPES[extname(filePath)] ?? "application/octet-stream";
  const body = await readFile(filePath);
  res.writeHead(200, { "Content-Type": contentType });
  res.end(body);
}).listen(PORT, () => {
  console.log(`frontend listening on ${PORT}`);
});
