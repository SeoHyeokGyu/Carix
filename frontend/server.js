import { createServer } from "node:http";
import { readFile, stat } from "node:fs/promises";
import { extname, join, normalize } from "node:path";
import { fileURLToPath } from "node:url";

const PORT = Number(process.env.PORT ?? 3000);
const CLIENT_DIR = fileURLToPath(new URL("./build/client/", import.meta.url));

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

createServer(async (req, res) => {
  const url = new URL(req.url ?? "/", `http://${req.headers.host}`);
  const filePath = await resolveFile(url.pathname);

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
