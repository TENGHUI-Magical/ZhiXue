// 学情画像（契约 GET /api/portraits/me）：无快照时后端返回 data:null。
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

export const getPortrait = () => api.get<Portrait | null>("/api/portraits/me");

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
