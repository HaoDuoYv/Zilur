# 知录 · 云端服务（可选）

邮箱账号、用户资料、手动云端备份。**不部署它，App 依然完全可用** —— 笔记、AI、复习都在本地。
这一套只解决两件事：

1. 让「账号」有个落脚点（换设备也能记住你是谁）；
2. 让 `.dtk` 备份有个地方存（手动上传 / 手动恢复，不做自动同步）。

技术栈刻意选得轻：Node + Express + SQLite，**一个容器 + 一个数据卷**。
2 核 2G 的小机器足够，空闲常驻约 60–120 MB。

---

## 一、最小部署（Docker Compose）

```bash
cd server
cp .env.example .env
vi .env                       # 只填两项：JWT_SECRET 与 SMTP 相关
docker compose up -d --build
curl http://127.0.0.1:8787/api/health
```

第 1 项必填：

```bash
openssl rand -hex 32          # 把输出粘到 .env 的 JWT_SECRET=
```

第 2 项是邮件（找回密码 / 注册验证码）。以 QQ 邮箱为例：

| 变量 | 值 |
|---|---|
| `SMTP_HOST` | `smtp.qq.com` |
| `SMTP_PORT` | `465` |
| `SMTP_SECURE` | `true` |
| `SMTP_USER` | 你的完整邮箱地址 |
| `SMTP_PASS` | **邮箱设置里生成的「授权码」**（不是登录密码） |

> 授权码获取：QQ 邮箱 → 设置 → 账号 → 开启「IMAP/SMTP 服务」→ 生成授权码。
> 163 邮箱同理。**这个授权码只写进 `server/.env`，`.env` 已被 `.gitignore` 排除。**

**邮件长什么样**（不用改就能用）：

| 项 | 值 |
|---|---|
| 发件人显示名 | `知录`（`MAIL_FROM_NAME`，可改） |
| 发件人地址 | `MAIL_FROM`，留空用 `SMTP_USER` |
| 标题 | `【知录】注册验证码` / `【知录】重置密码验证码` |
| 正文 | `text/plain` + `text/html` 两份（`multipart/alternative`） |

> ⚠️ **`MAIL_FROM` 的地址必须等于 `SMTP_USER`**。QQ / 163 / Gmail 都要求信封发件人
> 与认证账号一致，改了会被直接拒发（501）。想换"看起来是谁发的"只改 `MAIL_FROM_NAME`
> —— 那是显示名，随便填都不影响投递。

邮件是系统自动发的、**不需要回复**：报文里带 `Auto-Submitted: auto-generated` 与
`X-Auto-Response-Suppress: All`，让对方的自动回复/外出助理不要再回一封。
真要给用户一个回复去处才配 `MAIL_REPLY_TO`（留空就是不设 `Reply-To`）。
验证码也**不写进标题** —— 标题会被邮件客户端的通知栏直接显示出来。

**不想配邮件也行**：把 `SMTP_*` 全部留空。此时注册不要求验证码、找回密码不可用
（接口会返回"未配置邮件服务"），其余功能照常。自己一个人用时这样最省事。

部署完在 App 里：`我的 → 云端服务 → 服务地址` 填 `http://你的服务器IP:8787` → 测试连接。

---

## 二、上 HTTPS（强烈建议，尤其公网）

App 默认允许 http（自建局域网很常见），但明文传输的密码与令牌等于没有密码。
小机器上用 Caddy 一行搞定，它会自动申请并续期证书：

```caddyfile
# /etc/caddy/Caddyfile
zhilu.example.com {
    reverse_proxy 127.0.0.1:8787
}
```

然后：

1. `docker-compose.yml` 的 ports 改成只监听回环（不暴露公网）：
   ```yaml
   ports: ["127.0.0.1:8787:8787"]
   ```
2. `.env` 里设 `TRUST_PROXY=true`（限流才能拿到真实客户端 IP）
3. App 里的服务地址改成 `https://zhilu.example.com`

> 云厂商安全组记得只放 80/443，别把 8787 开到公网。

---

## 三、运维

```bash
docker compose logs -f --tail 100      # 看日志（SMTP 验证失败会在这里）
docker compose restart                 # 改完 .env 要重启才生效
docker compose up -d --build           # 升级
```

**备份服务器**：整个 `./data` 目录就是全部状态。

```bash
tar czf zhilu-cloud-$(date +%F).tar.gz data/
```

里面是：`app.db`（SQLite，含用户表）、`avatars/`、`backups/`。
恢复时把这个目录放回去再 `docker compose up -d` 即可。

### 升级

```bash
git status                       # 先看有没有改过仓库里的文件（改过的话 git pull 会冲突）
git pull
docker compose up -d --build     # 重建镜像并换掉容器
```

`.env` 与 `data/` 都不在版本库里，**`git pull` 不会动它们** —— 账号、头像、
云端备份原样保留，不需要迁移数据库。

升级完确认一下跑的确实是新代码（以邮件模板为例）：

```bash
docker exec zhilu-cloud grep -c "Auto-Submitted" src/mail.js   # 输出非 0 = 新代码
curl http://127.0.0.1:8787/api/health
```

只改了 `.env`、没改代码的话不必重建，`docker compose restart` 就够。

**回滚**：

```bash
git log --oneline -5              # 挑一个旧 SHA
git checkout <旧SHA>
docker compose up -d --build
```

老镜像会变成 dangling，攒多了清理：`docker image prune`。

> `docker compose up -d --build` 期间服务会短暂中断（几十秒）。
> 想更稳就先 `docker compose build` 再 `up -d`。

---

## 四、接口一览

除 `health` / `auth/*` 外都需要 `Authorization: Bearer <token>`。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/health` | 探活 + 服务端能力（邮件是否已配置等） |
| POST | `/api/auth/send-code` | `{email, purpose:'register'\|'reset'}` 发验证码（60s 冷却） |
| POST | `/api/auth/register` | `{email, password, code?, username?}` |
| POST | `/api/auth/login` | `{email, password}` → `{token, user}` |
| POST | `/api/auth/reset-password` | `{email, code, password}` → 直接就登录 |
| GET | `/api/me` | 当前账号 |
| PATCH | `/api/me` | `{username}` |
| POST | `/api/me/avatar` | multipart，字段 `avatar`，JPG/PNG/WebP ≤2MB |
| DELETE | `/api/me/avatar` | 删除头像 |
| GET | `/api/backup` | 备份元信息（`null` = 还没有） |
| PUT | `/api/backup` | multipart，字段 `backup`，上传即替换 |
| GET | `/api/backup/download` | 下载原始字节 |
| DELETE | `/api/backup` | 删除云端备份 |

---

## 五、冒烟测试

对着**已经跑起来**的服务端走一遍主链路（注册→登录→资料→头像→备份上传/下载/删除）：

```bash
node scripts/smoke.js http://127.0.0.1:8787
```

要求邮箱验证码时无法自动注册，脚本会说明并跳过注册链路。想完整跑：

```bash
# .env 里临时设 REQUIRE_EMAIL_VERIFICATION=false，重启后
node scripts/smoke.js
```

---

## 六、构建常见问题

### `SQLITE_READONLY: attempt to write a readonly database`

不是代码问题，是**宿主机上 `./data` 的属主不对**。容器里以 `node`(uid 1000) 运行，
而 compose 挂进去的 `./data` 通常属于 root。启动能过（目录已存在），第一次写库才炸。

```bash
# 先看清楚是谁的
docker exec zhilu-cloud id              # uid=1000(node)
ls -ld data data/app.db 2>/dev/null

# 在宿主机上修（别进容器里改，bind mount 的属主以宿主机为准）
sudo chown -R 1000:1000 ./data
docker compose restart
```

新版本会在启动阶段就检查这些路径的可写性，直接把上面这段话打进日志，
不用再从路由栈里猜。

**仍失败**：`getenforce` 返回 `Enforcing` 就是 SELinux（OpenCloudOS / TencentOS 常见）：

```bash
sudo chcon -Rt svirt_sandbox_file_t ./data
```

### `npm ci` 卡在 better-sqlite3：`Request timed out` + `Could not find any Python installation`

这个依赖带原生二进制，装的时候分两条路：

1. `prebuild-install` 从 **GitHub Releases** 下现成的 `.node`；
2. 下不到就回退 `node-gyp` 源码编译 —— slim 镜像里没有 Python/make/g++，直接失败。

国内云主机（含腾讯云）访问 GitHub 经常超时，于是必走第 2 条，然后必挂。
Dockerfile 已经默认把下载源换到 npmmirror（内容与 GitHub 产物一致），一般不用管。
如果你在境外部署，覆盖回官方源：

```bash
docker build \
  --build-arg NPM_REGISTRY=https://registry.npmjs.org \
  --build-arg SQLITE3_PREBUILD_MIRROR=https://github.com/WiseLibs/better-sqlite3/releases/download \
  -t zhilu-cloud:1.0.0 .
```

镜像源换不动、又确实连不上外网的极端情况：Dockerfile 里的 apt 兜底会装上
`python3 make g++` 让源码编译跑通（装完依赖后 purge，不进最终镜像）。
apt 本身失败也不会阻断构建。

---

## 七、安全边界（部署者需要知道的）

已经做了：

- 密码 bcrypt(10) 存哈希；登录失败文案统一、且对不存在账号也做一次 compare（防时序探测）
- 令牌 HS256 + 过期时间；每次请求回查用户（删号后旧令牌立即失效）
- 验证码：只存哈希、10 分钟有效、错 5 次作废、60 秒冷却、新码发出即作废旧码
- 全局限流 + 鉴权接口单独限流（内存滑动窗口；重启清零，单容器够用）
- 头像按**魔数**判定格式（不信任 Content-Type/文件名），服务端生成随机文件名，静态目录 `nosniff`
- 备份落临时名 → 校验 zip 头 → 再改名（坏包不会覆盖上一份好备份）
- `.env` / `data/` / `*.dtk` / 密钥材料全部在 `.gitignore` 里

没有做（按需要再补）：

- **令牌吊销**：JWT 无状态，改密码不会让其它设备掉线。要"全部下线"得加 token 版本号
- **邮箱真实性**：关闭 `REQUIRE_EMAIL_VERIFICATION` 时无法确认邮箱属于本人
- **备份的完整校验**：服务端只认 zip 头，路径穿越与 zip bomb 由 App 侧 `ArchiveManager` 挡
- **多副本 / 高可用**：SQLite 单文件，适合单机；要横向扩展得换 Postgres
