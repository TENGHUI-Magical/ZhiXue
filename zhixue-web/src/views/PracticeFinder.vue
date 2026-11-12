<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>找题练习</h2>
        <p class="sub">输入知识点名称或编号，模糊搜索后选题开练。</p>
      </div>
    </div>

    <el-card class="finder-card">
      <el-input
        v-model="keyword"
        class="finder-input"
        placeholder="例如：连接、关系模型、KP0304"
        aria-label="搜索知识点"
        clearable
      />

      <div v-if="loadingMeta" class="finder-state">加载知识点中…</div>
      <el-alert v-else-if="metaError" :title="metaError" type="error" :closable="false" show-icon />
      <template v-else>
        <div v-if="!keyword.trim()" class="finder-state">
          <p class="state-title">输入关键词开始搜索；也可以直接选一个常见知识点。</p>
          <div class="chip-row">
            <button v-for="word in SUGGESTIONS" :key="word" type="button" class="chip" @click="keyword = word">
              {{ word }}
            </button>
          </div>
        </div>

        <div v-else-if="!matched.length" class="finder-state finder-empty">
          <p class="state-title">没有找到匹配的知识点</p>
          <p class="state-note">换个词试试：用更短的词或教材用语，例如“连接”“范式”。</p>
          <div class="chip-row">
            <button v-for="word in SUGGESTIONS" :key="word" type="button" class="chip" @click="keyword = word">
              {{ word }}
            </button>
          </div>
        </div>

        <div v-else class="kp-results">
          <button
            v-for="item in matched"
            :key="item.id"
            type="button"
            :class="['kp-result', { active: item.id === selectedKpId }]"
            @click="selectKp(item.id)"
          >
            <span class="kp-name">{{ item.id }} {{ item.name }}</span>
            <span class="kp-meta-text">{{ chapterNoLabel(item.chapterId) }}</span>
            <span class="kp-meta-text">{{ countText(item.id) }}</span>
            <span class="kp-meta-text">{{ difficultyText(item.id) }}</span>
          </button>
        </div>

        <div v-if="selectedKpId" class="question-panel">
          <h3 class="panel-title">{{ selectedKpId }} 的题目</h3>
          <div v-if="loadingQuestions" class="finder-state">加载题目中…</div>
          <div v-else-if="questionError" class="finder-state">
            <p class="state-title">题目加载失败</p>
            <p class="state-note">{{ questionError }}</p>
            <el-button size="small" type="primary" @click="retryQuestions">重试</el-button>
          </div>
          <div v-else-if="!selectedQuestions.length" class="finder-state">
            该知识点暂无题目，换一个知识点试试。
          </div>
          <div v-else class="question-list">
            <button
              v-for="question in selectedQuestions"
              :key="question.qId"
              type="button"
              :class="['question-row', layerClass(question)]"
              @click="startPractice(question)"
            >
              <span class="q-type">{{ question.questionType || "题目" }}</span>
              <span v-if="layerOf(question)" class="q-layer" :class="layerClass(question)">{{ layerLabel(question) }}</span>
              <span class="q-diff">难度 {{ question.difficulty ?? "-" }}</span>
              <span class="q-text">{{ question.questionText }}</span>
            </button>
          </div>
        </div>
      </template>
    </el-card>

    <QuestionPracticeDrawer
      v-model="practiceVisible"
      :q-id="practiceQId"
      :kp-id="selectedKpId"
      @completed="practiceVisible = false"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import QuestionPracticeDrawer from "../components/QuestionPracticeDrawer.vue";
import {
  getKnowledgeGraph,
  listChapters,
  listCourses,
  listQuestionsByKp,
  type Chapter,
  type GraphNode,
  type QuestionBrief
} from "../api/course";

const SUGGESTIONS = ["SQL", "连接", "关系模型", "范式", "视图"];

/** 题目卡片的三层表达：题目接口只给 difficulty（1—3），按难度映射到项目既有的
 *  CONSOLIDATE / VARIANT / ADVANCE 语义，仅用于左边框与标签的视觉分层，不参与筛选或业务判断。 */
type PracticeLayer = "CONSOLIDATE" | "VARIANT" | "ADVANCE";
const LAYER_LABELS: Record<PracticeLayer, string> = {
  CONSOLIDATE: "同点巩固",
  VARIANT: "变式迁移",
  ADVANCE: "进阶挑战"
};
const layerOf = (question: QuestionBrief): PracticeLayer | null => {
  const level = question.difficulty;
  if (typeof level !== "number") return null;
  if (level <= 1) return "CONSOLIDATE";
  if (level === 2) return "VARIANT";
  return "ADVANCE";
};
const layerClass = (question: QuestionBrief) => {
  const layer = layerOf(question);
  return layer ? `q-layer--${layer.toLowerCase()}` : "";
};
const layerLabel = (question: QuestionBrief) => {
  const layer = layerOf(question);
  return layer ? LAYER_LABELS[layer] : "";
};

const keyword = ref("");
const kps = ref<GraphNode[]>([]);
const chapters = ref<Chapter[]>([]);
const loadingMeta = ref(false);
const metaError = ref("");
const questionsByKp = ref<Map<string, QuestionBrief[]>>(new Map());
const questionErrors = ref<Record<string, string>>({});
const selectedKpId = ref("");
const practiceVisible = ref(false);
const practiceQId = ref("");
const inFlight = new Set<string>();

const chapterMeta = computed(() => {
  const map = new Map<number, Chapter>();
  for (const chapter of chapters.value) {
    if (typeof chapter.chapterId === "number") map.set(chapter.chapterId, chapter);
  }
  return map;
});

const chapterNoLabel = (chapterId?: number) => {
  if (chapterId == null) return "章节待定";
  const meta = chapterMeta.value.get(chapterId);
  return meta?.chapterNo != null ? `第 ${meta.chapterNo} 章` : "章节待定";
};

const matched = computed(() => {
  const tokens = keyword.value.trim().toLowerCase().split(/\s+/).filter(Boolean);
  if (!tokens.length) return [];
  return kps.value
    .filter((node) => !!node.id)
    .filter((node) => {
      const haystack = `${node.id ?? ""} ${node.name ?? ""}`.toLowerCase();
      return tokens.every((token) => haystack.includes(token));
    })
    .slice(0, 20);
});

const matchedKey = computed(() => matched.value.map((node) => node.id).join("|"));

const fetchQuestions = (ids: string[]) => {
  for (const id of ids.slice(0, 8)) {
    if (!id || questionsByKp.value.has(id) || inFlight.has(id)) continue;
    inFlight.add(id);
    listQuestionsByKp(id, 50)
      .then((list) => {
        questionsByKp.value.set(id, list ?? []);
        delete questionErrors.value[id];
      })
      .catch((e) => {
        // 记住失败原因，避免把“请求失败”展示成“该知识点暂无题目”
        questionErrors.value[id] = (e as Error)?.message || "题目加载失败";
        questionsByKp.value.set(id, []);
      })
      .finally(() => {
        inFlight.delete(id);
      });
  }
};

const retryQuestions = () => {
  const id = selectedKpId.value;
  if (!id) return;
  questionsByKp.value.delete(id);
  delete questionErrors.value[id];
  fetchQuestions([id]);
};

watch(matchedKey, (key) => {
  fetchQuestions(key ? key.split("|") : []);
});

const selectedQuestions = computed(() => questionsByKp.value.get(selectedKpId.value) ?? []);
const loadingQuestions = computed(() => !!selectedKpId.value && !questionsByKp.value.has(selectedKpId.value));
const questionError = computed(() => questionErrors.value[selectedKpId.value] ?? "");

const countText = (id?: string) => {
  if (!id) return "题量 -";
  const list = questionsByKp.value.get(id);
  return list ? `题量 ${list.length}` : "题量 …";
};

const difficultyText = (id?: string) => {
  if (!id) return "难度 -";
  const list = questionsByKp.value.get(id);
  if (!list) return "难度 …";
  const levels = list
    .map((question) => question.difficulty)
    .filter((level): level is number => typeof level === "number");
  if (!levels.length) return "难度 -";
  const min = Math.min(...levels);
  const max = Math.max(...levels);
  return min === max ? `难度 ${min}` : `难度 ${min}—${max}`;
};

const selectKp = (id?: string) => {
  if (!id) return;
  selectedKpId.value = id;
  fetchQuestions([id]);
};

const startPractice = (question: QuestionBrief) => {
  if (!question.qId) return;
  practiceQId.value = question.qId;
  practiceVisible.value = true;
};

const load = async () => {
  loadingMeta.value = true;
  metaError.value = "";
  try {
    const courses = await listCourses();
    const courseId = courses[0]?.courseId ?? 1;
    const [graph, chapterList] = await Promise.all([
      getKnowledgeGraph(courseId),
      listChapters(courseId).catch(() => [] as Chapter[])
    ]);
    kps.value = graph.nodes ?? [];
    chapters.value = chapterList;
  } catch (e) {
    metaError.value = (e as Error).message || "知识点加载失败";
  } finally {
    loadingMeta.value = false;
  }
};

onMounted(load);
</script>

<style scoped>
.page {
  max-width: var(--app-page-max);
}
.page-head h2 {
  margin: 0;
}
.sub {
  margin: 6px 0 0;
  color: var(--app-text-secondary);
  font-size: var(--app-fs-sm);
}
/* C 阅读承载面：搜索结果与题干列表是阅读主载体，近实底 + 发丝边，外层 24px */
.el-card.finder-card {
  margin-top: 16px;
  border: 1px solid var(--app-hairline);
  border-radius: var(--app-radius-2xl);
  background: color-mix(in srgb, var(--app-surface) 97%, transparent);
  box-shadow: var(--app-shadow-sm);
}
.finder-input {
  max-width: 560px;
}
.finder-input :deep(.el-input__wrapper) {
  min-height: 44px;
  border-radius: var(--app-radius-md);
}
.finder-input :deep(.el-input__inner) {
  min-height: 44px;
}
.finder-state {
  padding: 12px 2px 4px;
  color: var(--app-text-secondary);
  font-size: var(--app-fs-sm);
  line-height: 1.6;
}
.state-title {
  margin: 0;
  color: var(--app-text);
  font-weight: 600;
}
.finder-empty .state-title {
  font-weight: 600;
}
.state-note {
  margin: 4px 0 0;
}
.chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
.chip {
  min-height: 44px;
  padding: 0 14px;
  border: 1px solid var(--app-hairline);
  border-radius: var(--app-radius-pill);
  background: var(--app-surface-muted);
  color: var(--app-text-secondary);
  font-size: var(--app-fs-sm);
  cursor: pointer;
  transition: transform var(--app-dur-fast) var(--app-ease-spring),
    background-color var(--app-dur-fast) var(--app-ease-standard),
    border-color var(--app-dur-fast) var(--app-ease-standard),
    color var(--app-dur-fast) var(--app-ease-standard);
}
.chip:hover {
  color: var(--app-primary);
  border-color: color-mix(in srgb, var(--app-primary) 40%, var(--app-border));
  background: var(--app-surface);
}
.chip:active {
  transform: scale(0.97);
}
.kp-results {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 14px;
}
/* 子卡：18px 同心圆角，与 24px 外层形成层次 */
.kp-result {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
  align-items: center;
  min-height: 44px;
  padding: 10px 14px;
  border: 1px solid var(--app-hairline);
  border-radius: var(--app-radius-inner);
  background: var(--app-surface);
  color: var(--app-text);
  font-size: var(--app-fs-sm);
  text-align: left;
  cursor: pointer;
  transition: transform var(--app-dur-fast) var(--app-ease-spring),
    background-color var(--app-dur-fast) var(--app-ease-standard),
    border-color var(--app-dur-fast) var(--app-ease-standard),
    box-shadow var(--app-dur-fast) var(--app-ease-standard);
}
.kp-result:hover,
.kp-result.active {
  border-color: color-mix(in srgb, var(--app-primary) 40%, var(--app-border));
}
.kp-result:hover {
  background: color-mix(in srgb, var(--app-surface-muted) 70%, var(--app-surface));
}
.kp-result.active {
  background: var(--app-primary-soft);
  box-shadow: inset 3px 0 0 var(--app-primary);
}
.kp-result:active {
  transform: scale(0.97);
}
.kp-name {
  flex: 1 1 240px;
  min-width: 0;
  font-weight: 600;
  overflow-wrap: anywhere;
}
.kp-meta-text {
  color: var(--app-text-secondary);
  font-size: var(--app-fs-xs);
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.question-panel {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--app-hairline);
}
.panel-title {
  margin: 0 0 10px;
  font-size: var(--app-fs-lg);
}
.question-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.question-row {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  width: 100%;
  min-height: 44px;
  padding: 10px 12px;
  border: 1px solid var(--app-hairline);
  /* 左边框承担分层语义：低饱和语义色，不用霓虹 */
  border-left: 3px solid var(--app-border-strong);
  border-radius: var(--app-radius-inner);
  background: var(--app-surface);
  color: var(--app-text);
  font-size: var(--app-fs-sm);
  text-align: left;
  cursor: pointer;
  transition: transform var(--app-dur-fast) var(--app-ease-spring),
    background-color var(--app-dur-fast) var(--app-ease-standard),
    border-color var(--app-dur-fast) var(--app-ease-standard),
    box-shadow var(--app-dur-fast) var(--app-ease-standard);
}
.question-row:hover {
  border-color: color-mix(in srgb, var(--app-primary) 40%, var(--app-border));
  box-shadow: var(--app-shadow-sm);
}
.question-row:active {
  transform: scale(0.97);
}
.question-row.q-layer--consolidate {
  border-left-color: color-mix(in srgb, var(--app-success) 60%, var(--app-border));
  background: color-mix(in srgb, var(--app-success) 5%, var(--app-surface));
}
.question-row.q-layer--variant {
  border-left-color: color-mix(in srgb, var(--app-primary) 60%, var(--app-border));
  background: color-mix(in srgb, var(--app-primary) 5%, var(--app-surface));
}
.question-row.q-layer--advance {
  border-left-color: color-mix(in srgb, var(--app-warning) 60%, var(--app-border));
  background: color-mix(in srgb, var(--app-warning) 6%, var(--app-surface));
}
.q-type,
.q-diff {
  flex: 0 0 auto;
  padding: 2px 8px;
  border: 1px solid var(--app-hairline);
  border-radius: var(--app-radius-pill);
  color: var(--app-text-secondary);
  font-size: var(--app-fs-xs);
}
.q-layer {
  flex: 0 0 auto;
  padding: 2px 8px;
  border-radius: var(--app-radius-pill);
  font-size: var(--app-fs-xs);
  font-weight: 600;
}
.q-layer--consolidate {
  color: color-mix(in srgb, var(--app-success) 82%, var(--app-text));
  background: color-mix(in srgb, var(--app-success) 12%, var(--app-surface));
}
.q-layer--variant {
  color: color-mix(in srgb, var(--app-primary) 82%, var(--app-text));
  background: color-mix(in srgb, var(--app-primary) 12%, var(--app-surface));
}
.q-layer--advance {
  color: color-mix(in srgb, var(--app-warning) 82%, var(--app-text));
  background: color-mix(in srgb, var(--app-warning) 14%, var(--app-surface));
}
.q-text {
  flex: 1 1 260px;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
@media (hover: hover) and (pointer: fine) {
  .chip:hover,
  .kp-result:hover,
  .question-row:hover {
    transform: translateY(-1px);
  }
}
@media (max-width: 768px) {
  .finder-input {
    max-width: none;
  }
  /* iOS 聚焦自动放大：输入字号 ≥16px */
  .finder-input :deep(.el-input__inner) {
    font-size: 16px;
  }
  .question-row {
    flex-wrap: wrap;
  }
  .q-text {
    flex: 1 1 100%;
    white-space: normal;
    overflow-wrap: anywhere;
  }
}
</style>
