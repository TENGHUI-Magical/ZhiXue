// 学情画像（契约 GET /api/portraits/me）：无数据时后端返回 data:null。
import type { AxiosRequestConfig } from "axios";
import { api } from "./request";

export interface WeakestKp {
  kpId?: string;
  name?: string | null;
  mastery?: number;
  sampleCount?: number;
}

export interface Portrait {
  statDate?: string;
  dimensionScores?: Record<string, number | null>;
  weakestKps?: WeakestKp[];
  errorDist?: Record<string, number>;
  totalPractice?: number;
  totalError?: number;
}

/**
 * config.silent 判定：仅「视图已有内联错误位」的首屏画像卡可传 { silent: true }
 * （个人中心 learning-card 的 el-alert、今日工作台每卡独立 error）。
 * 知识图谱里 getPortrait 是 .catch(()=>null) 降级、无内联位，refresh 用户主动触发——都不传。
 */
export const getPortrait = (config?: AxiosRequestConfig) =>
  api.get<Portrait | null>("/api/portraits/me", undefined, config);

export interface ClassWeakestKp {
  kpId?: string;
  name?: string | null;
  avgMastery?: number;
  errorCount?: number;
}

export interface ClassPortrait {
  courseId?: number;
  studentCount?: number;
  weakestKpTop?: ClassWeakestKp[];
  errorDist?: Record<string, number>;
  activeRate?: number;
}

export const getClassPortrait = (courseId: number) =>
  api.get<ClassPortrait>("/api/portraits/class/" + courseId);
