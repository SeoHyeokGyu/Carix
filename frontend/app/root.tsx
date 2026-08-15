import { Links, Meta, Outlet, Scripts, ScrollRestoration } from "react-router";
import "pretendard/dist/web/variable/pretendardvariable.css";
import "./styles/tokens.css";

export function Layout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko">
      <head>
        <meta charSet="UTF-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1.0" />
        <title>Carix — 소득, 물가, 스킬 수요로 보는 커리어 실질가치</title>
        <meta
          name="description"
          content="Carix는 소득 변화와 소비 구조, 노동시장의 스킬 수요와 공개연봉을 연결해 실질 구매력과 시장가치를 함께 추적하는 개인 커리어 지수 플랫폼입니다."
        />
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link
          rel="preconnect"
          href="https://fonts.gstatic.com"
          crossOrigin="anonymous"
        />
        <link
          href="https://fonts.googleapis.com/css2?family=Outfit:wght@500;600;700&display=swap"
          rel="stylesheet"
        />
        <Meta />
        <Links />
      </head>
      <body className="bg-carix-bg text-carix-text font-body antialiased">
        {children}
        <ScrollRestoration />
        <Scripts />
      </body>
    </html>
  );
}

export default function Root() {
  return <Outlet />;
}
