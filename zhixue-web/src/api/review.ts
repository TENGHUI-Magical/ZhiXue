// 教师评阅（契约 teacher 组）：页面只经本文件调用。
import { api, downloadCsv } from "./request";
import { shanghaiDateStamp } from "./filenameDate";

export interface ReviewSource {
  kpId?: string;
  textbookPage?: string | null;
  title?: string | null;
}

export interface ReviewOption {
  key?: string;
  text?: string;
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
  // C3 评阅证据
  questionText?: string | null;
  questionType?: string | null;
  options?: ReviewOption[] | null;
  correctAnswer?: string | null;
  analysisText?: string | null;
  studentNote?: string | null;
  waitingSeconds?: number;
  reviewResult?: "PASS" | "RETURN" | null;
  reviewStatus?: "PENDING" | "REVIEWED";
}

export interface ReviewPage {
  page: number;
  size: number;
  total: number;
  records: ReviewItem[];
}

export interface ReviewQuery {
  page: number;
  size: number;
  status?: "PENDING" | "REVIEWED" | "ALL";
  kpId?: string;
  from?: string;
  to?: string;
}

export const listReviews = (params: ReviewQuery) =>
  api.get<ReviewPage>("/api/teacher/reviews", params);

export const submitReview = (
  logPk: number,
  payload: { score: number; comment: string; result?: "PASS" | "RETURN" }
) => api.post<null>(`/api/teacher/reviews/${logPk}`, payload);

/** 评阅留痕 CSV 导出（V2.2.8）：与队列同筛选，导出完整结果 */
export const exportReviewLog = (params: {
  status?: "PENDING" | "REVIEWED" | "ALL";
  kpId?: string;
  from?: string;
  to?: string;
}) => {
  const date = shanghaiDateStamp();
  return downloadCsv("/api/teacher/reviews/export", params, `review-log-${date}.csv`);
};
