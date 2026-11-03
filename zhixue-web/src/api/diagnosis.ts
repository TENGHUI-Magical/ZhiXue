// 诊断域 API 层（契约 §6.3：页面禁止直写 URL，一律经本文件调用）。
import { api } from "./request";
import { DEFAULT_COURSE_ID } from "../constants/course";

/** 契约 §4：OCR 置信度低于该阈值必须人工核对，禁止直接确认 */
export const LOW_CONFIDENCE_THRESHOLD = 0.75;

/** 兼容旧引用：课程 ID 单一事实源在 constants/course.ts */
export { DEFAULT_COURSE_ID };

export interface MapCandidate {
  kpId: string;
  name: string;
  score: number;
  channel: string;
}

export interface MapResult {
  decision: "AUTO" | "CONFIRM" | "FAILED";
  top1?: MapCandidate | null;
  candidates: MapCandidate[];
  degraded: boolean;
}

export interface SourceRef {
  kpId: string;
  textbookPage?: string | null;
  title?: string | null;
}

export interface Recommendation {
  recommendationId?: number | null;
  qId: string;
  layer: "CONSOLIDATE" | "VARIANT" | "ADVANCE";
  reason: string;
  difficulty: number;
  isAdopted: boolean;
  questionText: string;
}

export interface OcrTask {
  taskId: string;
  status: "PENDING" | "RUNNING" | "SUCCESS" | "FAILED";
  ocrText?: string | null;
  confidence?: number | null;
  fields?: { stem?: string; rawText?: string } | null;
  errorCode?: number | null;
  errorMessage?: string | null;
}

export interface ErrorRecord {
  logId: string;
  status: string;
}

export interface ExplainLog {
  explainId: string;
  traceId?: string;
  model?: string;
  degraded?: boolean;
  degradeLevel?: "L2" | "L3" | "L4" | null;
  content: string;
  sources?: SourceRef[];
  blocked?: boolean;
  createdAt?: string;
  reviewResult?: "PASS" | "RETURN" | null;
  reviewComment?: string | null;
  reviewedAt?: string | null;
}

export interface ExplainPayload {
  logId: string;
  kpId: string;
  errorType: string;
  questionText: string;
  forceRefresh: boolean;
}

/**
 * 生成 clientToken。
 * crypto.randomUUID 只在安全上下文（HTTPS/localhost）可用；局域网 HTTP 演示时
 * 回退到 crypto.getRandomValues（非安全上下文也可用）或时间戳+随机数。
 */
export const newClientToken = (): string => {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  if (typeof crypto !== "undefined" && typeof crypto.getRandomValues === "function") {
    const bytes = crypto.getRandomValues(new Uint8Array(16));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
  }
  return `ct-${Date.now()}-${Math.random().toString(16).slice(2)}`;
};

/** 上传错题图片（契约 §4：multipart 固定 file + clientToken，clientToken 每次新生成） */
export const uploadOcrImage = (file: File) => {
  const form = new FormData();
  form.append("file", file);
  form.append("clientToken", newClientToken());
  return api.upload<{ taskId: string }>("/api/error/upload", form);
};

/** 轮询 OCR 任务（契约 §3.1：GET /api/error/tasks/{taskId}） */
export const getOcrTask = (taskId: string) =>
  api.get<OcrTask>(`/api/error/tasks/${taskId}`);

/** 知识点三通道映射 */
export const mapKnowledgePoint = (text: string, courseId = DEFAULT_COURSE_ID) =>
  api.post<MapResult>("/api/diagnosis/map", { text, courseId });

/** 创建 DRAFT 错题记录；qId 在有题库来源时传入，错题本才能直接练习 */
export const createErrorRecord = (payload: {
  courseId: number;
  clientToken: string;
  qId?: string | null;
  kpId: string;
  errorType: string;
  correctedText: string;
}) => api.post<ErrorRecord>("/api/error/records", payload);

/** 确认错题（DRAFT → CONFIRMED） */
export const confirmErrorRecord = (
  logId: string,
  payload: { kpId: string; errorType: string; correctedText: string }
) => api.put<ErrorRecord>(`/api/error/records/${logId}/confirm`, payload);

/** 三层推荐（契约参数 logId 优先；无记录时才退回 kpId） */
export const getRecommendations = (params: {
  logId?: string;
  kpId?: string;
  courseId: number;
}) => api.get<Recommendation[]>("/api/recommendations", params);

export interface PracticeStartResult {
  clientToken: string;
  startedAt: string;
}

export const startPractice = (payload: { qId: string; clientToken: string }) =>
  api.post<PracticeStartResult>("/api/practices/start", payload);

export interface PracticeSubmitPayload {
  qId: string;
  kpId: string;
  /** 客观题可不传（服务端判分并忽略）；主观题必传自评 */
  isCorrect?: boolean | null;
  myAnswer?: string | null;
  errorType?: string | null;
  costSeconds?: number | null;
  /** 服务端 begin 打点令牌；携带后由服务端计时，客户端 costSeconds 被忽略 */
  startToken?: string | null;
  source?: "RECOMMEND" | "SELF";
  recommendationId?: number | null;
  clientToken: string;
}

export interface PracticeSubmitResult {
  practiceId: number;
  duplicate: boolean;
  /** AUTO=客观题服务端判分；SELF=主观题自评；幂等回放时可能为空 */
  gradingMode?: "AUTO" | "SELF" | null;
  /** 服务端最终对错；幂等回放时来自首次记录 */
  isCorrect?: boolean | null;
  /** 服务端 begin → submit 计时；未打点或超过 30 分钟为 null */
  costSeconds?: number | null;
}

/** 提交作答（契约 POST /api/practices）：客观题服务端判分，主观题自评 */
export const submitPractice = (payload: PracticeSubmitPayload) =>
  api.post<PracticeSubmitResult>("/api/practices", payload);

/** 解析单个 SSE 帧：Java 发出的是 `event:meta`（冒号后无空格），必须 trim */
export const parseSseFrame = (frame: string, onFrame: (event: string, payload: Record<string, unknown>) => void) => {
  if (!frame.trim()) return;
  let event = "message";
  const dataLines: string[] = [];
  for (const raw of frame.split("\n")) {
    const line = raw.trimEnd();
    if (line.startsWith("event:")) {
      event = line.slice(6).trim();
    } else if (line.startsWith("data:")) {
      dataLines.push(line.slice(5).trimStart());
    }
  }
  if (!dataLines.length) return;
  let payload: Record<string, unknown>;
  try {
    payload = JSON.parse(dataLines.join("\n")) as Record<string, unknown>;
  } catch {
    // 坏帧只丢弃当前帧，不能中断整条流
    return;
  }
  onFrame(event, payload);
};

export const getExplain = (explainId: string) =>
  api.get<ExplainLog>(`/api/diagnosis/explain/${encodeURIComponent(explainId)}`);

/**
 * 流式讲解（契约 §5：POST /api/diagnosis/explain，SSE）。
 * EventSource 不支持 POST + 自定义 header，必须用 fetch + ReadableStream（避坑 S1）。
 */
export const streamExplain = async (
  payload: ExplainPayload,
  onFrame: (event: string, data: Record<string, unknown>) => void,
  signal: AbortSignal,
  onUnauthorized: (body: unknown) => boolean
): Promise<void> => {
  const token = localStorage.getItem("satoken");
  const response = await fetch("/api/diagnosis/explain", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { satoken: token } : {})
    },
    body: JSON.stringify(payload),
    signal
  });

  if (!response.ok || !response.body) {
    const body = await response.json().catch(() => null);
    if (onUnauthorized(body)) return;
    throw new Error((body as { message?: string } | null)?.message ?? `讲解请求失败（HTTP ${response.status}）`);
  }
  // 参数校验失败时后端返回 200 + application/json，不能按 SSE 流解析
  if (!(response.headers.get("content-type") ?? "").includes("text/event-stream")) {
    const body = await response.json().catch(() => null);
    if (onUnauthorized(body)) return;
    throw new Error((body as { message?: string } | null)?.message ?? "讲解服务返回了非流式响应");
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder("utf-8");
  let buffer = "";
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const frames = buffer.split("\n\n");
    buffer = frames.pop() ?? "";
    frames.forEach((frame) => parseSseFrame(frame, onFrame));
  }
  buffer += decoder.decode();
  if (buffer.trim()) parseSseFrame(buffer, onFrame);
};
