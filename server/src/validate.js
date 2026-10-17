'use strict';

const { HttpError } = require('./http');

/** 邮箱 / 密码 / 用户名 / 验证码的形状校验。路由里只负责"进不来就给 400"。 */
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
const MIN_PASSWORD = 8;
// bcrypt 只取前 72 字节，超出部分被静默忽略 —— 不如直接拒绝，免得用户以为长密码更安全
const MAX_PASSWORD = 72;
const MAX_USERNAME = 24;

function normalizeEmail(raw) {
  return String(raw ?? '').trim().toLowerCase();
}

function emailOrThrow(raw) {
  const email = normalizeEmail(raw);
  if (!EMAIL_RE.test(email) || email.length > 254) {
    throw new HttpError(400, '邮箱格式不正确');
  }
  return email;
}

function passwordOrThrow(raw) {
  const password = String(raw ?? '');
  if (password.length < MIN_PASSWORD) {
    throw new HttpError(400, `密码至少 ${MIN_PASSWORD} 位`);
  }
  if (Buffer.byteLength(password, 'utf8') > MAX_PASSWORD) {
    throw new HttpError(400, `密码最多 ${MAX_PASSWORD} 字节`);
  }
  return password;
}

/** 用户名：去掉首尾空白、压掉换行；空时回落到 [fallback]（注册用邮箱前缀）。 */
function usernameOrThrow(raw, fallback) {
  const name = String(raw ?? '')
    .replace(/[\r\n\t]+/g, ' ')
    .trim();
  if (!name) {
    if (fallback) return fallback.slice(0, MAX_USERNAME);
    throw new HttpError(400, '用户名不能为空');
  }
  if (name.length > MAX_USERNAME) {
    throw new HttpError(400, `用户名最多 ${MAX_USERNAME} 个字`);
  }
  return name;
}

function codeOrThrow(raw) {
  const code = String(raw ?? '').trim();
  if (!/^\d{6}$/.test(code)) {
    throw new HttpError(400, '验证码是 6 位数字');
  }
  return code;
}

/** 邮箱前缀兜底用户名：`a.b@x.com` → `a.b`。 */
function defaultUsernameFor(email) {
  const prefix = email.split('@')[0].replace(/[^\p{L}\p{N}._-]/gu, '');
  return (prefix || '知录用户').slice(0, MAX_USERNAME);
}

module.exports = {
  normalizeEmail,
  emailOrThrow,
  passwordOrThrow,
  usernameOrThrow,
  codeOrThrow,
  defaultUsernameFor,
  MIN_PASSWORD,
  MAX_USERNAME,
};
