<template>
  <el-drawer
    v-model="visible"
    title="题目练习"
    size="560px"
    destroy-on-close
    class="practice-drawer"
    append-to-body
    @closed="onClosed"
  >
    <PageState :loading="loading" :error="error" retry-text="重新加载" @retry="load">
      <div v-if="submissionResult" class="submission-result">
        <el-result
          :icon="submissionResult.isCorrect ? 'success' : 'error'"
          :title="submissionResult.message"
        />
        <div class="answer-compare">
          <div>
            <span>你的答案</span>
            <strong>{{ combinedAnswer || "未作答" }}</strong>
          </div>
          <div>
            <span>正确答案</span>
            <strong>{{ answer?.answerText || "暂无参考答案" }}</strong>
          </div>
        </div>
        <p class="result-reason">{{ reasonText }}</p>
        <p class="result-progress">{{ progressText }}</p>
      </div>
      <template v-else-if="detail">
        <div class="meta">
          <el-tag size="small">{{ detail.questionType || "题目" }}</el-tag>
          <el-tag v-if="detail.difficulty" size="small" type="info">
            难度 {{ detail.difficulty }}
          </el-tag>
          <span class="kp">{{ detail.kpId }} {{ detail.kpName || "" }}</span>
        </div>

        <div class="stem">{{ detail.questionText }}</div>

        <el-radio-group
          v-if="(detail.questionType === '单选' || isJudge) && detail.options?.length"
          v-model="selectedOption"
          class="options"
        >
          <el-radio
            v-for="option in detail.options"
            :key="option.key"
            :value="option.key"
            border
            class="option"
          >
            {{ option.key }}. {{ option.text }}
          </el-radio>
        </el-radio-group>

        <el-checkbox-group
          v-else-if="detail.questionType === '多选' && detail.options?.length"
          v-model="selectedOptions"
          class="options"
        >
          <el-checkbox
            v-for="option in detail.options"
            :key="option.key"
            :value="option.key"
            border
            class="option"
          >
            {{ option.key }}. {{ option.text }}
          </el-checkbox>
        </el-checkbox-group>

        <el-radio-group
          v-else-if="isJudge"
          v-model="selectedOption"
          class="options"
        >
          <el-radio value="对" border class="option">对</el-radio>
          <el-radio value="错" border class="option">错</el-radio>
        </el-radio-group>

        <el-input
          v-else
          v-model="myAnswer"
          type="textarea"
          :rows="6"
          maxlength="3000"
          show-word-limit
          placeholder="先完整写下你的答案或解题思路，再查看参考答案"
        />

        <el-alert
          v-if="!revealed"
          class="tip"
          type="info"
          :closable="false"
          show-icon
          title="先独立做一遍，再查看答案。推荐题的目标是掌握题型，不是背答案。"
        />

        <div v-if="revealed && answer" class="answer-panel">
          <el-divider content-position="left">参考答案</el-divider>
          <div class="answer">{{ answer.answerText || "暂无参考答案" }}</div>
          <div v-if="answer.analysisText" class="analysis">
            <strong>解析：</strong>{{ answer.analysisText }}
          </div>
          <el-divider content-position="left">{{ isObjective ? "系统判分" : "自评" }}</el-divider>
          <el-select
            v-if="!isObjective"
            v-model="selfErrorType"
            class="error-type"
            clearable
            placeholder="错因（可选，默认未归类）"
          >
            <el-option
              v-for="item in ERROR_TYPE_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
          <div class="hint">
            {{ isObjective
              ? "客观题由服务端按标准答案判分，所选答案提交后不可自行改判。"
              : "主观题无法自动判分，提交后按自评计入；错因可不选，默认按“未归类”统计。" }}
          </div>
        </div>
      </template>
    </PageState>
    <template #footer>
      <div v-if="submissionResult" class="actions drawer-footer-actions">
        <el-button
          type="primary"
          :disabled="mode !== 'plan' && !nextQuestion"
          @click="goNext"
        >
          下一题
        </el-button>
        <el-button @click="goExplain">查看本题讲解</el-button>
        <el-button text @click="visible = false">完成</el-button>
      </div>
      <div v-else-if="detail && !revealed" class="actions drawer-footer-actions">
        <el-button
          type="primary"
          :loading="answerLoading"
          :disabled="!canReveal"
          @click="reveal"
        >
          查看参考答案
        </el-button>
        <span v-if="!canReveal" class="hint">先作答或写下思路，再查看答案</span>
        <template v-if="mode === 'plan'">
          <el-button :loading="submitting" @click="recordOnly">仅记录进度（未作答）</el-button>
          <span class="hint">不计正确率、不入可信闭环</span>
        </template>
      </div>
      <div v-else-if="detail && revealed && answer" class="actions drawer-footer-actions">
        <el-button
          v-if="isObjective"
          type="primary"
          :loading="submitting"
          @click="submit()"
        >
          提交本题（系统判分）
        </el-button>
        <template v-else>
          <el-button type="success" :loading="submitting" @click="submit(true)">
            我答对了（自评）
          </el-button>
          <el-button type="warning" :loading="submitting" @click="submit(false)">
            我答错了（自评）
          </el-button>
        </template>
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import PageState from "./PageState.vue";
import {
  getQuestionDetail,
  getQuestionAnswer,
  type QuestionAnswer,
  type QuestionDetail
} from "../api/questions";
import {
  DEFAULT_COURSE_ID,
  getRecommendations,
  newClientToken,
  startPractice,
  submitPractice,
  type Recommendation
} from "../api/diagnosis";
import { checkinPlan } from "../api/plan";

interface Props {
  modelValue: boolean;
  qId: string;
  kpId: string;
  recommendationId?: number | null;
  /** practice=推荐/自主练习写入 /api/practices；plan=计划打卡写入 checkin */
  mode?: "practice" | "plan";
  planId?: number | null;
}

const props = withDefaults(defineProps<Props>(), {
  recommendationId: null,
  mode: "practice",
  planId: null
});

const router = useRouter();

const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void;
  (e: "completed", payload: { isCorrect: boolean; practiceId?: number; verified?: boolean }): void;
  (e: "next"): void;
}>();

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit("update:modelValue", value)
});

const loading = ref(false);
const answerLoading = ref(false);
const submitting = ref(false);
const error = ref("");
const detail = ref<QuestionDetail | null>(null);
const answer = ref<QuestionAnswer | null>(null);
const currentQId = ref(props.qId);
const currentRecommendationId = ref<number | null>(props.recommendationId ?? null);
const nextQuestion = ref<Recommendation | null>(null);
const nextLoading = ref(false);
const timingUnavailable = ref(false);
const selectedOption = ref("");
const selectedOptions = ref<string[]>([]);
const myAnswer = ref("");
const revealed = ref(false);
/** 主观题自评错因；不选时后端落 UNCLASSIFIED，客观题由服务端规则推断 */
const selfErrorType = ref("");
const completedResult = ref<{
  isCorrect: boolean;
  practiceId?: number;
  verified?: boolean;
} | null>(null);
const submissionResult = ref<{
  isCorrect: boolean;
  practiceId?: number;
  verified?: boolean;
  message: string;
  errorType?: string | null;
  costSeconds: number | null;
} | null>(null);

const ERROR_TYPE_OPTIONS = [
  { value: "CONCEPT_CONFUSION", label: "概念混淆" },
  { value: "METHOD_MISSING", label: "方法缺失" },
  { value: "STEP_OMISSION", label: "步骤遗漏" },
  { value: "CALCULATION_ERROR", label: "计算错误" },
  { value: "BOUNDARY_ERROR", label: "边界条件" },
  { value: "UNCLASSIFIED", label: "暂不确定（未归类）" }
];

const isJudge = computed(() =>
  ["判断", "判断题"].includes(detail.value?.questionType ?? ""));

const canReveal = computed(() => {
  if (!detail.value) return false;
  if (detail.value.questionType === "单选" || isJudge.value) return !!selectedOption.value;
  if (detail.value.questionType === "多选") return selectedOptions.value.length > 0;
  return myAnswer.value.trim().length > 0;
});

const combinedAnswer = computed(() => {
  if (detail.value?.questionType === "单选" || isJudge.value) return selectedOption.value;
  if (detail.value?.questionType === "多选") return selectedOptions.value.join(",");
  return myAnswer.value.trim();
});

/** 单选/多选/判断由服务端判分；其余题型保留自评 */
const isObjective = computed(() =>
  ["单选", "多选", "判断", "判断题"].includes(detail.value?.questionType ?? ""));

const reasonText = computed(() => {
  const result = submissionResult.value;
  if (!result) return "";
  if (result.isCorrect) return "本次作答正确";
  const reason = ERROR_TYPE_OPTIONS.find((item) => item.value === result.errorType)?.label;
  return `错因：${reason || "待接口返回"}`;
});

const progressText = computed(() => {
  const result = submissionResult.value;
  if (!result || result.costSeconds == null) {
    return timingUnavailable.value ? "本次作答已记录（服务端耗时不可用）" : "本次作答已记录";
  }
  return `本次用时 ${result.costSeconds} 秒（服务端计时）`;
});

const reset = () => {
  loadSeq += 1;
  clearFields();
};

const clearFields = () => {
  loading.value = false;
  answerLoading.value = false;
  submitting.value = false;
  error.value = "";
  detail.value = null;
  answer.value = null;
  selectedOption.value = "";
  selectedOptions.value = [];
  myAnswer.value = "";
  revealed.value = false;
  selfErrorType.value = "";
  submissionResult.value = null;
  nextQuestion.value = null;
  nextLoading.value = false;
  timingUnavailable.value = false;
  submitToken = "";
  submitTokenKey = "";
};

let loadSeq = 0;
let submitToken = "";
let submitTokenKey = "";
let openedAt = 0;

const onClosed = () => {
  // 提交后由学生主动关闭时再通知父页刷新，避免结果与讲解入口被提前卸载。
  if (!visible.value) {
    const completed = completedResult.value;
    completedResult.value = null;
    if (completed) {
      emit("completed", {
        isCorrect: completed.isCorrect,
        practiceId: completed.practiceId,
        verified: completed.verified
      });
    }
    reset();
  }
};

const goExplain = async () => {
  const kpId = detail.value?.kpId || props.kpId;
  if (!currentQId.value || !kpId) return;
  await router.push({
    path: "/diagnosis",
    query: { qId: currentQId.value, kpId, autoExplain: "1" }
  });
  visible.value = false;
};

const prepareNext = async (kpId: string) => {
  if (props.mode === "plan") return;
  nextLoading.value = true;
  try {
    const recommendations = await getRecommendations({ kpId, courseId: DEFAULT_COURSE_ID });
    nextQuestion.value = recommendations.find((item) => item.qId !== currentQId.value) ?? null;
  } catch {
    nextQuestion.value = null;
  } finally {
    nextLoading.value = false;
  }
};

const goNext = async () => {
  if (props.mode === "plan") {
    emit("next");
    return;
  }
  const next = nextQuestion.value;
  if (!next) return;
  currentQId.value = next.qId;
  currentRecommendationId.value = next.recommendationId ?? null;
  reset();
  await load();
};

const tokenForCurrentQuestion = () => {
  const key = `${currentQId.value}:${currentRecommendationId.value ?? ""}`;
  if (submitTokenKey !== key || !submitToken) {
    submitTokenKey = key;
    submitToken = newClientToken();
  }
  return submitToken;
};

const load = async () => {
  const seq = ++loadSeq;
  clearFields();
  if (!currentQId.value) {
    error.value = "缺少题目编号";
    return;
  }
  loading.value = true;
  openedAt = Date.now();
  try {
    const data = await getQuestionDetail(currentQId.value);
    if (seq !== loadSeq) return; // 快速切题时丢弃旧响应
    if (props.mode !== "plan") {
      try {
        await startPractice({ qId: currentQId.value, clientToken: tokenForCurrentQuestion() });
      } catch {
        // 打点不可用不阻断作答，结果态只显示服务端耗时不可用，不用前端秒表顶替。
        timingUnavailable.value = true;
      }
    }
    if (seq !== loadSeq) return;
    detail.value = data;
  } catch (e) {
    if (seq !== loadSeq) return;
    error.value = (e as Error).message || "题目加载失败";
  } finally {
    if (seq === loadSeq) loading.value = false;
  }
};

const reveal = async () => {
  if (!canReveal.value) return;
  answerLoading.value = true;
  try {
    answer.value = await getQuestionAnswer(currentQId.value);
    revealed.value = true;
  } catch (e) {
    ElMessage.error((e as Error).message || "参考答案加载失败");
  } finally {
    answerLoading.value = false;
  }
};

const submit = async (isCorrect?: boolean) => {
  if (!detail.value || submitting.value) return;
  const kpId = detail.value.kpId || props.kpId;
  if (!kpId) {
    ElMessage.error("题目缺少知识点归属，无法提交");
    return;
  }
  submitting.value = true;
  // 主观题自评错误时才提交错因；客观题由服务端按规则推断，前端不传
  const errorType = isCorrect === false && !isObjective.value
    ? (selfErrorType.value || null)
    : null;
  try {
    // 计划模式：打卡走 checkin（answered=true）；客观题由服务端判分，主观题自评
    if (props.mode === "plan" && props.planId != null) {
      const costSeconds = openedAt ? Math.max(0, Math.round((Date.now() - openedAt) / 1000)) : null;
      const result = await checkinPlan(props.planId, {
        qId: currentQId.value,
        clientToken: tokenForCurrentQuestion(),
        answered: true,
        myAnswer: combinedAnswer.value,
        isCorrect,
        errorType,
        costSeconds
      });
      submitToken = "";
      const actual = result.isCorrect ?? isCorrect ?? false;
      const mode = result.gradingMode ?? (isObjective.value ? "AUTO" : "SELF");
      const message = mode === "AUTO"
        ? (actual ? "已打卡：系统判分正确" : "已打卡：系统判分错误")
        : (actual ? "已打卡：自评正确" : "已打卡：自评错误");
      ElMessage.success(message);
      completedResult.value = {
        isCorrect: actual,
        practiceId: result.practiceId ?? undefined,
        verified: result.verified
      };
      submissionResult.value = {
        isCorrect: actual,
        practiceId: result.practiceId ?? undefined,
        verified: result.verified,
        message,
        errorType,
        // 计划打卡契约暂未返回服务端耗时，结果态不展示前端自算值。
        costSeconds: null
      };
      return;
    }
    const result = await submitPractice({
      qId: currentQId.value,
      kpId,
      isCorrect,
      myAnswer: combinedAnswer.value,
      errorType,
      startToken: tokenForCurrentQuestion(),
      source: currentRecommendationId.value != null ? "RECOMMEND" : "SELF",
      recommendationId: currentRecommendationId.value,
      // 同一题复用 token：超时/连点重试不会写出两条作答
      clientToken: tokenForCurrentQuestion()
    });
    submitToken = "";
    const actual = result.isCorrect ?? isCorrect ?? false;
    const mode = result.gradingMode ?? (isObjective.value ? "AUTO" : "SELF");
    const message = result.duplicate
      ? "已提交过，返回首次结果"
      : (mode === "AUTO"
        ? (actual ? "系统判分：正确" : "系统判分：错误")
        : (actual ? "已记录自评：正确" : "已记录自评：错误"));
    ElMessage.success(message);
    completedResult.value = { isCorrect: actual, practiceId: result.practiceId };
    submissionResult.value = {
      isCorrect: actual,
      practiceId: result.practiceId,
      message,
      errorType,
      costSeconds: result.costSeconds ?? null
    };
    void prepareNext(kpId);
  } catch (e) {
    ElMessage.error((e as Error).message || "提交失败");
  } finally {
    submitting.value = false;
  }
};

/** 仅记录进度（计划模式）：不写 practice_record，不计正确率/不入可信闭环 */
const recordOnly = async () => {
  if (submitting.value) return;
  if (props.mode !== "plan" || props.planId == null) return;
  try {
    await ElMessageBox.confirm(
      "未作答仅记录进度：不计正确率、不计入可信闭环。确认？",
      "仅记录进度",
      { type: "warning", confirmButtonText: "仅记录", cancelButtonText: "去作答" }
    );
  } catch {
    return;
  }
  submitting.value = true;
  try {
    await checkinPlan(props.planId, {
      qId: currentQId.value,
      clientToken: newClientToken(),
      answered: false
    });
    ElMessage.success("已记录进度（未验证）");
    emit("completed", { isCorrect: false, verified: false });
    visible.value = false;
  } catch (e) {
    ElMessage.error((e as Error).message || "记录失败");
  } finally {
    submitting.value = false;
  }
};

watch(
  () => [props.modelValue, props.qId, props.recommendationId] as const,
  ([open]) => {
    if (open) {
      currentQId.value = props.qId;
      currentRecommendationId.value = props.recommendationId ?? null;
      load();
    }
  },
  { immediate: true }
);
</script>

<style scoped>
.meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.kp {
  color: var(--app-text-muted);
  font-size: var(--app-fs-sm);
}
.stem {
  font-size: var(--app-fs-md);
  line-height: 1.7;
  color: var(--app-text);
  margin-bottom: 16px;
  white-space: pre-wrap;
}
.options {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 10px;
  margin-bottom: 16px;
}
.option {
  margin-right: 0;
  width: 100%;
  height: auto;
  min-height: 44px;
  padding: 10px 12px;
}
.tip {
  margin-top: 12px;
}
.actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 16px;
}
.error-type {
  width: 240px;
  max-width: 100%;
  margin-top: 8px;
}
.hint {
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
}
.submission-result {
  display: grid;
  place-items: center;
  min-height: 320px;
}
.answer-compare {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  width: 100%;
  margin-bottom: 12px;
}
.answer-compare > div {
  min-width: 0;
  padding: 12px;
  border-radius: var(--app-radius-sm);
  background: var(--app-surface-muted);
}
.answer-compare span,
.answer-compare strong {
  display: block;
}
.answer-compare span {
  margin-bottom: 4px;
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
}
.answer-compare strong {
  overflow-wrap: anywhere;
}
.result-reason,
.result-progress {
  margin: 4px 0;
  text-align: center;
}
.result-progress {
  color: var(--app-text-secondary);
  font-size: var(--app-fs-sm);
}
.drawer-footer-actions {
  width: 100%;
  margin-top: 0;
}
:deep(.el-drawer__footer) {
  padding: 12px 20px;
  border-top: 1px solid var(--app-border);
  background: var(--app-surface);
}
.answer-panel {
  margin-top: 8px;
}
.answer {
  font-size: var(--app-fs-md);
  line-height: 1.7;
  color: var(--app-text);
  white-space: pre-wrap;
  background: var(--app-surface-muted);
  border-radius: var(--app-radius-sm);
  padding: 12px;
}
.analysis {
  margin-top: 12px;
  line-height: 1.7;
  color: var(--app-text-secondary);
  white-space: pre-wrap;
}
:deep(.el-drawer) {
  max-width: 92vw;
}
@media (max-width: 768px) {
  :deep(.el-drawer) {
    width: 92vw !important;
  }
}
</style>
