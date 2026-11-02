// 错题本列表（契约 GET /api/error/records）：只走 API 层，页面不直写 URL。
import { api } from "./request";

export interface ErrorRecordItem {
  logId: string;
  userId?: number;
  qId?: string | null;
  kpId?: string | null;
  kpName?: string | null;
  imageUrl?: string | null;
  ocrText?: string | null;
  correctedText?: string | null;
  errorType?: string | null;
  studentNote?: string | null;
  status?: "DRAFT" | "CONFIRMED" | string;
  createdAt?: string;
  reviewResult?: "PASS" | "RETURN" | null;
  reviewComment?: string | null;
  reviewedAt?: string | null;
}

export interface ErrorRecordPage {
  page: number;
  size: number;
  total: number;
  records: ErrorRecordItem[];
}

export interface ErrorRecordQuery {
  page: number;
  size: number;
  kpId?: string;
  status?: string;
}

export const listErrorRecords = (params: ErrorRecordQuery) =>
  api.get<ErrorRecordPage>("/api/error/records", params);

export const getErrorRecord = (logId: string) =>
  api.get<ErrorRecordItem>(`/api/error/records/${encodeURIComponent(logId)}`);
