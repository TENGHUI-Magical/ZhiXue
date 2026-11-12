/**
 * 登录凭证的单一读写出口（KG-13 缓解）。
 *
 * 现状约束：后端用 Sa-Token header 鉴权，没有 httpOnly Cookie 通道，
 * token 只能落 localStorage——XSS 一旦发生即可窃取。前端能做的是收窄暴露面：
 * 1) 只在这一处读写 `satoken`，其它文件不再直接摸 localStorage；
 * 2) 记录签发时间并按服务端 `sa-token.timeout`（7 天）本地判定过期，过期即视为未登录；
 * 3) 40100 或主动登出时连同用户缓存一起清，避免"token 没了但界面还是登录态"；
 * 4) 监听 storage 事件，跨标签页登出立即同步（另一个标签页不会继续显示已登录）。
 *
 * 彻底方案仍是后端下发 httpOnly + Secure + SameSite Cookie，需契约变更，本轮不动。
 */

const TOKEN_KEY = "satoken";
const USER_KEY = "zhixue_user";
const ISSUED_AT_KEY = "satoken_issued_at";

/** 与服务端 zhixue-bootstrap/application.yml `sa-token.timeout: 604800`（7 天）对齐 */
export const SESSION_TTL_MS = 7 * 24 * 60 * 60 * 1000;

export interface SessionUser {
  userId: number;
  username: string;
  realName: string;
  role: string;
}

const safeGet = (key: string): string | null => {
  try {
    return localStorage.getItem(key);
  } catch {
    // 隐私模式 / 存储被禁用：降级为未登录，不抛异常打断渲染
    return null;
  }
};

const safeSet = (key: string, value: string): void => {
  try {
    localStorage.setItem(key, value);
  } catch {
    // 配额满或禁用存储：本次会话内仍可用内存态（见 memoryToken）
  }
};

const safeRemove = (key: string): void => {
  try {
    localStorage.removeItem(key);
  } catch {
    // 忽略：清理失败不应阻断登出流程
  }
};

/** localStorage 不可用时的内存兜底，保证当前标签页还能正常请求 */
let memoryToken: string | null = null;
let memoryUser: SessionUser | null = null;

/**
 * 判定 localStorage 中的会话是否过期（修复：NaN/脏值不再无限续期）。
 * - `issued_at` 缺失或非法 → 补记一次当前时间（仅一次，不会反复续期）；
 * - 超过 TTL → 过期。
 */
const isExpiredInStorage = (): boolean => {
  const token = safeGet(TOKEN_KEY);
  if (!token) return true;
  const raw = safeGet(ISSUED_AT_KEY);
  const issuedAt = Number(raw);
  if (!Number.isFinite(issuedAt) || issuedAt <= 0) {
    // 缺失或脏值：补记一次，避免升级后把所有人踢下线
    safeSet(ISSUED_AT_KEY, String(Date.now()));
    return false;
  }
  return Date.now() - issuedAt > SESSION_TTL_MS;
};

export const getToken = (): string | null => {
  // 内存态也要过期判定（长期开着的标签页不能无限续期）
  if (memoryToken) {
    if (isExpiredInStorage()) {
      clearSession();
      return null;
    }
    return memoryToken;
  }
  const token = safeGet(TOKEN_KEY);
  if (!token) return null;
  if (isExpiredInStorage()) {
    clearSession();
    return null;
  }
  return token;
};

export const isSessionExpired = (): boolean => {
  return isExpiredInStorage();
};

export const saveSession = (token: string, user: SessionUser | null): void => {
  memoryToken = token;
  safeSet(TOKEN_KEY, token);
  safeSet(ISSUED_AT_KEY, String(Date.now()));
  if (user) saveUser(user);
};

export const saveUser = (user: SessionUser): void => {
  memoryUser = user;
  safeSet(USER_KEY, JSON.stringify(user));
};

/** 校验 localStorage 中 user 的形状（修复：防止 XSS 篡改 role 骗过前端守卫） */
function isValidUser(value: unknown): value is SessionUser {
  if (!value || typeof value !== "object") return false;
  const u = value as Record<string, unknown>;
  return typeof u.userId === "number"
    && typeof u.username === "string"
    && typeof u.realName === "string"
    && typeof u.role === "string"
    && (u.role === "STUDENT" || u.role === "TEACHER");
}

export const getUser = (): SessionUser | null => {
  if (memoryUser) return memoryUser;
  const raw = safeGet(USER_KEY);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw);
    if (!isValidUser(parsed)) return null;
    return parsed;
  } catch {
    // 缓存损坏：当未登录处理，让上层回查 /api/auth/me
    return null;
  }
};

export const clearSession = (): void => {
  memoryToken = null;
  memoryUser = null;
  safeRemove(TOKEN_KEY);
  safeRemove(USER_KEY);
  safeRemove(ISSUED_AT_KEY);
};

/** 路由守卫与请求层共用：有 token 且未过期才算"已登录" */
export const hasSession = (): boolean => !!getToken();

/**
 * 跨标签页同步：storage 事件只在"其它标签页"触发，正好用来同步登出/登录。
 * 修复：先同步内存态再判定，否则登录页内存 token 遮蔽 localStorage 清除。
 */
export const onSessionChange = (handler: (loggedIn: boolean) => void): (() => void) => {
  const listener = (event: StorageEvent) => {
    if (event.key !== TOKEN_KEY && event.key !== null) return;
    // 先同步内存态：其他标签页清了 localStorage，本页 memoryToken 也要同步清除
    memoryToken = safeGet(TOKEN_KEY);
    if (!memoryToken) memoryUser = null;
    handler(hasSession());
  };
  window.addEventListener("storage", listener);
  return () => window.removeEventListener("storage", listener);
};
