# 单阶段就够：这个服务没有编译步骤，镜像里只需要 node + 依赖 + src。
# 用 slim（Debian glibc）而不是 alpine：better-sqlite3 的预编译二进制按 glibc 发布，
# alpine 的 musl 会让 npm install 退化成源码编译（要 python3 + make + g++，镜像反而更大）。
FROM node:22-slim

ENV NODE_ENV=production \
    npm_config_update_notifier=false \
    npm_config_fund=false \
    npm_config_audit=false

WORKDIR /app

# 先只拷依赖清单：改代码不会让依赖层失效
COPY package.json package-lock.json* ./
# 没有 lock 文件时用 install（CI 首次构建），有则用 ci（更快更确定）
RUN if [ -f package-lock.json ]; then npm ci --omit=dev; else npm install --omit=dev; fi \
    && npm cache clean --force

COPY src ./src
COPY scripts ./scripts

# 非 root 运行；数据目录由 compose 挂卷，这里只保证目录存在且属主正确
RUN mkdir -p /data && chown -R node:node /app /data
USER node

VOLUME ["/data"]
EXPOSE 8787

# 健康检查走 App 同一个探活接口（compose 里也有一份，便于 docker run 时也能用）
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD node -e "fetch('http://127.0.0.1:'+(process.env.PORT||8787)+'/api/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"

CMD ["node", "src/index.js"]
