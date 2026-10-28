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
