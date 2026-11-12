<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>周计划与打卡</h2>
        <p class="sub">按周生成练习计划，完成后逐题打卡；历史进度自动保留。</p>
      </div>
      <div class="week-actions">
        <el-button text @click="shiftWeek(-1)">上一周</el-button>
        <el-button
          text
          :aria-current="isCurrentWeek ? 'true' : undefined"
          @click="goCurrentWeek"
        >
          本周
        </el-button>
        <span class="week-title">{{ weekStart }} 至 {{ weekEnd }}</span>
        <el-button text @click="shiftWeek(1)">下一周</el-button>
      </div>
    </div>

    <el-alert
      v-if="actionError"
      :title="actionError"
      type="error"
      show-icon
      closable
      class="action-alert"
      @close="actionError = ''"
    />

    <el-card v-if="plans.length" class="week-brief" shadow="never">
      <div class="brief-main">
        <div>
          <div class="brief-eyebrow">{{ isCurrentWeek ? "本周进度" : "该周进度" }}</div>
          <h3 class="brief-title">
            {{ weekDone }} / {{ weekTotal }} 已完成<span v-if="overdueCount"> · {{ overdueCount }} 题逾期</span>
          </h3>
          <p class="brief-sub">{{ briefSub }}</p>
        </div>
        <div class="brief-progress">
          <strong>{{ weekPct }}%</strong>
          <el-progress :percentage="weekPct" :stroke-width="8" :show-text="false" aria-label="本周完成进度" />
        </div>
      </div>
    </el-card>

    <el-card class="section-card">
      <template #header>
        <div class="card-head">
          <span>{{ isCurrentWeek ? "本周打卡清单" : "该周打卡清单" }}</span>
          <span class="head-right">
            <el-tag v-if="plans.length" type="success" size="small">计划已生成</el-tag>
            <el-button
              v-else
              type="primary"
              :loading="generating"
              @click="generate"
            >
              {{ isCurrentWeek ? "生成本周计划" : "生成该周计划" }}
            </el-button>
          </span>
        </div>
      </template>
      <PageState
        :loading="loading"
        :error="error"
        :empty="!loading && !error && plans.length === 0"
        :empty-text="emptyText"
        @retry="loadPlans"
      >
        <template #empty-action>
          <el-button type="primary" :loading="generating" @click="generate">
            {{ isCurrentWeek ? "生成本周计划" : "生成该周计划" }}
          </el-button>
        </template>

        <div class="day-grid">
          <el-card v-for="plan in plans" :key="plan.planId" class="day-card" shadow="never">
            <div class="day-head">
              <strong>{{ planDateLabel(plan.planDate) }}</strong>
              <el-tag :type="statusType(plan.status)" size="small">
                {{ statusLabel(plan.status) }}
              </el-tag>
            </div>
            <div class="day-progress">
              {{ plan.doneCount || 0 }} / {{ plan.planCount || 0 }} 已完成
            </div>
            <div class="plan-items">
              <div v-for="item in plan.items || []" :key="item.itemId" class="plan-item">
                <div class="item-main">
                  <span class="item-title">{{ item.sortNo || "-" }}. {{ item.qId }}</span>
                  <span class="item-kp">{{ item.kpId }}</span>
                </div>
                <el-tag v-if="item.isDone" type="success" size="small">已完成</el-tag>
                <el-button
                  v-else
                  type="primary"
                  size="small"
                  plain
                  title="先作答再打卡"
                  @click="startCheckin(plan, item)"
                >
                  去打卡
                </el-button>
              </div>
              <BrandEmpty
                v-if="!(plan.items || []).length"
                description="当天暂无题目"
              />
            </div>
          </el-card>
        </div>
      </PageState>
    </el-card>

    <el-card class="section-card">
      <template #header>
        <div class="card-head">
          <span>分层练习推荐</span>
          <span class="head-hint">依据最近一次已确认错题</span>
        </div>
      </template>
      <PageState
        :loading="recLoading"
        :error="recError"
        :empty="!recLoading && !recError && recommendations.length === 0"
        empty-text="完成一次错题诊断并确认后，系统按巩固、变式、进阶推荐练习。"
        @retry="loadRecommendations"
      >
        <template #empty-action>
          <el-button type="primary" @click="router.push('/diagnosis')">开始诊断</el-button>
        </template>

        <div class="rec-grid">
          <el-card v-for="rec in recommendations" :key="`${rec.qId}-${rec.layer}`" class="rec-card" shadow="never">
            <div class="rec-head">
              <el-tag size="small">{{ layerLabel(rec.layer) }}</el-tag>
              <el-tag size="small" type="info">难度 {{ rec.difficulty }}</el-tag>
              <span class="rec-reason">{{ rec.reason }}</span>
            </div>
            <div class="rec-text">{{ rec.questionText }}</div>
            <div class="rec-foot">
              <span class="rec-id">{{ rec.qId }}</span>
              <el-button type="primary" size="small" @click="openPractice(rec)">开始练习</el-button>
            </div>
          </el-card>
        </div>
      </PageState>
    </el-card>

    <QuestionPracticeDrawer
      v-model="drawerVisible"
      :q-id="activeQId"
      :kp-id="activeKpId"
      :recommendation-id="activeRecommendationId"
      @completed="onPracticeCompleted"
    />

    <QuestionPracticeDrawer
      v-model="planDrawerVisible"
      :q-id="planQId"
      :kp-id="planKpId"
      mode="plan"
      :plan-id="planId"
      @completed="onPlanCheckinCompleted"
      @next="onPlanNext"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import PageState from "../components/PageState.vue";
import BrandEmpty from "../components/BrandEmpty.vue";
import QuestionPracticeDrawer from "../components/QuestionPracticeDrawer.vue";
import { listErrorRecords } from "../api/errorBook";
import {
  DEFAULT_COURSE_ID,
  getRecommendations,
  type Recommendation
} from "../api/diagnosis";
import { generatePlans, listPlans, type PlanItem, type StudyPlan } from "../api/plan";
import { useStudyDataRefresh } from "../composables/useStudyData";

const router = useRouter();
const weekStartDate = ref(mondayOf(new Date()));
const plans = ref<StudyPlan[]>([]);
const loading = ref(false);
const generating = ref(false);
const error = ref("");
const actionError = ref("");
const recommendations = ref<Recommendation[]>([]);
const recLoading = ref(false);
const recError = ref("");
const drawerVisible = ref(false);
const activeQId = ref("");
const activeKpId = ref("");
const activeRecommendationId = ref<number | null>(null);
const planDrawerVisible = ref(false);
const planQId = ref("");
const planKpId = ref("");
const planId = ref<number | null>(null);

const weekStart = computed(() => formatDate(weekStartDate.value));
const weekEnd = computed(() => formatDate(addDays(weekStartDate.value, 6)));
const isCurrentWeek = computed(
  () => formatDate(weekStartDate.value) === formatDate(mondayOf(new Date()))
);
const weekDone = computed(() => plans.value.reduce((sum, plan) => sum + (plan.doneCount ?? 0), 0));
const weekTotal = computed(() => plans.value.reduce((sum, plan) => sum + (plan.planCount ?? 0), 0));
const weekPct = computed(() =>
  weekTotal.value === 0 ? 0 : Math.round((weekDone.value / weekTotal.value) * 100)
);
const overdueCount = computed(() =>
  plans.value.reduce((sum, plan) => {
    const remaining = Math.max(0, (plan.planCount ?? 0) - (plan.doneCount ?? 0));
    return sum + (plan.status === "OVERDUE" ? remaining : 0);
  }, 0)
);
const todayPlan = computed(() => {
  const today = formatDate(new Date());
  return plans.value.find((item) => item.planDate === today) ?? null;
});
const todayRemaining = computed(() =>
  Math.max(0, (todayPlan.value?.planCount ?? 0) - (todayPlan.value?.doneCount ?? 0))
);
const briefSub = computed(() => {
  if (!isCurrentWeek.value) {
    if (!weekTotal.value) return "该周尚未生成计划。";
    return `该周共 ${Math.max(0, weekTotal.value - weekDone.value)} 题未完成；历史进度只读。`;
  }
  if (!weekTotal.value) return "本周尚未生成计划，生成后按天完成打卡。";
  if (!todayPlan.value) return "今天暂无计划；可检查本周安排。";
  if (todayRemaining.value > 0) return `今天还有 ${todayRemaining.value} 题待完成；完成后逐题打卡。`;
  return "今天的计划已完成，可以按节奏预习或补弱。";
});
const emptyText = computed(() => (isCurrentWeek.value ? "本周尚未生成练习计划。" : "该周尚未生成练习计划。"));

function pad(value: number) {
  return String(value).padStart(2, "0");
}

function formatDate(date: Date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function addDays(date: Date, days: number) {
  const next = new Date(date);
  next.setDate(next.getDate() + days);
  return next;
}

function mondayOf(date: Date) {
  const next = new Date(date);
  const day = next.getDay();
  next.setDate(next.getDate() - ((day + 6) % 7));
  return next;
}

function planDateLabel(value?: string) {
  if (!value) return "日期待定";
  const raw = String(value).slice(0, 10);
  const date = new Date(`${raw}T00:00:00`);
  if (Number.isNaN(date.getTime())) return "日期待定";
  const names = ["周日", "周一", "周二", "周三", "周四", "周五", "周六"];
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${names[date.getDay()]}`;
}

const statusLabel = (status?: string) => {
  if (status === "DONE") return "已完成";
  if (status === "OVERDUE") return "已逾期";
  return "进行中";
};

const statusType = (status?: string) => {
  if (status === "DONE") return "success";
  if (status === "OVERDUE") return "danger";
  return "info";
};

const layerLabel = (layer?: string) => {
  if (layer === "CONSOLIDATE") return "同点巩固";
  if (layer === "VARIANT") return "变式迁移";
  if (layer === "ADVANCE") return "进阶挑战";
  return layer || "推荐";
};

let planReqSeq = 0;
/** 同周并发刷新合并成一次请求：提交广播与关抽屉回执在同一 tick 触发时只发一个 GET */
let plansInFlight: Promise<void> | null = null;
let plansInFlightKey = "";

const loadPlans = (): Promise<void> => {
  // 直接重拉时取消已排队的尾沿刷新，避免同一次变更发两次 GET
  if (plansRefreshTimer !== null) {
    window.clearTimeout(plansRefreshTimer);
    plansRefreshTimer = null;
  }
  const key = `${weekStart.value}|${weekEnd.value}`;
  if (plansInFlight && plansInFlightKey === key) return plansInFlight;
  plansInFlightKey = key;
  const run = (async () => {
    const seq = ++planReqSeq;
    loading.value = true;
    error.value = "";
    try {
      const data = await listPlans(weekStart.value, weekEnd.value);
      if (seq !== planReqSeq) return; // 快速切周时丢弃旧响应
      plans.value = data;
    } catch (e) {
      if (seq !== planReqSeq) return;
      error.value = (e as Error).message || "计划加载失败";
    } finally {
      if (seq === planReqSeq) loading.value = false;
    }
  })();
  const settled = run.finally(() => {
    if (plansInFlight === settled) {
      plansInFlight = null;
      plansInFlightKey = "";
    }
  });
  plansInFlight = settled;
  return settled;
};

const generate = async () => {
  if (generating.value) return;
  generating.value = true;
  actionError.value = "";
  try {
    plans.value = await generatePlans({
      courseId: DEFAULT_COURSE_ID,
      weekStart: weekStart.value,
      dailyQuestionCount: 3
    });
    ElMessage.success("已生成本周计划");
  } catch (e) {
    actionError.value = (e as Error).message || "计划生成失败";
  } finally {
    generating.value = false;
  }
};

/** 打卡前必先作答：打开练习抽屉（plan 模式），由抽屉提交真实作答并打卡 */
const startCheckin = (plan: StudyPlan, item: PlanItem) => {
  if (!plan.planId || !item.qId) return;
  if (!item.kpId) {
    ElMessage.warning("这道计划题缺少知识点归属，暂不能打卡");
    return;
  }
  planId.value = plan.planId;
  planQId.value = item.qId;
  planKpId.value = item.kpId;
  actionError.value = "";
  planDrawerVisible.value = true;
};

const onPlanNext = async () => {
  // 提交回执在关抽屉时才刷新父页；切下一题前必须主动取最新完成状态。
  await loadPlans();
  const currentPlan = plans.value.find((plan) => plan.planId === planId.value);
  const items = currentPlan?.items ?? [];
  const currentIndex = items.findIndex((item) => item.qId === planQId.value);
  const next = items.slice(Math.max(0, currentIndex + 1)).find((item) => !item.isDone)
    ?? items.find((item) => !item.isDone && item.qId !== planQId.value);
  if (!next?.qId || !next.kpId) {
    planDrawerVisible.value = false;
    return;
  }
  planQId.value = next.qId;
  planKpId.value = next.kpId;
};

/**
 * 关抽屉后延迟一次计划重拉：
 * 1) 打卡/记录进度的写操作先广播，聚合结果落库有毫秒级延迟，立刻重拉可能读到旧周进度；
 * 2) 提交广播与关抽屉回执可能同 tick 触发，用尾沿计时器合并成一次 GET。
 */
let plansRefreshTimer: number | null = null;
const schedulePlansRefresh = () => {
  if (plansRefreshTimer !== null) window.clearTimeout(plansRefreshTimer);
  plansRefreshTimer = window.setTimeout(() => {
    plansRefreshTimer = null;
    void loadPlans();
  }, 400);
};

const onPlanCheckinCompleted = () => {
  planDrawerVisible.value = false;
  schedulePlansRefresh();
};

const loadRecommendations = async () => {
  recLoading.value = true;
  recError.value = "";
  try {
    const latest = await listErrorRecords({ page: 1, size: 1, status: "CONFIRMED" });
    const record = latest.records?.[0];
    if (!record?.logId) {
      recommendations.value = [];
      return;
    }
    recommendations.value = await getRecommendations({
      logId: record.logId,
      courseId: DEFAULT_COURSE_ID
    });
  } catch (e) {
    recError.value = (e as Error).message || "推荐加载失败";
  } finally {
    recLoading.value = false;
  }
};

const openPractice = (rec: Recommendation) => {
  activeQId.value = rec.qId;
  activeKpId.value = "";
  activeRecommendationId.value = rec.recommendationId ?? null;
  drawerVisible.value = true;
};

const onPracticeCompleted = () => {
  drawerVisible.value = false;
};

const shiftWeek = (offset: number) => {
  weekStartDate.value = addDays(weekStartDate.value, offset * 7);
  loadPlans();
};

const goCurrentWeek = () => {
  weekStartDate.value = mondayOf(new Date());
  loadPlans();
};

onMounted(() => {
  loadPlans();
  loadRecommendations();
});

/** 计划内打卡会在抽屉里写 practice，周进度与推荐需要跟着变 */
useStudyDataRefresh(() => {
  schedulePlansRefresh();
  loadRecommendations();
});

onBeforeUnmount(() => {
  if (plansRefreshTimer !== null) window.clearTimeout(plansRefreshTimer);
});
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
  flex-wrap: wrap;
}
.page-head h2 {
  margin: 0;
}
.sub {
  margin: 6px 0 0;
  color: var(--app-text-secondary);
  font-size: var(--app-fs-sm);
}
.week-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--app-text-secondary);
  font-size: var(--app-fs-sm);
}
.week-title {
  min-width: 180px;
  text-align: center;
  font-variant-numeric: tabular-nums;
}
.action-alert {
  margin-bottom: 12px;
  border-radius: var(--app-radius-md);
}
/* B 层：本周主结论卡（唯一焦点；光感靠渐变与顶部高光，不叠 backdrop-filter） */
.page .week-brief {
  margin-bottom: 16px;
  border-radius: var(--app-radius-inner);
  border-color: color-mix(in srgb, var(--app-primary) 18%, var(--app-hairline));
  background:
    radial-gradient(110% 130% at 100% 0%, color-mix(in srgb, var(--app-primary) 10%, transparent), transparent 58%),
    linear-gradient(160deg, color-mix(in srgb, var(--app-primary) 5%, var(--app-surface)), var(--app-surface) 64%);
  box-shadow: var(--app-shadow-md), inset 0 1px 0 var(--app-card-hi);
}
.page .week-brief :deep(.el-card__body) {
  padding: var(--app-space-5) var(--app-space-6);
}
.week-brief .brief-main {
  grid-template-columns: minmax(0, 1fr) 260px;
}
.brief-progress {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.brief-progress strong {
  font-size: var(--app-fs-xl);
  font-weight: 600;
  color: var(--app-text);
  font-variant-numeric: tabular-nums;
}
/* C 层：清单与推荐是阅读承载面，近实底 + 发丝边 + 轻阴影 */
.page .section-card {
  margin-bottom: 16px;
  border-radius: var(--app-radius-inner);
  border-color: var(--app-hairline);
  box-shadow: var(--app-shadow-sm), inset 0 1px 0 var(--app-card-hi);
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.head-right {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.head-hint {
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
}
.day-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 12px;
}
/* 子卡 16px：与 18px 区块卡同心；近实底避免“卡中卡”叠模糊 */
.page .day-card {
  min-width: 0;
  border-radius: var(--app-radius-lg);
  border-color: var(--app-hairline);
  background: color-mix(in srgb, var(--app-surface-muted) 38%, var(--app-surface));
  box-shadow: var(--app-shadow-xs), inset 0 1px 0 var(--app-card-hi);
}
.page .day-card :deep(.el-card__body) {
  padding: 12px;
}
.plan-item :deep(.el-button) {
  flex: none;
  padding: 0 8px;
}
.day-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.day-head strong {
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.01em;
}
.day-progress {
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
  margin: 6px 0 10px;
  font-variant-numeric: tabular-nums;
}
.plan-items {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.plan-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 0;
  border-top: 1px solid var(--app-hairline);
}
.item-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.item-title {
  font-size: var(--app-fs-sm);
  color: var(--app-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.item-kp {
  font-size: var(--app-fs-xs);
  color: var(--app-text-muted);
}
.rec-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 12px;
}
.page .rec-card {
  min-width: 0;
  border-radius: var(--app-radius-lg);
  border-color: var(--app-hairline);
  background: color-mix(in srgb, var(--app-surface-muted) 30%, var(--app-surface));
  box-shadow: var(--app-shadow-xs), inset 0 1px 0 var(--app-card-hi);
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
  margin: 12px 0;
  line-height: 1.65;
  color: var(--app-text);
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  overflow: hidden;
  white-space: pre-wrap;
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
@media (max-width: 1279px) {
  .day-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}
@media (max-width: 1080px) {
  .day-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 1024px) {
  .week-brief .brief-main {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 768px) {
  /* 周条：横向滚动只发生在这个容器内，页面本身不产生横向溢出 */
  .week-actions {
    width: 100%;
    max-width: 100%;
    justify-content: flex-start;
    flex-wrap: nowrap;
    gap: 2px;
    padding: 2px;
    border-radius: var(--app-radius-md);
    overflow-x: auto;
    overscroll-behavior-x: contain;
    -webkit-overflow-scrolling: touch;
    scrollbar-width: none;
  }
  .week-actions::-webkit-scrollbar {
    display: none;
  }
  .week-actions :deep(.el-button) {
    flex: none;
  }
  .week-title {
    flex: 0 0 auto;
    min-width: 156px;
    padding: 0 4px;
    font-size: var(--app-fs-xs);
  }
  .day-grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }
  .page .day-card :deep(.el-card__body) {
    padding: 14px;
  }
  .plan-item :deep(.el-button) {
    min-height: 44px;
  }
}
</style>
