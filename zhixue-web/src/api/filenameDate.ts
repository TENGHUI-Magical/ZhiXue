/**
 * 文件名日期戳统一按上海时区生成（YYYYMMDD）。
 *
 * `toISOString()` 按 UTC 取日期：上海 00:00—07:59 导出文件时，日期会回退到前一天。
 * 用 `Intl.DateTimeFormat.formatToParts` 固定 Asia/Shanghai 并手工拼装，避免依赖系统时区，
 * 也不受系统语言/locale 的输出格式影响。
 */
const FILE_DATE_FORMAT = new Intl.DateTimeFormat("en-US", {
  timeZone: "Asia/Shanghai",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
});

export function shanghaiDateStamp(date: Date = new Date()): string {
  const parts = FILE_DATE_FORMAT.formatToParts(date);
  let year = "";
  let month = "";
  let day = "";
  for (const part of parts) {
    if (part.type === "year") year = part.value;
    else if (part.type === "month") month = part.value;
    else if (part.type === "day") day = part.value;
  }
  return `${year}${month}${day}`;
}
