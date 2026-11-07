/** 诊断台文本工具：错题原文预填、答案页识别、选项块补齐。 */

export const DIAGNOSIS_PREFILL_KEY = "zhixue.diagnosis.prefill";

/** 与后端 MapRequest.text 的 @Size(max=3000) 对齐 */
export const MAX_MAPPING_TEXT_CHARS = 3000;
const ANSWER_KEY_PREFIX_CHARS = 16;
const MIN_OPTION_MARKERS = 3;
const MAX_OPTION_BLOCK_CHARS = 600;

export interface MappingOption {
  key?: string;
  text?: string;
}

/**
 * 答案页/答案区前缀；只匹配开头，且要求题型前缀、参考答案或冒号三种形态之一，
 * 避免把“答案正确的是（ ）”这类题干误判为答案页。
 */
const ANSWER_KEY_PREFIX =
  /^(?:(?:选择题|判断题|填空题|问答题|单项选择|多项选择)(?:参考)?答案|参考答案|答案[:：])/;

/** 去空白 + 统一全半角标点，用于“题干里是否已含选项”的比较。 */
const normalizeForCompare = (text: string) =>
  text.replace(/\s+/g, "").replace(/[．。、]/g, ".");

/** OCR 结果是否是答案页/答案区，而不是题干页。 */
export function isAnswerKeyText(text?: string | null): boolean {
  const compact = (text ?? "").replace(/\s+/g, "");
  return ANSWER_KEY_PREFIX.test(compact.slice(0, ANSWER_KEY_PREFIX_CHARS));
}

/**
 * 从 OCR 原文中截取选项块（A./B./C./D.）。
 * 只在至少出现三个选项标记时返回，降低把题干里的“A.、B.”误当选项的概率；
 * 两选项题（A.对 B.错）宁可不补，也不引入错块。
 * 只取第一个 A. 起的块，多题页面可能取错；超过 600 字直接不补（宁缺勿错）。
 */
export function extractOptionBlock(text?: string | null): string {
  const raw = (text ?? "").trim();
  if (!raw) return "";
  const markers = raw.match(/[A-D][.．、]/g) ?? [];
  if (markers.length < MIN_OPTION_MARKERS) return "";
  const start = raw.search(/A[.．、]/);
  if (start < 0) return "";
  const block = raw.slice(start).trim();
  return block.length <= MAX_OPTION_BLOCK_CHARS ? block : "";
}

/**
 * 映射输入：题干 + 选项；选项已在题干里时不重复拼接。
 * options 既可以是结构化数组，也可以是 `extractOptionBlock` 返回的文本块。
 */
export function buildMappingText(
  stem: string,
  options?: MappingOption[] | string | null
): string {
  const base = (stem ?? "").trim();
  if (!base) return base;
  const extra = typeof options === "string"
    ? options.trim()
    : (options ?? [])
        .map((opt) => `${opt.key ?? ""} ${opt.text ?? ""}`.trim())
        .filter(Boolean)
        .join("\n");
  if (!extra) return base;
  const compact = normalizeForCompare(base);
  const missing = extra
    .split(/\n+/)
    .map((line) => line.trim())
    .filter((line) => line && !compact.includes(normalizeForCompare(line)));
  return missing.length ? `${base}\n${missing.join("\n")}` : base;
}
