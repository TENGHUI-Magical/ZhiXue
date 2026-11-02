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

/** 到期复习项（契约 DueReviewItem；判定与排序见 docs/05 §3.8） */
export interface DueReviewItem {
  /** 来源标识：今日任务三来源之一，到期复习排最前 */
  source: "DUE_REVIEW";
  kpId: string;
  kpName?: string | null;
  /** 建议重做的题目（优先最近答错的题） */
  qId: string;
  questionText?: string | null;
  difficulty?: number | null;
  daysSinceLastCorrect?: number;
  /** 掌握度（与画像同口径）；窗口外无证据按先验 0.5 */
  mastery?: number | null;
  sampleCount?: number;
  /** 可解释理由，如“距上次答对 40 天，已过半衰期 + 概念混淆型错误” */
  reason?: string;
}

export const getDueReviews = (courseId: number) =>
  api.get<DueReviewItem[]>("/api/plans/due-reviews", { courseId });

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
