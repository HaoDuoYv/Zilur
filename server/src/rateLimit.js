'use strict';

/**
 * 极简限流：内存里的滑动窗口，按 key 计数。
 *
 * 不引 express-rate-limit 是因为这里只需要"挡住脚本刷验证码/撞密码"这一件事，
 * 二十行足够；也不用 Redis —— 单容器部署，进程内的计数就是全部真相。
 * 代价是重启后计数清零（可接受）。
 */
function createLimiter({ windowMs, max, keyOf, message }) {
  const hits = new Map();

  // 定期清掉过期条目，避免 key 无限增长（unref 让它不阻止进程退出）
  const sweeper = setInterval(() => {
    const now = Date.now();
    for (const [key, stamps] of hits) {
      const alive = stamps.filter((at) => now - at < windowMs);
      if (alive.length === 0) hits.delete(key);
      else hits.set(key, alive);
    }
  }, Math.min(windowMs, 60_000));
  sweeper.unref?.();

  return function limiter(req, res, next) {
    const key = keyOf ? keyOf(req) : req.ip;
    if (!key) return next();

    const now = Date.now();
    const stamps = (hits.get(key) || []).filter((at) => now - at < windowMs);
    if (stamps.length >= max) {
      const retryAfter = Math.ceil((windowMs - (now - stamps[0])) / 1000);
      res.set('Retry-After', String(Math.max(retryAfter, 1)));
      return res.status(429).json({
        error: { message: message || '操作过于频繁，请稍后再试', code: 'rate_limited' },
      });
    }
    stamps.push(now);
    hits.set(key, stamps);
    next();
  };
}

module.exports = { createLimiter };
