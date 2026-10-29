# 12 · FAQ

**Q1：`mvn` 报 `MissingProjectException`？**
A：在 `zhixue` 根目录没有 `pom.xml`；先 `cd zhixue-server`。

**Q2：`class file version 65/61`？**
A：JDK 不是 21；不要直接 `mvn`，在 `zhixue-server` 下用 `..\scripts\mvn21.ps1 -B -ntp verify`（pom 的 enforcer 会在 validate 阶段提前拦截）。

**Q3：Java 起来了但前端全 40100？**
A：token 过期或未登录；清 `satoken/zhixue_user` 后重新登录。

**Q4：学生访问教师评阅 40300？**
A：教师端只允许 `TEACHER`；路由守卫与后端 role 都会拦。

**Q5：重复评阅/打卡提示 40900？**
A：这是幂等/状态冲突，不是故障；刷新列表，不要重复点。

**Q6：讲解总是 L3？**
A：没有 `DEEPSEEK_API_KEY` 或 AI 8000 不可达；检查 key 和 `/api/rag/explain`。

**Q7：`/api/rag/retrieve` 返回空？**
A：先 `/api/rag/embed` 入库；query 文本必填；阈值 0.35；索引目录 `zhixue-ai/data/vector_index`。

**Q8：推荐题为什么先看不到答案？**
A：产品规则是“先独立做，再查看答案”；详情接口不返回答案，点“查看参考答案”才调用 `/answer`。

**Q9：计划打卡为什么没有对错？**
A：当前 checkin 是“完成”语义，写 `is_correct=1`；要精确掌握度需后续给 checkin 加 `isCorrect/myAnswer`。

**Q10：移动端表格横向溢出？**
A：错题本/教师队列在 1024px 以下切换卡片列表；若新增表格，请复用同一断点。

**Q11：RAG 模型没下载会怎样？**
A：自动回退字符 n-gram 向量（hash-bigram），接口仍可用；`mode` 在 embed 响应里可见。

**Q12：为什么 bundle 棘轮会被卡？**
A：`npm run build` 串了体积检查；新增依赖/页面后需清理重复 CSS 或经批准 `node perf/bench/bundle-budget.mjs --update` 并记录理由。
