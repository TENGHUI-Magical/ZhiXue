// 教师评阅（契约 teacher 组）：页面只经本文件调用。
import { api } from "./request";

export interface ReviewSource {
  kpId?: string;
  textbookPage?: string | null;
  title?: string | null;
}

export interface ReviewItem {
  logPk?: number;
  explainId?: string;
  userName?: string;
  kpId?: string;
  errorType?: string | null;
  model?: string | null;
  degraded?: boolean;
  content?: string;
  sources?: ReviewSource[];
  createdAt?: string;
}

export interface ReviewPage {
  page: number;
  size: number;
  total: number;
  records: ReviewItem[];
}

export const listReviews = (params: { page: number; size: number }) =>
  api.get<ReviewPage>("/api/teacher/reviews", params);

export const submitReview = (logPk: number, payload: { score: number; comment: string }) =>
  api.post<null>(`/api/teacher/reviews/${logPk}`, payload);
