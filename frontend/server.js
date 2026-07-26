// 배포 파이프라인 검증용 최소 서버.
// carix-caddy 의 catch-all 라우팅이 frontend-node:3000 을 향하므로,
// 실제 프런트엔드가 스캐폴드되기 전까지 이 자리를 채운다.
import { createServer } from 'node:http';

const PORT = Number(process.env.PORT ?? 3000);

createServer((req, res) => {
  res.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8' });
  res.end('carix frontend stub\n');
}).listen(PORT, () => {
  console.log(`frontend stub listening on ${PORT}`);
});
