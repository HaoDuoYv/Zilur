'use strict';

const path = require('node:path');
const Database = require('better-sqlite3');
const config = require('./config');

/**
 * SQLite 单文件库。
 *
 * 选它是为了"部署就一个容器 + 一个数据卷"：没有独立数据库进程，
 * 2 核 2G 的小机器上常驻内存可以忽略，备份服务器就是拷 ./data 目录。
 * 换 Postgres 时只需要替换本文件里的 SQL 与连接，路由层不用动。
 */
const db = new Database(path.join(config.dataDir, 'app.db'));

// WAL：读写不互相阻塞（App 上传备份时还能登录）
db.pragma('journal_mode = WAL');
// 崩溃后不至于因为半条写入起不来
db.pragma('synchronous = NORMAL');
db.pragma('foreign_keys = ON');

db.exec(`
  CREATE TABLE IF NOT EXISTS users (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    email          TEXT    NOT NULL UNIQUE,
    password_hash  TEXT    NOT NULL,
    username       TEXT    NOT NULL,
    avatar_file    TEXT,
    email_verified INTEGER NOT NULL DEFAULT 0,
    created_at     INTEGER NOT NULL,
    updated_at     INTEGER NOT NULL
  );

  -- 验证码：只存哈希。明文只在邮件里出现一次。
  CREATE TABLE IF NOT EXISTS email_codes (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    email      TEXT    NOT NULL,
    purpose    TEXT    NOT NULL,
    code_hash  TEXT    NOT NULL,
    expires_at INTEGER NOT NULL,
    attempts   INTEGER NOT NULL DEFAULT 0,
    used       INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL
  );
  CREATE INDEX IF NOT EXISTS idx_codes_email_purpose
    ON email_codes (email, purpose, created_at DESC);

  -- 云端备份：一个用户最多一份（手动上传即替换）
  CREATE TABLE IF NOT EXISTS backups (
    user_id    INTEGER PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    file_name  TEXT    NOT NULL,
    size       INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
  );
`);

const users = {
  byEmail: db.prepare('SELECT * FROM users WHERE email = ?'),
  byId: db.prepare('SELECT * FROM users WHERE id = ?'),
  insert: db.prepare(`
    INSERT INTO users (email, password_hash, username, email_verified, created_at, updated_at)
    VALUES (@email, @passwordHash, @username, @emailVerified, @now, @now)
  `),
  updateUsername: db.prepare('UPDATE users SET username = ?, updated_at = ? WHERE id = ?'),
  updateAvatar: db.prepare('UPDATE users SET avatar_file = ?, updated_at = ? WHERE id = ?'),
  updatePassword: db.prepare('UPDATE users SET password_hash = ?, updated_at = ? WHERE id = ?'),
  markVerified: db.prepare('UPDATE users SET email_verified = 1, updated_at = ? WHERE id = ?'),
};

const codes = {
  insert: db.prepare(`
    INSERT INTO email_codes (email, purpose, code_hash, expires_at, created_at)
    VALUES (?, ?, ?, ?, ?)
  `),
  latestFor: db.prepare(`
    SELECT * FROM email_codes
    WHERE email = ? AND purpose = ? AND used = 0
    ORDER BY created_at DESC LIMIT 1
  `),
  // 冷却判定：不看 used，只看最近一次发送时间
  lastSentAt: db.prepare(`
    SELECT created_at FROM email_codes
    WHERE email = ? AND purpose = ?
    ORDER BY created_at DESC LIMIT 1
  `),
  bumpAttempts: db.prepare('UPDATE email_codes SET attempts = attempts + 1 WHERE id = ?'),
  markUsed: db.prepare('UPDATE email_codes SET used = 1 WHERE id = ?'),
  // 新的验证码发出后，旧的一律作废：否则上一封邮件里的码还能用
  invalidateOthers: db.prepare('UPDATE email_codes SET used = 1 WHERE email = ? AND purpose = ? AND used = 0'),
};

const backups = {
  byUser: db.prepare('SELECT * FROM backups WHERE user_id = ?'),
  upsert: db.prepare(`
    INSERT INTO backups (user_id, file_name, size, updated_at)
    VALUES (@userId, @fileName, @size, @now)
    ON CONFLICT(user_id) DO UPDATE SET
      file_name = excluded.file_name,
      size = excluded.size,
      updated_at = excluded.updated_at
  `),
  remove: db.prepare('DELETE FROM backups WHERE user_id = ?'),
};

module.exports = { db, users, codes, backups };
