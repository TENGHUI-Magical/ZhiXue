<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>课程知识图谱</h2>
        <p class="sub">节点是按先修关系连接的知识点；红色表示当前画像里的薄弱点。</p>
      </div>
      <div class="legend">
        <span class="dot weak" /> 薄弱
        <span class="dot normal" /> 正常
      </div>
    </div>

    <PageState
      :loading="loading"
      :error="error"
      :empty="!loading && !error && nodes.length === 0"
      empty-text="课程暂无知识点图谱"
      @retry="load"
    >
      <template #empty-action>
        <el-button type="primary" @click="router.push('/diagnosis')">去诊断工作台</el-button>
      </template>

      <el-card class="graph-card">
        <svg
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
              <path d="M 0 0 L 10 5 L 0 10 z" fill="#cbd5e1" />
            </marker>
          </defs>
          <line
            v-for="edge in edgeLines"
            :key="edge.id"
            :x1="edge.x1"
            :y1="edge.y1"
            :x2="edge.x2"
            :y2="edge.y2"
            stroke="#cbd5e1"
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
              r="18"
              :class="['node-circle', { weak: node.weak }]"
            />
            <text :x="node.x" :y="node.y + 4" text-anchor="middle" class="node-id">
              {{ node.id }}
            </text>
            <title>{{ node.id }} {{ node.name }}</title>
          </g>
        </svg>
        <div class="graph-note">
          点击节点查看知识点详情；当前共 {{ nodes.length }} 个节点、{{ edges.length }} 条先修边。
          完整名称见下方节点列表。
        </div>
      </el-card>

      <el-card header="节点列表（无障碍替代）" class="list-card">
        <div class="node-list">
          <el-tag
            v-for="node in nodes"
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
          <el-tag size="small" type="info">{{ detail.textbookPage || "页码待补" }}</el-tag>
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

const CENTER = { x: 400, y: 300 };
const RADIUS = 230;

const nodePoints = computed(() => {
  const total = Math.max(nodes.value.length, 1);
  return nodes.value
    .filter((node) => !!node.id)
    .map((node, index) => {
      const angle = -Math.PI / 2 + (index * 2 * Math.PI) / total;
      return {
        id: node.id as string,
        name: node.name ?? node.id ?? "",
        x: CENTER.x + Math.cos(angle) * RADIUS,
        y: CENTER.y + Math.sin(angle) * RADIUS,
        weak: weakIds.value.has(node.id as string)
      };
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
.legend {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--app-text-muted);
  font-size: 12px;
}
.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  display: inline-block;
}
.dot.weak {
  background: #d92d20;
}
.dot.normal {
  background: #3b5bdb;
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
  border-radius: 10px;
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
  fill: #3b5bdb;
  stroke: #ffffff;
  stroke-width: 2;
  transition: fill 0.2s ease;
}
.node-circle.weak {
  fill: #d92d20;
}
.graph-node:hover .node-circle {
  fill: var(--app-primary-strong);
}
.graph-node:hover .node-circle.weak {
  fill: #b42318;
}
.node-id {
  fill: #ffffff;
  font-size: 9px;
  font-weight: 600;
  pointer-events: none;
}
.graph-note {
  margin-top: 10px;
  color: var(--app-text-muted);
  font-size: 12px;
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
  font-size: 18px;
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
