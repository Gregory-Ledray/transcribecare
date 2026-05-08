FROM node:20-slim AS build

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM node:20-slim AS production

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --omit=dev

COPY --from=build /app/dist ./dist
COPY --from=build /app/server.ts ./server.ts
COPY --from=build /app/tsconfig.json ./tsconfig.json

# tsx runs TypeScript directly without a separate compile step
RUN npx tsx --version > /dev/null 2>&1 || npm install tsx

EXPOSE 8080
ENV PORT=8080

CMD ["npx", "tsx", "server.ts"]
