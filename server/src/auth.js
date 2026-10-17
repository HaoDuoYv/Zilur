'use strict';

const jwt = require('jsonwebtoken');
const config = require('./config');
const { users } = require('./db');
const { HttpError } = require('./http');

const ALGORITHM = 'HS256';

function signToken(user) {
  return jwt.sign({ sub: String(user.id), email: user.email }, config.jwtSecret, {
    algorithm: ALGORITHM,
    expiresIn: `${config.jwtTtlDays}d`,
  });
}

function bearerToken(req) {
  const header = req.get('authorization') || '';
  return header.startsWith('Bearer ') ? header.slice(7).trim() : '';
}

/**
 * 校验令牌并**顺手查一次用户**。
 *
 * 只验签是不够的：账号被删掉之后，旧令牌在过期前仍然签名有效。
 * 一次主键查询换掉"幽灵账号还能读数据"，很划算。
 */
function requireAuth(req, res, next) {
  const token = bearerToken(req);
  if (!token) return next(new HttpError(401, '请先登录'));

  let payload;
  try {
    payload = jwt.verify(token, config.jwtSecret, { algorithms: [ALGORITHM] });
  } catch {
    return next(new HttpError(401, '登录已过期，请重新登录'));
  }

  const user = users.byId.get(Number(payload.sub));
  if (!user) return next(new HttpError(401, '账号不存在，请重新登录'));

  req.user = user;
  return next();
}

module.exports = { signToken, requireAuth };
