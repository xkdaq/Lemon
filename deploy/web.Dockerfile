# 前端镜像：构建管理系统和用户端 H5，由 Nginx 提供静态文件并反向代理接口
# 构建上下文为仓库根目录：docker compose 里 context: ..

# 构建阶段用构建机自己的平台（产物是静态文件，与 CPU 架构无关），跨平台构建时不必走 QEMU 模拟
# ---------- 管理系统 ----------
FROM --platform=$BUILDPLATFORM node:22-alpine AS admin
WORKDIR /src
COPY lemon-admin/package.json lemon-admin/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY lemon-admin/ ./
RUN npm run build

# ---------- 用户端 H5 ----------
FROM --platform=$BUILDPLATFORM node:22-alpine AS app
WORKDIR /src
COPY lemon-app/package.json lemon-app/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY lemon-app/ ./
RUN npm run build:h5

# ---------- 运行 ----------
FROM nginx:1.27-alpine
ENV TZ=Asia/Shanghai
COPY --from=admin /src/dist /usr/share/nginx/admin
COPY --from=app /src/dist/build/h5 /usr/share/nginx/www
COPY deploy/nginx/snippets /etc/nginx/snippets
