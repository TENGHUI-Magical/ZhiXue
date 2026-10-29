<template>
  <div class="page">
    <div class="page-head">
      <div>
        <div class="eyebrow">DIAGNOSIS</div>
        <h2>错题诊断工作台</h2>
        <p class="sub">上传或录入错题，匹配知识点后生成启发式讲解与分层练习。</p>
      </div>
      <el-button @click="router.push('/error-book')">查看错题本</el-button>
    </div>

    <el-alert
      v-if="errorMsg"
      :title="errorMsg"
      type="error"
      show-icon
      closable
      class="error-bar"
      @close="errorMsg = ''"
    />

    <el-row :gutter="16" class="workspace-row">
      <el-col :xs="24" :sm="24" :md="12" :lg="12">
        <el-card>
          <template #header>
            <div class="card-head"><span>01 · 题目录入</span><span class="card-sub">图片识别或手动输入</span></div>
          </template>
          <el-upload
            class="ocr-upload"
            drag
            accept="image/jpeg,image/png"
            :show-file-list="false"
            :http-request="handleUpload"
          >
            <UploadIcon class="upload-icon" />
            <div class="upload-title">上传错题图片</div>
            <div class="upload-sub">JPG / PNG，单张不超过 5MB；识别结果可手动校正</div>
          </el-upload>
          <div v-if="parsing" class="ocr-status">
            <span class="ocr-dot" />正在识别，请稍候…
          </div>
          <el-alert
            v-if="lowConfidence"
            type="warning"
            :closable="false"
            show-icon
            class="ocr-alert"
            :title="`识别置信度低于 ${LOW_CONFIDENCE_THRESHOLD}，请核对题干并确认知识点后再分析`"
          />
          <el-alert
            v-if="ocrError"
            type="error"
            :closable="false"
            show-icon
            class="ocr-alert"
            :title="ocrError"
          />
          <el-input
            v-model="questionText"
            type="textarea"
            :rows="4"
            maxlength="3000"
            show-word-limit
            placeholder="粘贴或手动输入题干，也可先上传图片自动识别。"
            @input="onManualInput"
          />
          <el-form-item label="错因分类" class="error-type">
            <el-select v-model="errorType" class="full-select" placeholder="选择错因分类">
              <el-option
                v-for="item in ERROR_TYPES"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <div class="actions">
            <el-button type="primary" :loading="matching" :disabled="lowConfidence" @click="analyze">
              匹配知识点
            </el-button>
            <el-tag v-if="mapResult" :type="decisionType">{{ decisionLabel }}</el-tag>
          </div>

          <el-skeleton v-if="matching" :rows="3" animated class="skeleton" />
          <div v-else-if="candidates.length" class="match-list">
            <div class="label">候选知识点（请选择确认）</div>
            <el-radio-group v-model="selectedKpId">
              <el-radio
                v-for="item in candidates"
                :key="item.kpId"
                :value="item.kpId"
                border
                class="match-item"
              >
                {{ item.kpId }} {{ item.name }} · {{ percent(item.score) }}
              </el-radio>
            </el-radio-group>
          </div>
          <el-empty v-else description="录入题干后开始知识点匹配" :image-size="72" />
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="24" :md="12" :lg="12">
        <el-card>
          <template #header>
            <div class="card-head"><span>02 · 启发式讲解</span><span class="card-sub">基于教材知识切片</span></div>
          </template>
          <div class="actions">
            <el-button
              type="primary"
              :loading="explaining"
              :disabled="!selectedKpId"
              @click="startExplain"
            >
              生成启发式讲解
            </el-button>
            <el-tag v-if="degraded" type="warning">{{ degradeLabel }}</el-tag>
            <el-tag v-if="blocked" type="danger">学术诚信拦截</el-tag>
          </div>

          <el-skeleton v-if="explaining && !explainText" :rows="4" animated class="skeleton" />
          <div v-else-if="explainText" class="explain-text">{{ explainText }}</div>
          <div v-else class="explain-empty">
            <p class="empty-title">确认知识点后可生成讲解</p>
            <div class="guide">
              <span><i>1</i>录入或上传题干</span>
              <span><i>2</i>确认匹配知识点</span>
              <span><i>3</i>生成讲解并查看教材依据</span>
            </div>
            <p class="empty-note">讲解将引用教材知识点切片，并标注来源页码。</p>
          </div>

          <div v-if="sources.length" class="sources">
            <div class="label">教材依据</div>
            <div v-for="source in sources" :key="source.kpId" class="source-item">
              《数据库系统概论（第6版）》{{ source.textbookPage || "页码待定" }} ·
              {{ source.title || source.kpId }}
            </div>
          </div>
          <div v-if="explainId" class="meta-line">
            讲解编号 {{ explainId }} · 生成耗时 {{ (latencyMs / 1000).toFixed(1) }} 秒
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="recommend-card">
      <template #header>
        <div class="card-head"><span>03 · 分层练习推荐</span><span class="card-sub">巩固 / 变式 / 进阶</span></div>
      </template>
      <el-empty
        v-if="!questions.length"
        description="完成讲解后，系统按巩固、变式、进阶推荐练习。"
        :image-size="72"
      />
      <div v-else>
        <div v-for="question in questions" :key="question.qId" class="question">
          <div class="q-head">
            <el-tag size="small">{{ layerLabel(question.layer) }}</el-tag>
            <el-tag size="small" type="info">难度 {{ question.difficulty }}</el-tag>
            <span class="q-id">{{ question.qId }} · {{ question.reason }}</span>
          </div>
          <div class="q-text">{{ question.questionText }}</div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { useRouter } from "vue-router";
import type { UploadRequestOptions } from "element-plus";
import { Upload as UploadIcon } from "@element-plus/icons-vue";
import { ApiError } from "../api/request";
import {
  DEFAULT_COURSE_ID,
  LOW_CONFIDENCE_THRESHOLD,
  createErrorRecord,
  confirmErrorRecord,
  getOcrTask,
  getRecommendations,
  mapKnowledgePoint,
  newClientToken,
  streamExplain,
  uploadOcrImage,
  type ErrorRecord,
  type MapResult,
  type Recommendation,
  type SourceRef
} from "../api/diagnosis";

const ERROR_TYPES = [
  { value: "CONCEPT_CONFUSION", label: "概念混淆" },
  { value: "STEP_OMISSION", label: "步骤遗漏" },
  { value: "BOUNDARY_ERROR", label: "边界条件忽略" },
  { value: "CALCULATION_ERROR", label: "计算失误" },
  { value: "METHOD_MISSING", label: "方法缺失" }
];

const LAYER_LABELS: Record<string, string> = {
  CONSOLIDATE: "同点巩固",
  VARIANT: "变式迁移",
  ADVANCE: "进阶挑战"
};

const router = useRouter();
const questionText = ref("");
const errorType = ref("CONCEPT_CONFUSION");
const matching = ref(false);
const mapResult = ref<MapResult | null>(null);
const selectedKpId = ref("");

const candidates = computed(() => mapResult.value?.candidates.slice(0, 3) ?? []);
const decisionLabel = computed(() => {
  const decision = mapResult.value?.decision;
  if (decision === "AUTO") return "自动匹配";
  if (decision === "CONFIRM") return "待人工确认";
  if (decision === "FAILED") return "未匹配";
  return "";
});
const decisionType = computed(() => {
  const decision = mapResult.value?.decision;
  if (decision === "AUTO") return "success";
  if (decision === "CONFIRM") return "warning";
  return "danger";
});

const explaining = ref(false);
const explainText = ref("");
const sources = ref<SourceRef[]>([]);
const degraded = ref(false);
const degradeLevel = ref("");
const blocked = ref(false);
const explainId = ref("");
const logId = ref("");
/** 本轮讲解对应的 DRAFT 记录：confirm 失败重试时复用，不重复建草稿 */
const draftLogId = ref("");
const latencyMs = ref(0);
const questions = ref<Recommendation[]>([]);
const errorMsg = ref("");

const parsing = ref(false);
const lowConfidence = ref(false);
const ocrError = ref("");
let pollTimer: number | null = null;

/** 与后端 zhixue.ocr.max-image-bytes（5MB）对齐，超限时上传前直接拦截 */
const MAX_UPLOAD_BYTES = 5 * 1024 * 1024;

let controller: AbortController | null = null;

const stopPolling = () => {
  if (pollTimer !== null) {
    window.clearInterval(pollTimer);
    pollTimer = null;
  }
};

const pollTask = (taskId: string) => {
  let attempts = 0;
  stopPolling();
  pollTimer = window.setInterval(async () => {
    attempts += 1;
    if (attempts > 30) {
      stopPolling();
      parsing.value = false;
      ocrError.value = "识别超时，请手动录入题干";
      return;
    }
    try {
      const task = await getOcrTask(taskId);
      if (task.status === "SUCCESS") {
        stopPolling();
        parsing.value = false;
        const text = task.fields?.stem || task.fields?.rawText || "";
        if (text) {
          questionText.value = text;
        }
        lowConfidence.value = (task.confidence ?? 0) < LOW_CONFIDENCE_THRESHOLD;
      } else if (task.status === "FAILED") {
        stopPolling();
        parsing.value = false;
        ocrError.value = task.errorMessage || "OCR 识别失败，请手动录入题干";
      }
    } catch {
      stopPolling();
      parsing.value = false;
      ocrError.value = "识别状态查询失败，请手动录入题干";
    }
  }, 2000);
};

const handleUpload = async (options: UploadRequestOptions) => {
  stopPolling();
  parsing.value = false;
  lowConfidence.value = false;
  ocrError.value = "";
  // 拖拽上传不受 accept 限制，浏览器不会替我们过滤文件，先做与后端一致的预校验
  const file: File = options.file;
  if (file.size > MAX_UPLOAD_BYTES) {
    ocrError.value = "图片超过 5MB 上限，请压缩后重试";
    return;
  }
  if (file.type && file.type !== "image/jpeg" && file.type !== "image/png") {
    ocrError.value = "仅支持 JPG/PNG 图片";
    return;
  }
  parsing.value = true;
  try {
    const data = await uploadOcrImage(file);
    pollTask(data.taskId);
  } catch {
    parsing.value = false;
    ocrError.value = "上传失败，请手动录入题干";
  }
};

const percent = (value: number) => `${Math.round(value * 100)}%`;
const layerLabel = (layer: string) => LAYER_LABELS[layer] ?? layer;
const degradeLabel = computed(() =>
  degradeLevel.value ? `降级 ${degradeLevel.value}` : "已降级"
);

const resetExplainState = () => {
  explainText.value = "";
  sources.value = [];
  degraded.value = false;
  degradeLevel.value = "";
  blocked.value = false;
  explainId.value = "";
  latencyMs.value = 0;
  questions.value = [];
  errorMsg.value = "";
};

const analyze = async () => {
  if (!questionText.value.trim()) {
    ElMessage.warning("请先输入题干");
    return;
  }
  // 先中止在途讲解流，避免旧流继续写入已清空的 explainText（竞态）
  controller?.abort();
  controller = null;
  explaining.value = false;
  resetExplainState();
  logId.value = "";
  draftLogId.value = "";
  mapResult.value = null;
  selectedKpId.value = "";
  matching.value = true;
  try {
    const data = await mapKnowledgePoint(questionText.value);
    mapResult.value = data;
    if (data.candidates.length) {
      selectedKpId.value = data.candidates[0].kpId;
    }
    if (data.decision === "FAILED") {
      ElMessage.warning("未匹配到可靠知识点，请手动选择或改写题干");
    }
  } catch (error) {
    errorMsg.value = error instanceof Error ? error.message : "知识点映射失败";
  } finally {
    matching.value = false;
  }
};

/** SSE 事件分发：delta/source/degraded/done/error（error 含 60004 合规拦截） */
const handleFrame = (event: string, payload: Record<string, unknown>) => {
  switch (event) {
    case "meta":
      explainId.value = String(payload.explainId ?? "");
      break;
    case "source": {
      const source = payload as unknown as SourceRef;
      if (source.kpId && !sources.value.some((item) => item.kpId === source.kpId)) {
        sources.value.push(source);
      }
      break;
    }
    case "delta":
      explainText.value += String(payload.text ?? "");
      break;
    case "degraded":
      degraded.value = true;
      degradeLevel.value = String(payload.level ?? "");
      break;
    case "done":
      explainId.value = String(payload.explainId ?? explainId.value);
      latencyMs.value = Number(payload.latencyMs ?? 0);
      void loadRecommend();
      break;
    case "error":
      // 契约 §2：60004 展示学术诚信提示，不展示讲解正文
      if (Number(payload.code) === 60004) {
        blocked.value = true;
        explainText.value = "";
      }
      errorMsg.value = String(payload.message ?? "讲解失败");
      break;
    default:
      // `: heartbeat` 注释帧在无 data 时已被跳过
      break;
  }
};

const redirectIfUnauthorized = (raw: unknown) => {
  const body = raw as { code?: number } | null;
  if (body?.code === 40100) {
    localStorage.removeItem("satoken");
    window.location.assign("/login");
    return true;
  }
  return false;
};

const startExplain = async () => {
  if (!selectedKpId.value || explaining.value) return;
  controller?.abort();
  controller = new AbortController();
  resetExplainState();
  explaining.value = true;

  try {
    // 契约前置：先落一条 CONFIRMED 错题记录，再请求讲解
    const recordId = await ensureRecord();
    await streamExplain(
      {
        logId: recordId,
        kpId: selectedKpId.value,
        errorType: errorType.value,
        questionText: questionText.value,
        forceRefresh: false
      },
      handleFrame,
      controller.signal,
      redirectIfUnauthorized
    );
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") return;
    errorMsg.value = error instanceof Error ? error.message : "讲解失败，请稍后重试";
  } finally {
    explaining.value = false;
  }
};

/** 创建并确认错题记录：讲解必须基于 CONFIRMED 记录（契约前置）。
 *  create 成功但 confirm 失败时复用同一 DRAFT 重试确认，避免每次失败都多落一条孤儿草稿；
 *  confirm 返回 40900 视为上次确认已生效（响应丢失），直接复用该记录。 */
const ensureRecord = async (): Promise<string> => {
  if (logId.value) return logId.value;
  if (!draftLogId.value) {
    const created = await createErrorRecord({
      courseId: DEFAULT_COURSE_ID,
      clientToken: newClientToken(),
      kpId: selectedKpId.value,
      errorType: errorType.value,
      correctedText: questionText.value
    });
    draftLogId.value = created.logId;
  }
  const confirmed = await confirmErrorRecord(draftLogId.value, {
    kpId: selectedKpId.value,
    errorType: errorType.value,
    correctedText: questionText.value
  }).catch((error: unknown) => {
    if (error instanceof ApiError && error.code === 40900) return null;
    throw error;
  });
  logId.value = confirmed ? confirmed.logId : draftLogId.value;
  return logId.value;
};

const loadRecommend = async () => {
  try {
    // 契约参数是 logId；没有记录时才退回 kpId（理论上不会发生）
    const params = logId.value
      ? { logId: logId.value }
      : { kpId: selectedKpId.value };
    questions.value = await getRecommendations({ ...params, courseId: DEFAULT_COURSE_ID });
  } catch {
    // request.ts 已统一提示；推荐失败不影响讲解展示
  }
};

// 题干、知识点或错因变化后，旧错题记录不再对应当前内容，必须重新建记录
watch([questionText, selectedKpId, errorType], () => {
  logId.value = "";
  draftLogId.value = "";
});
// 低置信门禁（契约 §4）：只有用户手动修改（输入题干 / 重选知识点 / 换错因）才算完成人工核对；
// OCR 回填题干属于程序写入，不能顺带解除门禁，否则警告会被本 watcher 一闪而过地清掉
watch([selectedKpId, errorType], () => {
  lowConfidence.value = false;
});

/** 用户在题干框手动输入 / 粘贴：视为完成人工核对，解除低置信禁止 */
const onManualInput = () => {
  lowConfidence.value = false;
};

onBeforeUnmount(() => {
  controller?.abort();
  stopPolling();
});
</script>

<style scoped>
.page {
  width: 100%;
  max-width: 1280px;
  margin: 0 auto;
}
.page :deep(.el-card) {
  transition: background-color 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}
@media (prefers-reduced-motion: no-preference) {
  .page :deep(.el-card) {
    animation: card-in 0.28s ease-out both;
  }
  .page :deep(.el-col:nth-child(2) .el-card) {
    animation-delay: 0.05s;
  }
}
@keyframes card-in {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
.eyebrow {
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.18em;
  color: var(--app-text-muted);
}
.page-head h2 {
  margin: 6px 0 3px;
  font-size: 26px;
  letter-spacing: -0.012em;
}
.page-head .sub {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: 12.5px;
}
.card-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.card-sub {
  font-size: 11px;
  font-weight: 400;
  color: var(--app-text-muted);
}
.error-bar {
  margin-bottom: 12px;
}
.ocr-upload {
  margin-bottom: 12px;
}
.ocr-upload :deep(.el-upload-dragger) {
  padding: 18px 12px;
  border-radius: 10px;
  background: var(--app-surface-muted);
  border-color: var(--app-border);
}
.upload-title {
  font-size: 13px;
  color: var(--app-text);
}
.upload-icon {
  display: block;
  margin: 0 auto 8px;
  font-size: 28px;
  color: var(--app-text-muted);
}
.upload-sub {
  margin-top: 4px;
  font-size: 11px;
  color: var(--app-text-muted);
}
.ocr-status {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 8px 0;
  font-size: 12px;
  color: var(--app-text-secondary);
}
.ocr-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--app-primary);
  animation: ocr-pulse 1.1s ease-in-out infinite;
}
@keyframes ocr-pulse {
  0%,
  100% {
    opacity: 0.35;
  }
  50% {
    opacity: 1;
  }
}
.ocr-alert {
  margin-bottom: 10px;
}
.error-type {
  margin-top: 12px;
}
.full-select {
  width: 100%;
}
.actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 12px;
}
.label {
  font-size: clamp(11px, 0.3vw + 10px, 12px);
  color: var(--app-text-muted);
  margin-bottom: 8px;
}
.skeleton {
  padding: 8px 0;
}
.match-list {
  margin-top: 16px;
}
.match-item {
  display: flex;
  margin-bottom: 8px;
  width: 100%;
  height: auto;
  align-items: flex-start;
}
.match-item :deep(.el-radio__label) {
  white-space: normal;
  overflow-wrap: anywhere;
  line-height: 1.6;
}
.explain-text {
  margin-top: 12px;
  line-height: 1.8;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  min-height: 80px;
}
.explain-empty {
  margin-top: 10px;
  padding: 20px 4px 8px;
  text-align: center;
}
.empty-title {
  margin: 0 0 14px;
  font-size: 12.5px;
  color: var(--app-text-muted);
}
.guide {
  display: flex;
  flex-direction: column;
  gap: 9px;
  max-width: 280px;
  margin: 0 auto;
  text-align: left;
}
.guide span {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 12px;
  color: var(--app-text-secondary);
}
.guide i {
  width: 18px;
  height: 18px;
  flex: 0 0 18px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--app-surface-muted);
  color: var(--app-primary);
  font-size: 10px;
  font-style: normal;
  font-weight: 600;
}
.empty-note {
  margin: 18px 0 0;
  font-size: 11px;
  line-height: 1.6;
  color: var(--app-text-muted);
}
.sources {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--app-border);
  font-size: clamp(11px, 0.3vw + 10px, 12px);
  color: var(--app-text-secondary);
}
.source-item {
  line-height: 1.8;
}
.meta-line {
  margin-top: 8px;
  font-size: clamp(11px, 0.3vw + 10px, 12px);
  color: var(--app-text-muted);
}
.recommend-card {
  margin-top: 16px;
}
.question {
  padding: 12px 0;
  border-bottom: 1px dashed var(--app-border);
}
.question:last-child {
  border-bottom: none;
}
.q-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.q-id {
  font-size: clamp(11px, 0.3vw + 10px, 12px);
  color: var(--app-text-muted);
}
.q-text {
  margin-top: 8px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
@media (max-width: 767px) {
  .workspace-row {
    row-gap: 12px;
  }
  .actions > .el-button {
    flex: 1 1 auto;
  }
  .q-text {
    font-size: 14px;
  }
}
</style>
