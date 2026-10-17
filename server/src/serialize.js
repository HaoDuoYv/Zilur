'use strict';

/**
 * 对外的用户对象。**永远不包含 `password_hash`** —— 这个函数是唯一的出口，
 * 别在路由里直接 `res.json(user)`。
 *
 * `avatarUrl` 给的是**相对路径**：服务端不知道自己被哪个域名/反代暴露，
 * 由客户端拼上它正在用的服务地址（也顺带支持 IP 直连与域名两种部署）。
 */
function toPublicUser(user) {
  return {
    id: user.id,
    email: user.email,
    username: user.username,
    avatarUrl: user.avatar_file ? `/avatars/${encodeURIComponent(user.avatar_file)}` : null,
    emailVerified: user.email_verified === 1,
    createdAt: user.created_at,
  };
}

module.exports = { toPublicUser };
