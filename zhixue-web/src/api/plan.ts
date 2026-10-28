// 学习计划（契约 /api/plans）：列表、生成、打卡。
import { api } from "./request";

export interface PlanItem {
  itemId?: number;
  qId?: string;
  kpId?: string;
  sortNo?: number;
  isDone?: boolean;
}

export interface StudyPlan {
  planId?: number;
  planDate?: string;
  planCount?: number;
  doneCount?: number;
  status?: "PENDING" | "DONE" | "OVERDUE";
  items?: PlanItem[];
}

export const listPlans = (from: string, to: string) =>
  api.get<StudyPlan[]>("/api/plans", { from, to });

export const generatePlans = (payload: {
  courseId: number;
  weekStart: string;
  dailyQuestionCount: number;
}) => api.post<StudyPlan[]>("/api/plans/generate", payload);

export const checkinPlan = (
  planId: number,
  payload: { qId: string; clientToken: string; note?: string }
) => api.post<null>(`/api/plans/${planId}/checkin`, payload);
