<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>今日工作台</h2>
        <p class="sub">{{ todayLabel }} · 先做最值得的一件事，其余按节奏来。</p>
      </div>
      <el-button :loading="loading" @click="loadAll">刷新</el-button>
    </div>

    <el-alert
      v-if="hasPartialError && !loading"
      class="partial-alert"
      type="warning"
      :closable="false"
      show-icon
      :title="partialErrorText"
    />

    <el-skeleton v-if="loading" :rows="6" animated />

    <template v-else>
      <div v-if="fatalError" class="fatal-state">
        <el-alert
          type="error"
          :closable="false"
          show-icon
          :title="`今日建议加载失败：${planError}；${portraitError}`"
        />
        <el-button type="primary" @click="loadAll">重试</el-button>
      </div>

      <!-- 首次使用：三步启动 -->
      <el-card v-else-if="isFirstUse" class="hero-card" shadow="never">
        <div class="hero-eyebrow">开始使用</div>
        <h3 class="hero-title">还没有学习数据？用三步跑通第一条闭环</h3>
        <p class="hero-desc">上传一道错题，生成本周计划，完成第一次练习。之后这里会每天告诉你最值得做什么。</p>
        <div class="start-grid">
          <div class="start-step">
            <span class="step-no">1</span>
            <div>
              <strong>上传第一道错题</strong>
              <p>拍照或粘贴题干，系统会匹配知识点并生成讲解。</p>
            </div>
            <el-button type="primary" @click="router.push('/diagnosis')">去上传</el-button>
          </div>
          <div class="start-step">
            <span class="step-no">2</span>
            <div>
              <strong>生成本周计划</strong>
              <p>按你的薄弱点安排每天 3 题，完成即可打卡。</p>
            </div>
            <el-button @click="router.push('/plan')">去生成</el-button>
          </div>
          <div class="start-step">
            <span class="step-no">3</span>
            <div>
              <strong>完成第一次练习</strong>
              <p>练习、看解析、回写画像，证据从第一条记录开始。</p>
            </div>
            <el-button @click="router.push('/plan')">去练习</el-button>
          </div>
        </div>
      </el-card>

      <template v-else>
        <!-- 今日建议卡：结论 + 本周进度环 -->
        <el-card class="hero-card" shadow="never">
          <div class="hero-main">
            <div class="hero-copy">
              <div class="hero-eyebrow">今日建议</div>
              <h3 class="hero-title">{{ conclusion }}</h3>
              <div class="hero-evidence">
                <span v-for="item in evidence" :key="item" class="evidence-chip">{{ item }}</span>
              </div>
              <div class="hero-actions">
                <el-button v-if="primary" type="primary" @click="handlePrimary">
                  {{ primary.label }}
                </el-button>
                <el-button v-if="overdueCount > 0" @click="router.push('/plan')">查看计划</el-button>
                <el-button v-else @click="router.push('/portrait')">查看画像</el-button>
              </div>
            </div>
            <div class="hero-focus">
              <div
                class="progress-ring"
                :style="{ '--pct': weekPct }"
                role="img"
                :aria-label="`本周完成 ${weekPct}%`"
              >
                <div class="progress-ring-inner">
                  <strong>{{ weekTotal ? weekPct + "%" : "—" }}</strong>
                  <span>{{ weekTotal ? "本周完成" : "暂无计划" }}</span>
                </div>
              </div>
              <p class="focus-caption">
                {{ weekTotal ? `${weekDone} / ${weekTotal} 题` : "去计划页生成" }}
              </p>
            </div>
          </div>
        </el-card>

        <el-card class="metric-band" shadow="never">
          <div class="metric-grid">
            <div class="metric-card">
              <div class="metric-label">今日待完成</div>
              <div class="metric-value">{{ todayRemaining }} <small>题</small></div>
              <p class="metric-hint">
                {{ todayPlan ? (todayRemaining > 0 ? "完成后可打卡" : "今天的题已完成") : "今天暂无计划" }}
              </p>
              <button type="button" class="metric-link" @click="router.push('/plan')">去看计划</button>
            </div>
            <div class="metric-card">
              <div class="metric-label">训练样本</div>
              <div class="metric-value">{{ sampleCount }} <small>次</small></div>
              <p class="metric-hint">画像数据 {{ snapshotDate || "暂无" }}</p>
              <button type="button" class="metric-link" @click="router.push('/portrait')">查看画像</button>
            </div>
            <div class="metric-card">
              <div class="metric-label">最该补</div>
              <div class="metric-value small">{{ weakest ? weakest.name || weakest.kpId : "暂无" }}</div>
              <p class="metric-hint">
                {{ weakest ? `掌握度 ${pct(weakest.mastery)} · 样本 ${weakest.sampleCount ?? 0}` : "样本不足时不排名" }}
              </p>
              <button type="button" class="metric-link" @click="router.push('/knowledge-graph')">看知识图谱</button>
            </div>
          </div>
        </el-card>

        <!-- 推荐练习 -->
        <el-card v-if="recommendations.length" class="section-card" header="接着练这些" shadow="never">
          <div class="rec-grid">
            <div v-for="rec in recommendations" :key="`${rec.qId}-${rec.layer}`" class="rec-card">
              <div class="rec-head">
                <el-tag size="small">{{ layerLabel(rec.layer) }}</el-tag>
                <el-tag size="small" type="info">难度 {{ rec.difficulty }}</el-tag>
                <span class="rec-reason">{{ rec.reason }}</span>
              </div>
              <p class="rec-text">{{ rec.questionText }}</p>
              <div class="rec-foot">
                <span class="rec-id">{{ rec.qId }}</span>
                <el-button type="primary" size="small" @click="openPractice(rec)">开始练习</el-button>
              </div>
            </div>
          </div>
        </el-card>

        <el-card v-if="latestError" class="section-card" shadow="never">
          <div class="section-head">
            <span>最近一条已确认错题</span>
            <button type="button" class="metric-link" @click="router.push('/error-book')">去错题本</button>
          </div>
          <div class="error-row">
            <el-tag size="small" type="warning">{{ errorTypeLabel(latestError.errorType) }}</el-tag>
            <span class="error-kp">{{ latestError.kpId }} {{ latestError.kpName || "" }}</span>
            <span class="error-text">{{ latestError.correctedText || latestError.ocrText || "（未校正）" }}</span>
            <span class="error-time">{{ (latestError.createdAt || "").slice(0, 10) }}</span>
          </div>
        </el-card>
      </template>
    </template>

    <QuestionPracticeDrawer
      v-model="drawerVisible"
      :q-id="activeQId"
      :kp-id="activeKpId"
      :recommendation-id="activeRecommendationId"
      @completed="onPracticeCompleted"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import QuestionPracticeDrawer from "../components/QuestionPracticeDrawer.vue";
import { DEFAULT_COURSE_ID, getRecommendations, type Recommendation } from "../api/diagnosis";
import { listErrorRecords, type ErrorRecordItem } from "../api/errorBook";
import { getPortrait, type Portrait } from "../api/portrait";
import { listPlans, type StudyPlan } from "../api/plan";

const router = useRouter();

const loading = ref(true);
const planError = ref("");
const portraitError = ref("");
const latestErrorMsg = ref("");
const recError = ref("");
const allPlans = ref<StudyPlan[]>([]);
const portrait = ref<Portrait | null>(null);
const latestError = ref<ErrorRecordItem | null>(null);
const recommendations = ref<Recommendation[]>([]);

const drawerVisible = ref(false);
const activeQId = ref("");
const activeKpId = ref("");
const activeRecommendationId = ref<number | null>(null);

const pad = (value: number) => String(value).padStart(2, "0");
const formatDate = (date: Date) =>
  `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
const addDays = (date: Date, days: number) => {
  const next = new Date(date);
  next.setDate(next.getDate() + days);
  return next;
};
const mondayOf = (date: Date) => {
  const next = new Date(date);
  next.setDate(next.getDate() - ((next.getDay() + 6) % 7));
  return next;
};

const today = formatDate(new Date());
const weekStart = formatDate(mondayOf(new Date()));
const weekEnd = formatDate(addDays(mondayOf(new Date()), 6));
// 逾期可能来自更早的周，向后多取 4 周；本周指标只统计本周区间
const overdueStart = formatDate(addDays(mondayOf(new Date()), -28));
const todayLabel = (() => {
  const names = ["周日", "周一", "周二", "周三", "周四", "周五", "周六"];
  const now = new Date();
  return `${now.getMonth() + 1} 月 ${now.getDate()} 日 ${names[now.getDay()]}`;
})();

const weekPlans = computed(() =>
  allPlans.value.filter(
    (plan) => !!plan.planDate && plan.planDate >= weekStart && plan.planDate <= weekEnd
  )
);
const overduePlans = computed(() =>
  allPlans.value.filter((plan) => {
    const remaining = Math.max(0, (plan.planCount ?? 0) - (plan.doneCount ?? 0));
    if (remaining === 0) return false;
    if (plan.status === "OVERDUE") return true;
    return !!plan.planDate && plan.planDate < today;
  })
);
const overdueCount = computed(() =>
  overduePlans.value.reduce(
    (sum, plan) => sum + Math.max(0, (plan.planCount ?? 0) - (plan.doneCount ?? 0)),
    0
  )
);
const todayPlan = computed(() => weekPlans.value.find((plan) => plan.planDate === today));
const todayRemaining = computed(() =>
  Math.max(0, (todayPlan.value?.planCount ?? 0) - (todayPlan.value?.doneCount ?? 0))
);
const weekDone = computed(() =>
  weekPlans.value.reduce((sum, plan) => sum + (plan.doneCount ?? 0), 0)
);
const weekTotal = computed(() =>
  weekPlans.value.reduce((sum, plan) => sum + (plan.planCount ?? 0), 0)
);
const weekPct = computed(() =>
  weekTotal.value === 0 ? 0 : Math.round((weekDone.value / weekTotal.value) * 100)
);
const sampleCount = computed(() => portrait.value?.totalPractice ?? 0);
const snapshotDate = computed(() => portrait.value?.statDate ?? "");
const weakest = computed(() => {
  const list = (portrait.value?.weakestKps ?? []).filter(
    (item) => (item.sampleCount ?? 0) >= 3
  );
  return [...list].sort((a, b) => (a.mastery ?? 1) - (b.mastery ?? 1))[0] ?? null;
});

const pct = (value?: number | null) =>
  typeof value === "number" ? `${Math.round(value * 100)}%` : "—";

const conclusion = computed(() => {
  if (overdueCount.value > 0) return `先补上逾期的 ${overdueCount.value} 道计划题`;
  if (weakest.value) return `最该补的是「${weakest.value.name || weakest.value.kpId}」`;
  if (todayRemaining.value > 0) return `今天还有 ${todayRemaining.value} 道计划题`;
  if (latestError.value) return "最近一条错题还没练，先把它消化掉";
  return "今天没有紧急任务，保持节奏就好";
});

const evidence = computed(() => {
  const items: string[] = [];
  if (overdueCount.value > 0) items.push(`计划：逾期 ${overdueCount.value} 题`);
  if (weakest.value) {
    items.push(
      `薄弱点：${weakest.value.name || weakest.value.kpId} 掌握度 ${pct(weakest.value.mastery)} · 样本 ${weakest.value.sampleCount ?? 0}`
    );
  }
  if (latestError.value) {
    items.push(`最近错题：${latestError.value.kpId} ${latestError.value.kpName || ""}`.trim());
  }
  if (recommendations.value.length) {
    items.push(`推荐：${recommendations.value.length} 道（按错题/知识点生成）`);
  }
  return items;
});

type Primary = { label: string; kind: "route" | "practice"; to?: string; rec?: Recommendation };
const primary = computed<Primary | null>(() => {
  if (overdueCount.value > 0) {
    return { label: `继续计划（${overdueCount.value} 题）`, kind: "route", to: "/plan" };
  }
  if (weakest.value) {
    const rec = recommendations.value[0];
    if (rec) return { label: "开始练习", kind: "practice", rec };
    return {
      label: "查看薄弱知识点",
      kind: "route",
      to: `/knowledge-graph?kpId=${encodeURIComponent(weakest.value.kpId ?? "")}`
    };
  }
  if (todayRemaining.value > 0) {
    return { label: `完成今日 ${todayRemaining.value} 题`, kind: "route", to: "/plan" };
  }
  if (latestError.value) {
    return { label: "去练这道错题", kind: "route", to: "/error-book?status=CONFIRMED" };
  }
  return null;
});

const isFirstUse = computed(
  () =>
    !planError.value &&
    !portraitError.value &&
    !latestErrorMsg.value &&
    allPlans.value.length === 0 &&
    !portrait.value &&
    !latestError.value
);
const hasPartialError = computed(
  () => !!planError.value || !!portraitError.value || !!latestErrorMsg.value || !!recError.value
);
// 计划与画像都拿不到时，不能把失败写成“今天没有紧急任务”
const fatalError = computed(() => !!planError.value && !!portraitError.value);
const partialErrorText = computed(() =>
  ["部分数据暂不可用", planError.value, portraitError.value, latestErrorMsg.value, recError.value]
    .filter(Boolean)
    .join("；")
);

const errorTypeLabel = (value?: string | null) => {
  const map: Record<string, string> = {
    CONCEPT_CONFUSION: "概念混淆",
    METHOD_MISSING: "方法缺失",
    STEP_OMISSION: "步骤遗漏",
    CALCULATION_ERROR: "计算失误",
    BOUNDARY_ERROR: "边界条件"
  };
  return value ? map[value] ?? value : "未标注";
};

const layerLabel = (layer?: string) => {
  if (layer === "CONSOLIDATE") return "同点巩固";
  if (layer === "VARIANT") return "变式迁移";
  if (layer === "ADVANCE") return "进阶挑战";
  return layer || "推荐";
};

const loadPlans = async () => {
  planError.value = "";
  try {
    allPlans.value = await listPlans(overdueStart, weekEnd);
  } catch (e) {
    planError.value = (e as Error).message || "计划加载失败";
    allPlans.value = [];
  }
};

const loadPortrait = async () => {
  portraitError.value = "";
  try {
    portrait.value = await getPortrait();
  } catch (e) {
    portraitError.value = (e as Error).message || "画像加载失败";
    portrait.value = null;
  }
};

const loadLatestError = async () => {
  latestErrorMsg.value = "";
  try {
    const page = await listErrorRecords({ page: 1, size: 1, status: "CONFIRMED" });
    latestError.value = page.records?.[0] ?? null;
  } catch (e) {
    latestErrorMsg.value = (e as Error).message || "最近错题加载失败";
    latestError.value = null;
  }
};

const loadRecommendations = async () => {
  recError.value = "";
  const params = latestError.value?.logId
    ? { logId: latestError.value.logId, courseId: DEFAULT_COURSE_ID }
    : weakest.value?.kpId
      ? { kpId: weakest.value.kpId, courseId: DEFAULT_COURSE_ID }
      : null;
  if (!params) {
    recommendations.value = [];
    return;
  }
  try {
    recommendations.value = await getRecommendations(params);
  } catch (e) {
    recError.value = (e as Error).message || "推荐加载失败";
    recommendations.value = [];
  }
};

const loadAll = async () => {
  loading.value = true;
  await Promise.all([loadPlans(), loadPortrait(), loadLatestError()]);
  await loadRecommendations();
  loading.value = false;
};

const handlePrimary = () => {
  if (!primary.value) return;
  if (primary.value.kind === "practice" && primary.value.rec) {
    openPractice(primary.value.rec);
    return;
  }
  if (primary.value.to) router.push(primary.value.to);
};

const openPractice = (rec: Recommendation) => {
  activeQId.value = rec.qId;
  activeKpId.value = "";
  activeRecommendationId.value = rec.recommendationId ?? null;
  drawerVisible.value = true;
};

const onPracticeCompleted = async () => {
  drawerVisible.value = false;
  await loadAll();
};

onMounted(loadAll);
</script>

<style scoped>
.page {
  max-width: var(--app-page-max);
}
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}
.page-head h2 {
  margin: 0;
}
.sub {
  margin: 6px 0 0;
  color: var(--app-text-secondary);
  font-size: var(--app-fs-sm);
}
.partial-alert {
  margin-bottom: 12px;
}
.fatal-state {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
}
.hero-card {
  border-radius: var(--app-radius-lg);
  margin-bottom: 16px;
}
.hero-main {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 168px;
  align-items: center;
  gap: var(--app-space-6);
}
.hero-copy {
  min-width: 0;
}
.hero-eyebrow {
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
  letter-spacing: 0.04em;
}
.hero-title {
  margin: 8px 0 12px;
  max-width: 760px;
  font-size: var(--app-fs-hero);
  font-weight: 600;
  letter-spacing: -0.02em;
  line-height: 1.2;
  color: var(--app-text);
}
.hero-desc {
  margin: 0 0 16px;
  color: var(--app-text-secondary);
  line-height: 1.7;
}
.hero-evidence {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}
.evidence-chip {
  padding: 4px 10px;
  border-radius: var(--app-radius-full);
  background: var(--app-surface-muted);
  color: var(--app-text-secondary);
  font-size: var(--app-fs-xs);
}
.hero-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.hero-focus {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}
.progress-ring {
  width: 136px;
  height: 136px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: conic-gradient(
    var(--app-primary) calc(var(--pct, 0) * 1%),
    var(--app-surface-muted) 0
  );
}
.progress-ring-inner {
  width: 106px;
  height: 106px;
  border-radius: 50%;
  background: var(--app-surface);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
}
.progress-ring-inner strong {
  font-size: var(--app-fs-xl);
  font-weight: 600;
  color: var(--app-text);
  font-variant-numeric: tabular-nums;
}
.progress-ring-inner span {
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
}
.focus-caption {
  margin: 0;
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
  font-variant-numeric: tabular-nums;
}
.start-grid {
  display: grid;
  gap: 12px;
}
.start-step {
  display: grid;
  grid-template-columns: 32px 1fr auto;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  background: var(--app-surface-muted);
}
.start-step p {
  margin: 4px 0 0;
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
  line-height: 1.6;
}
.step-no {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--app-primary);
  color: #fff;
  font-weight: 600;
}
.metric-band {
  border-radius: var(--app-radius-md);
  margin-bottom: 16px;
}
.metric-band :deep(.el-card__body) {
  padding: 18px 20px;
}
.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}
.metric-card {
  min-width: 0;
  padding: 2px 20px;
  border-left: 1px solid var(--app-hairline);
}
.metric-card:first-child {
  border-left: none;
  padding-left: 0;
}
.metric-card:last-child {
  padding-right: 0;
}
.metric-label {
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
}
.metric-value {
  margin-top: 6px;
  font-size: var(--app-fs-xl);
  font-weight: 600;
  color: var(--app-text);
}
.metric-value.small {
  font-size: var(--app-fs-md);
}
.metric-value small {
  font-size: var(--app-fs-sm);
  color: var(--app-text-muted);
  font-weight: 400;
}
.metric-hint {
  margin: 8px 0 0;
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
  line-height: 1.6;
}
.metric-link {
  margin-top: 8px;
  padding: 0;
  border: none;
  background: none;
  color: var(--app-primary);
  font-size: var(--app-fs-xs);
  cursor: pointer;
}
.metric-link:hover {
  text-decoration: underline;
}
.section-card {
  border-radius: var(--app-radius-md);
  margin-bottom: 16px;
}
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-weight: 600;
  color: var(--app-text);
}
.rec-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}
.rec-card {
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  padding: 12px;
  background: var(--app-surface-muted);
}
.rec-head {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.rec-reason {
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
}
.rec-text {
  margin: 8px 0;
  color: var(--app-text-secondary);
  line-height: 1.6;
  font-size: var(--app-fs-sm);
}
.rec-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.rec-id {
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
}
.error-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 10px;
  color: var(--app-text-secondary);
}
.error-kp {
  color: var(--app-text);
  font-weight: 500;
}
.error-text {
  flex: 1;
  min-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.error-time {
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
}
@media (max-width: 1024px) {
  .hero-main {
    grid-template-columns: minmax(0, 1fr) 132px;
    gap: 18px;
  }
  .progress-ring {
    width: 112px;
    height: 112px;
  }
  .progress-ring-inner {
    width: 86px;
    height: 86px;
  }
  .rec-grid {
    grid-template-columns: 1fr;
  }
  .start-step {
    grid-template-columns: 32px 1fr;
  }
}
@media (max-width: 768px) {
  .page-head {
    flex-direction: column;
  }
  .metric-grid {
    grid-template-columns: 1fr;
  }
  .metric-card {
    padding: 12px 0;
    border-left: none;
    border-top: 1px solid var(--app-hairline);
  }
  .metric-card:first-child {
    border-top: none;
    padding-top: 2px;
  }
  .metric-card:last-child {
    padding-bottom: 2px;
  }
  .hero-main {
    grid-template-columns: 1fr;
  }
  .hero-focus {
    flex-direction: row;
    justify-content: flex-start;
    gap: 14px;
  }
  .hero-title {
    font-size: var(--app-fs-xl);
  }
}
</style>
