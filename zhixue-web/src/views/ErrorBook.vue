<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>我的错题本</h2>
        <p class="sub">按知识点与状态筛选错题；建议先独立作答，再查看解析。</p>
      </div>
      <el-button type="primary" @click="router.push('/diagnosis')">录入错题</el-button>
    </div>

    <el-card class="filter-card">
      <div class="filters">
        <el-select
          v-model="kpId"
          clearable
          filterable
          placeholder="全部知识点"
          class="filter"
          aria-label="按知识点筛选错题"
        >
          <el-option
            v-for="item in kpOptions"
            :key="item.kpId"
            :label="`${item.kpId} ${item.name}`"
            :value="item.kpId"
          />
        </el-select>
        <el-select v-model="status" clearable placeholder="全部状态" class="filter" aria-label="按状态筛选错题">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已确认" value="CONFIRMED" />
        </el-select>
        <el-button @click="search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
    </el-card>

    <PageState
      :loading="loading"
      :error="error"
      :empty="!loading && !error && records.length === 0"
      empty-text="暂无错题记录，完成一次诊断后自动归档。"
      @retry="load"
    >
      <template #empty-action>
        <el-button type="primary" @click="router.push('/diagnosis')">开始诊断</el-button>
      </template>

      <el-card class="table-card desktop-only">
        <el-table :data="records" row-key="logId" stripe>
          <el-table-column prop="logId" label="编号" width="150" show-overflow-tooltip />
          <el-table-column label="知识点" width="160" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.kpId || "—" }} {{ row.kpName || "" }}
            </template>
          </el-table-column>
          <el-table-column label="错因" width="110">
            <template #default="{ row }">
              <el-tag v-if="row.errorType" size="small" type="warning">
                {{ errorTypeLabel(row.errorType) }}
              </el-tag>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column label="题干（校正后）" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.correctedText || row.ocrText || "（未校正）" }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'CONFIRMED' ? 'success' : 'info'" size="small">
                {{ statusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="录入时间" width="150" show-overflow-tooltip />
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.qId && row.kpId"
                link
                type="primary"
                @click="openPractice(row as ErrorRecordItem)"
              >
                开始练习
              </el-button>
              <span v-else class="pending-link" title="该错题尚未关联题库题目，暂不能直接练习">
                待关联题目
              </span>
              <el-button
                v-if="!row.qId || !row.kpId"
                link
                type="primary"
                @click="goDiagnosis(row as ErrorRecordItem)"
              >
                去诊断
              </el-button>
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
        <el-card v-for="row in records" :key="row.logId" class="mobile-card">
          <div class="mobile-head">
            <span class="mobile-id">{{ row.logId }}</span>
            <el-tag :type="row.status === 'CONFIRMED' ? 'success' : 'info'" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </div>
          <div class="mobile-kp">{{ row.kpId || "—" }} {{ row.kpName || "" }}</div>
          <div class="mobile-text">{{ row.correctedText || row.ocrText || "（未校正）" }}</div>
          <div class="mobile-foot">
            <el-tag v-if="row.errorType" size="small" type="warning">
              {{ errorTypeLabel(row.errorType) }}
            </el-tag>
            <el-button
              v-if="row.qId && row.kpId"
              link
              type="primary"
              @click="openPractice(row)"
            >
              开始练习
            </el-button>
            <span v-else class="pending-link">待关联题目</span>
            <el-button
              v-if="!row.qId || !row.kpId"
              link
              type="primary"
              @click="goDiagnosis(row)"
            >
              去诊断
            </el-button>
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

    <QuestionPracticeDrawer
      v-model="drawerVisible"
      :q-id="activeQId"
      :kp-id="activeKpId"
      @completed="drawerVisible = false"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import PageState from "../components/PageState.vue";
import QuestionPracticeDrawer from "../components/QuestionPracticeDrawer.vue";
import { listErrorRecords, type ErrorRecordItem } from "../api/errorBook";
import { getKnowledgeGraph } from "../api/course";
import { DEFAULT_COURSE_ID } from "../api/diagnosis";

const router = useRouter();
const records = ref<ErrorRecordItem[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(10);
const kpId = ref("");
const status = ref("");
const loading = ref(false);
const error = ref("");
const kpOptions = ref<{ kpId: string; name: string }[]>([]);
const drawerVisible = ref(false);
const activeQId = ref("");
const activeKpId = ref("");

const ERROR_LABELS: Record<string, string> = {
  CONCEPT_CONFUSION: "概念混淆",
  METHOD_MISSING: "方法缺失",
  STEP_OMISSION: "步骤遗漏",
  CALCULATION_ERROR: "计算失误",
  BOUNDARY_ERROR: "边界条件"
};

const errorTypeLabel = (value: string) => ERROR_LABELS[value] ?? value;
const statusLabel = (value?: string) =>
  value === "CONFIRMED" ? "已确认" : value === "DRAFT" ? "草稿" : value || "—";

let loadSeq = 0;

const load = async () => {
  const seq = ++loadSeq;
  loading.value = true;
  error.value = "";
  try {
    const result = await listErrorRecords({
      page: page.value,
      size: size.value,
      kpId: kpId.value || undefined,
      status: status.value || undefined
    });
    if (seq !== loadSeq) return; // 快速筛选/翻页时丢弃旧响应
    records.value = result.records ?? [];
    total.value = result.total ?? 0;
  } catch (e) {
    if (seq !== loadSeq) return;
    error.value = (e as Error).message || "错题列表加载失败";
  } finally {
    if (seq === loadSeq) loading.value = false;
  }
};

const search = () => {
  page.value = 1;
  load();
};

const resetFilters = () => {
  kpId.value = "";
  status.value = "";
  page.value = 1;
  load();
};


const openPractice = (row: ErrorRecordItem) => {
  if (!row.qId || !row.kpId) {
    ElMessage.warning("这条错题缺少题目编号，无法直接练习");
    return;
  }
  activeQId.value = row.qId;
  activeKpId.value = row.kpId;
  drawerVisible.value = true;
};

/** 未关联题库题目的错题：去诊断台重新录入并选择关联题目 */
const goDiagnosis = (row: ErrorRecordItem) => {
  router.push({
    path: "/diagnosis",
    query: row.kpId ? { kpId: row.kpId } : undefined
  });
};

const loadKpOptions = async () => {
  try {
    const graph = await getKnowledgeGraph(DEFAULT_COURSE_ID);
    kpOptions.value = (graph.nodes ?? []).map((node) => ({
      kpId: node.id ?? "",
      name: node.name ?? ""
    }));
  } catch {
    // 知识点筛选是增强项，加载失败不阻断错题列表
  }
};

onMounted(() => {
  loadKpOptions();
  load();
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
}
.page-head h2 {
  margin: 0;
}
.sub {
  margin: 6px 0 0;
  color: var(--app-text-secondary);
  font-size: var(--app-fs-sm);
}
.filter-card {
  margin-bottom: 16px;
}
.filters {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
.filter {
  width: 220px;
}
.table-card {
  overflow: hidden;
}
.pager {
  display: flex;
  justify-content: flex-end;
  padding-top: 14px;
}
.pending-link {
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
  cursor: help;
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
.mobile-id {
  font-weight: 600;
  color: var(--app-text);
  font-size: var(--app-fs-sm);
}
.mobile-kp {
  margin-top: 8px;
  color: var(--app-text-secondary);
  font-size: var(--app-fs-sm);
}
.mobile-text {
  margin: 8px 0 12px;
  line-height: 1.6;
  color: var(--app-text);
  word-break: break-word;
}
.desktop-only {
  display: block;
}
.mobile-only {
  display: none;
}
@media (max-width: 1024px) {
  .page-head {
    flex-direction: column;
  }
  .filter {
    width: 100%;
  }
  .desktop-only {
    display: none;
  }
  .mobile-only {
    display: flex;
  }
}
</style>
