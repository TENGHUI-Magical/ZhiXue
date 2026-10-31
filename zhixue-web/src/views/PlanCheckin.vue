<template>
  <div class="page">
    <div class="page-head">
      <div>
        <div class="eyebrow">WEEKLY PLAN</div>
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

    <el-card class="section-card">
      <template #header>
        <div class="card-head">
          <span>本周打卡清单</span>
          <span class="head-right">
            <el-tag v-if="plans.length" type="success" size="small">计划已生成</el-tag>
            <el-button
              v-else
              type="primary"
              :loading="generating"
              @click="generate"
            >
              生成本周计划
            </el-button>
          </span>
        </div>
      </template>
      <PageState
        :loading="loading"
        :error="error"
        :empty="!loading && !error && plans.length === 0"
        empty-text="本周尚未生成练习计划。"
        @retry="loadPlans"
      >
        <template #empty-action>
          <el-button type="primary" :loading="generating" @click="generate">生成本周计划</el-button>
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
                  :loading="checkingKey === `${plan.planId}-${item.itemId}`"
                  @click="checkin(plan, item)"
                >
                  打卡
                </el-button>
              </div>
              <el-empty
                v-if="!(plan.items || []).length"
                description="当天暂无题目"
                :image-size="60"
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
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import PageState from "../components/PageState.vue";
import QuestionPracticeDrawer from "../components/QuestionPracticeDrawer.vue";
import { listErrorRecords } from "../api/errorBook";
import {
  DEFAULT_COURSE_ID,
  getRecommendations,
  newClientToken,
  type Recommendation
} from "../api/diagnosis";
import { checkinPlan, generatePlans, listPlans, type PlanItem, type StudyPlan } from "../api/plan";
import { ApiError } from "../api/request";

const router = useRouter();
const weekStartDate = ref(mondayOf(new Date()));
const plans = ref<StudyPlan[]>([]);
const loading = ref(false);
const generating = ref(false);
const error = ref("");
const actionError = ref("");
const checkingKey = ref("");
const recommendations = ref<Recommendation[]>([]);
const recLoading = ref(false);
const recError = ref("");
const drawerVisible = ref(false);
const activeQId = ref("");
const activeKpId = ref("");
const activeRecommendationId = ref<number | null>(null);

const weekStart = computed(() => formatDate(weekStartDate.value));
const weekEnd = computed(() => formatDate(addDays(weekStartDate.value, 6)));
const isCurrentWeek = computed(
  () => formatDate(weekStartDate.value) === formatDate(mondayOf(new Date()))
);

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

const loadPlans = async () => {
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

const checkin = async (plan: StudyPlan, item: PlanItem) => {
  if (!plan.planId || !item.qId) return;
  const key = `${plan.planId}-${item.itemId}`;
  checkingKey.value = key;
  actionError.value = "";
  try {
    await checkinPlan(plan.planId, {
      qId: item.qId,
      clientToken: newClientToken()
    });
    ElMessage.success("打卡成功");
    await loadPlans();
  } catch (e) {
    if (e instanceof ApiError && e.code === 40900) {
      ElMessage.warning("这道题已经打卡过了");
      await loadPlans();
    } else {
      actionError.value = (e as Error).message || "打卡失败";
    }
  } finally {
    checkingKey.value = "";
  }
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

const onPracticeCompleted = async () => {
  drawerVisible.value = false;
  await loadRecommendations();
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
.eyebrow {
  font-size: var(--app-fs-xs);
  font-weight: 600;
  letter-spacing: 0.18em;
  color: var(--app-text-muted);
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
}
.section-card {
  margin-bottom: 16px;
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
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}
.day-card {
  min-width: 0;
}
.day-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.day-progress {
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
  margin: 6px 0 10px;
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
  border-top: 1px solid var(--app-border);
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
.rec-card {
  min-width: 0;
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
@media (max-width: 1080px) {
  .day-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 768px) {
  .week-actions {
    width: 100%;
    justify-content: space-between;
  }
  .week-title {
    min-width: 0;
  }
  .day-grid {
    grid-template-columns: 1fr;
  }
}
</style>
