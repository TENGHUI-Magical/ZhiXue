// 课程 / 章节 / 知识图谱 / 知识点详情（契约 course 组）。
import { api } from "./request";

export interface DimensionDef {
  key: string;
  name: string;
}

export interface Course {
  courseId?: number;
  courseName?: string;
  textbook?: string | null;
  description?: string | null;
  dimensionConfig?: DimensionDef[];
}

export interface GraphNode {
  id?: string;
  name?: string;
  chapterId?: number;
}

export interface GraphEdge {
  source?: string;
  target?: string;
  relation?: string;
}

export interface KnowledgeGraph {
  nodes?: GraphNode[];
  edges?: GraphEdge[];
}

export interface KnowledgePointDetail {
  kpId?: string;
  courseId?: number;
  chapterId?: number;
  chapterName?: string | null;
  name?: string;
  conceptText?: string | null;
  misconception?: string | null;
  textbookPage?: string | null;
  trialHint?: string | null;
  sortNo?: number;
  dimensionKey?: string | null;
}

export const listCourses = () => api.get<Course[]>("/api/courses");

export const getKnowledgeGraph = (courseId: number) =>
  api.get<KnowledgeGraph>(`/api/courses/${courseId}/knowledge-graph`);

export const getKnowledgePoint = (kpId: string) =>
  api.get<KnowledgePointDetail>(`/api/course/kp/${encodeURIComponent(kpId)}`);
