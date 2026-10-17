'use strict';

const express = require('express');
const bcrypt = require('bcryptjs');
const crypto = require('node:crypto');

const config = require('../config');
const { users, codes } = require('../db');
const { HttpError, wrap } = require('../http');
const { signToken } = require('../auth');
const { toPublicUser } = require('../serialize');
const mail = require('../mail');
const {
  emailOrThrow,
  passwordOrThrow,
  usernameOrThrow,
  codeOrThrow,
  defaultUsernameFor,
} = require('../validate');

const router = express.Router();

const BCRYPT_ROUNDS = 10;
const MAX_CODE_ATTEMPTS = 5;
const PURPOSES = new Set(['register', 'reset']);

/**
 * 一个恒定的 bcrypt 哈希，用来在"账号不存在"时也做一次 compare。
 * 目的不是加密，而是让两条分支的**耗时**接近 —— 否则可以用响应时间探出哪些邮箱注册过。
 */
const DUMMY_HASH = bcrypt.hashSync('zhilu-dummy-password-for-timing', BCRYPT_ROUNDS);

function hashCode(email, purpose, code) {
  // 6 位码本身没有强度，但不是靠它保密：有效期 10 分钟 + 5 次尝试 + 一次性。
  // 哈希只是为了"拖库的人不能直接读到还在等待使用的验证码"。
  return crypto
    .createHash('sha256')
    .update(`${email}|${purpose}|${code}`)
    .digest('hex');
}

function issueCode(email, purpose) {
  const code = String(crypto.randomInt(0, 1_000_000)).padStart(6, '0');
  const now = Date.now();
  // 发新的就作废旧的一律作废：上一封邮件里的码不该还能用
  codes.invalidateOthers.run(email, purpose);
  codes.insert.run(email, purpose, hashCode(email, purpose, code), now + config.codeTtlMs, now);
  return code;
}

/** 校验并消费一个验证码；失败按"错一次"计。 */
function consumeCode(email, purpose, code) {
  const row = codes.latestFor.get(email, purpose);
  if (!row) throw new HttpError(400, '验证码已失效，请重新获取');
  if (row.expires_at < Date.now()) throw new HttpError(400, '验证码已过期，请重新获取');
  if (row.attempts >= MAX_CODE_ATTEMPTS) {
    throw new HttpError(429, '验证码错误次数过多，请重新获取');
  }
  if (row.code_hash !== hashCode(email, purpose, code)) {
    codes.bumpAttempts.run(row.id);
    throw new HttpError(400, '验证码不正确');
  }
  codes.markUsed.run(row.id);
}

function authPayload(user) {
  return { token: signToken(user), user: toPublicUser(user) };
}

/** POST /api/auth/send-code —— 发送验证码（注册 / 找回密码） */
router.post(
  '/send-code',
  wrap(async (req, res) => {
    const email = emailOrThrow(req.body?.email);
    const purpose = String(req.body?.purpose ?? 'register');
    if (!PURPOSES.has(purpose)) throw new HttpError(400, 'purpose 只能是 register 或 reset');

    if (!mail.isConfigured) {
      throw new HttpError(503, '服务端未配置邮件服务，暂时无法发送验证码');
    }

    if (purpose === 'register') {
      if (!config.registrationEnabled) throw new HttpError(403, '本服务已关闭注册');
      // 注册时如实告知"已注册"：这是必要的可用性反馈，用户得知道自己该去登录
      if (users.byEmail.get(email)) throw new HttpError(409, '该邮箱已注册，请直接登录');
    } else if (!users.byEmail.get(email)) {
      // 找回密码：账号不存在也回成功。否则这个接口就成了"邮箱是否注册"的探测器
      return res.json({ ok: true });
    }

    const now = Date.now();
    const last = codes.lastSentAt.get(email, purpose);
    if (last && now - last.created_at < config.codeCooldownMs) {
      const wait = Math.ceil((config.codeCooldownMs - (now - last.created_at)) / 1000);
      throw new HttpError(429, `请 ${wait} 秒后再获取验证码`);
    }

    const code = issueCode(email, purpose);
    await mail.sendCode(email, code, purpose);

    return res.json({
      ok: true,
      cooldownSeconds: Math.round(config.codeCooldownMs / 1000),
      ttlMinutes: Math.round(config.codeTtlMs / 60000),
    });
  })
);

/** POST /api/auth/register —— 邮箱 + 密码（+ 验证码）注册 */
router.post(
  '/register',
  wrap(async (req, res) => {
    if (!config.registrationEnabled) throw new HttpError(403, '本服务已关闭注册');

    const email = emailOrThrow(req.body?.email);
    const password = passwordOrThrow(req.body?.password);

    if (users.byEmail.get(email)) throw new HttpError(409, '该邮箱已注册，请直接登录');

    const verified = config.requireEmailVerification;
    if (verified) {
      const code = codeOrThrow(req.body?.code);
      consumeCode(email, 'register', code);
    }

    const username = usernameOrThrow(req.body?.username, defaultUsernameFor(email));
    const passwordHash = await bcrypt.hash(password, BCRYPT_ROUNDS);
    const info = users.insert.run({
      email,
      passwordHash,
      username,
      emailVerified: verified ? 1 : 0,
      now: Date.now(),
    });

    const user = users.byId.get(info.lastInsertRowid);
    return res.status(201).json(authPayload(user));
  })
);

/** POST /api/auth/login */
router.post(
  '/login',
  wrap(async (req, res) => {
    const email = emailOrThrow(req.body?.email);
    const password = String(req.body?.password ?? '');

    const user = users.byEmail.get(email);
    // 账号不存在也走一次 compare，让两条分支耗时接近（见 DUMMY_HASH 的说明）
    const ok = await bcrypt.compare(password, user ? user.password_hash : DUMMY_HASH);
    if (!user || !ok) throw new HttpError(401, '邮箱或密码不正确');

    return res.json(authPayload(user));
  })
);

/** POST /api/auth/reset-password —— 验证码重置密码 */
router.post(
  '/reset-password',
  wrap(async (req, res) => {
    const email = emailOrThrow(req.body?.email);
    const code = codeOrThrow(req.body?.code);
    const password = passwordOrThrow(req.body?.password);

    const user = users.byEmail.get(email);
    // 账号不存在时同样走验证码校验（必然失败），文案与"码不对"一致，不泄漏账号是否存在
    consumeCode(email, 'reset', code);
    if (!user) throw new HttpError(400, '验证码不正确');

    const passwordHash = await bcrypt.hash(password, BCRYPT_ROUNDS);
    users.updatePassword.run(passwordHash, Date.now(), user.id);
    users.markVerified.run(Date.now(), user.id);

    // 顺带登录：重置完还要再手动登一次很烦。注意旧令牌不会被吊销（JWT 无状态），
    // 需要"所有设备下线"的场景得另加 token 版本号，当前不作为必要功能。
    return res.json(authPayload(user));
  })
);

module.exports = router;
