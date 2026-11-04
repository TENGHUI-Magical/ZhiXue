import { test } from "node:test";
import assert from "node:assert/strict";

import { shanghaiDateStamp } from "../src/api/filenameDate.ts";

// 2026-11-03T16:30:00Z = 上海 2026-11-04 00:30；旧实现（toISOString）在 TZ=UTC 下会得到 20261103
test("上海凌晨 00:30 的文件名日期必须是当天，不能回退到前一天", () => {
  assert.equal(shanghaiDateStamp(new Date("2026-11-03T16:30:00Z")), "20261104");
});

test("上海白天的时间不受 UTC 偏移影响", () => {
  assert.equal(shanghaiDateStamp(new Date("2026-11-04T04:00:00Z")), "20261104");
});

test("跨年边界按上海时区计算", () => {
  assert.equal(shanghaiDateStamp(new Date("2026-12-31T16:30:00Z")), "20270101");
});
