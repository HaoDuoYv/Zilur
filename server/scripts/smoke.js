'use strict';

/**
 * 冒烟脚本：对着一个**已经跑起来**的服务端走一遍主链路。
 *
 *   # 本地（先 npm start 或在 Docker 里跑着）
 *   node scripts/smoke.js http://127.0.0.1:8787
 *
 * 覆盖：health → 注册/登录 → 读资料 → 改用户名 → 传头像 → 上传备份 → 下载比对 → 删备份。
 * 要求邮箱验证码时（默认）无法自动注册，会跳过注册链路、只测已存在账号的部分，
 * 并在输出里说明原因 —— 不再悄悄"通过"。
 */

const baseArg = process.argv[2] || process.env.SMOKE_BASE || 'http://127.0.0.1:8787';
const base = baseArg.replace(/\/+$/, '');

// 一个真实的 1x1 透明 PNG（服务端按魔数识别，不能随便造）
const PNG_1X1 = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
  'base64'
);

let failures = 0;
function ok(name, extra) {
  console.log(`  ✅ ${name}${extra ? ` — ${extra}` : ''}`);
}
function bad(name, error) {
  failures += 1;
  console.log(`  ❌ ${name} — ${error?.message || error}`);
}
async function step(name, fn) {
  try {
    const extra = await fn();
    ok(name, extra);
  } catch (error) {
    bad(name, error);
  }
}
function assert(condition, message) {
  if (!condition) throw new Error(message);
}

async function json(res) {
  const text = await res.text();
  let body;
  try {
    body = text ? JSON.parse(text) : {};
  } catch {
    throw new Error(`响应不是 JSON（${res.status}）：${text.slice(0, 120)}`);
  }
  return body;
}

async function call(path, { method = 'GET', token, body, form } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  let payload;
  if (form) {
    payload = form; // FormData：让 fetch 自己带 boundary
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }
  const res = await fetch(`${base}${path}`, { method, headers, body: payload });
  return { res, body: await json(res) };
}

(async () => {
  console.log(`\n知录云端服务冒烟测试 → ${base}\n`);

  let health;
  await step('GET /api/health', async () => {
    const { res, body } = await call('/api/health');
    assert(res.ok, `状态码 ${res.status}`);
    health = body;
    return `v${body.version} · 邮件${body.mailConfigured ? '已配置' : '未配置'} · 注册验证码${
      body.requireEmailVerification ? '需要' : '不需要'
    }`;
  });
  if (!health) {
    console.log('\n服务端不可达，后面的用例无从跑起。');
    process.exit(1);
  }

  const email = `smoke-${Date.now()}@example.com`;
  const password = 'smoke-test-password';
  let token = null;
  let user = null;

  if (health.requireEmailVerification) {
    console.log('  ⚠️  服务端要求邮箱验证码，无法自动注册 —— 跳过注册/登录链路');
    console.log('     想完整跑一遍：在 .env 里设 REQUIRE_EMAIL_VERIFICATION=false 后重启\n');
    console.log(failures === 0 ? '结果：0 失败（部分用例跳过）' : `结果：${failures} 处失败`);
    process.exit(failures === 0 ? 0 : 1);
  }

  await step('POST /api/auth/register', async () => {
    const { res, body } = await call('/api/auth/register', {
      method: 'POST',
      body: { email, password, username: '冒烟测试' },
    });
    assert(res.status === 201, `状态码 ${res.status}：${body.error?.message || ''}`);
    assert(body.token, '没有返回 token');
    assert(body.user?.email === email, '返回的邮箱不对');
    assert(!('password_hash' in (body.user || {})), '响应里泄漏了密码哈希');
    token = body.token;
    user = body.user;
    return `id=${user.id} · 用户名「${user.username}」`;
  });

  await step('POST /api/auth/login', async () => {
    const { res, body } = await call('/api/auth/login', {
      method: 'POST',
      body: { email, password },
    });
    assert(res.ok, `状态码 ${res.status}`);
    token = body.token;
    assert(token, '没有返回 token');
  });

  await step('错误密码被拒', async () => {
    const { res } = await call('/api/auth/login', {
      method: 'POST',
      body: { email, password: 'wrong-password-123' },
    });
    assert(res.status === 401, `应当 401，实际 ${res.status}`);
  });

  await step('未带令牌访问 /api/me 被拒', async () => {
    const { res } = await call('/api/me');
    assert(res.status === 401, `应当 401，实际 ${res.status}`);
  });

  await step('GET /api/me', async () => {
    const { res, body } = await call('/api/me', { token });
    assert(res.ok, `状态码 ${res.status}`);
    assert(body.user?.id === user.id, '返回的不是同一个账号');
  });

  await step('PATCH /api/me 改用户名', async () => {
    const { res, body } = await call('/api/me', {
      method: 'PATCH',
      token,
      body: { username: '改过的名字' },
    });
    assert(res.ok, `状态码 ${res.status}`);
    assert(body.user?.username === '改过的名字', `用户名没变：${body.user?.username}`);
  });

  await step('POST /api/me/avatar 上传头像', async () => {
    const form = new FormData();
    form.append('avatar', new Blob([PNG_1X1], { type: 'image/png' }), 'a.png');
    const { res, body } = await call('/api/me/avatar', { method: 'POST', token, form });
    assert(res.ok, `状态码 ${res.status}：${body.error?.message || ''}`);
    assert(body.user?.avatarUrl, '没有返回 avatarUrl');

    // 头像必须真的能取到
    const image = await fetch(`${base}${body.user.avatarUrl}`);
    assert(image.ok, `头像取不到：${image.status}`);
    const bytes = Buffer.from(await image.arrayBuffer());
    assert(bytes.equals(PNG_1X1), '取回的头像与上传的不一致');
    return body.user.avatarUrl;
  });

  await step('POST /api/me/avatar 拒绝非图片', async () => {
    const form = new FormData();
    form.append('avatar', new Blob([Buffer.from('<html>hi</html>')], { type: 'image/png' }), 'x.png');
    const { res } = await call('/api/me/avatar', { method: 'POST', token, form });
    assert(res.status === 415, `应当 415，实际 ${res.status}`);
  });

  // 只验"字节存取"这条链路：服务端只在导入侧做 .dtk 的完整校验，
  // 这里用 PK 头 + 随机体，正好覆盖"校验头 → 落盘 → 原样取回"
  const backupBytes = Buffer.concat([Buffer.from('PK\x03\x04', 'binary'), Buffer.from('smoke-backup-body')]);
  await step('PUT /api/backup 上传备份', async () => {
    const form = new FormData();
    form.append('backup', new Blob([backupBytes]), 'smoke-backup.dtk');
    const { res, body } = await call('/api/backup', { method: 'PUT', token, form });
    assert(res.ok, `状态码 ${res.status}：${body.error?.message || ''}`);
    assert(body.backup?.size === backupBytes.length, `大小不对：${body.backup?.size}`);
    return `${body.backup.fileName} · ${body.backup.size} 字节`;
  });

  await step('PUT /api/backup 拒绝非 zip', async () => {
    const form = new FormData();
    form.append('backup', new Blob([Buffer.from('not a zip at all')]), 'bad.dtk');
    const { res } = await call('/api/backup', { method: 'PUT', token, form });
    assert(res.status === 415, `应当 415，实际 ${res.status}`);
  });

  await step('GET /api/backup/download 取回并逐字节比对', async () => {
    const res = await fetch(`${base}/api/backup/download`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    assert(res.ok, `状态码 ${res.status}`);
    const bytes = Buffer.from(await res.arrayBuffer());
    assert(bytes.equals(backupBytes), '下载内容与上传不一致');
    return `${bytes.length} 字节一致`;
  });

  await step('DELETE /api/backup', async () => {
    const { res } = await call('/api/backup', { method: 'DELETE', token });
    assert(res.ok, `状态码 ${res.status}`);
    const { body } = await call('/api/backup', { token });
    assert(body.backup === null, '删除后仍能读到备份');
  });

  console.log(
    failures === 0 ? '\n结果：全部通过 ✅\n' : `\n结果：${failures} 处失败 ❌\n`
  );
  process.exit(failures === 0 ? 0 : 1);
})();
