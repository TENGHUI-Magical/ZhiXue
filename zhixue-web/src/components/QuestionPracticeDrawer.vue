<template>
  <el-drawer
    v-model="visible"
    title="题目练习"
    size="560px"
    destroy-on-close
    class="practice-drawer"
    @closed="reset"
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
          v-if="detail.questionType === '单选' && detail.options?.length"
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

        <div v-if="revealed && answer" class="answer-panel">
          <el-divider content-position="left">参考答案</el-divider>
          <div class="answer">{{ answer.answerText || "暂无参考答案" }}</div>
          <div v-if="answer.analysisText" class="analysis">
            <strong>解析：</strong>{{ answer.analysisText }}
          </div>
          <el-divider content-position="left">自评</el-divider>
          <div class="actions">
            <el-button type="success" :loading="submitting" @click="submit(true)">
              我答对了
            </el-button>
            <el-button type="warning" :loading="submitting" @click="submit(false)">
              我答错了
            </el-button>
          </div>
          <div class="hint">自评会写入练习记录；推荐题会标记为已采纳。</div>
        </div>
      </template>
    </PageState>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import PageState from "./PageState.vue";
import {
  getQuestionDetail,
  getQuestionAnswer,
  type QuestionAnswer,
  type QuestionDetail
} from "../api/questions";
import { newClientToken, submitPractice } from "../api/diagnosis";

interface Props {
  modelValue: boolean;
  qId: string;
  kpId: string;
  recommendationId?: number | null;
}

const props = withDefaults(defineProps<Props>(), {
  recommendationId: null
});

const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void;
  (e: "completed", payload: { isCorrect: boolean; practiceId?: number }): void;
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

const canReveal = computed(() => {
  if (!detail.value) return false;
  if (detail.value.questionType === "单选") return !!selectedOption.value;
  if (detail.value.questionType === "多选") return selectedOptions.value.length > 0;
  return myAnswer.value.trim().length > 0;
});

const combinedAnswer = computed(() => {
  if (detail.value?.questionType === "单选") return selectedOption.value;
  if (detail.value?.questionType === "多选") return selectedOptions.value.join(",");
  return myAnswer.value.trim();
});

const reset = () => {
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
};

const load = async () => {
  reset();
  if (!props.qId) {
    error.value = "缺少题目编号";
    return;
  }
  loading.value = true;
  try {
    detail.value = await getQuestionDetail(props.qId);
  } catch (e) {
    error.value = (e as Error).message || "题目加载失败";
  } finally {
    loading.value = false;
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

const submit = async (isCorrect: boolean) => {
  if (!detail.value) return;
  const kpId = detail.value.kpId || props.kpId;
  if (!kpId) {
    ElMessage.error("题目缺少知识点归属，无法提交");
    return;
  }
  submitting.value = true;
  try {
    const result = await submitPractice({
      qId: props.qId,
      kpId,
      isCorrect,
      myAnswer: combinedAnswer.value,
      errorType: null,
      source: props.recommendationId != null ? "RECOMMEND" : "SELF",
      recommendationId: props.recommendationId,
      clientToken: newClientToken()
    });
    ElMessage.success(result.duplicate ? "已提交过，返回首次结果" : "已记录作答");
    emit("completed", { isCorrect, practiceId: result.practiceId });
    visible.value = false;
  } catch (e) {
    ElMessage.error((e as Error).message || "提交失败");
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
  font-size: 13px;
}
.stem {
  font-size: 16px;
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
.hint {
  color: var(--app-text-muted);
  font-size: 12px;
}
.answer-panel {
  margin-top: 8px;
}
.answer {
  font-size: 15px;
  line-height: 1.7;
  color: var(--app-text);
  white-space: pre-wrap;
  background: var(--app-surface-muted);
  border-radius: 10px;
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
