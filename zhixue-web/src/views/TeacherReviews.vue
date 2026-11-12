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
        <input type="date" v-model="exportFrom" class="export-date" aria-label="导出起始日期" />
        <input type="date" v-model="exportTo" class="export-date" aria-label="导出结束日期" />
        <el-button :loading="loading" @click="load">刷新队列</el-button>
        <el-button :loading="exporting" @click="onExport">导出留痕</el-button>
      </div>
    </div>

    <el-card class="review-brief" shadow="never">
      <div class="brief-main">
        <div>
          <div class="brief-eyebrow">{{ queueEyebrow }}</div>
          <h3 class="brief-title">{{ queueTitle }}</h3>
          <p class="brief-sub">{{ queueSub }}</p>
        </div>
        <div class="brief-stats">
          <div><span>{{ firstStatLabel }}</span><strong>{{ total }}</strong></div>
          <div><span>本页记录</span><strong>{{ records.length }}</strong></div>
          <div><span>评分标准</span><strong>1—5</strong></div>
        </div>
      </div>
    </el-card>

    <PageState
      :loading="loading"
      :error="error"
      :empty="!loading && !error && records.length === 0"
      empty-text="当前没有待评阅的讲解。学生生成讲解后将进入评阅队列。"
      @retry="load"
    >

      <el-card class="table-card desktop-only">
        <div v-if="selectedRows.length" class="batch-bar">
          <span class="batch-count">已选 {{ selectedRows.length }} 条</span>
          <el-button size="small" type="primary" :disabled="batchRunning" @click="runBatch('PASS')">
            批量通过
          </el-button>
          <el-button size="small" type="danger" plain :disabled="batchRunning" @click="runBatch('RETURN')">
            批量退回
          </el-button>
          <el-button size="small" link :disabled="batchRunning" @click="clearSelection">取消选择</el-button>
          <span v-if="batchProgress" class="batch-progress">{{ batchProgress }}</span>
        </div>
        <el-table ref="tableRef" :data="records" row-key="logPk" stripe @selection-change="onSelectionChange">
          <el-table-column type="selection" width="44" :selectable="canSelectRow" />
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

    <el-drawer
      v-model="drawerVisible"
      title="讲解评阅"
      :direction="isMobile ? 'btt' : 'rtl'"
      :size="isMobile ? '92%' : '520px'"
      :class="['review-drawer', { 'glass-sheet': isMobile }]"
      append-to-body
      @closed="onReviewClosed"
    >
      <div v-if="isMobile" class="sheet-grabber" aria-hidden="true"></div>
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
        <div class="tpl-row">
          <span class="tpl-label">常用评语</span>
          <button
            v-for="tpl in COMMENT_TEMPLATES"
            :key="tpl"
            type="button"
            class="tpl-btn"
            :disabled="isReviewed"
            @click="applyTemplate(tpl)"
          >
            {{ tpl }}
          </button>
        </div>
        <el-input
          v-model="comment"
          class="comment-input"
          type="textarea"
          aria-label="评语"
          :rows="4"
          maxlength="500"
          :readonly="isReviewed"
          show-word-limit
          placeholder="填写评语：讲解准确性、错因贴合度与后续建议"
        />
        <div class="drawer-actions">
          <el-button @click="drawerVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="submit(false)">
            提交评阅
          </el-button>
          <el-button
            type="primary"
            plain
            :disabled="!canSubmit || submitting"
            @click="submit(true)"
          >
            提交并评阅下一条
          </el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ElMessage, ElMessageBox, type TableInstance } from "element-plus";
import PageState from "../components/PageState.vue";
import { ApiError } from "../api/request";
import { describeError, isGloballyNotified } from "../api/errors";
import { getExplain } from "../api/diagnosis";
import { exportReviewLog, listReviews, submitReview, type ReviewItem } from "../api/review";
import { useIsMobile } from "../composables/useBreakpoint";
import { reportError } from "../utils/telemetry";

/** 手机（<768）：评阅抽屉改为底部 Sheet，桌面保持右侧全高抽屉 */
const isMobile = useIsMobile();

const records = ref<ReviewItem[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(10);
const statusFilter = ref<"PENDING" | "REVIEWED" | "ALL">("PENDING");
const loading = ref(false);
const error = ref("");
const exporting = ref(false);
/** 导出起止日期（可选）：超限提示要求缩小范围时，老师可在此设置后重试 */
const exportFrom = ref("");
const exportTo = ref("");

/** 导出评阅留痕 CSV（与当前状态筛选同口径，完整结果；可按起止日期缩小范围） */
const onExport = async () => {
  exporting.value = true;
  try {
    await exportReviewLog({
      status: statusFilter.value,
      from: exportFrom.value || undefined,
      to: exportTo.value || undefined
    });
    ElMessage.success("已导出评阅留痕 CSV");
  } catch {
    // downloadCsv 已按业务 code 提示，这里只需止住未处理拒绝
  } finally {
    exporting.value = false;
  }
};
const drawerVisible = ref(false);
const selected = ref<ReviewItem | null>(null);
const score = ref<number | null>(null);
const comment = ref("");
const result = ref<"PASS" | "RETURN">("PASS");
const submitting = ref(false);

/** 批量评阅：仅桌面表格可勾选，已评阅行由 selectable 排除 */
const tableRef = ref<TableInstance>();
const selectedRows = ref<ReviewItem[]>([]);
const batchRunning = ref(false);
const batchProgress = ref("");

const ERROR_LABELS: Record<string, string> = {
  CONCEPT_CONFUSION: "概念混淆",
  METHOD_MISSING: "方法缺失",
  STEP_OMISSION: "步骤遗漏",
  CALCULATION_ERROR: "计算失误",
  BOUNDARY_ERROR: "边界条件"
};

const errorTypeLabel = (value: string) => ERROR_LABELS[value] ?? (value || "—");

/** 请求层已统一提示的码（40300/40900/42900/50000/网络）不再重复弹 toast */
const notifyFailure = (error: unknown, fallback: string) => {
  const code = (error as { code?: number } | null)?.code;
  if (typeof code === "number" && isGloballyNotified(code)) return;
  ElMessage.error(describeError(error, fallback));
};
const queueEyebrow = computed(() =>
  statusFilter.value === "ALL" ? "全部评阅" : statusFilter.value === "REVIEWED" ? "评阅记录" : "评阅队列"
);
const queueTitle = computed(() =>
  statusFilter.value === "ALL"
    ? `共 ${total.value} 条`
    : statusFilter.value === "REVIEWED"
      ? `已评阅 ${total.value} 条`
      : `待评阅 ${total.value} 条`
);
const firstStatLabel = computed(() =>
  statusFilter.value === "ALL" ? "全部" : statusFilter.value === "REVIEWED" ? "已评阅" : "待评阅"
);
const queueSub = computed(() => {
  if (statusFilter.value === "ALL") return "全部评阅记录；切换到“待评阅”处理未评阅队列。";
  if (statusFilter.value === "REVIEWED") return "评阅结论会同步回写讲解质量，供后续抽样与复盘。";
  if (!records.value.length) return "队列为空时，学生生成的新讲解会自动进入这里。";
  if (oldestWaitingText.value) {
    return `本页待评阅最长等待 ${oldestWaitingText.value}；建议先处理等待最久的讲解。`;
  }
  return "本页暂未读到等待时长。";
});

const oldestWaitingText = computed(() => {
  const values = records.value
    .map((row) => row.waitingSeconds)
    .filter((value): value is number => typeof value === "number" && value > 0);
  if (!values.length) return "";
  return waitingText(Math.max(...values));
});

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

/** 评语快捷短语：对应通过、退回、引用存疑三种常见场景 */
const COMMENT_TEMPLATES = [
  "讲解准确，证据充分。",
  "错因判断偏离，请重新生成。",
  "引用页码与教材不符，请核对。"
];

/** 已有评语时另起一行追加，避免覆盖老师已经写好的内容 */
const applyTemplate = (text: string) => {
  if (isReviewed.value) return;
  comment.value = comment.value ? `${comment.value}\n${text}` : text;
};

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


let openReviewSeq = 0;
const openReview = async (row: ReviewItem) => {
  const seq = ++openReviewSeq;
  selected.value = row;
  score.value = null;
  // 先清空上一条评语，再按当前 explainId 回填，避免串数据
  comment.value = "";
  // 题干与参考答案齐全才能默认通过；缺任一只能退回
  const complete = !!(row.questionText ?? "").trim() && !!(row.correctAnswer ?? "").trim();
  result.value = complete ? "PASS" : "RETURN";
  drawerVisible.value = true;
  if (!row.explainId) return;
  try {
    const detail = await getExplain(row.explainId);
    if (seq !== openReviewSeq || selected.value?.logPk !== row.logPk) return;
    comment.value = detail?.reviewComment ?? "";
    if (detail?.reviewResult) result.value = detail.reviewResult;
  } catch (e) {
    if (seq === openReviewSeq) notifyFailure(e, "评阅详情加载失败，请重试");
  }
};

const resetForm = () => {
  openReviewSeq += 1;
  selected.value = null;
  score.value = null;
  comment.value = "";
  result.value = "PASS";
};

/** 关闭动画结束时可能已切到下一条，确实关闭才清空 */
const onReviewClosed = () => {
  if (!drawerVisible.value) resetForm();
};

/** 提交流程共用：advance=true 时提交成功后不关抽屉，直接打开本页下一条待评阅 */
const submit = async (advance: boolean) => {
  const row = selected.value;
  if (!row?.logPk || !canSubmit.value || submitting.value) return;
  const logPk = row.logPk;
  const scoreValue = score.value as number;
  const resultValue = result.value;
  const resultLabel = resultValue === "PASS" ? "通过" : "退回";
  // 评阅不可逆：先把后果讲清楚，老师点“再检查一下”就停在原表单
  try {
    await ElMessageBox.confirm(
      `评分 ${scoreValue} 分 · 结论「${resultLabel}」。通过后学生立即可见该讲解的评阅结果；退回后学生需重新生成讲解。提交后不能修改或撤销。`,
      "确认提交评阅？",
      { confirmButtonText: "确认提交", cancelButtonText: "再检查一下", type: "warning" }
    );
  } catch {
    return;
  }
  submitting.value = true;
  try {
    await submitReview(logPk, {
      score: scoreValue,
      comment: comment.value.trim(),
      result: resultValue
    });
    ElMessage.success(
      resultValue === "PASS" ? "已通过，学生现在能看到评阅结果" : "已退回，学生需要重新生成讲解"
    );
    // 先刷新队列，再按刷新后的 records 定位下一条，避免拿到已过期的行
    await load();
    if (!advance) {
      drawerVisible.value = false;
      return;
    }
    const next = records.value.find(
      (item) => item.reviewStatus === "PENDING" && item.logPk !== logPk
    );
    if (next) {
      void openReview(next);
      return;
    }
    ElMessage.info("本页已无待评阅，正在刷新队列");
    await load();
    drawerVisible.value = false;
  } catch (e) {
    if (e instanceof ApiError && e.code === 40900) {
      // 40900 已由请求层统一提示，这里只刷新队列，不再重复弹一次
      drawerVisible.value = false;
      await load();
    } else {
      notifyFailure(e, "提交失败");
    }
  } finally {
    submitting.value = false;
  }
};

const onSelectionChange = (rows: ReviewItem[]) => {
  selectedRows.value = rows;
};

/** 已评阅的行不参与批量，也避免“重新提交”触发 40900 */
const canSelectRow = (row: ReviewItem) =>
  row.reviewStatus === "PENDING" && typeof row.logPk === "number";

const clearSelection = () => {
  tableRef.value?.clearSelection();
  selectedRows.value = [];
};

type BatchAction = "PASS" | "RETURN";
/** 批量通过没有逐条打分入口，统一记 5 分并在确认文案里说明 */
const BATCH_SCORE = 5;
const BATCH_COMMENT_MAX = 200;

/** 收集共同评语；取消返回 null，方便和“提交失败”区分 */
const askBatchComment = async (action: BatchAction): Promise<string | null> => {
  const actionLabel = action === "PASS" ? "通过" : "退回";
  try {
    const { value } = await ElMessageBox.prompt(
      `这 ${selectedRows.value.length} 条讲解会共用一条评语，学生会在评阅结果里看到。`,
      `批量${actionLabel} · 填写共同评语`,
      {
        confirmButtonText: "下一步",
        cancelButtonText: "取消",
        inputType: "textarea",
        inputPlaceholder: "例如：讲解准确，证据充分。",
        inputValidator: (val: string) => {
          const text = (val ?? "").trim();
          if (!text) return "请先填写共同评语";
          if (text.length > BATCH_COMMENT_MAX) return `评语不能超过 ${BATCH_COMMENT_MAX} 字`;
          return true;
        }
      }
    );
    return value.trim();
  } catch {
    return null;
  }
};

/** 批量评阅：无批量端点，只能逐条串行提交 */
const runBatch = async (action: BatchAction) => {
  if (batchRunning.value) return;
  const rows = selectedRows.value.filter(
    (row): row is ReviewItem & { logPk: number } => typeof row.logPk === "number"
  );
  if (!rows.length) {
    ElMessage.warning("请先勾选待评阅的讲解");
    return;
  }
  const actionLabel = action === "PASS" ? "通过" : "退回";
  // 逐条评阅里“证据不全不能通过”，批量同样不能绕过
  if (action === "PASS") {
    const incomplete = rows.filter(
      (row) => !(row.questionText ?? "").trim() || !(row.correctAnswer ?? "").trim()
    );
    if (incomplete.length) {
      ElMessage.warning(
        `所选 ${rows.length} 条中有 ${incomplete.length} 条缺少题干或参考答案，不能批量通过；请改用「批量退回」，或单条打开评阅说明原因。`
      );
      return;
    }
  }
  const sharedComment = await askBatchComment(action);
  if (sharedComment == null) return;
  try {
    await ElMessageBox.confirm(
      action === "PASS"
        ? `将逐条提交 ${rows.length} 条「通过」。批量通过统一记 ${BATCH_SCORE} 分，可在单条评阅中调整；通过后学生立即可见结果，提交后不能修改或撤销。`
        : `将逐条提交 ${rows.length} 条「退回」。退回后学生需重新生成讲解；提交后不能修改或撤销。`,
      `确认批量${actionLabel}？`,
      { confirmButtonText: "确认提交", cancelButtonText: "再检查一下", type: "warning" }
    );
  } catch {
    return;
  }

  let ok = 0;
  let skipped = 0;
  let failed = 0;
  const failureSamples: string[] = [];
  batchRunning.value = true;
  try {
    for (const [index, row] of rows.entries()) {
      batchProgress.value = `正在提交 ${index + 1}/${rows.length}`;
      try {
        await submitReview(row.logPk, {
          score: BATCH_SCORE,
          comment: sharedComment,
          result: action
        });
        ok += 1;
      } catch (e) {
        if (e instanceof ApiError && e.code === 40900) {
          // 已被他人评阅：请求层已统一提示，这里只计数，不重复刷屏
          skipped += 1;
          continue;
        }
        failed += 1;
        reportError(e, "teacher-reviews.batch");
        if (failureSamples.length < 3) {
          failureSamples.push(`${row.userName || "学生"}：${describeError(e, "提交失败")}`);
        }
      }
    }
  } finally {
    batchRunning.value = false;
    batchProgress.value = "";
  }
  const summary = `成功 ${ok} 条，跳过 ${skipped} 条，失败 ${failed} 条`;
  ElMessage.success(failureSamples.length ? `${summary}；失败原因：${failureSamples.join("；")}` : summary);
  await load();
  clearSelection();
};

onMounted(load);
</script>

<style scoped>
.page {
  max-width: var(--app-page-max);
}
/* B 层焦点卡：队列结论带用外层 24px + 近实底 + 高一档阴影（父层已模糊，卡内不再叠 backdrop-filter） */
.review-brief {
  margin-bottom: 16px;
}
.page .review-brief {
  border-radius: var(--app-radius-2xl);
  border-color: var(--app-hairline);
  background: var(--app-surface-raised);
  box-shadow: var(--app-shadow-md);
}
.review-brief :deep(.el-card__body) {
  padding: var(--app-space-6);
}
.brief-stats {
  display: flex;
  gap: var(--app-space-6);
}
.brief-stats span {
  display: block;
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
  white-space: nowrap;
}
.brief-stats strong {
  display: block;
  margin-top: 4px;
  font-size: var(--app-fs-xl);
  font-weight: 600;
  color: var(--app-text);
  font-variant-numeric: tabular-nums;
}
.table-card :deep(.el-table th.el-table__cell) {
  background: transparent;
  color: var(--app-text-muted);
  font-weight: 600;
  border-bottom: 1px solid var(--app-hairline);
}
.table-card {
  overflow: hidden;
}
.batch-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 10px;
  padding: 8px 12px;
  border-radius: var(--app-radius-md);
  background: var(--app-surface-muted);
}
.batch-count {
  color: var(--app-text);
  font-size: var(--app-fs-sm);
  font-weight: 600;
}
.batch-progress {
  margin-left: auto;
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
}
.tpl-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 6px;
}
.tpl-label {
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
}
.tpl-btn {
  padding: 0;
  border: 0;
  background: none;
  color: var(--app-primary);
  font-size: var(--app-fs-xs);
  cursor: pointer;
}
.tpl-btn:hover:not(:disabled) {
  text-decoration: underline;
}
.tpl-btn:disabled {
  color: var(--app-text-muted);
  cursor: not-allowed;
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
/* C 层数据卡：手机列表 16px 圆角 + 发丝边 + 轻阴影，不再叠模糊 */
.mobile-list .mobile-card {
  border-radius: var(--app-radius-lg);
  border-color: var(--app-hairline);
  background: var(--app-surface);
  box-shadow: var(--app-shadow-sm);
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
  min-width: 0;
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
  border-radius: var(--app-radius-inner);
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
    color var(--app-dur-fast) var(--app-ease-standard),
    transform var(--app-dur-fast) var(--app-ease-standard);
}
@media (hover: hover) and (pointer: fine) {
  .score-pill:hover {
    border-color: var(--app-primary);
    color: var(--app-primary);
  }
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
.score-pill:active {
  transform: scale(0.97);
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
/* 底部 Sheet 抓手：仅手机渲染，纯装饰 */
.sheet-grabber {
  width: 40px;
  height: 4px;
  margin: -4px auto 14px;
  border-radius: var(--app-radius-full);
  background: var(--app-border-strong);
}
@media (max-width: 1024px) {
  .brief-stats {
    gap: 18px;
    justify-content: space-between;
  }
  .desktop-only {
    display: none;
  }
  .mobile-only {
    display: flex;
  }
}
@media (max-width: 768px) {
  /* 手机输入字号 ≥16px，避免 iOS 聚焦时自动放大页面 */
  .comment-input :deep(.el-textarea__inner) {
    font-size: 16px;
  }
  .score-pill {
    height: 44px;
  }
  .tpl-btn {
    min-height: 44px;
    display: inline-flex;
    align-items: center;
  }
  .drawer-actions {
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
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
.export-date {
  height: 32px;
  padding: 0 6px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  font-size: 12px;
  color: var(--el-text-color-regular);
  background: var(--app-surface);
}
@media (max-width: 768px) {
  .export-date {
    height: 44px;
    font-size: 16px;
  }
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
  border: 1px solid var(--app-hairline);
  border-radius: var(--app-radius-lg);
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
