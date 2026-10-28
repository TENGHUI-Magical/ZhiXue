<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>学情画像看板</h2>
        <p class="sub">数据来自最近一次画像快照；没有快照时先完成一次练习。</p>
      </div>
      <el-button type="primary" plain @click="router.push('/diagnosis')">去练习</el-button>
    </div>

    <PageState
      :loading="loading"
      :error="error"
      :empty="!loading && !error && !portrait"
      empty-text="还没有画像数据，先完成一次错题诊断或练习"
      @retry="load"
    >
      <template #empty-action>
        <el-button type="primary" @click="router.push('/diagnosis')">去诊断工作台</el-button>
      </template>

      <div class="summary-row">
        <el-card class="summary-card" shadow="never">
          <div class="summary-label">统计日期</div>
          <div class="summary-value">{{ portrait?.statDate || "—" }}</div>
        </el-card>
        <el-card class="summary-card" shadow="never">
          <div class="summary-label">累计作答</div>
          <div class="summary-value">{{ portrait?.totalPractice ?? 0 }}</div>
        </el-card>
        <el-card class="summary-card" shadow="never">
          <div class="summary-label">累计错误</div>
          <div class="summary-value">{{ portrait?.totalError ?? 0 }}</div>
        </el-card>
      </div>

      <el-row :gutter="16">
        <el-col :xs="24" :md="10">
          <el-card header="五维能力雷达">
            <svg
              v-if="radarLabels.length"
              class="radar-svg"
              viewBox="0 0 320 300"
              role="img"
              :aria-label="radarAriaLabel"
            >
              <polygon
                v-for="level in gridLevels"
                :key="level"
                :points="gridPolygon(level)"
                class="radar-grid"
              />
              <line
                v-for="axis in radarLabels"
                :key="axis.key"
                :x1="center.x"
                :y1="center.y"
                :x2="axis.outerX"
                :y2="axis.outerY"
                class="radar-axis"
              />
              <polygon :points="radarPolygon" class="radar-area" />
              <circle
                v-for="point in radarPoints"
                :key="point.key"
                :cx="point.x"
                :cy="point.y"
                r="3.5"
                class="radar-dot"
              />
              <text
                v-for="axis in radarLabels"
                :key="`${axis.key}-label`"
                :x="axis.labelX"
                :y="axis.labelY"
                text-anchor="middle"
                class="radar-label"
              >
                {{ axis.name }}
              </text>
            </svg>
            <el-empty v-else description="暂无维度配置" :image-size="72" />
            <div v-if="hasEmptyDimension" class="chart-note">
              灰色维度暂无样本，显示为 0，不代表能力为 0。
            </div>
            <table class="sr-only">
              <caption>五维能力分数</caption>
              <tbody>
                <tr v-for="axis in radarLabels" :key="axis.key">
                  <th>{{ axis.name }}</th>
                  <td>{{ axis.displayValue }}</td>
                </tr>
              </tbody>
            </table>
          </el-card>
        </el-col>
        <el-col :xs="24" :md="14">
          <el-card header="薄弱知识点 TOP">
            <div v-if="weakest.length" class="weak-list">
              <div v-for="item in weakest" :key="item.kpId" class="weak-item">
                <div class="weak-main">
                  <span class="weak-name">{{ item.kpId }} {{ item.name || "" }}</span>
                  <span class="weak-meta">样本 {{ item.sampleCount ?? 0 }} 次</span>
                </div>
                <el-progress
                  :percentage="Math.round((item.mastery ?? 0) * 100)"
                  :status="(item.mastery ?? 0) < 0.6 ? 'exception' : 'success'"
                  class="weak-progress"
                />
              </div>
            </div>
            <el-empty
              v-else
              description="样本不足：每个知识点至少 3 次练习才会进入薄弱点排名"
              :image-size="72"
            />
          </el-card>

          <el-card header="错因分布（5 类标准错因）" class="mt16">
            <div v-if="errorRows.length" class="error-list">
              <div v-for="row in errorRows" :key="row.key" class="error-item">
                <div class="error-head">
                  <span>{{ row.label }}</span>
                  <span class="error-count">{{ row.count }} 次</span>
                </div>
                <el-progress
                  :percentage="row.percent"
                  :stroke-width="8"
                  :show-text="false"
                  class="error-progress"
                />
              </div>
            </div>
            <el-empty v-else description="暂无错因分布数据" :image-size="72" />
          </el-card>
        </el-col>
      </el-row>
    </PageState>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import PageState from "../components/PageState.vue";
import { getPortrait, type Portrait } from "../api/portrait";
import { listCourses, type DimensionDef } from "../api/course";

const router = useRouter();
const loading = ref(false);
const error = ref("");
const portrait = ref<Portrait | null>(null);
const dimensionDefs = ref<DimensionDef[]>([]);

const ERROR_LABELS: Record<string, string> = {
  CONCEPT_CONFUSION: "概念混淆",
  METHOD_MISSING: "方法缺失",
  STEP_OMISSION: "步骤遗漏",
  CALCULATION_ERROR: "计算失误",
  BOUNDARY_ERROR: "边界条件"
};

const center = { x: 160, y: 145 };
const outerRadius = 104;

const weakest = computed(() => portrait.value?.weakestKps ?? []);
const hasEmptyDimension = computed(() =>
  dimensionDefs.value.some((def) => portrait.value?.dimensionScores?.[def.key] == null)
);

const errorRows = computed(() => {
  const dist = portrait.value?.errorDist ?? {};
  const total = Object.values(dist).reduce((sum, value) => sum + (value ?? 0), 0);
  return Object.entries(dist)
    .filter(([, count]) => count > 0)
    .sort((a, b) => b[1] - a[1])
    .map(([key, count]) => ({
      key,
      label: ERROR_LABELS[key] ?? key,
      count,
      percent: total > 0 ? Math.round((count / total) * 100) : 0
    }));
});

const radarLabels = computed(() => {
  const defs = dimensionDefs.value;
  return defs.map((def, index) => {
    const angle = -Math.PI / 2 + (index * 2 * Math.PI) / Math.max(defs.length, 1);
    const outerX = center.x + Math.cos(angle) * outerRadius;
    const outerY = center.y + Math.sin(angle) * outerRadius;
    const labelRadius = outerRadius + 24;
    const rawValue = portrait.value?.dimensionScores?.[def.key];
    const value = rawValue ?? 0;
    return {
      key: def.key,
      name: def.name,
      value,
      displayValue: rawValue == null ? "暂无样本" : `${Math.round(rawValue * 100)}%`,
      outerX,
      outerY,
      labelX: center.x + Math.cos(angle) * labelRadius,
      labelY: center.y + Math.sin(angle) * labelRadius,
      x: center.x + Math.cos(angle) * outerRadius * value,
      y: center.y + Math.sin(angle) * outerRadius * value
    };
  });
});

const radarPoints = computed(() =>
  radarLabels.value.map((axis) => ({ key: axis.key, x: axis.x, y: axis.y }))
);

const radarPolygon = computed(() =>
  radarPoints.value.map((point) => `${point.x},${point.y}`).join(" ")
);

const radarAriaLabel = computed(
  () =>
    "五维能力雷达图：" +
    radarLabels.value.map((axis) => `${axis.name}${axis.displayValue}`).join("，")
);

const gridLevels = [0.25, 0.5, 0.75, 1];

function gridPolygon(level: number) {
  return radarLabels.value
    .map((axis) => {
      const x = center.x + (axis.outerX - center.x) * level;
      const y = center.y + (axis.outerY - center.y) * level;
      return `${x},${y}`;
    })
    .join(" ");
}

const load = async () => {
  loading.value = true;
  error.value = "";
  try {
    const [data, courses] = await Promise.all([getPortrait(), listCourses().catch(() => [])]);
    portrait.value = data;
    const course = courses[0];
    dimensionDefs.value = course?.dimensionConfig?.length
      ? course.dimensionConfig
      : Object.keys(data?.dimensionScores ?? {}).map((key) => ({ key, name: key }));
  } catch (e) {
    error.value = (e as Error).message || "画像加载失败";
  } finally {
    loading.value = false;
  }
};

onMounted(load);
</script>

<style scoped>
.page {
  max-width: 1240px;
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
  color: var(--app-text-muted);
  font-size: 13px;
}
.summary-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.summary-card {
  text-align: left;
}
.summary-label {
  color: var(--app-text-muted);
  font-size: 12px;
}
.summary-value {
  margin-top: 6px;
  font-size: 24px;
  font-weight: 700;
  color: var(--app-text);
}
.radar-svg {
  width: 100%;
  height: 320px;
}
.radar-grid {
  fill: none;
  stroke: var(--app-border);
  stroke-width: 1;
}
.radar-axis {
  stroke: var(--app-border);
  stroke-width: 1;
}
.radar-area {
  fill: color-mix(in srgb, var(--app-primary) 22%, transparent);
  stroke: var(--app-primary);
  stroke-width: 2;
}
.radar-dot {
  fill: var(--app-primary);
}
.radar-label {
  fill: var(--app-text-secondary);
  font-size: 11px;
}
.chart-note {
  color: var(--app-text-muted);
  font-size: 12px;
  line-height: 1.5;
}
.weak-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.weak-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.weak-main {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}
.weak-name {
  font-size: 13px;
  color: var(--app-text);
}
.weak-meta {
  color: var(--app-text-muted);
  font-size: 12px;
  flex: none;
}
.weak-progress {
  width: 100%;
}
.error-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.error-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
  color: var(--app-text);
  margin-bottom: 4px;
}
.error-count {
  color: var(--app-text-muted);
}
.error-progress {
  width: 100%;
}
.mt16 {
  margin-top: 16px;
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
@media (max-width: 768px) {
  .page-head {
    flex-direction: column;
  }
  .summary-row {
    grid-template-columns: 1fr;
  }
  .radar-svg {
    height: 280px;
  }
}
</style>
