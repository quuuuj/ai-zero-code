# 前端 + nginx 镜像 — 多阶段构建
# 阶段1：Node 构建前端（无需本地预构建 dist/）
FROM node:22-alpine AS build
WORKDIR /build
COPY ai-zero-code-frontend/package*.json ./
RUN npm ci
COPY ai-zero-code-frontend/ ./
RUN npm run build-only

# 阶段2：nginx 运行
FROM nginx:alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY docker/build/ai-zero-code-frontend/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
