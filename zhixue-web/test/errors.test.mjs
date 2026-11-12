import { test } from "node:test";
import assert from "node:assert/strict";

import {
  ErrorCode,
  adviceFor,
  describeError,
  isConflict,
  isGloballyNotified,
  isRateLimited,
  messageOf
} from "../src/api/errors.ts";

test("统一提示码表：只有视图通常没有内联位的错误才由请求层 toast", () => {
  // 请求层统一提示：权限 / 冲突 / 限流 / 服务端 / 网络
  for (const code of [40300, 40900, 42900, 50000, -1]) {
    assert.equal(isGloballyNotified(code), true, `${code} 应由请求层提示`);
  }
  // 视图内联呈现：参数、未登录（跳登录）、不存在、AI 降级类
  for (const code of [40000, 40100, 40400, 60001, 60003, 60004]) {
    assert.equal(isGloballyNotified(code), false, `${code} 不应重复 toast`);
  }
});

test("错误分类：40900 / 42900 可被视图识别，不靠字符串匹配", () => {
  assert.equal(isConflict({ code: ErrorCode.CONFLICT }), true);
  assert.equal(isConflict({ code: 40000 }), false);
  assert.equal(isRateLimited({ code: ErrorCode.RATE_LIMITED }), true);
  assert.equal(isRateLimited(new Error("429")), false);
});

test("describeError：拼接下一步，且不重复追加同一句", () => {
  const forbidden = Object.assign(new Error("无权限访问该资源"), { code: 40300 });
  const text = describeError(forbidden, "请求失败");
  assert.match(text, /无权限访问该资源/);
  assert.match(text, /联系课程教师/);

  const already = Object.assign(new Error("请求过于频繁，请稍后再试"), { code: 42900 });
  const hint = adviceFor(42900).hint;
  const once = describeError(already, "请求失败");
  assert.equal(once.split(hint).length, 2, "下一步只应出现一次");
});

test("messageOf：Error / 字符串 / 未知值都有稳定兜底", () => {
  assert.equal(messageOf(new Error("boom"), "兜底"), "boom");
  assert.equal(messageOf("字符串错误", "兜底"), "字符串错误");
  assert.equal(messageOf(null, "兜底"), "兜底");
  assert.equal(messageOf({ message: "不是 Error" }, "兜底"), "兜底");
});

test("未知错误码不抛异常，回落到可重试兜底建议", () => {
  const advice = adviceFor(99999);
  assert.equal(advice.retryable, true);
  assert.ok(advice.hint.length > 0);
});
