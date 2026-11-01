<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>教师评阅</h2>
        <p class="sub">对 AI 讲解进行抽样评阅（1—5 分）；每条讲解仅可评阅一次。</p>
      </div>
      <div class="head-actions">
        <el-select v-model="statusFilter" size="small" class="status-select" aria-label="按评阅状态筛选" @change="onStatusChange">
          <el-option label="待评阅" value="PENDING" />
          <el-option label="已评阅" value="REVIEWED" />
          <el-option label="全部" value="ALL" />
        </el-select>
        <el-button :loading="loading" @click="load">刷新队列</el-button>
      </div>
    </div>

    <div class="stat-row">
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">{{ statusFilter === "PENDING" ? "待评阅" : statusFilter === "REVIEWED" ? "已评阅" : "记录" }}</div>
        <div class="stat-value">{{ total }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">本页记录</div>
        <div class="stat-value">{{ records.length }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">评分标准</div>
        <div class="stat-value small">1—5 分</div>
      </el-card>
    </div>

    <PageState
      :loading="loading"
      :error="error"
      :empty="!loading && !error && records.length === 0"
      empty-text="当前没有待评阅的讲解。学生生成讲解后将进入评阅队列。"
      @retry="load"
    >

      <el-card class="table-card desktop-only">
        <el-table :data="records" row-key="logPk" stripe>
          <el-table-column label="学生" width="100" show-overflow-tooltip>
            <template #default="{ row }">{{ row.userName || "—" }}</template>
          </el-table-column>
          <el-table-column label="知识点" width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ row.kpId || "—" }}</template>
          </el-table-column>
          <el-table-column label="错因" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.errorType" size="small" type="warning">
                {{ errorTypeLabel(row.errorType) }}
              </el-tag>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column label="模型 / 状态" width="130">
            <template #default="{ row }">
              <span class="model">{{ row.model || "—" }}</span>
              <el-tag v-if="row.degraded" size="small" type="warning">降级</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="讲解摘要" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ row.content || "（空）" }}</template>
          </el-table-column>
          <el-table-column prop="createdAt" label="生成时间" width="150" show-overflow-tooltip />
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openReview(row as ReviewItem)">评阅</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination
            v-model:current-page="page"
            :total="total"
            layout="total, prev, pager, next"
            @current-change="load"
          />
        </div>
      </el-card>

      <div class="mobile-only mobile-list">
        <el-card v-for="row in records" :key="row.logPk" class="mobile-card" shadow="never">
          <div class="mobile-head">
            <strong>{{ row.userName || "学生" }} · {{ row.kpId || "—" }}</strong>
            <el-tag v-if="row.degraded" size="small" type="warning">降级</el-tag>
          </div>
          <div class="mobile-meta">
            {{ errorTypeLabel(row.errorType || "") }} · {{ row.model || "—" }} · {{ row.createdAt }}
          </div>
          <div class="mobile-content">{{ row.content || "（空讲解）" }}</div>
          <div class="mobile-foot">
            <span class="sources">{{ sourceText(row) }}</span>
            <el-button type="primary" size="small" @click="openReview(row as ReviewItem)">评阅</el-button>
          </div>
        </el-card>
        <div class="pager">
          <el-pagination
            v-model:current-page="page"
            :total="total"
            layout="total, prev, pager, next"
            @current-change="load"
          />
        </div>
      </div>
    </PageState>

    <el-drawer v-model="drawerVisible" title="讲解评阅" size="520px" class="review-drawer" append-to-body @closed="onReviewClosed">
      <template v-if="selected">
        <div class="review-meta">
          <el-tag size="small">{{ selected.userName || "学生" }}</el-tag>
          <el-tag size="small" type="info">{{ selected.kpId || "—" }}</el-tag>
          <el-tag v-if="selected.degraded" size="small" type="warning">降级</el-tag>
          <el-tag
            v-if="selected.reviewStatus === 'REVIEWED'"
            size="small"
            :type="selected.reviewResult === 'RETURN' ? 'danger' : 'success'"
          >
            {{ selected.reviewResult === "RETURN" ? "已退回" : "已通过" }}
          </el-tag>
          <span class="review-time">{{ selected.createdAt }}</span>
        </div>
        <div class="section-label">评阅证据</div>
        <div v-if="selected.questionText" class="evidence-block">
          <div class="evidence-k">
            题干<span v-if="selected.questionType"> · {{ selected.questionType }}</span>
          </div>
          <div class="evidence-v">{{ selected.questionText }}</div>
          <div v-if="selected.options?.length" class="evidence-options">
            <div v-for="opt in selected.options" :key="opt.key">{{ opt.key }}. {{ opt.text }}</div>
          </div>
          <div v-if="selected.correctAnswer" class="evidence-v">
            <strong>参考答案：</strong>{{ selected.correctAnswer }}
          </div>
          <div v-if="selected.analysisText" class="evidence-v muted">
            <strong>解析：</strong>{{ selected.analysisText }}
          </div>
        </div>
        <el-alert
          v-if="isReviewed"
          class="reviewed-alert"
          type="info"
          :closable="false"
          show-icon
          title="该讲解已评阅，不能重复提交。"
        />
        <el-alert
          v-if="!isReviewed && !hasEvidence"
          type="warning"
          :closable="false"
          show-icon
          title="证据不完整：缺少题干/参考答案。只能选择“退回”并注明原因，不能通过。"
        />
        <div class="evidence-row">
          <span>错因：{{ errorTypeLabel(selected.errorType || "") || "—" }}</span>
          <span>等待：{{ waitingText(selected.waitingSeconds) }}</span>
        </div>
        <div v-if="selected.studentNote" class="evidence-v">
          <strong>学生笔记：</strong>{{ selected.studentNote }}
        </div>

        <div class="section-label">AI 讲解</div>
        <div class="review-content">{{ selected.content || "（空讲解）" }}</div>
        <div v-if="selected.sources?.length" class="review-sources">
          <div class="label">引用来源</div>
          <div v-for="source in selected.sources" :key="source.kpId">
            {{ source.kpId }} · {{ source.title || source.kpId }} · {{ source.textbookPage || "页码待确认" }}
          </div>
        </div>

        <div class="section-label">评分</div>
        <div class="score-row" role="radiogroup" aria-label="评分 1 到 5 分">
          <button
            v-for="n in 5"
            :key="n"
            type="button"
            class="score-pill"
            :class="{ active: score === n }"
            role="radio"
            :aria-checked="score === n"
            @click="score = n"
          >
            {{ n }}
          </button>
        </div>
        <div class="section-label">评阅结论</div>
        <el-radio-group v-model="result" class="result-group">
          <el-radio-button value="PASS" :disabled="!hasEvidence || isReviewed">通过</el-radio-button>
          <el-radio-button value="RETURN" :disabled="isReviewed">退回</el-radio-button>
        </el-radio-group>
        <p v-if="!hasEvidence && !isReviewed" class="result-hint">
          证据不完整时只能退回；请在评语里写清缺失项。
        </p>
        <el-input
          v-model="comment"
          type="textarea"
          aria-label="评语"
          :rows="4"
          maxlength="500"
          show-word-limit
          placeholder="填写评语：讲解准确性、错因贴合度与后续建议"
        />
        <div class="drawer-actions">
          <el-button @click="drawerVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="submit">
            提交评阅
          </el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import PageState from "../components/PageState.vue";
import { listReviews, submitReview, type ReviewItem } from "../api/review";
import { ApiError } from "../api/request";

const records = ref<ReviewItem[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(10);
const statusFilter = ref<"PENDING" | "REVIEWED" | "ALL">("PENDING");
const loading = ref(false);
const error = ref("");
const drawerVisible = ref(false);
const selected = ref<ReviewItem | null>(null);
const score = ref<number | null>(null);
const comment = ref("");
const result = ref<"PASS" | "RETURN">("PASS");
const submitting = ref(false);

const ERROR_LABELS: Record<string, string> = {
  CONCEPT_CONFUSION: "概念混淆",
  METHOD_MISSING: "方法缺失",
  STEP_OMISSION: "步骤遗漏",
  CALCULATION_ERROR: "计算失误",
  BOUNDARY_ERROR: "边界条件"
};

const errorTypeLabel = (value: string) => ERROR_LABELS[value] ?? (value || "—");
const waitingText = (seconds?: number) => {
  const s = typeof seconds === "number" ? seconds : 0;
  if (s <= 0) return "—";
  if (s < 3600) return `${Math.max(1, Math.floor(s / 60))} 分钟`;
  if (s < 86400) return `${Math.floor(s / 3600)} 小时`;
  return `${Math.floor(s / 86400)} 天`;
};
// C3：题干与参考答案齐全才算证据完整；缺任一只能退回（不能通过）；已评阅禁止重复提交
const hasEvidence = computed(
  () =>
    !!(selected.value?.questionText ?? "").trim() &&
    !!(selected.value?.correctAnswer ?? "").trim()
);
const isReviewed = computed(() => selected.value?.reviewStatus === "REVIEWED");
const canSubmit = computed(
  () =>
    !isReviewed.value &&
    score.value != null &&
    comment.value.trim().length > 0 &&
    (hasEvidence.value || result.value === "RETURN")
);
const sourceText = (row: ReviewItem) =>
  (row.sources ?? []).map((source) => source.kpId).filter(Boolean).join(" / ") || "无引用";

let loadSeq = 0;

/** 切换状态筛选必须回到第 1 页，否则会先请求空页再回退 */
const onStatusChange = () => {
  page.value = 1;
  load();
};

const load = async () => {
  const seq = ++loadSeq;
  loading.value = true;
  error.value = "";
  try {
    const data = await listReviews({
      page: page.value,
      size: size.value,
      status: statusFilter.value
    });
    if (seq !== loadSeq) return; // 快速翻页时丢弃旧响应
    records.value = data.records ?? [];
    total.value = data.total ?? 0;
    // 评阅掉最后一页最后一条后，当前页会变空；自动退回上一页，避免分页器被空态一起卸载
    if (records.value.length === 0 && page.value > 1) {
      page.value -= 1;
      await load();
    }
  } catch (e) {
    if (seq !== loadSeq) return;
    error.value = (e as Error).message || "评阅队列加载失败";
  } finally {
    if (seq === loadSeq) loading.value = false;
  }
};


const openReview = (row: ReviewItem) => {
  selected.value = row;
  score.value = null;
  comment.value = "";
  // 题干与参考答案齐全才能默认通过；缺任一只能退回
  const complete = !!(row.questionText ?? "").trim() && !!(row.correctAnswer ?? "").trim();
  result.value = complete ? "PASS" : "RETURN";
  drawerVisible.value = true;
};

const resetForm = () => {
  selected.value = null;
  score.value = null;
  comment.value = "";
  result.value = "PASS";
};

/** 关闭动画结束时可能已切到下一条，确实关闭才清空 */
const onReviewClosed = () => {
  if (!drawerVisible.value) resetForm();
};

const submit = async () => {
  if (!selected.value?.logPk || !canSubmit.value) return;
  submitting.value = true;
  try {
    await submitReview(selected.value.logPk, {
      score: score.value as number,
      comment: comment.value.trim(),
      result: result.value
    });
    ElMessage.success("评阅已提交");
    drawerVisible.value = false;
    await load();
  } catch (e) {
    if (e instanceof ApiError && e.code === 40900) {
      ElMessage.warning("这条讲解已经评阅过了");
      drawerVisible.value = false;
      await load();
    } else {
      ElMessage.error((e as Error).message || "提交失败");
    }
  } finally {
    submitting.value = false;
  }
};

onMounted(load);
</script>

<style scoped>
.page {
  max-width: var(--app-page-max);
}
.stat-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.stat-card {
  text-align: left;
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
.stat-value.small {
  font-size: var(--app-fs-lg);
}
.table-card {
  overflow: hidden;
}
.model {
  margin-right: 6px;
  color: var(--app-text-secondary);
  font-size: var(--app-fs-xs);
}
.pager {
  display: flex;
  justify-content: flex-end;
  padding-top: 14px;
}
.mobile-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.mobile-card {
  width: 100%;
}
.mobile-head,
.mobile-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.mobile-meta {
  margin-top: 6px;
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
}
.mobile-content {
  margin: 10px 0;
  line-height: 1.6;
  color: var(--app-text);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.sources {
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.review-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.review-time {
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
  margin-left: auto;
}
.review-content {
  margin: 14px 0;
  line-height: 1.75;
  color: var(--app-text);
  white-space: pre-wrap;
  background: var(--app-surface-muted);
  border-radius: var(--app-radius-md);
  padding: 14px;
}
.review-sources {
  color: var(--app-text-secondary);
  font-size: var(--app-fs-xs);
  line-height: 1.7;
}
.review-sources .label {
  color: var(--app-text-muted);
  margin-bottom: 4px;
}
.section-label {
  margin: 16px 0 8px;
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
  font-weight: 500;
}
.score-row {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
}
.score-pill {
  width: 44px;
  height: 40px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  background: var(--app-surface);
  color: var(--app-text-secondary);
  font-size: var(--app-fs-md);
  font-weight: 600;
  cursor: pointer;
  transition: background-color var(--app-dur-fast) var(--app-ease-standard),
    border-color var(--app-dur-fast) var(--app-ease-standard),
    color var(--app-dur-fast) var(--app-ease-standard);
}
.score-pill:hover {
  border-color: var(--app-primary);
  color: var(--app-primary);
}
.score-pill.active {
  background: var(--app-primary);
  border-color: var(--app-primary);
  color: var(--app-accent-contrast);
}
.score-pill:focus-visible {
  outline: 2px solid var(--app-primary);
  outline-offset: 2px;
}
.drawer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
}
.desktop-only {
  display: block;
}
.mobile-only {
  display: none;
}
:deep(.el-drawer) {
  max-width: 92vw;
}
@media (max-width: 1024px) {
  .stat-row {
    grid-template-columns: 1fr;
  }
  .desktop-only {
    display: none;
  }
  .mobile-only {
    display: flex;
  }
}
@media (max-width: 768px) {
  :deep(.el-drawer) {
    width: 92vw !important;
  }
}
.head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.status-select {
  width: 108px;
}
.result-group {
  margin-bottom: 12px;
}
.result-hint {
  margin: -4px 0 10px;
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
}
.reviewed-alert {
  margin-bottom: 10px;
}
.evidence-block {
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  padding: 10px 12px;
  background: var(--app-surface-muted);
  margin-bottom: 10px;
}
.evidence-k {
  font-weight: 600;
  color: var(--app-text);
  margin-bottom: 6px;
}
.evidence-v {
  color: var(--app-text-secondary);
  line-height: 1.6;
}
.evidence-v.muted {
  color: var(--app-text-muted);
}
.evidence-options {
  margin: 6px 0;
  color: var(--app-text-secondary);
  line-height: 1.6;
}
.evidence-row {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
  margin-bottom: 8px;
}
</style>
