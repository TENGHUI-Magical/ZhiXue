<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>学情画像</h2>
        <p class="sub">
          基于最近一次画像快照<span v-if="portrait?.statDate">（{{ portrait.statDate }}）</span>汇总练习与已确认错题数据。
        </p>
      </div>
      <div class="head-actions">
        <span class="sample-chip">样本量 {{ portrait?.totalPractice ?? 0 }}</span>
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" @click="router.push('/diagnosis')">进入诊断</el-button>
      </div>
    </div>

    <PageState
      :loading="loading"
      :error="error"
      :empty="!loading && !error && !portrait"
      empty-text="暂无画像数据，完成一次错题诊断后自动生成。"
      @retry="load"
    >
      <template #empty-action>
        <el-button type="primary" @click="router.push('/diagnosis')">开始诊断</el-button>
      </template>

      <div class="dash">
        <el-card class="c12 hero-card" shadow="never">
          <div class="hero-top">
            <div>
              <div class="hero-eyebrow">掌握概况 · 快照 {{ portrait?.statDate || "—" }}</div>
              <h3 class="hero-title">{{ conclusionText }}</h3>
              <p class="hero-note">
                综合掌握取五维均值；正确率按练习与已确认错题估算，不等同考试成绩；样本充分度按 20 次展示参考，非算法阈值。
              </p>
            </div>
            <el-button type="primary" @click="router.push(primaryTo)">{{ primaryLabel }}</el-button>
          </div>
          <div class="baseline-grid">
            <div class="baseline-row">
              <span class="baseline-name">掌握度</span>
              <span class="baseline-track"><i class="mastery" :style="{ width: masteryPct + '%' }"></i></span>
              <span class="baseline-value">{{ masteryPct }}%</span>
            </div>
            <div class="baseline-row">
              <span class="baseline-name">作答正确率</span>
              <span class="baseline-track"><i class="accuracy" :style="{ width: accuracyPct + '%' }"></i></span>
              <span class="baseline-value">{{ accuracyPct }}%</span>
            </div>
            <div class="baseline-row">
              <span class="baseline-name">样本充分度</span>
              <span class="baseline-track"><i class="sample" :style="{ width: samplePct + '%' }"></i></span>
              <span class="baseline-value">{{ samplePct }}%</span>
            </div>
          </div>
          <div class="hero-stats">
            <span>训练样本 {{ portrait?.totalPractice ?? 0 }} 次</span>
            <span>已确认错误 {{ portrait?.totalError ?? 0 }} 条</span>
            <span>薄弱知识点 {{ weakest.length }} 项</span>
            <span>快照 {{ portrait?.statDate || "—" }}</span>
          </div>
          <el-alert
            v-if="!sampleSufficient"
            class="hero-alert"
            type="warning"
            :closable="false"
            show-icon
            title="样本不足（少于 3 次），暂不参与薄弱点排名。先完成几次练习再看结论。"
          />
        </el-card>

        <el-card class="c7" shadow="never">
          <template #header>
            <div class="card-head"><span>能力维度</span><span class="card-sub">五维掌握度</span></div>
          </template>
          <div v-if="dimensionRows.length" class="cap" tabindex="0" role="group" aria-label="五维能力分数">
            <div v-for="row in dimensionRows" :key="row.key" class="cap-row" :class="row.level">
              <span class="cap-name">{{ row.name }}</span>
              <span class="cap-track"><i :style="{ width: row.pct + '%' }"></i></span>
              <span class="cap-value">{{ row.display }}</span>
            </div>
          </div>
          <el-empty v-else description="暂无维度配置" :image-size="64" />
          <div v-if="hasEmptyDimension" class="chart-note">灰色维度暂无样本，按 0 展示，不代表能力缺失。</div>
          <table class="sr-only">
            <caption>五维能力分数</caption>
            <tbody>
              <tr v-for="axis in dimensionRows" :key="axis.key">
                <th>{{ axis.name }}</th>
                <td>{{ axis.display }}</td>
              </tr>
            </tbody>
          </table>
        </el-card>

        <el-card class="c5" shadow="never">
          <template #header>
            <div class="card-head"><span>薄弱知识点</span><span class="card-sub">前 {{ weakest.length || 0 }} 项</span></div>
          </template>
          <div v-if="weakest.length" class="weak-list">
            <button
              v-for="item in weakest"
              :key="item.kpId"
              type="button"
              class="weak-item"
              :title="`查看 ${item.name || item.kpId} 的知识点详情`"
              @click="openKp(item.kpId)"
            >
              <div class="weak-head">
                <span class="weak-name">{{ item.kpId }} {{ item.name || "" }}</span>
                <span class="weak-right">
                  <span class="weak-meta">作答 {{ item.sampleCount ?? 0 }} 次</span>
                  <span class="weak-pct">掌握度 {{ Math.round((item.mastery ?? 0) * 100) }}%</span>
                </span>
              </div>
              <div class="weak-track">
                <i :style="{ width: Math.round((item.mastery ?? 0) * 100) + '%' }"></i>
              </div>
            </button>
          </div>
          <el-empty v-else description="知识点累计作答不少于 3 次后进入薄弱项排名。" :image-size="56">
            <el-button link type="primary" @click="router.push('/diagnosis')">开始诊断</el-button>
          </el-empty>
        </el-card>

        <el-card class="c7" shadow="never">
          <template #header>
            <div class="card-head"><span>错因分布</span><span class="card-sub">按已确认错题归类</span></div>
          </template>
          <div v-if="errorRows.length" class="cap" tabindex="0" role="group" aria-label="错因分布">
            <div v-for="row in errorRows" :key="row.key" class="cap-row error-row">
              <span class="cap-name">{{ row.label }}</span>
              <span class="cap-track"><i :style="{ width: row.percent + '%' }"></i></span>
              <span class="cap-value">{{ row.count }} 次</span>
            </div>
          </div>
          <el-empty v-else description="暂无错因分布数据" :image-size="64" />
        </el-card>

        <el-card class="c5 insight" shadow="never">
          <template #header>
            <div class="card-head"><span>下一步建议</span><span class="card-sub">依据画像</span></div>
          </template>
          <p v-if="adviceItems.length" class="advice">
            薄弱环节集中在<b>「{{ adviceItems[0].name }}」</b><template v-if="adviceItems[1]">与<b>「{{ adviceItems[1].name }}」</b></template>。
            建议按以下顺序补齐，可分批完成。
          </p>
          <p v-else class="advice">当前样本不足以生成建议，请先完成一次诊断或练习。</p>
          <div v-if="adviceItems.length" class="steps">
            <button
              v-for="(item, index) in adviceItems"
              :key="item.kpId"
              type="button"
              class="step"
              @click="openKp(item.kpId)"
            >
              <i>{{ index + 1 }}</i><span>专项诊断 2 题：{{ item.name }}</span>
            </button>
            <button type="button" class="step" @click="router.push('/error-book')">
              <i>{{ adviceItems.length + 1 }}</i><span>错题本复盘 3 题</span>
            </button>
          </div>
          <div class="advice-foot">
            <span>数据口径：{{ portrait?.totalPractice ?? 0 }} 次训练样本 · {{ portrait?.totalError ?? 0 }} 条已确认错误</span>
            <el-button v-if="adviceItems.length" link type="primary" @click="router.push('/diagnosis')">查看诊断 →</el-button>
          </div>
        </el-card>
      </div>
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

const weakest = computed(() =>
  (portrait.value?.weakestKps ?? [])
    .filter((item) => (item.sampleCount ?? 0) >= 3)
    .sort((a, b) => (a.mastery ?? 1) - (b.mastery ?? 1))
);
const sampleSufficient = computed(() => (portrait.value?.totalPractice ?? 0) >= 3);
const hasEmptyDimension = computed(() =>
  dimensionDefs.value.some((def) => portrait.value?.dimensionScores?.[def.key] == null)
);

const masteryPct = computed(() => {
  const values = Object.values(portrait.value?.dimensionScores ?? {}).filter(
    (value): value is number => typeof value === "number"
  );
  if (!values.length) return 0;
  return Math.round((values.reduce((sum, value) => sum + value, 0) / values.length) * 100);
});

const accuracyPct = computed(() => {
  const total = portrait.value?.totalPractice ?? 0;
  const errors = portrait.value?.totalError ?? 0;
  if (total <= 0) return 0;
  return Math.max(0, Math.min(100, Math.round((1 - errors / total) * 100)));
});

const samplePct = computed(() => Math.min(100, Math.round(((portrait.value?.totalPractice ?? 0) / 20) * 100)));

const dimensionRows = computed(() =>
  dimensionDefs.value.map((def) => {
    const raw = portrait.value?.dimensionScores?.[def.key];
    const pct = typeof raw === "number" ? Math.round(raw * 100) : 0;
    return {
      key: def.key,
      name: def.name,
      pct,
      display: typeof raw === "number" ? `${pct}%` : "暂无样本",
      level: typeof raw !== "number" ? "empty" : pct < 45 ? "crit" : pct < 60 ? "low" : ""
    };
  })
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

const adviceItems = computed(() =>
  weakest.value.slice(0, 2).map((item) => ({
    kpId: item.kpId ?? "",
    name: item.name || item.kpId || "知识点"
  }))
);

const conclusionText = computed(() => {
  const total = portrait.value?.totalPractice ?? 0;
  if (total === 0) return "还没有训练样本，先完成一次诊断或练习";
  if (total < 3) return `样本 ${total} 次，先积累到 3 次再看薄弱点`;
  const first = weakest.value[0];
  return first
    ? `掌握度 ${masteryPct.value}% · 最该补「${first.name || first.kpId}」`
    : `掌握度 ${masteryPct.value}% · 暂无明显薄弱点`;
});

const primaryTo = computed(() => {
  const kpId = weakest.value[0]?.kpId;
  return kpId ? `/knowledge-graph?kpId=${encodeURIComponent(kpId)}` : "/diagnosis";
});
const primaryLabel = computed(() => (weakest.value[0] ? "去练这个知识点" : "进入诊断"));

const openKp = (kpId?: string) => {
  if (!kpId) return;
  router.push(`/knowledge-graph?kpId=${encodeURIComponent(kpId)}`);
};

let loadSeq = 0;

const load = async () => {
  const seq = ++loadSeq;
  loading.value = true;
  error.value = "";
  try {
    const [data, courses] = await Promise.all([getPortrait(), listCourses().catch(() => [])]);
    if (seq !== loadSeq) return; // 连点刷新时丢弃旧响应
    portrait.value = data;
    const course = (courses ?? [])[0];
    dimensionDefs.value = course?.dimensionConfig?.length
      ? course.dimensionConfig
      : Object.keys(data?.dimensionScores ?? {}).map((key) => ({ key, name: key }));
  } catch (e) {
    if (seq !== loadSeq) return;
    error.value = (e as Error).message || "画像加载失败";
  } finally {
    if (seq === loadSeq) loading.value = false;
  }
};

onMounted(load);
</script>

<style scoped>
.page {
  max-width: var(--app-page-max);
}
.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}
.eyebrow {
  font-size: var(--app-fs-xs);
  font-weight: 600;
  letter-spacing: 0.2em;
  color: var(--app-text-muted);
  text-transform: uppercase;
}
.page-head h2 {
  margin: 6px 0 3px;
  font-size: var(--app-fs-xl);
  letter-spacing: -0.012em;
}
.sub {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: var(--app-fs-sm);
}
.head-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.sample-chip {
  height: 28px;
  padding: 0 10px;
  border-radius: var(--app-radius-full);
  display: inline-flex;
  align-items: center;
  font-size: var(--app-fs-xs);
  color: var(--app-text-secondary);
  background: var(--app-surface);
  border: 0.5px solid var(--app-border);
}
.dash {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 16px;
}
.c4 { grid-column: span 4; }
.c5 { grid-column: span 5; }
.c7 { grid-column: span 7; }
.c8 { grid-column: span 8; }
.c12 { grid-column: span 12; }
.hero-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.hero-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.hero-eyebrow {
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
}
.hero-title {
  margin: 8px 0;
  font-size: var(--app-fs-xl);
  line-height: 1.32;
  color: var(--app-text);
}
.hero-note {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: var(--app-fs-xs);
  line-height: 1.6;
}
.baseline-grid {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.baseline-row {
  display: grid;
  grid-template-columns: 96px 1fr 56px;
  align-items: center;
  gap: 12px;
}
.baseline-name {
  color: var(--app-text-secondary);
  font-size: var(--app-fs-sm);
}
.baseline-track {
  height: 8px;
  border-radius: var(--app-radius-full);
  background: var(--app-surface-muted);
  overflow: hidden;
}
.baseline-track i {
  display: block;
  height: 100%;
  border-radius: var(--app-radius-full);
}
.baseline-track i.mastery { background: var(--app-primary); }
.baseline-track i.accuracy { background: var(--app-success); }
.baseline-track i.sample { background: var(--app-warning); }
.baseline-value {
  text-align: right;
  color: var(--app-text);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  font-size: var(--app-fs-sm);
}
.hero-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
}
.hero-alert {
  margin-top: 2px;
}
.card-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.card-sub {
  font-size: var(--app-fs-xs);
  color: var(--app-text-muted);
  font-weight: 400;
}
.ring-card :deep(.el-card__body) {
  padding-top: 12px;
}
.rings {
  display: flex;
  align-items: center;
  gap: 18px;
}
.rings svg {
  width: 128px;
  height: 128px;
  flex: 0 0 128px;
  transform: rotate(-90deg);
}
.ring-track {
  fill: none;
  stroke: var(--app-surface-muted);
  stroke-linecap: round;
}
.ring {
  fill: none;
  stroke-linecap: round;
  transition: stroke-dashoffset 1.1s var(--app-ease-standard);
}
.ring-mastery { stroke: var(--app-primary); }
.ring-accuracy { stroke: var(--app-success); }
.ring-sample { stroke: var(--app-warning); }
.ring-legend {
  display: flex;
  flex-direction: column;
  gap: 9px;
  min-width: 0;
}
.rl {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--app-fs-xs);
  color: var(--app-text-secondary);
}
.rl b {
  margin-left: auto;
  color: var(--app-text);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.rl.muted { color: var(--app-text-muted); }
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex: 0 0 8px;
}
.dot.mastery { background: var(--app-primary); }
.dot.accuracy { background: var(--app-success); }
.dot.sample { background: var(--app-warning); }
.dot.track { background: var(--app-border-strong); }
.ring-note {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 0.5px solid var(--app-border);
  font-size: var(--app-fs-xs);
  line-height: 1.6;
  color: var(--app-text-muted);
}
.kpi-card :deep(.el-card__body) {
  height: 100%;
  display: flex;
}
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: stretch;
  width: 100%;
}
.kpi {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
  padding: 14px 22px;
  border-left: 0.5px solid var(--app-border);
  border-top: 0.5px solid var(--app-border);
  min-width: 0;
}
.kpi:nth-child(1),
.kpi:nth-child(2) {
  border-top: none;
}
.kpi:nth-child(odd) {
  border-left: none;
  padding-left: 4px;
}
.kpi-k {
  font-size: var(--app-fs-xs);
  font-weight: 500;
  color: var(--app-text-secondary);
}
.kpi-v {
  font-size: var(--app-fs-hero);
  font-weight: 700;
  letter-spacing: -0.03em;
  line-height: 1.05;
  color: var(--app-text);
  font-variant-numeric: tabular-nums;
}
.kpi-v small {
  margin-left: 3px;
  font-size: var(--app-fs-xs);
  font-weight: 500;
  color: var(--app-text-muted);
}
.kpi-v.date {
  font-size: var(--app-fs-lg);
  letter-spacing: -0.01em;
}
.kpi-d {
  font-size: var(--app-fs-xs);
  line-height: 1.5;
  color: var(--app-text-muted);
}
.cap {
  display: flex;
  flex-direction: column;
  gap: 13px;
}
.cap-row {
  display: grid;
  grid-template-columns: 82px minmax(0, 1fr) 60px;
  align-items: center;
  gap: 10px;
  font-size: var(--app-fs-xs);
}
.cap-name {
  color: var(--app-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cap-track {
  height: 6px;
  border-radius: var(--app-radius-full);
  background: var(--app-surface-muted);
  overflow: hidden;
}
.cap-track i {
  display: block;
  height: 100%;
  border-radius: var(--app-radius-full);
  background: var(--app-primary);
  transition: width 0.9s var(--app-ease-standard);
}
.cap-value {
  text-align: right;
  color: var(--app-text);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.cap-row.low .cap-track i { background: var(--app-warning); }
.cap-row.crit .cap-track i { background: var(--app-danger); }
.cap-row.empty .cap-track i { background: var(--app-border-strong); }
.cap-row.empty .cap-value { color: var(--app-text-muted); }
.error-row .cap-track i { background: color-mix(in srgb, var(--app-primary) 72%, var(--app-text-muted)); }
.error-row:nth-child(1) .cap-track i { background: var(--app-danger); }
.error-row:nth-child(2) .cap-track i { background: var(--app-warning); }
.chart-note {
  margin-top: 12px;
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
  line-height: 1.5;
}
.weak-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.weak-item {
  width: 100%;
  padding: 0;
  border: none;
  background: none;
  text-align: left;
  color: inherit;
  font: inherit;
  cursor: pointer;
}
.weak-item:hover .weak-name {
  color: var(--app-primary);
}
.weak-item:focus-visible {
  outline: 2px solid var(--app-primary);
  outline-offset: 3px;
  border-radius: var(--app-radius-sm);
}
.weak-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
}
.weak-name {
  font-size: var(--app-fs-xs);
  color: var(--app-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.weak-meta {
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
  flex: none;
}
.weak-right {
  display: inline-flex;
  align-items: baseline;
  gap: 10px;
  flex: none;
}
.weak-pct {
  font-size: var(--app-fs-xs);
  font-weight: 600;
  color: var(--app-text);
  font-variant-numeric: tabular-nums;
}
.weak-track {
  position: relative;
  display: flex;
  align-items: center;
  height: 6px;
  border-radius: var(--app-radius-full);
  background: var(--app-surface-muted);
  overflow: hidden;
}
.weak-track i {
  display: block;
  height: 100%;
  border-radius: var(--app-radius-full);
  background: var(--app-danger);
}
.insight {
  background: linear-gradient(150deg, color-mix(in srgb, var(--app-primary) 9%, var(--app-surface)), var(--app-surface) 62%);
}
.advice {
  margin: 0 0 12px;
  font-size: var(--app-fs-sm);
  line-height: 1.7;
  color: var(--app-text);
}
.advice b { font-weight: 600; }
.steps {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 14px;
}
.step {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  padding: 0;
  border: none;
  background: none;
  text-align: left;
  font: inherit;
  font-size: var(--app-fs-xs);
  color: var(--app-text-secondary);
  cursor: pointer;
}
.step:hover {
  color: var(--app-primary);
}
.step:focus-visible {
  outline: 2px solid var(--app-primary);
  outline-offset: 3px;
  border-radius: var(--app-radius-sm);
}
.step i {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--app-surface);
  box-shadow: 0 0 0 0.5px var(--app-border);
  display: grid;
  place-items: center;
  font-size: var(--app-fs-xs);
  font-style: normal;
  font-weight: 600;
  color: var(--app-primary);
  flex: 0 0 18px;
}
.advice-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  border-top: 0.5px solid var(--app-border);
  padding-top: 10px;
  font-size: var(--app-fs-xs);
  color: var(--app-text-muted);
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

@media (max-width: 1080px) {
  .c4, .c5, .c7, .c8, .c12 { grid-column: span 12; }
}
@media (max-width: 720px) {
  .page-head { flex-direction: column; align-items: flex-start; }
  .dash { grid-template-columns: minmax(0, 1fr) !important; }
  .dash > .el-card { grid-column: 1 / -1 !important; }
  .c4, .c5, .c7, .c8, .c12 { grid-column: span 1; }
  .kpi-grid { grid-template-columns: 1fr; }
  .kpi {
    border-left: none;
    border-top: 0.5px solid var(--app-border);
    padding: 12px 4px;
  }
  .kpi:nth-child(2) { border-top: 0.5px solid var(--app-border); }
  .kpi:first-child { border-top: none; }
  .rings { flex-direction: column; align-items: flex-start; }
  .ring-legend { width: 100%; }
}
</style>
