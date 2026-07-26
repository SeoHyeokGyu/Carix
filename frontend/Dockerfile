# 의존성이 없는 스텁이라 빌드 단계가 없다.
# 실제 프런트엔드 스캐폴드 시 멀티스테이지(pnpm install → build → 런타임 prod deps)로 교체한다.
FROM node:24-alpine
WORKDIR /app
COPY package.json server.js ./
ENV NODE_ENV=production
EXPOSE 3000
CMD ["node", "server.js"]
