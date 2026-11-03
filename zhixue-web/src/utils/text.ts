/**
 * 归一化误区等多行文本字段的换行分隔符：
 * 把字面 "\n"（反斜杠+n）、"\\n" 与真实换行统一为真实换行，并去掉每行首尾空白与空行，
 * 配合 white-space: pre-wrap 渲染为可读多行。
 *
 * 背景：种子数据（如 seed-kp-ch05.sql 的 KP0501）把换行存成字面 "\n"；
 * Java（RagExplainService.MISCONCEPTION_SPLIT）与 Python（rag_service._misconceptions）
 * 讲解链路已兼容该形态，前端显示层统一用本函数归一化。
 */
export function normalizeLineBreaks(text: string | null | undefined): string {
  if (!text) return "";
  return text
    .replace(/\r\n?/g, "\n")
    .replace(/\\\\n/g, "\n")
    .replace(/\\n/g, "\n")
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line.length > 0)
    .join("\n");
}
