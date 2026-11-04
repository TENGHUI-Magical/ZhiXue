// 教师查看学生学习记录（契约 C12）
import { api, downloadCsv } from "./request";
import { shanghaiDateStamp } from "./filenameDate";

export interface TeacherStudentItem {
  userId?: number;
  displayName?: string;
  practiceCount?: number;
  correctRate?: number | null;
  avgCostSeconds?: number | null;
  avgMastery?: number | null;
  errorCount?: number;
  planDone?: number;
  planTotal?: number;
  lastActiveAt?: string | null;
}

export interface TeacherStudentPage {
  page: number;
  size: number;
  total: number;
  records: TeacherStudentItem[];
}

export interface StudentPracticeItem {
  practiceId?: number;
  qId?: string;
  questionText?: string | null;
  kpId?: string;
  kpName?: string | null;
  isCorrect?: boolean | null;
  myAnswer?: string | null;
  correctAnswer?: string | null;
  costSeconds?: number | null;
  errorType?: string | null;
  source?: "PLAN" | "SELF" | "RECOMMEND";
  createdAt?: string;
}

export interface StudentErrorItem {
  logId?: string;
  qId?: string | null;
  kpId?: string;
  kpName?: string | null;
  status?: "DRAFT" | "CONFIRMED";
  errorType?: string | null;
  ocrConfidence?: number | null;
  createdAt?: string;
}

export interface StudentKpMastery {
  kpId?: string;
  name?: string | null;
  mastery?: number;
  sampleCount?: number;
  errorCount?: number;
}

export interface StudentLearningRecord {
  summary: TeacherStudentItem;
  practices: {
    page: number;
    size: number;
    total: number;
    records: StudentPracticeItem[];
  };
  errors: StudentErrorItem[];
  kpMastery: StudentKpMastery[];
}

export const listCourseStudents = (params: {
  courseId: number;
  page: number;
  size: number;
  sort: string;
}) => api.get<TeacherStudentPage>("/api/teacher/students", params);

export const getStudentLearningRecords = (
  userId: number,
  params: { courseId: number; from?: string; to?: string; page: number; size: number }
) => api.get<StudentLearningRecord>(`/api/teacher/students/${userId}/learning-records`, params);

/** 班级学情 CSV 导出（V2.2.8）：完整筛选结果而非当前分页 */
export const exportClassLearning = (courseId: number, sort: string) => {
  const date = shanghaiDateStamp();
  return downloadCsv(
    "/api/teacher/students/export",
    { courseId, sort },
    `class-learning-${courseId}-${date}.csv`
  );
};
