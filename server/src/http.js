'use strict';

/**
 * 统一的业务异常：路由里直接 `throw new HttpError(400, '...')`，
 * 由 index.js 的错误处理中间件转成 `{ error: { message } }`。
 */
class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

/**
 * Express 4 不会捕获 async 函数里抛出的 rejection —— 不包一层的话，
 * 一个 `await` 失败会变成"请求挂住直到超时"，而不是 4xx/5xx。
 */
function wrap(handler) {
  return (req, res, next) => Promise.resolve(handler(req, res, next)).catch(next);
}

module.exports = { HttpError, wrap };
