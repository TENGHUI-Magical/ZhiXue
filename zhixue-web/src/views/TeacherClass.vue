<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>班级学情</h2>
        <p class="sub">查看本课程学生的学习记录：练习量、正确率、做题时长、进度与薄弱知识点。</p>
      </div>
      <div class="head-actions">
        <el-select v-model="sort" size="small" class="sort-select" @change="load">
          <el-option label="最近活跃" value="lastActiveDesc" />
          <el-option label="掌握度升序" value="masteryAsc" />
          <el-option label="练习量" value="practiceDesc" />
        </el-select>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="stat-row">
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">学生数</div>
        <div class="stat-value">{{ total }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">本页记录</div>
        <div class="stat-value">{{ records.length }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">本页平均掌握度</div>
        <div class="stat-value">{{ avgMasteryText }}</div>
      </el-card>
    </div>

    <PageState
      :loading="loading"
      :error="error"
      :empty="!loading && !error && records.length === 0"
      empty-text="本课程暂无学生作答记录。学生完成练习/打卡后会出现在这里。"
      @retry="load"
    >
      <el-card class="table-card desktop-only">
        <el-table :data="records" row-key="userId" stripe>
          <el-table-column label="学生" width="100">
            <template #default="{ row }">{{ row.displayName || "学生" }}</template>
          </el-table-column>
          <el-table-column label="练习" width="80">
            <template #default="{ row }">{{ row.practiceCount ?? 0 }} 次</template>
          </el-table-column>
          <el-table-column label="正确率" width="90">
            <template #default="{ row }">{{ pct(row.correctRate) }}</template>
          </el-table-column>
          <el-table-column label="平均时长" width="100">
            <template #default="{ row }">{{ duration(row.avgCostSeconds) }}</template>
          </el-table-column>
          <el-table-column label="平均掌握度" width="110">
            <template #default="{ row }">{{ pct(row.avgMastery) }}</template>
          </el-table-column>
          <el-table-column label="错题" width="80">
            <template #default="{ row }">{{ row.errorCount ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="本周进度" width="100">
            <template #default="{ row }">{{ row.planDone ?? 0 }} / {{ row.planTotal ?? 0 }}</template>
          </el-table-column>
          <el-table-column prop="lastActiveAt" label="最近活跃" width="160" show-overflow-tooltip />
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row as TeacherStudentItem)">
                学习记录
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="size"
            :total="total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @current-change="load"
            @size-change="handleSizeChange"
          />
        </div>
      </el-card>

      <div class="mobile-only mobile-list">
        <el-card v-for="row in records" :key="row.userId" class="mobile-card" shadow="never">
          <div class="mobile-head">
            <strong>{{ row.displayName || "学生" }}</strong>
            <el-tag size="small" type="info">{{ row.practiceCount ?? 0 }} 次练习</el-tag>
          </div>
          <div class="mobile-meta">
            正确率 {{ pct(row.correctRate) }} · 平均 {{ duration(row.avgCostSeconds) }} · 掌握度 {{ pct(row.avgMastery) }}
          </div>
          <div class="mobile-meta">错题 {{ row.errorCount ?? 0 }} · 本周 {{ row.planDone ?? 0 }}/{{ row.planTotal ?? 0 }}</div>
          <div class="mobile-foot">
            <span class="muted">{{ row.lastActiveAt || "暂无活跃" }}</span>
            <el-button type="primary" size="small" @click="openDetail(row)">学习记录</el-button>
          </div>
        </el-card>
        <div class="pager">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="size"
            :total="total"
            :page-sizes="[10, 20, 50]"
            layout="total, prev, pager, next"
            @current-change="load"
            @size-change="handleSizeChange"
          />
        </div>
      </div>
    </PageState>

    <el-drawer v-model="drawerVisible" title="学生学习记录" size="640px" class="student-drawer" append-to-body>
      <PageState
        :loading="detailLoading"
        :error="detailError"
        :empty="!detailLoading && !detailError && !detail"
        empty-text="暂无学习记录"
        @retry="loadDetail"
      >
        <template v-if="detail">
          <div class="summary">
            <span class="summary-name">{{ detail.summary.displayName || "学生" }}</span>
            <el-tag size="small">练习 {{ detail.summary.practiceCount ?? 0 }} 次</el-tag>
            <el-tag size="small" type="info">正确率 {{ pct(detail.summary.correctRate) }}</el-tag>
            <el-tag size="small" type="info">平均 {{ duration(detail.summary.avgCostSeconds) }}</el-tag>
            <el-tag size="small" type="info">掌握度 {{ pct(detail.summary.avgMastery) }}</el-tag>
            <el-tag size="small" type="warning">错题 {{ detail.summary.errorCount ?? 0 }}</el-tag>
            <el-tag size="small">本周 {{ detail.summary.planDone ?? 0 }}/{{ detail.summary.planTotal ?? 0 }}</el-tag>
          </div>

          <div class="section-label">作答明细</div>
          <el-table :data="detail.practices.records" row-key="practiceId" size="small" stripe>
            <el-table-column label="题目" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">{{ row.questionText || row.qId || "—" }}</template>
            </el-table-column>
            <el-table-column label="知识点" width="110" show-overflow-tooltip>
              <template #default="{ row }">{{ row.kpId || "—" }} {{ row.kpName || "" }}</template>
            </el-table-column>
            <el-table-column label="结果" width="72">
              <template #default="{ row }">
                <el-tag v-if="row.isCorrect === true" size="small" type="success">正确</el-tag>
                <el-tag v-else-if="row.isCorrect === false" size="small" type="danger">错误</el-tag>
                <el-tag v-else size="small" type="info">未验证</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="用时" width="80">
              <template #default="{ row }">{{ duration(row.costSeconds) }}</template>
            </el-table-column>
            <el-table-column prop="createdAt" label="时间" width="150" show-overflow-tooltip />
          </el-table>
          <div class="pager">
            <el-pagination
              v-model:current-page="practicePage"
              :page-size="10"
              :total="detail.practices.total"
              layout="total, prev, pager, next"
              @current-change="loadDetail"
            />
          </div>

          <div class="section-label">知识点掌握度</div>
          <div v-if="detail.kpMastery.length" class="kp-list">
            <div v-for="kp in detail.kpMastery" :key="kp.kpId" class="kp-row">
              <span class="kp-name">{{ kp.name || kp.kpId }}</span>
              <el-progress :percentage="Math.round((kp.mastery ?? 0) * 100)" :stroke-width="8" />
              <span class="kp-meta">样本 {{ kp.sampleCount ?? 0 }} · 错 {{ kp.errorCount ?? 0 }}</span>
            </div>
          </div>
          <div v-else class="muted">暂无知识点样本</div>

          <div class="section-label">错题记录</div>
          <div v-if="detail.errors.length" class="error-list">
            <div v-for="err in detail.errors" :key="err.logId" class="error-row">
              <el-tag size="small" :type="err.status === 'CONFIRMED' ? 'success' : 'info'">
                {{ err.status === "CONFIRMED" ? "已确认" : "草稿" }}
              </el-tag>
              <span class="error-kp">{{ err.kpId }} {{ err.kpName || "" }}</span>
              <span class="muted">{{ err.errorType || "—" }}</span>
              <span class="muted">{{ err.createdAt }}</span>
            </div>
          </div>
          <div v-else class="muted">暂无错题记录</div>
        </template>
      </PageState>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import PageState from "../components/PageState.vue";
import { DEFAULT_COURSE_ID } from "../api/diagnosis";
import {
  getStudentLearningRecords,
  listCourseStudents,
  type StudentLearningRecord,
  type TeacherStudentItem
} from "../api/teacherStudent";

const records = ref<TeacherStudentItem[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(10);
const sort = ref("lastActiveDesc");
const loading = ref(false);
const error = ref("");

const drawerVisible = ref(false);
const detailLoading = ref(false);
const detailError = ref("");
const detail = ref<StudentLearningRecord | null>(null);
const selectedUserId = ref<number | null>(null);
const practicePage = ref(1);

const avgMasteryText = computed(() => {
  const values = records.value
    .map((row) => row.avgMastery)
    .filter((value): value is number => typeof value === "number");
  if (!values.length) return "—";
  return pct(values.reduce((sum, value) => sum + value, 0) / values.length);
});

const pct = (value?: number | null) =>
  typeof value === "number" ? `${Math.round(value * 100)}%` : "—";

const duration = (seconds?: number | null) => {
  if (typeof seconds !== "number" || seconds <= 0) return "—";
  if (seconds < 60) return `${seconds} 秒`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)} 分`;
  return `${Math.floor(seconds / 3600)} 小时`;
};

let loadSeq = 0;
const load = async () => {
  const seq = ++loadSeq;
  loading.value = true;
  error.value = "";
  try {
    const data = await listCourseStudents({
      courseId: DEFAULT_COURSE_ID,
      page: page.value,
      size: size.value,
      sort: sort.value
    });
    if (seq !== loadSeq) return;
    records.value = data.records ?? [];
    total.value = data.total ?? 0;
  } catch (e) {
    if (seq !== loadSeq) return;
    error.value = (e as Error).message || "学生列表加载失败";
  } finally {
    if (seq === loadSeq) loading.value = false;
  }
};

const handleSizeChange = () => {
  page.value = 1;
  load();
};

const loadDetail = async () => {
  if (selectedUserId.value == null) return;
  detailLoading.value = true;
  detailError.value = "";
  try {
    detail.value = await getStudentLearningRecords(selectedUserId.value, {
      courseId: DEFAULT_COURSE_ID,
      page: practicePage.value,
      size: 10
    });
  } catch (e) {
    detailError.value = (e as Error).message || "学习记录加载失败";
  } finally {
    detailLoading.value = false;
  }
};

const openDetail = (row: TeacherStudentItem) => {
  if (row.userId == null) return;
  selectedUserId.value = row.userId;
  practicePage.value = 1;
  detail.value = null;
  drawerVisible.value = true;
  loadDetail();
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
.sort-select {
  width: 128px;
}
.stat-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.stat-card {
  border-radius: var(--app-radius-md);
}
.stat-label {
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
}
.stat-value {
  margin-top: 6px;
  font-size: var(--app-fs-xl);
  font-weight: 600;
  color: var(--app-text);
}
.table-card {
  border-radius: var(--app-radius-md);
}
.pager {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
}
.section-label {
  margin: 16px 0 8px;
  color: var(--app-text);
  font-weight: 600;
}
.summary {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 10px 12px;
  border-radius: var(--app-radius-md);
  background: var(--app-surface-muted);
}
.summary-name {
  font-weight: 600;
  color: var(--app-text);
}
.kp-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.kp-row {
  display: grid;
  grid-template-columns: 130px 1fr 120px;
  align-items: center;
  gap: 10px;
}
.kp-name {
  color: var(--app-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.kp-meta {
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
}
.error-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.error-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  color: var(--app-text-secondary);
}
.error-kp {
  color: var(--app-text);
}
.muted {
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
}
.mobile-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.mobile-meta {
  margin-top: 6px;
  color: var(--app-text-secondary);
  font-size: var(--app-fs-sm);
}
.mobile-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}
.desktop-only {
  display: block;
}
.mobile-only {
  display: none;
}
.mobile-list {
  flex-direction: column;
  gap: 12px;
}
@media (max-width: 1024px) {
  .desktop-only {
    display: none;
  }
  .mobile-only {
    display: flex;
  }
}
@media (max-width: 768px) {
  .page-head {
    flex-direction: column;
  }
  .stat-row {
    grid-template-columns: 1fr;
  }
  .kp-row {
    grid-template-columns: 1fr;
    gap: 4px;
  }
  :deep(.el-drawer) {
    width: 92vw !important;
  }
}
</style>
