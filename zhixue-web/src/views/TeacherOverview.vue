<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>教学概览</h2>
        <p class="sub">先看本课程最该讲的知识点和最需要关注的学生，再进入班级学情或评阅队列。</p>
      </div>
      <div class="head-actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button @click="router.push('/teacher/reviews')">
          评阅队列<template v-if="pendingTotal">（{{ pendingTotal }}）</template>
        </el-button>
      </div>
    </div>

    <PageState
      :loading="loading"
      :error="error"
      :empty="!loading && !error && isEmpty"
      empty-text="本课程暂无学习数据。学生完成练习或打卡后，这里会出现教学概览。"
      @retry="load"
    >
      <el-card class="overview-brief" shadow="never">
        <div class="brief-main">
          <div>
            <div class="brief-eyebrow">课程结论</div>
            <h3 class="brief-title">{{ overviewTitle }}</h3>
            <p class="brief-sub">{{ overviewSub }}</p>
          </div>
          <el-button type="primary" @click="router.push('/teacher/class')">查看班级学情</el-button>
        </div>
        <div class="stat-row">
          <div class="stat-card">
            <div class="stat-label">学生数</div>
            <div class="stat-value">{{ classPortrait?.studentCount ?? 0 }}</div>
            <p class="stat-hint">有作答记录的学生</p>
          </div>
          <div class="stat-card">
            <div class="stat-label">活跃率</div>
            <div class="stat-value">{{ pct(classPortrait?.activeRate) }}</div>
            <p class="stat-hint">近 7 天有作答记录</p>
          </div>
          <div class="stat-card">
            <div class="stat-label">待评阅</div>
            <div class="stat-value">{{ pendingTotal ?? "—" }}</div>
            <p class="stat-hint">AI 讲解待教师抽样</p>
          </div>
          <div class="stat-card">
            <div class="stat-label">薄弱 Top</div>
            <div class="stat-value">{{ weakest.length }}</div>
            <p class="stat-hint">样本 ≥3 且有过错，最多 5 个</p>
          </div>
        </div>
      </el-card>

      <div class="two-col">
        <el-card class="section-card" shadow="never">
          <template #header>
            <div class="section-head">
              <span>本课程最该讲的知识点</span>
              <span class="section-sub">{{ weakest.length ? `Top ${weakest.length}` : "样本不足" }}</span>
            </div>
          </template>
          <div v-if="weakest.length" class="kp-list">
            <div v-for="(item, index) in weakest" :key="item.kpId || index" class="kp-row">
              <span class="kp-rank">{{ index + 1 }}</span>
              <div class="kp-main">
                <div class="kp-head">
                  <strong>{{ item.name || item.kpId || "未命名知识点" }}</strong>
                  <span class="kp-id">{{ item.kpId }}</span>
                </div>
                <el-progress
                  :percentage="Math.round((item.avgMastery ?? 0) * 100)"
                  :stroke-width="8"
                  :show-text="false"
                  :aria-label="`${item.name || item.kpId} 平均掌握度`"
                />
                <div class="kp-meta">
                  平均掌握度 {{ pct(item.avgMastery) }} · 错误 {{ item.errorCount ?? 0 }} 次
                </div>
              </div>
            </div>
          </div>
          <div v-else class="muted">暂无足够样本，暂不排名</div>
        </el-card>

        <el-card class="section-card" shadow="never">
          <template #header>
            <div class="section-head">
              <span>本页需要关注的学生</span>
              <button type="button" class="link-btn" @click="router.push('/teacher/class')">
                查看全部
              </button>
            </div>
          </template>
          <div v-if="attentionStudents.length" class="student-list">
            <div v-for="student in attentionStudents" :key="student.userId" class="student-row">
              <span class="student-name">{{ student.displayName || "学生" }}</span>
              <span class="student-meta">练习 {{ student.practiceCount ?? 0 }} 次</span>
              <span class="student-meta">掌握度 {{ pct(student.avgMastery) }}</span>
              <span class="student-time">{{ (student.lastActiveAt || "").slice(0, 10) || "暂无活跃" }}</span>
            </div>
          </div>
          <div v-else class="muted">
            {{ students.length ? "本页暂无低于 75% 的学生" : "暂无学生作答记录" }}
          </div>
        </el-card>
      </div>

      <el-card class="section-card" shadow="never">
        <template #header>
          <div class="section-head">
            <span>班级错因分布</span>
            <span class="section-sub">按练习中的错误作答统计</span>
          </div>
        </template>
        <div v-if="errorRows.length" class="error-bars">
          <div v-for="row in errorRows" :key="row.key" class="error-row">
            <span class="error-label">{{ row.label }}</span>
            <div class="error-track" aria-hidden="true">
              <i :style="{ width: row.width }"></i>
            </div>
            <span class="error-count">{{ row.count }}</span>
          </div>
        </div>
        <div v-else class="muted">暂无错因数据</div>
      </el-card>
    </PageState>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import PageState from "../components/PageState.vue";
import { DEFAULT_COURSE_ID } from "../api/diagnosis";
import { getClassPortrait, type ClassPortrait } from "../api/portrait";
import { listReviews } from "../api/review";
import { listCourseStudents, type TeacherStudentItem } from "../api/teacherStudent";

const router = useRouter();
const loading = ref(true);
const error = ref("");
const classPortrait = ref<ClassPortrait | null>(null);
const pendingTotal = ref<number | null>(null);
const students = ref<TeacherStudentItem[]>([]);

const ERROR_LABELS: Record<string, string> = {
  CONCEPT_CONFUSION: "概念混淆",
  METHOD_MISSING: "方法缺失",
  STEP_OMISSION: "步骤遗漏",
  CALCULATION_ERROR: "计算失误",
  BOUNDARY_ERROR: "边界条件"
};

const pct = (value?: number | null) =>
  typeof value === "number" ? `${Math.round(value * 100)}%` : "—";

const ATTENTION_THRESHOLD = 0.75;
const weakest = computed(() => classPortrait.value?.weakestKpTop ?? []);
const attentionStudents = computed(() =>
  students.value
    .filter((student) => (student.avgMastery ?? 0) < ATTENTION_THRESHOLD)
    .slice(0, 5)
);
const weakestTop = computed(() => weakest.value[0] ?? null);
const isEmpty = computed(() => (classPortrait.value?.studentCount ?? 0) === 0);
const overviewTitle = computed(() =>
  weakestTop.value
    ? `本课程最该讲：${weakestTop.value.name || weakestTop.value.kpId || "未命名知识点"}`
    : "本课程暂无足够样本"
);
const overviewSub = computed(() => {
  const head = weakestTop.value;
  if (!head) {
    return `当前覆盖 ${classPortrait.value?.studentCount ?? 0} 名学生；样本达到 3 次后会给出薄弱点排名。`;
  }
  return `平均掌握度 ${pct(head.avgMastery)} · 错误 ${head.errorCount ?? 0} 次 · 覆盖 ${classPortrait.value?.studentCount ?? 0} 名学生 · 待评阅 ${pendingTotal.value ?? "—"} 条`;
});

const errorRows = computed(() => {
  const dist = classPortrait.value?.errorDist ?? {};
  const rows = Object.entries(dist)
    .filter(([, count]) => count > 0)
    .sort((a, b) => b[1] - a[1]);
  const total = rows.reduce((sum, [, count]) => sum + count, 0);
  return rows.map(([key, count]) => ({
    key,
    label: ERROR_LABELS[key] ?? key,
    count,
    width: total === 0 ? "0%" : `${Math.round((count / total) * 100)}%`
  }));
});

let loadSeq = 0;
const load = async () => {
  const seq = ++loadSeq;
  loading.value = true;
  error.value = "";
  try {
    classPortrait.value = await getClassPortrait(DEFAULT_COURSE_ID);
  } catch (e) {
    if (seq !== loadSeq) return;
    error.value = (e as Error).message || "教学概览加载失败";
    loading.value = false;
    return;
  }
  const [reviewPage, studentPage] = await Promise.all([
    listReviews({ page: 1, size: 1, status: "PENDING" }).catch(() => null),
    listCourseStudents({
      courseId: DEFAULT_COURSE_ID,
      page: 1,
      size: 20,
      sort: "masteryAsc"
    }).catch(() => null)
  ]);
  if (seq !== loadSeq) return;
  pendingTotal.value = reviewPage?.total ?? null;
  students.value = studentPage?.records ?? [];
  loading.value = false;
};

onMounted(load);
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
.head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.overview-brief {
  border-radius: var(--app-radius-lg);
  margin-bottom: 16px;
}
.overview-brief :deep(.el-card__body) {
  padding: var(--app-space-6);
}
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-top: var(--app-space-5);
  padding-top: var(--app-space-4);
  border-top: 1px solid var(--app-hairline);
}
.stat-card {
  min-width: 0;
  padding: 0 var(--app-space-6);
  border-left: 1px solid var(--app-hairline);
}
.stat-card:first-child {
  padding-left: 0;
  border-left: none;
}
.stat-card:last-child {
  padding-right: 0;
}
.stat-label {
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
}
.stat-value {
  margin-top: 6px;
  color: var(--app-text);
  font-size: var(--app-fs-xl);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.stat-hint {
  margin: 4px 0 0;
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
}
.two-col {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.section-card {
  border-radius: var(--app-radius-md);
  margin-bottom: 12px;
}
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.section-sub {
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
}
.link-btn {
  border: none;
  background: transparent;
  color: var(--app-primary);
  font: inherit;
  font-size: var(--app-fs-sm);
  cursor: pointer;
}
.link-btn:focus-visible {
  outline: 2px solid var(--app-primary);
  outline-offset: 2px;
}
.kp-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.kp-row {
  display: grid;
  grid-template-columns: 22px 1fr;
  gap: 10px;
  align-items: start;
}
.kp-rank {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: var(--app-radius-full);
  background: var(--app-primary-soft);
  color: var(--app-primary);
  font-size: var(--app-fs-xs);
  font-weight: 600;
}
.kp-main {
  min-width: 0;
}
.kp-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
}
.kp-head strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--app-text);
  font-size: var(--app-fs-sm);
}
.kp-id {
  flex: none;
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
}
.kp-meta {
  margin-top: 4px;
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
}
.student-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.student-row {
  display: grid;
  grid-template-columns: 1.2fr 1fr 1fr 0.9fr;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
  border-bottom: 1px solid var(--app-divider);
}
.student-row:last-child {
  border-bottom: 0;
}
.student-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--app-text);
  font-weight: 600;
  font-size: var(--app-fs-sm);
}
.student-meta,
.student-time {
  color: var(--app-text-secondary);
  font-size: var(--app-fs-xs);
}
.error-bars {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.error-row {
  display: grid;
  grid-template-columns: 92px 1fr 44px;
  align-items: center;
  gap: 10px;
}
.error-label {
  color: var(--app-text-secondary);
  font-size: var(--app-fs-xs);
}
.error-track {
  height: 8px;
  border-radius: var(--app-radius-full);
  background: var(--app-surface-muted);
  overflow: hidden;
}
.error-track i {
  display: block;
  height: 100%;
  border-radius: var(--app-radius-full);
  background: var(--app-primary);
}
.error-count {
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
  text-align: right;
  font-variant-numeric: tabular-nums;
}
.muted {
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
}
/* 矮桌面视口只压缩留白，指标说明与全部错因保持可见。 */
@media (min-width: 1025px) and (max-height: 900px) {
  .page-head {
    margin-bottom: 8px;
  }
  .overview-brief {
    margin-bottom: 8px;
  }
  .overview-brief :deep(.el-card__body) {
    padding: 16px 20px;
  }
  .brief-title {
    margin: 4px 0;
  }
  .stat-row {
    margin-top: 12px;
    padding-top: 8px;
  }
  .section-card {
    margin-bottom: 8px;
  }
  .section-card :deep(.el-card__header) {
    padding: 12px 16px;
  }
  .section-card :deep(.el-card__body) {
    padding: 16px;
  }
  .kp-list {
    gap: 8px;
  }
  .error-bars {
    gap: 6px;
  }
}
@media (max-width: 1024px) {
  .stat-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--app-space-4) 0;
  }
  .stat-card:nth-child(odd) {
    padding-left: 0;
    border-left: none;
  }
  .two-col {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 768px) {
  .page-head {
    flex-direction: column;
  }
  .link-btn {
    min-height: 44px;
    display: inline-flex;
    align-items: center;
    padding: 0 4px;
  }
  .stat-row {
    grid-template-columns: 1fr;
  }
  .stat-card {
    padding: var(--app-space-3) 0;
    border-left: none;
    border-top: 1px solid var(--app-hairline);
  }
  .stat-card:first-child {
    padding-top: 2px;
    border-top: none;
  }
  .stat-card:last-child {
    padding-bottom: 2px;
  }
  .student-row {
    grid-template-columns: 1fr 1fr;
  }
  .error-row {
    grid-template-columns: 80px 1fr 34px;
  }
}
</style>
