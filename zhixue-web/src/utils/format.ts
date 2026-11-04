/** 跨页共用展示格式化（F1）：百分比 / 日期工具 / 做题时长，统一行为与空值口径。 */

export function pct(value?: number | null) {
  return typeof value === "number" ? `${Math.round(value * 100)}%` : "—";
}

export function pad(value: number) {
  return String(value).padStart(2, "0");
}

export function formatDate(date: Date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

export function addDays(date: Date, days: number) {
  const next = new Date(date);
  next.setDate(next.getDate() + days);
  return next;
}

export function mondayOf(date: Date) {
  const next = new Date(date);
  next.setDate(next.getDate() - ((next.getDay() + 6) % 7));
  return next;
}

/** 平均做题时长（秒）；与 TeacherClass 原行为一致 */
export function duration(seconds?: number | null) {
  if (typeof seconds !== "number" || seconds <= 0) return "—";
  if (seconds < 60) return `${seconds} 秒`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)} 分`;
  return `${Math.floor(seconds / 3600)} 小时`;
}
