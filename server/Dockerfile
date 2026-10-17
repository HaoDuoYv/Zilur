# 单阶段就够：这个服务没有编译步骤，镜像里只需要 node + 依赖 + src。
# 用 slim（Debian glibc）而不是 alpine：better-sqlite3 的预编译二进制按 glibc 发布，
# alpine 的 musl 会让 npm install 退化成源码编译（要 python3 + make + g++，镜像反而更大）。
FROM node:22-slim

# ── 镜像源（构建期可覆盖）────────────────────────────────
# better-sqlite3 的预编译包默认从 GitHub Releases 下载。国内云主机常常连不上，
# prebuild-install 超时后会回退 node-gyp 源码编译，而 slim 镜像里没有 Python/make/g++，
# 表现就是 npm ci 卡在 better-sqlite3 这一步失败。
# 这里默认改用 npmmirror（内容与 GitHub 同名产物一致）；境外部署可覆盖：
#   docker build --build-arg NPM_REGISTRY=https://registry.npmjs.org \
#     --build-arg SQLITE3_PREBUILD_MIRROR=https://github.com/WiseLibs/better-sqlite3/releases/download
ARG NPM_REGISTRY=https://registry.npmmirror.com
ARG SQLITE3_PREBUILD_MIRROR=https://registry.npmmirror.com/-/binary/better-sqlite3

ENV NODE_ENV=production \
    npm_config_update_notifier=false \
    npm_config_fund=false \
    npm_config_audit=false \
    npm_config_registry=${NPM_REGISTRY} \
    npm_config_better_sqlite3_binary_host_mirror=${SQLITE3_PREBUILD_MIRROR}

WORKDIR /app

# 先只拷依赖清单：改代码不会让依赖层失效
COPY package.json package-lock.json* ./

# 编译工具链是**兜底**：只有预编译包没下到、node-gyp 回退时才用得上。
# apt 装不上也不阻断构建 —— 有镜像时根本用不到它；装上了会在装完依赖后 purge 掉，不进最终层。
# 没有 lock 文件时用 install（CI 首次构建），有则用 ci（更快更确定）
RUN (apt-get update && apt-get install -y --no-install-recommends python3 make g++) \
    || echo "[warn] 编译工具链未装上，将完全依赖 better-sqlite3 预编译包" \
    && if [ -f package-lock.json ]; then npm ci --omit=dev; else npm install --omit=dev; fi \
    && (apt-get purge -y --auto-remove python3 make g++ || true) \
    && rm -rf /var/lib/apt/lists/* /root/.npm/_logs \
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
