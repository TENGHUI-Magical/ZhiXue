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
      <template v-if="detail">
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

        <div class="actions">
          <el-button
            type="primary"
            :loading="answerLoading"
            :disabled="!canReveal"
            @click="reveal"
          >
            查看参考答案
          </el-button>
          <span v-if="!canReveal" class="hint">先作答或写下思路，再查看答案</span>
        </div>

        <div v-if="mode === 'plan'" class="actions plan-only">
          <el-button :loading="submitting" @click="recordOnly">仅记录进度（未作答）</el-button>
          <span class="hint">不计正确率、不入可信闭环</span>
        </div>

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
          <div class="actions">
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
          <div class="hint">
            {{ isObjective
              ? "客观题由服务端按标准答案判分，所选答案提交后不可自行改判。"
              : "主观题无法自动判分，提交后按自评计入；错因可不选，默认按“未归类”统计。" }}
          </div>
        </div>
      </template>
    </PageState>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import PageState from "./PageState.vue";
import {
  getQuestionDetail,
  getQuestionAnswer,
  type QuestionAnswer,
  type QuestionDetail
} from "../api/questions";
import { newClientToken, submitPractice } from "../api/diagnosis";
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

const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void;
  (e: "completed", payload: { isCorrect: boolean; practiceId?: number; verified?: boolean }): void;
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
const selectedOption = ref("");
const selectedOptions = ref<string[]>([]);
const myAnswer = ref("");
const revealed = ref(false);
/** 主观题自评错因；不选时后端落 UNCLASSIFIED，客观题由服务端规则推断 */
const selfErrorType = ref("");

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
  submitToken = "";
  submitTokenKey = "";
};

let loadSeq = 0;
let submitToken = "";
let submitTokenKey = "";
let openedAt = 0;

const onClosed = () => {
  // 关闭动画结束时可能已经切到下一题，只有确实关闭才清空
  if (!visible.value) reset();
};

const tokenForCurrentQuestion = () => {
  const key = `${props.qId}:${props.recommendationId ?? ""}`;
  if (submitTokenKey !== key || !submitToken) {
    submitTokenKey = key;
    submitToken = newClientToken();
  }
  return submitToken;
};

const load = async () => {
  const seq = ++loadSeq;
  clearFields();
  if (!props.qId) {
    error.value = "缺少题目编号";
    return;
  }
  loading.value = true;
  openedAt = Date.now();
  try {
    const data = await getQuestionDetail(props.qId);
    if (seq !== loadSeq) return; // 快速切题时丢弃旧响应
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
    answer.value = await getQuestionAnswer(props.qId);
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
        qId: props.qId,
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
      ElMessage.success(mode === "AUTO"
        ? (actual ? "已打卡：系统判分正确" : "已打卡：系统判分错误")
        : (actual ? "已打卡：自评正确" : "已打卡：自评错误"));
      emit("completed", { isCorrect: actual, practiceId: result.practiceId ?? undefined, verified: result.verified });
      visible.value = false;
      return;
    }
    const result = await submitPractice({
      qId: props.qId,
      kpId,
      isCorrect,
      myAnswer: combinedAnswer.value,
      errorType,
      source: props.recommendationId != null ? "RECOMMEND" : "SELF",
      recommendationId: props.recommendationId,
      // 同一题复用 token：超时/连点重试不会写出两条作答
      clientToken: tokenForCurrentQuestion()
    });
    submitToken = "";
    const actual = result.isCorrect ?? isCorrect ?? false;
    const mode = result.gradingMode ?? (isObjective.value ? "AUTO" : "SELF");
    ElMessage.success(result.duplicate
      ? "已提交过，返回首次结果"
      : (mode === "AUTO"
        ? (actual ? "系统判分：正确" : "系统判分：错误")
        : (actual ? "已记录自评：正确" : "已记录自评：错误")));
    emit("completed", { isCorrect: actual, practiceId: result.practiceId });
    visible.value = false;
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
      qId: props.qId,
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
  () => [props.modelValue, props.qId] as const,
  ([open]) => {
    if (open) load();
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
