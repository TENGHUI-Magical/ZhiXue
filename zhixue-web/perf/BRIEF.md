# 性能冲刺简报：智学领航前端

## 职责

负责学生端/教师端核心页面的打开速度：先测基线，再做可验证的优化，赢下来的收益用棘轮锁住。用户可见的视觉变化和上线由人拍板。

## 核心操作与「能用」定义

| 操作 | 页面 | 起点 | 终点（真的能用） | 判定表达式 |
|---|---|---|---|---|
| 打开登录页 | `/login` | 导航开始 | 账号输入框可见且可输入 | `document.querySelector('input[autocomplete="username"]')?.offsetParent !== null` |
| 打开诊断台 | `/diagnosis` | 导航开始 | 题干输入框可见且可输入 | `document.querySelector('textarea')?.offsetParent !== null` |
| 打开错题本 | `/error-book` | 导航开始 | 表格可见 | `document.querySelector('.el-table')?.offsetParent !== null` |
| 打开知识图谱 | `/knowledge-graph` | 导航开始 | 卡片可见 | `document.querySelector('.el-card')?.offsetParent !== null` |

> 骨架屏、空壳、没有事件响应的占位不算「能用」。

## 不能动的东西

- 视觉：除已批准的 Lucide 菜单图标、移动端 Menu、主题 ChevronDown、上传 Upload 图标外，不改布局、配色、字体。
- 功能：不改路由、API 契约、登录/诊断/SSE 流程。
- 数据：不改 `docs/openapi.yaml`、`docs/01-接口契约V2.md`。
- 第三方：不引入付费额度能力；图标不整包引入。

## 目标

- 本轮确定性指标（体积棘轮）：
  - JS 总 gzip ≤ **202 kB**（当前 201.44 kB）
  - CSS 总 gzip ≤ **28 kB**（当前 27.25 kB）
  - 最大 JS chunk gzip ≤ **121 kB**（当前 120.37 kB）
  - 最大 CSS chunk gzip ≤ **9 kB**（当前 8.90 kB）
- 下一轮真实体感目标：登录页/诊断台「打开到能用」p75 降 ≥50%，用 `perf/bench/bench.py` 配对测量。
- 测量口径：体积用 `npm run build` 的确定性 gzip；真实耗时用 Fast 4G + 冷缓存 + p50/p75/p95，至少 10 次。

## 上线与回滚

- 批准人：用户（2026-10-27 已批准本设计）。
- 部署方式：前端静态构建产物，沿用现有 CI。
- 回滚：`git revert <本次 commit>`；体积棘轮随代码一起回滚。

## 已知限制

- 本轮没有安装 Playwright，未跑真实浏览器计时；体积是确定性代理，不等于用户 p75。
- 本地无 CrUX 数据，线上真实用户指标要上线后用 PageSpeed Insights/CrUX 补。
- 图标改动属于用户可见变化，已单独列出，便于回退。
- 视觉冒烟已于 2026-10-28 用生产构建预览完成；`ThemeSwitcher` 当前未挂载，其图标改动无可见效果。
