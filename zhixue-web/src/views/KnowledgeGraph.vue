<template>
  <div class="page">
    <div class="page-head">
      <div>
        <div class="eyebrow">KNOWLEDGE MAP</div>
        <h2>课程知识图谱</h2>
        <p class="sub">节点按先修关系连接；红色标记当前画像中的薄弱知识点。</p>
      </div>
      <div class="graph-tools">
        <el-select
          v-model="chapterFilter"
          clearable
          placeholder="全部章节"
          size="small"
          class="chapter-select"
          aria-label="按章节筛选知识点"
        >
          <el-option
            v-for="chapter in chapterOptions"
            :key="chapter"
            :label="`第 ${chapter} 章`"
            :value="chapter"
          />
        </el-select>
        <div class="legend">
          <span class="dot weak" /> 薄弱知识点
          <span class="dot normal" /> 掌握正常
        </div>
      </div>
    </div>

    <PageState
      :loading="loading"
      :error="error"
      :empty="!loading && !error && nodes.length === 0"
      empty-text="课程暂无知识点图谱数据。"
      @retry="load"
    >
      <template #empty-action>
        <el-button type="primary" @click="router.push('/diagnosis')">开始诊断</el-button>
      </template>

      <el-card class="graph-card">
        <svg
          v-if="visibleNodes.length"
          class="graph-svg"
          viewBox="0 0 800 600"
          role="img"
          aria-label="知识点先修关系图，完整名称见下方节点列表"
        >
          <defs>
            <marker
              id="kg-arrow"
              viewBox="0 0 10 10"
              refX="16"
              refY="5"
              markerWidth="6"
              markerHeight="6"
              orient="auto-start-reverse"
            >
              <path d="M 0 0 L 10 5 L 0 10 z" fill="var(--app-border-strong)" />
            </marker>
          </defs>
          <line
            v-for="edge in edgeLines"
            :key="edge.id"
            :x1="edge.x1"
            :y1="edge.y1"
            :x2="edge.x2"
            :y2="edge.y2"
            stroke="var(--app-border-strong)"
            stroke-width="1.2"
            marker-end="url(#kg-arrow)"
          />
          <g
            v-for="node in nodePoints"
            :key="node.id"
            class="graph-node"
            role="button"
            tabindex="0"
            :aria-label="`${node.id} ${node.name}`"
            @click="openNode(node.id)"
            @keydown.enter.prevent="openNode(node.id)"
            @keydown.space.prevent="openNode(node.id)"
          >
            <circle
              :cx="node.x"
              :cy="node.y"
              :r="node.r"
              :class="['node-circle', { weak: node.weak }]"
            />
            <text
              :x="node.x"
              :y="node.y + node.r * 0.28"
              text-anchor="middle"
              class="node-id"
              :style="{ fontSize: `${node.font}px` }"
            >
              {{ node.label }}
            </text>
            <title>{{ node.id }} {{ node.name }}</title>
          </g>
        </svg>
        <div v-if="!visibleNodes.length" class="graph-empty">
          <el-empty description="本章暂无知识点" :image-size="64" />
        </div>
        <div class="graph-note">
          点击节点查看详情；当前显示 {{ visibleNodes.length }} 个知识点、{{ edgeLines.length }} 条先修关系<span v-if="chapterFilter != null">（已筛选第 {{ chapterFilter }} 章）</span>。
        </div>
      </el-card>

      <el-card v-if="visibleNodes.length" header="知识点清单" class="list-card">
        <div class="node-list">
          <el-tag
            v-for="node in visibleNodes"
            :key="node.id"
            :type="weakIds.has(node.id ?? '') ? 'danger' : 'info'"
            class="node-tag"
            tabindex="0"
            @click="openNode(node.id)"
            @keydown.enter.prevent="openNode(node.id)"
            @keydown.space.prevent="openNode(node.id)"
          >
            {{ node.id }} {{ node.name }}
          </el-tag>
        </div>
      </el-card>
    </PageState>

    <el-drawer v-model="drawerVisible" title="知识点详情" size="480px" class="kp-drawer">
      <el-skeleton v-if="detailLoading" :rows="5" animated />
      <el-alert v-else-if="detailError" :title="detailError" type="error" show-icon :closable="false" />
      <template v-else-if="detail">
        <h3 class="kp-title">{{ detail.kpId }} {{ detail.name }}</h3>
        <div class="kp-meta">
          <el-tag size="small">{{ detail.dimensionKey || "未分维度" }}</el-tag>
          <el-tag size="small" type="info">{{ detail.textbookPage || "页码待定" }}</el-tag>
        </div>
        <p class="kp-text">{{ detail.conceptText || "暂无概念切片" }}</p>
        <p v-if="detail.misconception" class="kp-text muted">
          <strong>常见误区：</strong>{{ detail.misconception }}
        </p>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import PageState from "../components/PageState.vue";
import {
  getKnowledgeGraph,
  getKnowledgePoint,
  listCourses,
  type GraphEdge,
  type GraphNode,
  type KnowledgePointDetail
} from "../api/course";
import { getPortrait } from "../api/portrait";

const router = useRouter();
const loading = ref(false);
const error = ref("");
const nodes = ref<GraphNode[]>([]);
const edges = ref<GraphEdge[]>([]);
const weakIds = ref<Set<string>>(new Set());
const drawerVisible = ref(false);
const detailLoading = ref(false);
const detailError = ref("");
const detail = ref<KnowledgePointDetail | null>(null);
const chapterFilter = ref<number | null>(null);

const chapterOptions = computed(() =>
  Array.from(
    new Set(
      nodes.value
        .map((node) => node.chapterId)
        .filter((id): id is number => typeof id === "number")
    )
  ).sort((a, b) => a - b)
);

const visibleNodes = computed(() =>
  chapterFilter.value == null
    ? nodes.value
    : nodes.value.filter((node) => node.chapterId === chapterFilter.value)
);

const CENTER = { x: 400, y: 300 };

/**
 * 单环 → 双环 → 三环。
 * 80 个知识点单环会互相压住，按周长比例分配环内数量，
 * 环半径 95/155/215 时，最密的外环仍有约 10px 间距。
 */
const nodePoints = computed(() => {
  const visible = visibleNodes.value
    .filter((node) => !!node.id)
    .sort(
      (a, b) =>
        (a.chapterId ?? Number.MAX_SAFE_INTEGER) - (b.chapterId ?? Number.MAX_SAFE_INTEGER) ||
        (a.id ?? "").localeCompare(b.id ?? "")
    );
  const count = visible.length;
  if (!count) return [];

  const ringCount = count <= 24 ? 1 : count <= 48 ? 2 : 3;
  const radii = ringCount === 1 ? [210] : ringCount === 2 ? [120, 205] : [95, 155, 215];
  const nodeRadius = count > 48 ? 13 : count > 24 ? 15 : 18;
  const font = count > 48 ? 7.5 : count > 24 ? 8 : 9;
  const totalWeight = radii.reduce((sum, radius) => sum + radius, 0);
  let remaining = count;
  const ringSizes = radii.map((radius, index) => {
    if (index === radii.length - 1) return remaining;
    const size = Math.max(1, Math.round((count * radius) / totalWeight));
    remaining -= size;
    return size;
  });

  let cursor = 0;
  return radii.flatMap((radius, ringIndex) => {
    const ringNodes = visible.slice(cursor, cursor + ringSizes[ringIndex]);
    cursor += ringSizes[ringIndex];
    const offset = -Math.PI / 2 + ringIndex * 0.34;
    return ringNodes.map((node, index) => {
      const angle = offset + (index * 2 * Math.PI) / Math.max(ringNodes.length, 1);
      const id = node.id as string;
      return {
        id,
        name: node.name ?? id,
        label: count > 48 ? id.replace(/^KP/i, "").slice(-4) : id,
        x: CENTER.x + Math.cos(angle) * radius,
        y: CENTER.y + Math.sin(angle) * radius,
        r: nodeRadius,
        font,
        weak: weakIds.value.has(id)
      };
    });
  });
});

const byId = computed(() => new Map(nodePoints.value.map((node) => [node.id, node])));

const edgeLines = computed(() =>
  edges.value
    .map((edge, index) => {
      const source = edge.source ? byId.value.get(edge.source) : undefined;
      const target = edge.target ? byId.value.get(edge.target) : undefined;
      if (!source || !target) return null;
      return {
        id: `edge-${index}`,
        x1: source.x,
        y1: source.y,
        x2: target.x,
        y2: target.y
      };
    })
    .filter((edge): edge is NonNullable<typeof edge> => !!edge)
);

const load = async () => {
  loading.value = true;
  error.value = "";
  try {
    const [courses, portrait] = await Promise.all([
      listCourses(),
      getPortrait().catch(() => null)
    ]);
    const courseId = courses[0]?.courseId ?? 1;
    const data = await getKnowledgeGraph(courseId);
    nodes.value = data.nodes ?? [];
    edges.value = data.edges ?? [];
    weakIds.value = new Set(
      (portrait?.weakestKps ?? [])
        .map((item) => item.kpId)
        .filter((id): id is string => !!id)
    );
  } catch (e) {
    error.value = (e as Error).message || "知识图谱加载失败";
  } finally {
    loading.value = false;
  }
};

const openNode = async (id?: string) => {
  if (!id) return;
  drawerVisible.value = true;
  detailLoading.value = true;
  detailError.value = "";
  detail.value = null;
  try {
    detail.value = await getKnowledgePoint(id);
  } catch (e) {
    detailError.value = (e as Error).message || "知识点详情加载失败";
  } finally {
    detailLoading.value = false;
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
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
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
.graph-tools {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.chapter-select {
  width: 150px;
}
.graph-empty {
  padding: 18px 0 6px;
}
.legend {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
}
.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  display: inline-block;
}
.dot.weak {
  background: var(--app-danger);
}
.dot.normal {
  background: var(--app-primary);
}
.graph-card {
  overflow: hidden;
}
.graph-svg {
  display: block;
  width: 100%;
  height: auto;
  min-height: 420px;
  background: var(--app-surface-muted);
  border-radius: var(--app-radius-sm);
}
.graph-node {
  cursor: pointer;
  outline: none;
}
.graph-node:focus .node-circle {
  stroke: var(--app-warning);
  stroke-width: 3;
}
.node-circle {
  fill: var(--app-primary);
  stroke: var(--app-surface);
  stroke-width: 2;
  transition: fill 0.2s ease;
}
.node-circle.weak {
  fill: var(--app-danger);
}
.graph-node:hover .node-circle {
  fill: var(--app-primary-strong);
}
.graph-node:hover .node-circle.weak {
  fill: color-mix(in srgb, var(--app-danger) 82%, black);
}
.node-id {
  fill: var(--app-accent-contrast);
  font-size: var(--app-fs-xs);
  font-weight: 600;
  pointer-events: none;
}
.graph-note {
  margin-top: 10px;
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
  line-height: 1.5;
}
.list-card {
  margin-top: 16px;
}
.node-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.node-tag {
  cursor: pointer;
}
.kp-title {
  margin: 0 0 10px;
  font-size: var(--app-fs-lg);
}
.kp-meta {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
}
.kp-text {
  line-height: 1.75;
  color: var(--app-text);
  white-space: pre-wrap;
}
.kp-text.muted {
  color: var(--app-text-secondary);
}
:deep(.el-drawer) {
  max-width: 92vw;
}
@media (max-width: 768px) {
  .page-head {
    flex-direction: column;
  }
  .graph-tools {
    width: 100%;
  }
  .chapter-select {
    width: 100%;
  }
  .graph-svg {
    min-height: 320px;
  }
  .node-id {
    display: none;
  }
  :deep(.el-drawer) {
    width: 92vw !important;
  }
}
</style>
