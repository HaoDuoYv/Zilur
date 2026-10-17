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

const PURPOSE_LABEL = {
  register: '注册验证',
  reset: '重置密码',
};

function buildMail(code, purpose) {
  const label = PURPOSE_LABEL[purpose] || '验证';
  const minutes = Math.round(config.codeTtlMs / 60000);
  const text = [
    `你正在「知录」的${label}。`,
    '',
    `验证码：${code}`,
    '',
    `${minutes} 分钟内有效。如果不是你本人操作，请忽略这封邮件。`,
  ].join('\n');

  const html = `<!doctype html><html><body style="margin:0;padding:24px;background:#f3f1ec;font-family:-apple-system,'PingFang SC','Microsoft YaHei',sans-serif;color:#23201c">
  <div style="max-width:420px;margin:0 auto;background:#fffdf9;border-radius:14px;padding:24px">
    <p style="margin:0 0 8px;font-size:13px;color:#8a8175">知录 · ${label}</p>
    <p style="margin:0 0 16px;font-size:28px;letter-spacing:6px;font-weight:600">${code}</p>
    <p style="margin:0;font-size:13px;line-height:1.7;color:#6b5b45">
      ${minutes} 分钟内有效。如果不是你本人操作，请忽略这封邮件。
    </p>
  </div>
</body></html>`;

  return {
    from: config.smtp.from || config.smtp.user,
    to: '',
    subject: `知录${label}验证码：${code}`,
    text,
    html,
  };
}

async function sendCode(email, code, purpose) {
  if (!transport) {
    const error = new Error('服务端未配置邮件服务，暂时无法发送验证码（请管理员在 .env 里填 SMTP）');
    error.status = 503;
    throw error;
  }
  const mail = buildMail(code, purpose);
  mail.to = email;
  await transport.sendMail(mail);
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

module.exports = { sendCode, verifyTransport, isConfigured: Boolean(transport) };
