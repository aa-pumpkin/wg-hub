FROM node:24-slim AS build
RUN apt-get update -qq && apt-get install -y -qq openssl >/dev/null
WORKDIR /app
COPY package.json package-lock.json ./
COPY backend/package.json backend/
COPY frontend/package.json frontend/
RUN npm ci -w backend --include-workspace-root=false
COPY backend backend
RUN npm run build -w backend
RUN npm prune --omit=dev -w backend --include-workspace-root=false

FROM node:24-slim
RUN apt-get update -qq && apt-get install -y -qq openssl >/dev/null
ENV NODE_ENV=production
WORKDIR /app
COPY --from=build /app/node_modules node_modules
COPY --from=build /app/package.json package.json
COPY --from=build /app/backend/package.json backend/package.json
COPY --from=build /app/backend/dist backend/dist
COPY --from=build /app/backend/prisma backend/prisma
WORKDIR /app/backend
USER node
EXPOSE 3000
CMD ["sh", "-c", "npx prisma migrate deploy && exec node dist/server.js"]
