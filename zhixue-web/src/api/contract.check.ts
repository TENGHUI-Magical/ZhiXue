/**
 * 契约一致性编译期检查（EN-01）。
 *
 * 背景：`npm run gen:types` 生成的 `types.generated.ts` 如果没人引用，
 * openapi.yaml 这个「唯一事实源」在编译期等于失效——前端手写类型怎么漂移都不会被发现。
 * 这个文件不产生任何运行时代码，只把「前端实际用的端点 / 字段」钉在契约类型上：
 * - 端点写错、契约删了某个 path → `satisfies` 直接编译失败；
 * - 手写 interface 多出契约里没有的字段 → 报错信息里会列出那个字段名。
 *
 * 修改契约后：`npm run gen:types` → `vue-tsc --noEmit`，两步都过才算对齐。
 */
import type { components, paths } from "./types.generated";
import type { PracticeSubmitPayload, PracticeSubmitResult, Recommendation } from "./diagnosis";
import type { Portrait } from "./portrait";
import type { ReviewItem } from "./review";
import type { ErrorRecordItem } from "./errorBook";
import type { QuestionDetail } from "./questions";
import type { QuestionBrief } from "./course";
import type { StudyPlan } from "./plan";

type Schema<K extends keyof components["schemas"]> = components["schemas"][K];
type ApiPath = keyof paths;
type DataOf<K extends keyof components["schemas"]> =
  Schema<K> extends { data?: infer D } ? NonNullable<D> : never;

/** 手写类型不允许出现契约里没有的字段（多一个就报出字段名） */
type NoExtraKeys<Manual, Contract> =
  [Exclude<keyof Manual, keyof Contract>] extends [never]
    ? true
    : Exclude<keyof Manual, keyof Contract>;

/**
 * 前端实际调用的静态端点（参数化端点见下方单独断言）。
 * 这里的每一项都必须能在 openapi.yaml 的 paths 里找到同名 key。
 */
const STATIC_ENDPOINTS = [
  "/api/auth/login",
  "/api/auth/logout",
  "/api/auth/me",
  "/api/courses",
  "/api/questions",
  "/api/error/upload",
  "/api/error/records",
  "/api/diagnosis/map",
  "/api/diagnosis/explain",
  "/api/recommendations",
  "/api/practices",
  "/api/practices/start",
  "/api/portraits/me",
  "/api/plans",
  "/api/plans/due-reviews",
  "/api/plans/generate",
  "/api/teacher/reviews",
  "/api/teacher/reviews/export",
  "/api/teacher/students",
  "/api/teacher/students/export",
  "/api/system/health"
] satisfies ApiPath[];

/** 参数化端点单独钉住，避免把 `{id}` 写成 `:id` 也能通过 */
const TEMPLATE_ENDPOINTS = [
  "/api/courses/{courseId}/chapters",
  "/api/courses/{courseId}/knowledge-graph",
  "/api/course/kp/{kpId}",
  "/api/questions/{qId}",
  "/api/questions/{qId}/answer",
  "/api/error/tasks/{taskId}",
  "/api/error/records/{logId}",
  "/api/error/records/{logId}/confirm",
  "/api/diagnosis/explain/{explainId}",
  "/api/portraits/class/{courseId}",
  "/api/plans/{planId}/checkin",
  "/api/teacher/reviews/{logPk}",
  "/api/teacher/students/{userId}/learning-records"
] satisfies ApiPath[];

/** 提交作答是写路径：字段名写错不会报错、只会静默丢数据，这里逐个钉住 */
const _practicePayloadKeys: NoExtraKeys<PracticeSubmitPayload, Schema<"PracticeCreate">> = true;
const _practiceResultKeys: NoExtraKeys<PracticeSubmitResult, DataOf<"PracticeResponse">> = true;

/** 读路径：手写 interface 可以更宽松（可选字段更多），但不能凭空多出字段 */
const _recommendationKeys: NoExtraKeys<Recommendation, Schema<"Recommendation">> = true;
const _portraitKeys: NoExtraKeys<Portrait, Schema<"Portrait">> = true;
const _reviewKeys: NoExtraKeys<ReviewItem, Schema<"ReviewItem">> = true;
const _errorRecordKeys: NoExtraKeys<ErrorRecordItem, Schema<"ErrorRecord">> = true;
const _questionBriefKeys: NoExtraKeys<QuestionBrief, Schema<"QuestionBrief">> = true;
const _questionDetailKeys: NoExtraKeys<QuestionDetail, Schema<"QuestionDetail">> = true;
const _planKeys: NoExtraKeys<StudyPlan, Schema<"Plan">> = true;

// 仅用于让 `satisfies` 断言参与编译；运行时不读取
export const CONTRACT_ENDPOINT_COUNT =
  STATIC_ENDPOINTS.length + TEMPLATE_ENDPOINTS.length;
