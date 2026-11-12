<template>
  <div class="metric-bar" :class="{ 'has-target': target != null }">
    <div v-if="!hideLabel" class="mb-head">
      <span class="mb-label">{{ label }}</span>
      <span class="mb-value" :class="{ empty: value == null }">{{ display }}</span>
    </div>
    <div class="mb-track" role="img" :aria-label="ariaText">
      <i class="mb-fill" :class="toneClass" :style="{ width: pct + '%' }"></i>
      <span
        v-if="target != null"
        class="mb-target"
        :style="{ left: target + '%' }"
        aria-hidden="true"
      ></span>
      <!-- 目标文案贴在刻度正上方：放在右下角会让"线"与"说明"彻底脱节 -->
      <span
        v-if="target != null"
        class="mb-target-label"
        :style="{ left: clampedTarget + '%' }"
        aria-hidden="true"
      >目标 {{ target }}%</span>
    </div>
    <div v-if="caption" class="mb-foot">
      <span class="mb-caption">{{ caption }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";

const props = withDefaults(
  defineProps<{
    label: string;
    /** 0–100；null 表示无样本，显示“—”且不画长度 */
    value: number | null;
    /** 目标线位置（0–100）；null 不显示 */
    target?: number | null;
    /** 低于该值才转 danger（规范：阈值才变色） */
    dangerBelow?: number | null;
    /** 语义色轨：neutral 用于数据可信度等“不是分数”的容量条 */
    tone?: "primary" | "neutral";
    caption?: string;
    /** 覆盖右侧显示文案，如“28 / 20 次” */
    displayValue?: string;
    hideLabel?: boolean;
  }>(),
  {
    target: null,
    dangerBelow: null,
    tone: "primary",
    caption: "",
    displayValue: "",
    hideLabel: false
  }
);

const pct = computed(() => {
  if (props.value == null || Number.isNaN(props.value)) return 0;
  return Math.max(0, Math.min(100, Math.round(props.value)));
});

const display = computed(() => {
  if (props.displayValue) return props.displayValue;
  if (props.value == null || Number.isNaN(props.value)) return "—";
  return `${pct.value}%`;
});

const toneClass = computed(() => {
  if (props.value == null) return "";
  if (props.tone === "neutral") return "is-neutral";
  if (props.dangerBelow != null && props.value < props.dangerBelow) return "is-danger";
  return "is-primary";
});

const ariaText = computed(() => {
  const base = `${props.label} ${display.value}`;
  return props.target != null ? `${base}，目标 ${props.target}%` : base;
});

/** 目标文案居中对齐刻度，但夹在 8%–92% 内，避免贴边被裁 */
const clampedTarget = computed(() =>
  props.target == null ? 0 : Math.max(8, Math.min(92, props.target))
);
</script>

<style scoped>
.metric-bar {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.mb-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}
.mb-label {
  color: var(--app-text-secondary);
  font-size: var(--app-fs-sm);
}
.mb-value {
  color: var(--app-text);
  font-size: var(--app-fs-sm);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.mb-value.empty {
  color: var(--app-text-muted);
  font-weight: 400;
}
.mb-track {
  position: relative;
  height: 8px;
  border-radius: var(--app-radius-full);
  /* 轨道必须看得见，否则只剩一条填色，"60%" 的比例无从读起。
     用 text 混 surface 保证浅/深两套主题都比卡底明显，再加发丝内环勾出边界。 */
  background: color-mix(in srgb, var(--app-text) 12%, var(--app-surface));
  box-shadow: inset 0 0 0 1px var(--app-hairline);
  overflow: visible;
}
.metric-bar.has-target .mb-track {
  margin-top: 15px;
}
.mb-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(
    180deg,
    color-mix(in srgb, var(--app-primary) 82%, var(--app-accent-contrast)),
    var(--app-primary)
  );
  /* 顶边一道光泽：让填充条从"扁色块"变成有厚度的玻璃条，
     与卡片顶光/底暗同一套光的语言（V4.0 视觉体系） */
  box-shadow: inset 0 1px 0 color-mix(in srgb, var(--app-accent-contrast) 26%, transparent);
  transition: width var(--app-dur-chart) var(--app-ease-emphasized);
}
.mb-fill.is-danger {
  background: linear-gradient(
    180deg,
    color-mix(in srgb, var(--app-danger) 82%, var(--app-accent-contrast)),
    var(--app-danger)
  );
}
/* neutral 是“容量”而非分数：用更实的 info 色，避免与轨道亮度接近 */
.mb-fill.is-neutral {
  background: linear-gradient(
    180deg,
    color-mix(in srgb, var(--app-info) 62%, var(--app-accent-contrast)),
    color-mix(in srgb, var(--app-info) 84%, var(--app-surface))
  );
}
.mb-target {
  position: absolute;
  top: 0;
  bottom: 0;
  z-index: 1;
  /* 目标线必须能看见：8px 轨道上 1.5px 淡灰等于没画（V1.1 §4.1-D4） */
  width: 2px;
  border-radius: var(--app-radius-full);
  background: var(--app-text-secondary);
  box-shadow: 0 0 0 1.5px var(--app-surface);
  opacity: 0.9;
}
.mb-foot {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
  line-height: 1.5;
}
.mb-caption {
  min-width: 0;
}
.mb-target-label {
  position: absolute;
  top: -15px;
  transform: translateX(-50%);
  color: var(--app-text-muted);
  font-size: var(--app-fs-xs);
  line-height: 1;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
</style>
