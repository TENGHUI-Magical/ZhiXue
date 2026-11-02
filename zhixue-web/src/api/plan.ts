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

export interface CheckinPayload {
  qId: string;
  clientToken: string;
  /** true=已作答（按真实对错计入）；false=仅记录进度（不计正确率/不入可信闭环） */
  answered: boolean;
  myAnswer?: string | null;
  isCorrect?: boolean | null;
  costSeconds?: number | null;
  errorType?: string | null;
  note?: string;
}

export interface CheckinResult {
  planId?: number;
  qId?: string;
  doneCount?: number;
  planCount?: number;
  verified?: boolean;
  isCorrect?: boolean | null;
  /** AUTO=客观题服务端判分；SELF=主观题自评 */
  gradingMode?: "AUTO" | "SELF" | null;
  practiceId?: number | null;
  duplicate?: boolean;
}

export const checkinPlan = (planId: number, payload: CheckinPayload) =>
  api.post<CheckinResult>(`/api/plans/${planId}/checkin`, payload);
