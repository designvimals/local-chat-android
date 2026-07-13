FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
COPY shared/api-contracts/package.json shared/api-contracts/package.json
COPY backend/package.json backend/package.json
COPY web/package.json web/package.json
RUN npm ci
COPY shared ./shared
COPY backend ./backend
COPY web ./web
RUN npm run build

FROM node:22-alpine AS runtime
ENV NODE_ENV=production
WORKDIR /app
COPY package.json package-lock.json ./
COPY shared/api-contracts/package.json shared/api-contracts/package.json
COPY backend/package.json backend/package.json
COPY web/package.json web/package.json
RUN npm ci --omit=dev
COPY --from=build /app/shared/api-contracts/dist ./shared/api-contracts/dist
COPY --from=build /app/backend/dist ./backend/dist
COPY --from=build /app/web/dist ./web/dist
EXPOSE 8787
CMD ["npm", "--workspace", "backend", "run", "start"]
