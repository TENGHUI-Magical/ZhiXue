<template>
  <div class="page">
    <div class="page-head">
      <div>
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
        <div v-if="visibleNodes.length" class="graph-controls" role="group" aria-label="图谱缩放与视图">
          <button type="button" aria-label="放大" @click="zoomBy(1.2)">+</button>
          <button type="button" aria-label="缩小" @click="zoomBy(0.83)">−</button>
          <button type="button" aria-label="重置视图" @click="resetView">重置</button>
        </div>
        <svg
          v-if="visibleNodes.length"
          ref="svgRef"
          class="graph-svg"
          :class="{ dragging }"
          viewBox="0 0 800 600"
          role="group"
          aria-label="知识点先修关系图，完整名称见下方节点列表"
          @wheel.prevent="onWheel"
          @pointerdown="onPointerDown"
          @pointermove="onPointerMove"
          @pointerup="onPointerUp"
          @pointercancel="onPointerUp"
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
          <g class="graph-viewport" :transform="viewportTransform">
            <line
              v-for="edge in edgeLines"
              :key="edge.id"
              :class="['graph-edge', { active: isEdgeActive(edge), dim: activeId && !isEdgeActive(edge) }]"
              :x1="edge.x1"
              :y1="edge.y1"
              :x2="edge.x2"
              :y2="edge.y2"
              stroke-width="1.2"
              marker-end="url(#kg-arrow)"
            />
            <g
              v-for="node in nodePoints"
              :key="node.id"
              :class="[
                'graph-node',
                {
                  active: node.id === activeId,
                  adjacent: adjacentIds.has(node.id),
                  dim: activeId && node.id !== activeId && !adjacentIds.has(node.id)
                }
              ]"
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
                v-if="node.showLabel"
                :x="node.x"
                :y="node.y + node.r + 14"
                text-anchor="middle"
                class="node-label"
              >
                {{ node.labelText }}
              </text>
              <title>{{ node.id }} {{ node.name }}</title>
            </g>
          </g>
        </svg>
        <div v-if="!visibleNodes.length" class="graph-empty">
          <el-empty description="本章暂无知识点" :image-size="64" />
        </div>
        <div class="graph-note">
          点击节点查看详情；当前显示 {{ visibleNodes.length }} 个知识点、{{ edgeLines.length }} 条先修关系<span v-if="chapterFilter != null">（已筛选第 {{ chapterFilter }} 章）</span>。节点较多时可按章节筛选显示名称，完整名称见下方“知识点清单”。
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

    <el-drawer v-model="drawerVisible" title="知识点详情" size="480px" class="kp-drawer" append-to-body @closed="activeId = ''">
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
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
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
const route = useRoute();
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
const svgRef = ref<SVGSVGElement | null>(null);
const scale = ref(1);
const tx = ref(0);
const ty = ref(0);
const activeId = ref("");
const dragging = ref(false);

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
  // 标签外置：节点较少时才显示名称（≥12px），密集时靠章节筛选/清单兜底，禁止 <12px 字号
  const showLabel = count <= 24;
  const truncate = (text: string, max: number) =>
    text.length > max ? `${text.slice(0, max)}…` : text;
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
        labelText: truncate(node.name ?? id, 5),
        showLabel,
        x: CENTER.x + Math.cos(angle) * radius,
        y: CENTER.y + Math.sin(angle) * radius,
        r: nodeRadius,
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
        sourceId: edge.source ?? "",
        targetId: edge.target ?? "",
        x1: source.x,
        y1: source.y,
        x2: target.x,
        y2: target.y
      };
    })
    .filter((edge): edge is NonNullable<typeof edge> => !!edge)
);

const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value));
const viewportTransform = computed(
  () => `translate(${tx.value} ${ty.value}) scale(${scale.value})`
);

/** 以 viewBox 坐标 (cx,cy) 为锚点缩放，保证光标下的点不漂移 */
const zoomAt = (factor: number, cx = 400, cy = 300) => {
  const next = clamp(scale.value * factor, 0.6, 2.5);
  if (next === scale.value) return;
  const ratio = next / scale.value;
  tx.value = cx - (cx - tx.value) * ratio;
  ty.value = cy - (cy - ty.value) * ratio;
  scale.value = next;
};
const zoomBy = (factor: number) => zoomAt(factor);
const resetView = () => {
  scale.value = 1;
  tx.value = 0;
  ty.value = 0;
};

const onWheel = (event: WheelEvent) => {
  const svg = svgRef.value;
  if (!svg) return;
  const rect = svg.getBoundingClientRect();
  if (!rect.width || !rect.height) return;
  const cx = ((event.clientX - rect.left) / rect.width) * 800;
  const cy = ((event.clientY - rect.top) / rect.height) * 600;
  zoomAt(event.deltaY < 0 ? 1.15 : 0.87, cx, cy);
};

let panStart = { x: 0, y: 0, tx: 0, ty: 0 };
const onPointerDown = (event: PointerEvent) => {
  const target = event.target as Element | null;
  if (target?.closest(".graph-node")) return; // 节点点击不触发拖拽
  if (event.button !== 0) return;
  dragging.value = true;
  panStart = { x: event.clientX, y: event.clientY, tx: tx.value, ty: ty.value };
  (event.currentTarget as SVGSVGElement).setPointerCapture?.(event.pointerId);
};
const onPointerMove = (event: PointerEvent) => {
  if (!dragging.value) return;
  const svg = svgRef.value;
  if (!svg) return;
  const rect = svg.getBoundingClientRect();
  if (!rect.width || !rect.height) return;
  tx.value = panStart.tx + ((event.clientX - panStart.x) / rect.width) * 800;
  ty.value = panStart.ty + ((event.clientY - panStart.y) / rect.height) * 600;
};
const onPointerUp = (event: PointerEvent) => {
  if (!dragging.value) return;
  dragging.value = false;
  (event.currentTarget as SVGSVGElement).releasePointerCapture?.(event.pointerId);
};

const isEdgeActive = (edge: { sourceId: string; targetId: string }) =>
  !!activeId.value && (edge.sourceId === activeId.value || edge.targetId === activeId.value);

const adjacentIds = computed(() => {
  const result = new Set<string>();
  if (!activeId.value) return result;
  for (const edge of edges.value) {
    if (edge.source === activeId.value && edge.target) result.add(edge.target);
    if (edge.target === activeId.value && edge.source) result.add(edge.source);
  }
  return result;
});

watch(chapterFilter, () => {
  activeId.value = "";
  resetView();
});

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
    await applyQuery();
  } catch (e) {
    error.value = (e as Error).message || "知识图谱加载失败";
  } finally {
    loading.value = false;
  }
};

const openNode = async (id?: string) => {
  if (!id) return;
  activeId.value = id;
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

/** 深链：?chapterId=3 先筛选章节；?kpId=KP0301 自动打开节点详情（来自画像/今日的深链） */
const applyQuery = async () => {
  const chapterRaw = route.query.chapterId;
  const chapter = typeof chapterRaw === "string" ? Number(chapterRaw) : NaN;
  if (Number.isFinite(chapter) && chapter > 0) {
    chapterFilter.value = chapter;
  }
  const kpRaw = route.query.kpId;
  const kpId = typeof kpRaw === "string" ? kpRaw.trim() : "";
  if (kpId && nodes.value.some((node) => node.id === kpId)) {
    await openNode(kpId);
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
.graph-card {
  position: relative;
  overflow: hidden;
}
.graph-controls {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 2;
  display: flex;
  gap: 6px;
}
.graph-controls button {
  min-width: 34px;
  height: 34px;
  padding: 0 8px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  background: var(--app-surface);
  color: var(--app-text-secondary);
  font-size: var(--app-fs-sm);
  cursor: pointer;
}
.graph-controls button:hover {
  color: var(--app-primary);
  border-color: var(--app-primary);
}
.graph-controls button:focus-visible {
  outline: 2px solid var(--app-primary);
  outline-offset: 2px;
}
.graph-svg {
  display: block;
  width: 100%;
  height: auto;
  min-height: 420px;
  background: var(--app-surface-muted);
  border-radius: var(--app-radius-sm);
  touch-action: none;
  cursor: grab;
}
.graph-svg.dragging {
  cursor: grabbing;
}
.graph-viewport {
  transition: transform 0.15s var(--app-ease-standard);
}
.graph-edge {
  stroke: var(--app-border-strong);
  transition: opacity 0.15s ease, stroke 0.15s ease;
}
.graph-edge.active {
  stroke: var(--app-warning);
  stroke-width: 2.2;
}
.graph-edge.dim {
  opacity: 0.18;
}
.graph-node {
  cursor: pointer;
  outline: none;
  transition: opacity 0.15s ease;
}
.graph-node.dim {
  opacity: 0.22;
}
.graph-node.active .node-circle {
  stroke: var(--app-warning);
  stroke-width: 3;
}
.graph-node.adjacent .node-circle {
  stroke: var(--app-primary);
  stroke-width: 2.5;
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
.node-label {
  fill: var(--app-text-secondary);
  font-size: var(--app-fs-xs);
  font-weight: 500;
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
  .graph-controls button {
    min-width: 44px;
    height: 44px;
  }
  .node-label {
    display: none;
  }
  :deep(.el-drawer) {
    width: 92vw !important;
  }
}
</style>
