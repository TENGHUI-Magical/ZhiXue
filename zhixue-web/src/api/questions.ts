// 题目详情 / 参考答案（契约 V2.1.0）：先做题，再显式查看答案。
import { api } from "./request";

export interface QuestionOption {
  key: string;
  text: string;
}

export interface QuestionDetail {
  qId?: string;
  courseId?: number;
  kpId?: string;
  kpName?: string | null;
  questionType?: "单选" | "多选" | "填空" | "简答" | "编程";
  questionText?: string;
  options?: QuestionOption[];
  difficulty?: number;
}

export interface QuestionAnswer {
  qId?: string;
  answerText?: string | null;
  analysisText?: string | null;
}

export const getQuestionDetail = (qId: string) =>
  api.get<QuestionDetail>(`/api/questions/${encodeURIComponent(qId)}`);

export const getQuestionAnswer = (qId: string) =>
  api.get<QuestionAnswer>(`/api/questions/${encodeURIComponent(qId)}/answer`);
