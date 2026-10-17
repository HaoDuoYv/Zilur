'use strict';

const express = require('express');
const path = require('node:path');

const config = require('./config');
const mail = require('./mail');
const { HttpError } = require('./http');
const { requireAuth } = require('./auth');
const { createLimiter } = require('./rateLimit');

const app = express();
const VERSION = require('../package.json').version;

app.set('trust proxy', config.trustProxy);
app.disable('x-powered-by');

// 客户端是 Android App，不需要跨域；只有用浏览器调试后台时才打开
if (config.corsOrigin) {
  app.use((req, res, next) => {
    res.setHeader('Access-Control-Allow-Origin', config.corsOrigin);
    res.setHeader('Access-Control-Allow-Headers', 'Authorization, Content-Type');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PATCH, PUT, DELETE, OPTIONS');
    if (req.method === 'OPTIONS') return res.sendStatus(204);
    return next();
  });
}

// 头像走静态目录：禁止浏览器"猜"类型，否则一个内容是 HTML 的 .png 会被当页面执行
app.use((req, res, next) => {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  next();
});

app.use(express.json({ limit: '64kb' }));

/** 探活：App 的「测试连接」与 docker healthcheck 都用它 */
app.get('/api/health', (req, res) => {
  res.json({
    ok: true,
    name: 'zhilu-cloud',
    version: VERSION,
    // 下面三项让 App 能提前把界面调成对的形态（比如没配邮件就别显示"获取验证码"）
    mailConfigured: mail.isConfigured,
    registrationEnabled: config.registrationEnabled,
    requireEmailVerification: config.requireEmailVerification,
  });
});

// 全局限流：挡住"一个脚本把 CPU 打满"，比拦单接口更重要
app.use(
  '/api',
  createLimiter({ windowMs: 10 * 60 * 1000, max: 1200, message: '请求过于频繁，请稍后再试' })
);

// 鉴权相关接口单独收紧：验证码与密码是唯一值得暴力破解的两处
app.use(
  '/api/auth',
  createLimiter({ windowMs: 10 * 60 * 1000, max: 60, message: '尝试过于频繁，请 10 分钟后再试' })
);
app.use('/api/auth', require('./routes/auth'));

app.use('/api/me', requireAuth, require('./routes/me'));
app.use('/api/backup', requireAuth, require('./routes/backup'));

// 头像文件。文件名带随机串且不可枚举；这里再关掉目录索引与 dotfiles
app.use(
  '/avatars',
  express.static(config.avatarDir, {
    index: false,
    dotfiles: 'ignore',
    maxAge: '7d',
    immutable: true,
  })
);

app.use((req, res) => {
  res.status(404).json({ error: { message: `没有这个接口：${req.method} ${path.basename(req.path)}` } });
});

// 错误处理：业务异常用它自己的 status；multer 的超限单独映射成 413
// eslint-disable-next-line no-unused-vars
app.use((error, req, res, next) => {
  if (error instanceof HttpError) {
    return res.status(error.status).json({ error: { message: error.message } });
  }
  if (error?.code === 'LIMIT_FILE_SIZE') {
    return res.status(413).json({ error: { message: '文件超出大小上限' } });
  }
  if (error?.type === 'entity.too.large') {
    return res.status(413).json({ error: { message: '请求体过大' } });
  }
  if (error instanceof SyntaxError && 'body' in error) {
    return res.status(400).json({ error: { message: '请求体不是合法 JSON' } });
  }
  console.error('[error]', error);
  return res.status(500).json({ error: { message: '服务器内部错误' } });
});

const server = app.listen(config.port, () => {
  console.log(`[zhilu-cloud] v${VERSION} 已启动：http://0.0.0.0:${config.port}`);
  console.log(`[zhilu-cloud] 数据目录：${config.dataDir}`);
  console.log(
    `[zhilu-cloud] 注册：${config.registrationEnabled ? '开放' : '关闭'}` +
      ` · 注册验证码：${config.requireEmailVerification ? '需要' : '不需要'}`
  );
  mail.verifyTransport();
});

// Docker 停容器发 SIGTERM：给正在写的请求一点时间，别硬切
for (const signal of ['SIGTERM', 'SIGINT']) {
  process.on(signal, () => {
    console.log(`[zhilu-cloud] 收到 ${signal}，正在退出…`);
    server.close(() => process.exit(0));
    setTimeout(() => process.exit(0), 5000).unref();
  });
}

module.exports = app;
