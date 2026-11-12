/**
 * 学习数据变更总线（CL-01 / CL-08 / CL-18 / ST-19 / TC-17 同一根因的收敛点）。
 *
 * 现状：练习提交、计划打卡、错题确认都只改服务端，页面各自局部重拉，
 * 于是「练习完 → 画像还是旧的」「错题本 → 练习完 → 列表不变」。
 * 这里提供一个进程内的变更信号：写操作成功后广播，任何展示学习数据的页面
 * 用 useStudyDataRefresh 订阅即可，不再要求每个调用点记住"我还要刷新谁"。
 *
 * 只做信号，不缓存数据、不发请求——避免把服务端状态在前端复制一份。
 * 修复：改订阅者集合 + 微任务逐个派发，避免 watch 批量合并丢失中间变更。
 */
import { onScopeDispose, readonly, ref } from "vue";

export type StudyChangeType = "practice" | "checkin" | "plan" | "error-record";

export interface StudyChange {
  type: StudyChangeType;
  /** 本次涉及的知识点：页面可据此做局部高亮或只刷新相关区块 */
  kpId?: string;
  /** 本次作答结果（仅 practice/checkin） */
  isCorrect?: boolean;
}

const revision = ref(0);
const lastChange = ref<StudyChange | null>(null);
/** 订阅者集合：每个组件的 handler */
const subscribers = new Set<(change: StudyChange) => void>();
/** 微任务挂起标记 */
let flushScheduled = false;

/** 只读版本号：需要在模板里显示"已更新"提示时可订阅 */
export const studyDataRevision = readonly(revision);
export const lastStudyChange = readonly(lastChange);

/** 写操作成功后调用（练习提交、打卡、生成计划、确认错题） */
export const notifyStudyDataChanged = (change: StudyChange): void => {
  lastChange.value = change;
  revision.value += 1;
  // 逐个派发到所有订阅者，同 tick 多次 notify 也能每个都收到
  if (!flushScheduled) {
    flushScheduled = true;
    queueMicrotask(() => {
      flushScheduled = false;
      const snapshot = [...subscribers];
      const c = lastChange.value;
      if (c) snapshot.forEach((fn) => fn(c));
    });
  }
};

/**
 * 在组件内订阅学习数据变更；组件卸载时自动停止。
 * handler 里通常只做一次轻量重拉（列表/画像），不要在这里做写操作。
 */
export const useStudyDataRefresh = (handler: (change: StudyChange) => void): void => {
  subscribers.add(handler);
  onScopeDispose(() => {
    subscribers.delete(handler);
  });
};
