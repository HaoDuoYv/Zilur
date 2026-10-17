'use strict';

const express = require('express');
const multer = require('multer');
const crypto = require('node:crypto');
const fsp = require('node:fs/promises');
const path = require('node:path');

const config = require('../config');
const { users } = require('../db');
const { HttpError, wrap } = require('../http');
const { toPublicUser } = require('../serialize');
const { usernameOrThrow } = require('../validate');

const router = express.Router();

/**
 * 头像先收进内存（≤2MB）再落盘。
 *
 * 用内存而不是直接落盘，是为了**先看真实字节**再决定扩展名：只看客户端给的
 * Content-Type 或文件名，等于让上传者决定服务端怎么解释这个文件（挂个 .png 的 HTML
 * 就能在别人浏览头像时执行脚本）。落盘名由服务端生成，与用户输入无关。
 */
const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: config.maxAvatarBytes, files: 1 },
});

const PNG_MAGIC = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

/** 按魔数判定格式，返回扩展名；不认识就返回 null（调用方拒绝）。 */
function sniffImageExt(buffer) {
  if (buffer.length >= 3 && buffer[0] === 0xff && buffer[1] === 0xd8 && buffer[2] === 0xff) {
    return 'jpg';
  }
  if (buffer.length >= 8 && buffer.subarray(0, 8).equals(PNG_MAGIC)) return 'png';
  if (
    buffer.length >= 12 &&
    buffer.subarray(0, 4).toString('ascii') === 'RIFF' &&
    buffer.subarray(8, 12).toString('ascii') === 'WEBP'
  ) {
    return 'webp';
  }
  return null;
}

async function removeAvatarFile(fileName) {
  if (!fileName) return;
  // 库里的名字是本服务生成的，但仍然只按 basename 处理 —— 不给自己留路径拼接的口子
  await fsp.unlink(path.join(config.avatarDir, path.basename(fileName))).catch(() => {});
}

/** GET /api/me */
router.get('/', (req, res) => {
  res.json({ user: toPublicUser(req.user) });
});

/** PATCH /api/me —— 目前只支持改用户名 */
router.patch(
  '/',
  wrap(async (req, res) => {
    const body = req.body ?? {};
    if (body.username !== undefined) {
      const username = usernameOrThrow(body.username);
      users.updateUsername.run(username, Date.now(), req.user.id);
    }
    const user = users.byId.get(req.user.id);
    res.json({ user: toPublicUser(user) });
  })
);

/** POST /api/me/avatar —— multipart，字段名 avatar */
router.post(
  '/avatar',
  upload.single('avatar'),
  wrap(async (req, res) => {
    if (!req.file || req.file.size === 0) throw new HttpError(400, '请选择一张图片');

    const ext = sniffImageExt(req.file.buffer);
    if (!ext) throw new HttpError(415, '只支持 JPG / PNG / WebP 图片');

    // 名字里带随机串：头像 URL 可枚举的话，别人换个 id 就能遍历所有用户头像
    const fileName = `${req.user.id}-${crypto.randomBytes(8).toString('hex')}.${ext}`;
    await fsp.writeFile(path.join(config.avatarDir, fileName), req.file.buffer);

    const previous = req.user.avatar_file;
    users.updateAvatar.run(fileName, Date.now(), req.user.id);
    await removeAvatarFile(previous);

    const user = users.byId.get(req.user.id);
    res.json({ user: toPublicUser(user) });
  })
);

/** DELETE /api/me/avatar */
router.delete(
  '/avatar',
  wrap(async (req, res) => {
    const previous = req.user.avatar_file;
    users.updateAvatar.run(null, Date.now(), req.user.id);
    await removeAvatarFile(previous);
    const user = users.byId.get(req.user.id);
    res.json({ user: toPublicUser(user) });
  })
);

module.exports = router;
