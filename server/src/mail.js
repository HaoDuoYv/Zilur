'use strict';

const config = require('./config');

/**
 * 验证码邮件。
 *
 * 没配 SMTP 时**不抛在启动阶段** —— 这个服务端本来就允许"只跑账号不跑邮件"，
 * 直到真的有人点「忘记密码」才需要它，那时给一句能看懂的报错即可。
 */
let transport = null;
if (config.mailConfigured) {
  const nodemailer = require('nodemailer');
  transport = nodemailer.createTransport({
    host: config.smtp.host,
    port: config.smtp.port,
    secure: config.smtp.secure,
    auth: { user: config.smtp.user, pass: config.smtp.pass },
  });
}

/**
 * 发件人只改**显示名**、不改地址：QQ / 163 这类 SMTP 要求信封发件人必须等于
 * 认证账号，改地址会直接被拒（501 / "mail from must equal authorized user"）。
 * 显示名走 `MAIL_FROM_NAME`，默认应用名。
 */
const FROM_NAME = config.smtp.fromName || '知录';
const FROM_ADDRESS = config.smtp.from || config.smtp.user;

const PURPOSE = {
  register: { short: '注册', action: '注册知录账号' },
  reset: { short: '重置密码', action: '重置知录账号的密码' },
};

/** 收信人统一成 trim + 小写（路由层已做过，这里再兜一次，防将来有人直接调 sendCode）。 */
function normalizeRecipient(raw) {
  return String(raw ?? '').trim().toLowerCase();
}

/**
 * 组装一封"正规"的邮件：显示名、统一前缀的标题、纯文本 + HTML 两份正文、
 * 以及让对方自动回复机制闭嘴的头。
 *
 * 验证码**不进标题** —— 标题会被邮件客户端的通知栏直接显示出来。
 */
function buildMail(code, purpose, to) {
  const { short, action } = PURPOSE[purpose] || { short: '验证', action: '操作知录账号' };
  const minutes = Math.round(config.codeTtlMs / 60000);

  const text = [
    '知录',
    '',
    `你正在${action}。`,
    '',
    `验证码：${code}`,
    '',
    `${minutes} 分钟内有效。验证码请勿转发给任何人。`,
    '',
    '——',
    '本邮件由系统自动发送，无需回复。',
    '如果不是你本人操作，请忽略这封邮件，你的账号不受影响。',
  ].join('\n');

  const html = `<!doctype html>
<html lang="zh-CN">
<head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
<body style="margin:0;padding:24px;background:#f3f1ec;font-family:-apple-system,'PingFang SC','Microsoft YaHei',sans-serif;color:#23201c">
  <div style="max-width:440px;margin:0 auto;background:#fffdf9;border-radius:14px;padding:24px">
    <p style="margin:0 0 2px;font-size:15px;font-weight:600">知录</p>
    <p style="margin:0 0 20px;font-size:13px;color:#8a8175">${short}验证</p>
    <p style="margin:0 0 6px;font-size:13px;color:#6b5b45">你正在${action}，验证码是：</p>
    <p style="margin:0 0 20px;font-size:28px;letter-spacing:6px;font-weight:600">${code}</p>
    <p style="margin:0;font-size:13px;line-height:1.7;color:#6b5b45">
      ${minutes} 分钟内有效。验证码请勿转发给任何人。
    </p>
    <hr style="border:0;border-top:1px solid #ece6dc;margin:20px 0">
    <p style="margin:0;font-size:12px;line-height:1.7;color:#9a9186">
      本邮件由系统自动发送，<strong style="color:#6b5b45">无需回复</strong>。<br>
      如果不是你本人操作，请忽略这封邮件，你的账号不受影响。
    </p>
  </div>
</body>
</html>`;

  const mail = {
    from: { name: FROM_NAME, address: FROM_ADDRESS },
    to: { address: to },
    subject: `【知录】${short}验证码`,
    text,
    html,
    // RFC 3834：告诉对方的自动回复机制「这封是机器发的，别回」。
    // X-Auto-Response-Suppress 是 Exchange / Outlook 认的同一件事。
    headers: {
      'Auto-Submitted': 'auto-generated',
      'X-Auto-Response-Suppress': 'All',
    },
  };

  // 真要给人一个回复去处才配；留空就是不设 Reply-To，配合上面的头就够了
  if (config.smtp.replyTo) {
    mail.replyTo = config.smtp.replyTo;
  }

  return mail;
}

async function sendCode(email, code, purpose) {
  if (!transport) {
    const error = new Error('服务端未配置邮件服务，暂时无法发送验证码（请管理员在 .env 里填 SMTP）');
    error.status = 503;
    throw error;
  }
  await transport.sendMail(buildMail(code, purpose, normalizeRecipient(email)));
}

/**
 * 启动时探一次 SMTP：授权码填错是最常见的部署问题，
 * 与其等用户点「忘记密码」才发现，不如在日志里先说一句。
 */
function verifyTransport() {
  if (!transport) {
    console.warn('[mail] 未配置 SMTP：注册将不要求邮箱验证码，找回密码不可用');
    return;
  }
  transport
    .verify()
    .then(() => console.log('[mail] SMTP 连接正常'))
    .catch((error) => {
      console.error('[mail] SMTP 验证失败，请检查 SMTP_HOST/用户/授权码：', error.message);
    });
}

// buildMail 一并导出：改邮件模板后不用真发信就能核对报文（scripts 与单测里用 jsonTransport 渲染）
module.exports = { sendCode, verifyTransport, buildMail, isConfigured: Boolean(transport) };
