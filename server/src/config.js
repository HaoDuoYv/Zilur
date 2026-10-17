'use strict';

const path = require('node:path');
const fs = require('node:fs');

// 本地 `npm start` 时从 server/.env 读；Docker 里由 compose 的 env_file 注入，
// 此时没有 .env 文件 —— dotenv 找不到文件只会返回空，不会抛错。
require('dotenv').config({ path: path.join(__dirname, '..', '.env') });

function toBool(raw, fallback) {
  if (raw === undefined || raw === null || String(raw).trim() === '') return fallback;
  return ['1', 'true', 'yes', 'on'].includes(String(raw).trim().toLowerCase());
}

function toInt(raw, fallback) {
  const value = Number.parseInt(String(raw ?? '').trim(), 10);
  return Number.isFinite(value) && value > 0 ? value : fallback;
}

const dataDir = process.env.DATA_DIR
  ? path.resolve(process.env.DATA_DIR)
  : path.join(__dirname, '..', 'data');

const smtp = {
  host: (process.env.SMTP_HOST || '').trim(),
  port: toInt(process.env.SMTP_PORT, 465),
  secure: toBool(process.env.SMTP_SECURE, true),
  user: (process.env.SMTP_USER || '').trim(),
  pass: (process.env.SMTP_PASS || '').trim(),
  from: (process.env.MAIL_FROM || '').trim(),
  // 只改发件人**显示名**（默认应用名）。地址必须等于认证账号，改了会被 SMTP 拒。
  fromName: (process.env.MAIL_FROM_NAME || '').trim(),
  // 留空 = 不设 Reply-To。系统邮件靠 Auto-Submitted 头抑制自动回复，不需要回复地址。
  replyTo: (process.env.MAIL_REPLY_TO || '').trim(),
};

// 三项齐全才算"配了邮件"。少一项就当没配 —— 免得运行到发信时才炸。
const mailConfigured = Boolean(smtp.host && smtp.user && smtp.pass);

const config = {
  port: toInt(process.env.PORT, 8787),
  dataDir,
  avatarDir: path.join(dataDir, 'avatars'),
  backupDir: path.join(dataDir, 'backups'),
  dbFile: path.join(dataDir, 'app.db'),
  jwtSecret: (process.env.JWT_SECRET || '').trim(),
  jwtTtlDays: toInt(process.env.JWT_TTL_DAYS, 30),
  corsOrigin: (process.env.CORS_ORIGIN || '').trim(),
  trustProxy: toBool(process.env.TRUST_PROXY, false),
  registrationEnabled: toBool(process.env.REGISTRATION_ENABLED, true),
  smtp,
  mailConfigured,
  // 没配邮件时强制按 false 处理：否则注册会卡在一个永远发不出去的验证码上
  requireEmailVerification: mailConfigured && toBool(process.env.REQUIRE_EMAIL_VERIFICATION, true),
  maxAvatarBytes: toInt(process.env.MAX_AVATAR_MB, 2) * 1024 * 1024,
  maxBackupBytes: toInt(process.env.MAX_BACKUP_MB, 128) * 1024 * 1024,
  codeTtlMs: toInt(process.env.CODE_TTL_MINUTES, 10) * 60 * 1000,
  codeCooldownMs: toInt(process.env.CODE_COOLDOWN_SECONDS, 60) * 1000,
};

const MIN_SECRET_LENGTH = 32;
if (config.jwtSecret.length < MIN_SECRET_LENGTH) {
  // 宁可起不来，也不要带着一个弱密钥把所有人的令牌暴露出去
  throw new Error(
    `JWT_SECRET 缺失或过短（需要至少 ${MIN_SECRET_LENGTH} 个字符）。\n` +
      '  生成一个：openssl rand -hex 32\n' +
      '  然后写进 server/.env 的 JWT_SECRET= 后面。'
  );
}

for (const dir of [config.dataDir, config.avatarDir, config.backupDir]) {
  fs.mkdirSync(dir, { recursive: true });
}

/**
 * 数据路径必须能被容器内用户写入。
 *
 * 这是最坑的部署问题：容器里以 `node`(uid 1000) 运行，而宿主机挂进来的 `./data`
 * 属主多半是 root。启动**能过**（目录已经存在），直到第一次写库才炸，而且错误是
 * `SQLITE_READONLY`、栈埋在路由深处 —— 完全看不出是权限问题。不如在这里先说清楚。
 */
const writableTargets = [
  config.dataDir,
  config.avatarDir,
  config.backupDir,
  // 库文件已经存在的话要单独查：目录可写不代表这个 root 建出来的文件也可写
  ...(fs.existsSync(config.dbFile) ? [config.dbFile] : []),
];
for (const target of writableTargets) {
  try {
    fs.accessSync(target, fs.constants.W_OK);
  } catch (error) {
    throw new Error(
      `数据路径不可写：${target}\n` +
        '  容器内以 node(uid 1000) 运行，宿主机挂进来的 ./data 多半属于 root。\n' +
        '  在**宿主机**上修（别进容器里改）：\n' +
        '    sudo chown -R 1000:1000 ./data\n' +
        '    docker compose restart\n' +
        '  若仍失败且 getenforce 返回 Enforcing，是 SELinux：\n' +
        '    sudo chcon -Rt svirt_sandbox_file_t ./data\n' +
        `  原始错误：${error.message}`
    );
  }
}

module.exports = config;
