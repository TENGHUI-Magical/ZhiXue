/**
 * /api/auth/me（openapi UserInfo）单一类型源（F1）。
 * App.vue / router / LoginView / ProfileView / 登录兜底对象均复用同一形状。
 */
export type MeUser = {
  userId: number;
  username: string;
  realName: string;
  role: string;
};
