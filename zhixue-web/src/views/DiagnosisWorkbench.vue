<template>
  <div class="page">
    <div class="page-head">
      <div>
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
            <UploadIcon class="upload-icon" :size="26" />
            <div class="upload-title">上传错题图片</div>
            <div class="upload-sub">JPG / PNG，单张不超过 5MB；识别结果可手动校正</div>
          </el-upload>
          <div v-if="parsing" class="ocr-status" role="status">
            <span class="ocr-dot" />正在识别，请稍候…
          </div>
          <div v-if="ocrFollowUp" class="ocr-status ocr-follow-up" role="status">
            {{ ocrFollowUp }}
          </div>
          <el-alert
            v-if="lowConfidence"
            type="warning"
            :closable="false"
            show-icon
            class="ocr-alert"
            title="识别置信度低于 75%，请核对题干；确认无误后可继续分析"
          />
          <el-button
            v-if="lowConfidence"
            class="ack-btn"
            link
            type="primary"
            @click="acknowledgeLowConfidence"
          >
            我已核对识别结果，继续
          </el-button>
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
          <BrandEmpty v-else description="录入题干后开始知识点匹配" />

          <div v-if="selectedKpId" class="linked-question">
            <div class="label">关联题库题目（可选）</div>
            <el-select
              v-model="linkedQId"
              clearable
              filterable
              class="full-select"
              :loading="linkedLoading"
              :disabled="!!logId"
              placeholder="选择一道题库题目，错题本即可直接练习"
            >
              <el-option
                v-for="(item, index) in linkedQuestions"
                :key="item.qId ?? index"
                :label="`${item.qId} · ${shortText(item.questionText)}`"
                :value="item.qId ?? ''"
              />
            </el-select>
            <p v-if="logId" class="linked-hint">
              错题记录已创建，关联题目已固定；如需更换，请修改题干或知识点后重新分析。
            </p>
            <p v-else-if="!linkedLoading && !linkedQuestions.length" class="linked-hint">
              该知识点暂无题目；可先保存错题，之后在错题本查看。
            </p>
            <p v-else class="linked-hint">
              当前列出该知识点最多 20 道题；若没有原题，可暂不关联。
            </p>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="24" :md="12" :lg="12">
        <el-card class="explain-detail">
          <template #header>
            <div class="card-head card-head-actions">
              <span class="card-title">
                <span>02 · 启发式讲解</span>
                <span class="card-sub">基于教材知识切片</span>
              </span>
              <span class="head-actions">
                <el-tooltip v-if="degraded" :content="degradeTip" placement="top">
                  <el-tag type="warning">{{ degradeLabel }}</el-tag>
                </el-tooltip>
                <el-tag v-if="blocked" type="danger">学术诚信拦截</el-tag>
                <el-button v-if="explaining" size="small" @click="stopExplain">停止生成</el-button>
                <el-button
                  type="primary"
                  :loading="explaining"
                  :disabled="!selectedKpId"
                  @click="startExplain"
                >
                  生成启发式讲解
                </el-button>
              </span>
            </div>
          </template>

          <el-skeleton v-if="explaining && !explainText" :rows="4" animated class="skeleton" />
          <div
            v-else-if="explainText"
            ref="explainBox"
            class="explain-text"
            aria-live="polite"
          >{{ explainText }}</div>
          <ReviewStatus v-if="explainText" :result="reviewResult" :comment="reviewComment" />
          <div
            v-if="completed && !explaining && !errorMsg && !blocked"
            class="explain-done"
            role="status"
          >
            <div class="done-copy">
              <strong>讲解已生成，错题已归档</strong>
              <span>本次诊断已保存，可继续去错题本复盘或安排练习。</span>
            </div>
            <div class="done-actions">
              <el-button type="primary" size="small" @click="goErrorBook">去错题本</el-button>
              <el-button size="small" @click="goPlan">去周计划</el-button>
            </div>
          </div>
          <div v-else-if="!explainText && !explaining" class="explain-empty">
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
            <div v-for="source in sources" :key="sourceKey(source)" class="source-item">
              《数据库系统概论（第6版）》{{ source.textbookPage || "页码待定" }} ·
              {{ source.title || source.kpId }}
            </div>
          </div>
          <div v-if="explainId" class="meta-line">
            讲解编号 {{ explainId }}<span v-if="latencyMs > 0"> · 生成耗时 {{ (latencyMs / 1000).toFixed(1) }} 秒</span>
          </div>
        </el-card>

        <el-card class="diagnosis-state" shadow="never">
          <template #header>
            <div class="card-head"><span>本次诊断</span><span class="card-sub">实时状态</span></div>
          </template>
          <ul class="state-list">
            <li :class="{ done: !!questionText.trim() }">
              <span>题干</span><strong>{{ questionText.trim() ? "已录入" : "待录入" }}</strong>
            </li>
            <li :class="{ done: !!selectedKpId }">
              <span>知识点</span><strong>{{ selectedKpId || "待确认" }}</strong>
            </li>
            <li :class="{ done: !!linkedQId }">
              <span>关联题目</span><strong>{{ linkedQId || "可选" }}</strong>
            </li>
            <li :class="{ done: !!explainText }">
              <span>讲解</span>
              <strong>{{ explainText ? "已生成" : explaining ? "生成中" : "未生成" }}</strong>
            </li>
          </ul>
          <p class="state-note">讲解生成后，下方会按巩固、变式、进阶给出练习。</p>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="recommend-card">
      <template #header>
        <div class="card-head"><span>03 · 分层练习推荐</span><span class="card-sub">巩固 / 变式 / 进阶</span></div>
      </template>
      <div v-if="!questions.length" class="recommend-empty">
        <div class="recommend-copy">
          <strong>{{ recommendLoading ? "正在生成分层练习…" : explainText ? "本次暂未生成推荐" : "还差一次讲解" }}</strong>
          <p>
            {{
              recommendLoading
                ? "讲解已生成，正在按巩固、变式、进阶取题。"
                : explainText
                  ? "推荐接口本次未返回结果；可稍后重试，或先去错题本查看已确认错题。"
                  : "确认知识点并生成讲解后，系统会按三层给出练习，而不是一次堆题。"
            }}
          </p>
        </div>
        <div class="layer-preview">
          <div v-for="(label, key) in LAYER_LABELS" :key="key" class="layer-chip">
            <span>{{ label }}</span>
            <small>{{ LAYER_HINTS[key] }}</small>
          </div>
        </div>
      </div>
      <div v-else>
        <button
          v-for="question in questions"
          :key="question.qId"
          type="button"
          class="question"
          @click="openPractice(question)"
        >
          <span class="q-head">
            <el-tag size="small">{{ layerLabel(question.layer) }}</el-tag>
            <el-tag size="small" type="info">难度 {{ question.difficulty }}</el-tag>
            <span class="q-id">{{ question.qId }} · {{ question.reason }}</span>
            <span class="q-action">开始练习</span>
          </span>
          <span class="q-text">{{ question.questionText }}</span>
        </button>
      </div>
    </el-card>

    <QuestionPracticeDrawer
      v-model="practiceVisible"
      :q-id="practiceQId"
      :kp-id="selectedKpId"
      :recommendation-id="practiceRecommendationId"
      @completed="onPracticeCompleted"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import type { UploadRequestOptions } from "element-plus";
import { Upload as UploadIcon } from "@lucide/vue";
import { ApiError } from "../api/request";
import QuestionPracticeDrawer from "../components/QuestionPracticeDrawer.vue";
import ReviewStatus from "../components/ReviewStatus.vue";
import { getQuestionDetail } from "../api/questions";
import BrandEmpty from "../components/BrandEmpty.vue";
import { listQuestionsByKp, type QuestionBrief } from "../api/course";
import {
  DEFAULT_COURSE_ID,
  LOW_CONFIDENCE_THRESHOLD,
  createErrorRecord,
  confirmErrorRecord,
  getExplain,
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
const LAYER_HINTS: Record<string, string> = {
  CONSOLIDATE: "先把同一知识点练稳",
  VARIANT: "换条件，迁移方法",
  ADVANCE: "再往前一步挑战"
};

const router = useRouter();
const route = useRoute();
const questionText = ref("");
const errorType = ref("CONCEPT_CONFUSION");
const matching = ref(false);
const mapResult = ref<MapResult | null>(null);
const selectedKpId = ref("");
/** 可选关联题库题目：有 qId 错题本才能直接进入练习（FR-05） */
const linkedQId = ref("");
const linkedQuestions = ref<QuestionBrief[]>([]);
const linkedLoading = ref(false);

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
const explainBox = ref<HTMLElement | null>(null);
const sources = ref<SourceRef[]>([]);
const degraded = ref(false);
const degradeLevel = ref("");
const blocked = ref(false);
const explainId = ref("");
/** 仅收到 SSE done 才置 true：用户主动停止/中断不显示“已完成” */
const completed = ref(false);
const reviewResult = ref<"PASS" | "RETURN" | null>(null);
const reviewComment = ref<string | null>(null);
const logId = ref("");
/** 本轮讲解对应的 DRAFT 记录：confirm 失败重试时复用，不重复建草稿 */
const draftLogId = ref("");
const latencyMs = ref(0);
const questions = ref<Recommendation[]>([]);
const recommendLoading = ref(false);
const practiceVisible = ref(false);
const practiceQId = ref("");
const practiceRecommendationId = ref<number | null>(null);
const errorMsg = ref("");

const parsing = ref(false);
const lowConfidence = ref(false);
const ocrError = ref("");
const ocrFollowUp = ref("");
let pollTimer: number | null = null;

/** 与后端 zhixue.ocr.max-image-bytes（5MB）对齐，超限时上传前直接拦截 */
const MAX_UPLOAD_BYTES = 5 * 1024 * 1024;

let controller: AbortController | null = null;
/** 本轮流是否收到 done；连接提前结束且无 done 时不能把半截内容当完成 */
let doneSeen = false;
let authExpired = false;

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
        lowConfidence.value = (task.confidence ?? 0) < LOW_CONFIDENCE_THRESHOLD;
        if (!text) {
          ocrFollowUp.value = "未识别到题干，请手动输入后匹配知识点";
          return;
        }
        questionText.value = text;
        if (lowConfidence.value) {
          ocrFollowUp.value = "识别完成。请先核对题干，再点击匹配知识点";
          return;
        }
        ocrFollowUp.value = "识别完成，正在自动匹配知识点…";
        await analyze();
        if (errorMsg.value) {
          ocrFollowUp.value = "自动匹配未完成，请调整题干后重试";
          return;
        }
        if (mapResult.value?.decision === "AUTO" && selectedKpId.value) {
          ocrFollowUp.value = `已自动确认 ${selectedKpId.value}，正在生成讲解…`;
          await nextTick();
          // 等待唯一题目关联完成，不能让 ensureRecord 抢先落一条缺 qId 的错题。
          await loadLinkedQuestions();
          await startExplain();
          ocrFollowUp.value = explainText.value
            ? "讲解已生成，可继续查看或开始分层练习"
            : "自动生成未完成，请检查提示后重试";
        } else if (mapResult.value?.decision === "CONFIRM") {
          ocrFollowUp.value = "识别完成。请确认候选知识点，再生成讲解";
        } else {
          ocrFollowUp.value = "未匹配到可靠知识点，请改写题干后重试";
        }
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
  ocrFollowUp.value = "";
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
const sourceKey = (source: SourceRef) =>
  `${source.kpId}|${source.textbookPage ?? ""}|${source.title ?? ""}`;
const layerLabel = (layer: string) => LAYER_LABELS[layer] ?? layer;
const shortText = (text?: string | null) => {
  const value = (text ?? "").replace(/\s+/g, " ").trim();
  return value.length > 24 ? `${value.slice(0, 24)}…` : value || "（无题干）";
};
/** 题干归一化：去空白/标点后比较，降低 OCR 空格与标点差异对自动匹配的影响 */
const normalizeQuestionText = (text?: string | null) =>
  (text ?? "").replace(/\s+/g, "").replace(/[^\w\u4e00-\u9fa5]/g, "").toLowerCase();

let linkedSeq = 0;
/** 选定知识点后拉该点题库题目；高置信唯一匹配时自动选中，失败只清空候选 */
const loadLinkedQuestions = async () => {
  const kp = selectedKpId.value;
  linkedQId.value = "";
  linkedQuestions.value = [];
  if (!kp) return;
  const seq = ++linkedSeq;
  linkedLoading.value = true;
  try {
    const data = await listQuestionsByKp(kp, 20);
    if (seq !== linkedSeq) return;
    linkedQuestions.value = data ?? [];
    const target = normalizeQuestionText(questionText.value);
    if (target.length >= 10) {
      const matches = linkedQuestions.value.filter((item) => {
        const candidate = normalizeQuestionText(item.questionText);
        return candidate && (candidate === target || candidate.includes(target) || target.includes(candidate));
      });
      if (matches.length === 1) {
        linkedQId.value = matches[0].qId ?? "";
      }
    }
  } catch {
    if (seq === linkedSeq) linkedQuestions.value = [];
  } finally {
    if (seq === linkedSeq) linkedLoading.value = false;
  }
};
watch(selectedKpId, loadLinkedQuestions);
// 降级标签用学生话术，不暴露 L2/L3 内部词；标签保留可读文本，释义放 tooltip。
// L3=本地教材知识卡（未调用大模型）；L2=备用模型完整产出或远程流中断仅返回部分正文，
// 两种情形都没有切换本地模板，文案不能写成“已切换备用模型”。
const degradeLabel = computed(() => {
  if (degradeLevel.value === "L3") return "本地教材知识卡";
  if (degradeLevel.value === "L4") return "暂未生成讲解";
  return "简化讲解";
});
const degradeTip = computed(() => {
  if (degradeLevel.value === "L3") return "当前用教材原文作答，未调用大模型";
  if (degradeLevel.value === "L4") return "知识点原文缺失，暂无法生成讲解，请联系教师补全";
  if (degradeLevel.value === "L2") return "当前讲解由备用通道生成或只返回了部分内容，可重试获取完整讲解";
  return "当前讲解未完整返回，可重试获取完整讲解";
});

let recSeq = 0;
let explainQuerySeq = 0;
const resetExplainState = () => {
  recSeq += 1;
  explainQuerySeq += 1;
  recommendLoading.value = false;
  explainText.value = "";
  sources.value = [];
  degraded.value = false;
  degradeLevel.value = "";
  blocked.value = false;
  explainId.value = "";
  completed.value = false;
  reviewResult.value = null;
  reviewComment.value = null;
  latencyMs.value = 0;
  questions.value = [];
  errorMsg.value = "";
  doneSeen = false;
  authExpired = false;
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
    // 从错题本“去诊断”带入 kpId 时优先选中该知识点，避免用户重新找一遍
    const preferredKp = typeof route.query.kpId === "string" ? route.query.kpId : "";
    const preferred = data.candidates.find((item) => item.kpId === preferredKp);
    if (preferred) {
      selectedKpId.value = preferred.kpId;
    } else if (data.decision === "AUTO" && data.candidates.length) {
      // 只在 AUTO 时预选 top1：CONFIRM 是“必须人工确认”，预选会让低质量候选被惯性接受
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

const loadExplainDetail = async (id: string) => {
  resetExplainState();
  const seq = ++explainQuerySeq;
  explaining.value = true;
  try {
    const data = await getExplain(id);
    if (seq !== explainQuerySeq) return;
    if (!data) throw new Error("讲解记录不存在");
    explainText.value = data.content || "";
    sources.value = data.sources ?? [];
    degraded.value = !!data.degraded;
    degradeLevel.value = data.degradeLevel ?? "";
    blocked.value = !!data.blocked;
    explainId.value = data.explainId;
    reviewResult.value = data.reviewResult ?? null;
    reviewComment.value = data.reviewComment ?? null;
  } catch (error) {
    if (seq === explainQuerySeq) {
      errorMsg.value = error instanceof Error ? error.message : "讲解记录加载失败";
    }
  } finally {
    if (seq === explainQuerySeq) explaining.value = false;
  }
};

const loadReviewState = async (id: string) => {
  const seq = ++explainQuerySeq;
  try {
    const data = await getExplain(id);
    if (seq !== explainQuerySeq || explainId.value !== id) return;
    reviewResult.value = data?.reviewResult ?? null;
    reviewComment.value = data?.reviewComment ?? null;
  } catch {
    // 讲解正文已生成，复核状态失败时保留正文且不制造已评阅标记
    if (seq === explainQuerySeq) {
      reviewResult.value = null;
      reviewComment.value = null;
    }
  }
};

watch(
  () => route.query.explainId,
  (id) => {
    if (typeof id === "string" && id) void loadExplainDetail(id);
  },
  { immediate: true }
);

/** SSE 事件分发：delta/source/degraded/done/error（error 含 60004 合规拦截） */
const handleFrame = (event: string, payload: Record<string, unknown>) => {
  switch (event) {
    case "meta":
      explainId.value = String(payload.explainId ?? "");
      break;
    case "source": {
      const source = payload as unknown as SourceRef;
      if (source.kpId && !sources.value.some((item) => sourceKey(item) === sourceKey(source))) {
        sources.value.push(source);
      }
      break;
    }
    case "delta":
      explainText.value += String(payload.text ?? "");
      // 流式长文自动跟随到底部（读屏由容器 aria-live 播报增量）
      void nextTick(() => {
        if (explainBox.value) explainBox.value.scrollTop = explainBox.value.scrollHeight;
      });
      break;
    case "degraded":
      degraded.value = true;
      degradeLevel.value = String(payload.level ?? "");
      break;
    case "done":
      doneSeen = true;
      completed.value = true;
      explainId.value = String(payload.explainId ?? explainId.value);
      latencyMs.value = Number(payload.latencyMs ?? 0);
      void loadRecommend();
      void loadReviewState(explainId.value);
      break;
    case "error":
      // 契约 §2：60004 展示学术诚信提示，不展示讲解正文
      if (Number(payload.code) === 60004) {
        blocked.value = true;
        explainText.value = "";
      }
      explainId.value = "";
      latencyMs.value = 0;
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
    authExpired = true;
    localStorage.removeItem("satoken");
    void router.replace("/login");
    return true;
  }
  return false;
};

let explainRunSeq = 0;
const startExplain = async () => {
  if (!selectedKpId.value || explaining.value) return;
  const seq = ++explainRunSeq;
  const streamController = new AbortController();
  controller?.abort();
  controller = streamController;
  resetExplainState();
  explaining.value = true;

  try {
    // 契约前置：先落一条 CONFIRMED 错题记录，再请求讲解
    const recordId = await ensureRecord();
    if (seq !== explainRunSeq) return;
    await streamExplain(
      {
        logId: recordId,
        kpId: selectedKpId.value,
        errorType: errorType.value,
        questionText: questionText.value,
        forceRefresh: false
      },
      (event, payload) => {
        if (seq === explainRunSeq) handleFrame(event, payload);
      },
      streamController.signal,
      redirectIfUnauthorized
    );
    if (seq !== explainRunSeq) return;
    // 连接正常结束却没有 done：不能把半截讲解当成完整结果
    if (!doneSeen && !blocked.value && !errorMsg.value && !authExpired) {
      explainText.value = "";
      sources.value = [];
      explainId.value = "";
      latencyMs.value = 0;
      errorMsg.value = "讲解生成中断，请重试";
    }
  } catch (error) {
    if (seq !== explainRunSeq || (error instanceof DOMException && error.name === "AbortError")) return;
    explainText.value = "";
    sources.value = [];
    explainId.value = "";
    latencyMs.value = 0;
    errorMsg.value = error instanceof Error ? error.message : "讲解失败，请稍后重试";
  } finally {
    if (seq === explainRunSeq) explaining.value = false;
  }
};

let autoExplainSeq = 0;
watch(
  () => [route.query.autoExplain, route.query.qId, route.query.kpId] as const,
  async ([autoExplain, qId, kpId]) => {
    const seq = ++autoExplainSeq;
    explainRunSeq += 1;
    controller?.abort();
    controller = null;
    explaining.value = false;
    if (autoExplain !== "1" || typeof qId !== "string" || typeof kpId !== "string") return;
    resetExplainState();
    matching.value = true;
    try {
      const detail = await getQuestionDetail(qId);
      if (seq !== autoExplainSeq) return;
      questionText.value = detail.questionText || "";
      selectedKpId.value = detail.kpId || kpId;
      // 让常规“题干/知识点变化” watcher 先完成，再恢复本题关联并自动讲解。
      await nextTick();
      if (seq !== autoExplainSeq) return;
      linkedQId.value = qId;
      if (!questionText.value.trim()) throw new Error("题目缺少题干，无法生成讲解");
      await startExplain();
    } catch (error) {
      if (seq === autoExplainSeq) {
        errorMsg.value = error instanceof Error ? error.message : "本题讲解准备失败";
      }
    } finally {
      if (seq === autoExplainSeq) matching.value = false;
    }
  },
  { immediate: true }
);

/** 用户主动中断流式讲解；abort 后 finally 会复位 explaining，不落错误提示 */
const stopExplain = () => {
  controller?.abort();
};

const goErrorBook = () => router.push("/error-book");
const goPlan = () => router.push("/plan");

/** 创建并确认错题记录：讲解必须基于 CONFIRMED 记录（契约前置）。
 *  create 成功但 confirm 失败时复用同一 DRAFT 重试确认，避免每次失败都多落一条孤儿草稿；
 *  confirm 返回 40900 视为上次确认已生效（响应丢失），直接复用该记录。 */
const ensureRecord = async (): Promise<string> => {
  if (logId.value) return logId.value;
  if (!draftLogId.value) {
    const created = await createErrorRecord({
      courseId: DEFAULT_COURSE_ID,
      clientToken: newClientToken(),
      qId: linkedQId.value || undefined,
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

const openPractice = (question: Recommendation) => {
  practiceQId.value = question.qId;
  practiceRecommendationId.value = question.recommendationId ?? null;
  practiceVisible.value = true;
};

const onPracticeCompleted = async () => {
  practiceVisible.value = false;
  await loadRecommend();
};

const loadRecommend = async () => {
  const seq = ++recSeq;
  recommendLoading.value = true;
  try {
    // 契约参数是 logId；没有记录时才退回 kpId（理论上不会发生）
    const params = logId.value
      ? { logId: logId.value }
      : { kpId: selectedKpId.value };
    const data = await getRecommendations({ ...params, courseId: DEFAULT_COURSE_ID });
    if (seq === recSeq) questions.value = data;
  } catch {
    // request.ts 已统一提示；推荐失败不影响讲解展示
  } finally {
    if (seq === recSeq) recommendLoading.value = false;
  }
};

// 题干/知识点变化后，旧错题记录与旧关联题目都不再对应当前内容；
// 只改错因时记录需重建，但关联题目仍有效，不能静默清空。
const resetForChange = (clearLinkedQuestion: boolean) => {
  logId.value = "";
  draftLogId.value = "";
  if (clearLinkedQuestion) {
    linkedQId.value = "";
  }
  if (explaining.value) {
    controller?.abort();
    controller = null;
    explaining.value = false;
    resetExplainState();
  }
};
watch([questionText, selectedKpId], () => resetForChange(true));
watch(errorType, () => resetForChange(false));
// 低置信门禁（契约 §4）：只认“手动改题干”或“显式点我已核对”，
// 不能靠改错因/重选知识点绕过；否则用户没看 OCR 文本也能继续。

/** 用户在题干框手动输入 / 粘贴：视为完成人工核对，解除低置信禁止 */
const onManualInput = () => {
  lowConfidence.value = false;
  if (ocrFollowUp.value) ocrFollowUp.value = "题干已更新，请点击匹配知识点";
};

/** 低置信度不要求改写题干；显式确认“已核对”即可继续，符合契约人工核对要求 */
const acknowledgeLowConfidence = () => {
  lowConfidence.value = false;
  ocrError.value = "";
};

onBeforeUnmount(() => {
  controller?.abort();
  stopPolling();
});
</script>

<style scoped>
.page {
  width: 100%;
  max-width: var(--app-page-max);
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
.page-head h2 {
  margin: 6px 0 3px;
  font-size: var(--app-fs-xl);
  letter-spacing: -0.012em;
}
.page-head .sub {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: var(--app-fs-sm);
}
.card-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.card-head-actions {
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  width: 100%;
}
.card-title {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}
.head-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.card-sub {
  font-size: var(--app-fs-xs);
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
  border-radius: var(--app-radius-sm);
  background: var(--app-surface-muted);
  border-color: var(--app-border);
}
.upload-title {
  font-size: var(--app-fs-sm);
  color: var(--app-text);
}
.upload-icon {
  display: block;
  margin: 0 auto 8px;
  color: var(--app-text-muted);
}
.upload-sub {
  margin-top: 4px;
  font-size: var(--app-fs-xs);
  color: var(--app-text-muted);
}
.ocr-status {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 8px 0;
  font-size: var(--app-fs-xs);
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
.ack-btn {
  margin: -2px 0 10px;
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
  font-size: var(--app-fs-xs);
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
.linked-question {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--app-divider);
}
.linked-hint {
  margin: 6px 0 0;
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
  line-height: 1.5;
}
.explain-text {
  margin-top: 12px;
  line-height: 1.8;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  min-height: 80px;
}
.explain-done {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 12px;
  padding: 10px 12px;
  border: 1px solid color-mix(in srgb, var(--app-success) 35%, var(--app-border));
  border-radius: var(--app-radius-md);
  background: color-mix(in srgb, var(--app-success) 8%, var(--app-surface));
}
.done-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.done-copy strong {
  font-size: var(--app-fs-sm);
}
.done-copy span {
  font-size: var(--app-fs-xs);
  color: var(--app-text-muted);
}
.done-actions {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 8px;
}
.explain-empty {
  margin-top: 10px;
  padding: 20px 4px 8px;
  text-align: center;
}
.empty-title {
  margin: 0 0 14px;
  font-size: var(--app-fs-sm);
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
  font-size: var(--app-fs-xs);
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
  font-size: var(--app-fs-xs);
  font-style: normal;
  font-weight: 600;
}
.empty-note {
  margin: 18px 0 0;
  font-size: var(--app-fs-xs);
  line-height: 1.6;
  color: var(--app-text-muted);
}
.sources {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--app-border);
  font-size: var(--app-fs-xs);
  color: var(--app-text-secondary);
}
.source-item {
  line-height: 1.8;
}
.meta-line {
  margin-top: 8px;
  font-size: var(--app-fs-xs);
  color: var(--app-text-muted);
}
.diagnosis-state {
  margin-top: 16px;
}
.state-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.state-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 0;
  border-bottom: 1px solid var(--app-hairline);
  color: var(--app-text-secondary);
  font-size: var(--app-fs-sm);
}
.state-list li:last-child {
  border-bottom: none;
}
.state-list strong {
  color: var(--app-text);
  font-weight: 600;
}
.state-list li.done strong {
  color: var(--app-success);
}
.state-note {
  margin: 10px 0 0;
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
  line-height: 1.6;
}
.recommend-card {
  margin-top: 16px;
}
.recommend-empty {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 1.1fr);
  align-items: center;
  gap: var(--app-space-6);
  padding: 6px 0;
}
.recommend-copy strong {
  display: block;
  color: var(--app-text);
  font-size: var(--app-fs-lg);
  font-weight: 600;
  letter-spacing: -0.01em;
}
.recommend-copy p {
  margin: 8px 0 0;
  color: var(--app-text-secondary);
  font-size: var(--app-fs-sm);
  line-height: 1.65;
}
.layer-preview {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--app-space-3);
}
.layer-chip {
  padding: var(--app-space-3);
  border: 1px solid var(--app-hairline);
  border-radius: var(--app-radius-md);
  background: var(--app-surface-muted);
}
.layer-chip span {
  display: block;
  color: var(--app-text);
  font-size: var(--app-fs-xs);
  font-weight: 600;
}
.layer-chip small {
  display: block;
  margin-top: 4px;
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
  line-height: 1.5;
}
.question {
  display: block;
  width: 100%;
  min-height: 44px;
  padding: 12px 8px;
  border: 0;
  border-bottom: 1px dashed var(--app-border);
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.question:hover {
  background: var(--app-surface-muted);
}
.question:focus-visible {
  outline: 2px solid var(--app-primary);
  outline-offset: 2px;
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
  font-size: var(--app-fs-xs);
  color: var(--app-text-muted);
}
.q-action {
  margin-left: auto;
  color: var(--app-primary);
  font-size: var(--app-fs-sm);
  white-space: nowrap;
}
.q-text {
  display: block;
  margin-top: 8px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
@media (max-width: 1024px) {
  .recommend-empty {
    grid-template-columns: 1fr;
    gap: var(--app-space-4);
  }
}
@media (max-width: 768px) {
  .workspace-row {
    row-gap: 12px;
  }
  .layer-preview {
    grid-template-columns: 1fr;
  }
  .actions > .el-button {
    flex: 1 1 auto;
  }
  .q-text {
    font-size: var(--app-fs-md);
  }
}
</style>
