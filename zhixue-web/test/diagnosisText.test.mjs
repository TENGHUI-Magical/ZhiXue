import { test } from "node:test";
import assert from "node:assert/strict";

import {
  buildMappingText,
  extractOptionBlock,
  isAnswerKeyText
} from "../src/utils/diagnosisText.ts";

test("答案页前缀识别：选择题/判断题/填空题/问答题答案", () => {
  assert.equal(isAnswerKeyText("选择题答案 ② ④ ? C B A A A C B D B A"), true);
  assert.equal(isAnswerKeyText("判断题答案 ② ? ④ ? X X"), true);
  assert.equal(isAnswerKeyText("填空题答案 ①层次模型网状模型关系模型"), true);
  assert.equal(isAnswerKeyText("问答题答案 *①试述在数据管理的文件系统阶段"), true);
});

test("参考答案/答案：是答案页；题干里的“答案”不能误判", () => {
  assert.equal(isAnswerKeyText("参考答案"), true);
  assert.equal(isAnswerKeyText("答案：A"), true);
  assert.equal(isAnswerKeyText("试述数据、数据库、数据库管理系统、数据库系统的概念。 解析。"), false);
  assert.equal(isAnswerKeyText("下列关于参考答案的说法正确的是（ ）。"), false);
  assert.equal(isAnswerKeyText("答案正确的是（ ）。"), false);
});

test("选项块提取：至少三个 A-D 标记才返回", () => {
  const text = "选择题 ①数据库系统的核心和基础是（ )。 A.物理模型 B．概念模型 C．数据模型 D.逻辑模型";
  assert.match(extractOptionBlock(text), /A\.物理模型/);
  assert.equal(extractOptionBlock("试述关系模型的概念。"), "");
  assert.equal(extractOptionBlock("图 A. 所示的关系模式"), "");
  assert.equal(extractOptionBlock("A.对 B.错"), "");
});

test("题干已含选项时不重复拼接", () => {
  const stem = "选择题 A.物理模型 B.概念模型 C.数据模型 D.逻辑模型";
  assert.equal(buildMappingText(stem, "A.物理模型 B.概念模型"), stem);
});

test("全角/半角标点不同也不重复拼接", () => {
  const stem = "选择题 A.物理模型 B.概念模型 C.数据模型 D.逻辑模型";
  assert.equal(buildMappingText(stem, "A.物理模型 B．概念模型"), stem);
});

test("顿号与句点混用也不重复拼接", () => {
  const stem = "选择题 A、物理模型 B、概念模型 C、数据模型 D、逻辑模型";
  assert.equal(buildMappingText(stem, "A.物理模型 B.概念模型"), stem);
});

test("MappingOption 数组与 null 分支", () => {
  assert.equal(
    buildMappingText("题干", [{ key: "A", text: "物理模型" }]),
    "题干\nA 物理模型"
  );
  assert.equal(buildMappingText("题干", null), "题干");
});

test("超过 600 字的选项块不补", () => {
  const long = "A." + "x".repeat(200) + " B." + "x".repeat(200) + " C." + "x".repeat(200);
  assert.equal(extractOptionBlock(long), "");
});

test("题干不含选项时追加选项块", () => {
  const out = buildMappingText("数据库系统的核心和基础是（ ）。", "A.物理模型 B.概念模型");
  assert.match(out, /A\.物理模型/);
});

test("空题干或空选项时保持原样", () => {
  assert.equal(buildMappingText("", "A.物理模型"), "");
  assert.equal(buildMappingText("题干", ""), "题干");
});
