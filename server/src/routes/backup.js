'use strict';

const express = require('express');
const multer = require('multer');
const crypto = require('node:crypto');
const fs = require('node:fs');
const fsp = require('node:fs/promises');
const path = require('node:path');

const config = require('../config');
const { backups } = require('../db');
const { HttpError, wrap } = require('../http');

const router = express.Router();

const ZIP_MAGIC = Buffer.from([0x50, 0x4b, 0x03, 0x04]); // 'PK\x03\x04'

/**
 * 上传先落到临时名，校验通过再改名 —— 否则一个坏包会当场覆盖掉上一份好备份，
 * 而这个动作往往是"我以为是新备份，其实传错了"，用户唯一的退路就是没被覆盖的旧文件。
 */
const upload = multer({
  storage: multer.diskStorage({
    destination: (req, file, cb) => cb(null, config.backupDir),
    filename: (req, file, cb) => cb(null, `${req.user.id}.${crypto.randomUUID()}.uploading`),
  }),
  limits: { fileSize: config.maxBackupBytes, files: 1 },
});

/** 只留安全字符；非 .dtk 结尾就补上。文件名只用于下载时显示，不参与路径拼接。 */
function safeFileName(raw, fallback) {
  const base = path
    .basename(String(raw || ''))
    .replace(/[^\w.\-]+/g, '_')
    .slice(0, 64);
  if (!base || base.startsWith('.')) return fallback;
  return base.endsWith('.dtk') ? base : `${base}.dtk`;
}

/** 每个用户固定一份：路径由 userId 推导，永远不会被上传者控制。 */
function backupPath(userId) {
  return path.join(config.backupDir, `${userId}.dtk`);
}

function toPublicBackup(row) {
  if (!row) return null;
  return { fileName: row.file_name, size: row.size, updatedAt: row.updated_at };
}

/** GET /api/backup —— 只给元信息，不下字节 */
router.get('/', (req, res) => {
  res.json({ backup: toPublicBackup(backups.byUser.get(req.user.id)) });
});

/** PUT /api/backup —— multipart，字段名 backup；上传即替换 */
router.put(
  '/',
  upload.single('backup'),
  wrap(async (req, res) => {
    const temp = req.file?.path;
    if (!temp) throw new HttpError(400, '缺少备份文件（字段名应为 backup）');

    try {
      // 只验 zip 头。.dtk 的完整校验（路径穿越、zip bomb）在 App 侧导入时做 ——
      // 服务端只负责"把这坨字节存好"，不该替所有客户端实现一遍解包逻辑。
      const head = Buffer.alloc(4);
      const handle = await fsp.open(temp, 'r');
      try {
        await handle.read(head, 0, 4, 0);
      } finally {
        await handle.close();
      }
      if (!head.equals(ZIP_MAGIC)) {
        throw new HttpError(415, '备份文件不是有效的 .dtk（zip）包');
      }
      await fsp.mkdir(config.backupDir, { recursive: true });
      await fsp.rename(temp, backupPath(req.user.id));
    } catch (error) {
      await fsp.unlink(temp).catch(() => {});
      throw error;
    }

    const size = await fsp.stat(backupPath(req.user.id)).then((stat) => stat.size);
    backups.upsert.run({
      userId: req.user.id,
      fileName: safeFileName(req.file.originalname, 'zhilu-backup.dtk'),
      size,
      now: Date.now(),
    });
    res.json({ backup: toPublicBackup(backups.byUser.get(req.user.id)) });
  })
);

/** GET /api/backup/download —— 下载原始字节 */
router.get(
  '/download',
  wrap(async (req, res) => {
    const row = backups.byUser.get(req.user.id);
    if (!row) throw new HttpError(404, '云端还没有备份');

    const file = backupPath(req.user.id);
    if (!fs.existsSync(file)) {
      // 记录与文件分叉（比如运维手滑删了 data 目录）时清掉脏记录：
      // 让客户端看到"没有备份"，而不是每次点恢复都 500
      backups.remove.run(req.user.id);
      throw new HttpError(404, '云端备份文件已丢失，请重新上传');
    }

    const size = fs.statSync(file).size;
    res.setHeader('Content-Type', 'application/zip');
    res.setHeader('Content-Disposition', `attachment; filename="${row.file_name}"`);
    res.setHeader('Content-Length', String(size));
    fs.createReadStream(file).pipe(res);
  })
);

/** DELETE /api/backup */
router.delete(
  '/',
  wrap(async (req, res) => {
    backups.remove.run(req.user.id);
    await fsp.unlink(backupPath(req.user.id)).catch(() => {});
    res.json({ ok: true });
  })
);

module.exports = router;
