<template>
  <div class="page">
    <h2 class="page-title">错题诊断工作台</h2>

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
        <el-card header="① 录入题干">
          <el-upload
            class="ocr-upload"
            drag
            accept="image/jpeg,image/png"
            :show-file-list="false"
            :http-request="handleUpload"
          >
            <div class="upload-title">拖拽错题图片，或点击上传</div>
            <div class="upload-sub">JPG/PNG，≤5MB；识别失败可直接手动输入</div>
          </el-upload>
          <div v-if="parsing" class="ocr-status">
            <span class="ocr-dot" />识别中…（2 秒轮询）
          </div>
          <el-alert
            v-if="lowConfidence"
            type="warning"
            :closable="false"
            show-icon
            class="ocr-alert"
            :title="`识别置信度低于 ${LOW_CONFIDENCE_THRESHOLD}：请核对并修正题干（或重新选择知识点）后再分析`"
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
            placeholder="粘贴或手动输入题目。OCR 上传通道将在下一步接入，当前先走手动录入。"
          />
          <el-form-item label="错因类型" class="error-type">
            <el-select v-model="errorType" class="full-select" placeholder="选择五类标准错因">
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
              分析知识点
            </el-button>
            <el-tag v-if="mapResult" :type="decisionType">{{ decisionLabel }}</el-tag>
            <span class="hint">映射返回 AUTO / CONFIRM / FAILED</span>
          </div>

          <el-skeleton v-if="matching" :rows="3" animated class="skeleton" />
          <div v-else-if="candidates.length" class="match-list">
            <div class="label">匹配到的知识点（请确认）</div>
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
          <el-empty v-else description="输入题干后点击分析" :image-size="72" />
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="24" :md="12" :lg="12">
        <el-card header="② 启发式讲解">
          <div class="actions">
            <el-button
              type="success"
              :loading="explaining"
              :disabled="!selectedKpId"
              @click="startExplain"
            >
              生成讲解
            </el-button>
            <el-tag v-if="degraded" type="warning">{{ degradeLabel }}</el-tag>
            <el-tag v-if="blocked" type="danger">学术诚信拦截</el-tag>
          </div>

          <el-skeleton v-if="explaining && !explainText" :rows="4" animated class="skeleton" />
          <div v-else-if="explainText" class="explain-text">{{ explainText }}</div>
          <el-empty v-else description="确认知识点后生成讲解" :image-size="72" />

          <div v-if="sources.length" class="sources">
            <div class="label">引用来源</div>
            <div v-for="source in sources" :key="source.kpId" class="source-item">
              《数据库系统概论（第6版）》{{ source.textbookPage || "页码待确认" }} ·
              {{ source.title || source.kpId }}
            </div>
          </div>
          <div v-if="explainId" class="meta-line">
            explainId：{{ explainId }} · 耗时 {{ latencyMs }} ms
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card header="③ 三层推荐（巩固 / 变式 / 进阶）" class="recommend-card">
      <el-empty
        v-if="!questions.length"
        description="讲解完成后自动推荐 3 题"
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
import { ElMessage } from "element-plus";
import type { UploadRequestOptions } from "element-plus";
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

const questionText = ref("");
const errorType = ref("CONCEPT_CONFUSION");
const matching = ref(false);
const mapResult = ref<MapResult | null>(null);
const selectedKpId = ref("");

const candidates = computed(() => mapResult.value?.candidates.slice(0, 3) ?? []);
const decisionLabel = computed(() => {
  const decision = mapResult.value?.decision;
  if (decision === "AUTO") return "自动接受";
  if (decision === "CONFIRM") return "需人工确认";
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
const latencyMs = ref(0);
const questions = ref<Recommendation[]>([]);
const errorMsg = ref("");

const parsing = ref(false);
const lowConfidence = ref(false);
const ocrError = ref("");
let pollTimer: number | null = null;

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
    }
  }, 2000);
};

const handleUpload = async (options: UploadRequestOptions) => {
  stopPolling();
  parsing.value = true;
  lowConfidence.value = false;
  ocrError.value = "";
  try {
    const data = await uploadOcrImage(options.file);
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

/** 创建并确认错题记录：讲解必须基于 CONFIRMED 记录（契约前置） */
const ensureRecord = async (): Promise<string> => {
  if (logId.value) return logId.value;
  const created = await createErrorRecord({
    courseId: DEFAULT_COURSE_ID,
    clientToken: newClientToken(),
    kpId: selectedKpId.value,
    errorType: errorType.value,
    correctedText: questionText.value
  });
  const confirmed = await confirmErrorRecord(created.logId, {
    kpId: selectedKpId.value,
    errorType: errorType.value,
    correctedText: questionText.value
  });
  logId.value = confirmed.logId;
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

// 题干、知识点或错因变化后，旧错题记录不再对应当前内容，必须重新建记录；
// 低置信门禁（契约 §4）：编辑题干或重新选择知识点即视为完成人工核对，解除禁止
watch([questionText, selectedKpId, errorType], () => {
  logId.value = "";
  lowConfidence.value = false;
});

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
.page-title {
  margin: 0 0 clamp(12px, 1.6vw, 18px);
  font-size: clamp(1.15rem, 0.9vw + 0.85rem, 1.5rem);
  letter-spacing: -0.01em;
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
.ocr-confirm {
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
.hint {
  font-size: clamp(11px, 0.3vw + 10px, 12px);
  color: var(--app-text-muted);
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
  .actions .hint {
    flex-basis: 100%;
  }
  .q-text {
    font-size: 14px;
  }
}
</style>
