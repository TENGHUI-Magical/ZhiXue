# zhixue-web 工程骨架说明

智学领航前端 · Vue3 + TypeScript + Element Plus + Pinia + Vue Router（规划书 2.1 节阶段 A，3 个部署单元中的前端侧）。

## 结构

```
zhixue-web/
├── index.html            入口页
├── vite.config.ts        端口 5173，/api 代理到 8080；第三方库分包
├── src/
│   ├── main.ts           挂载 Element Plus / Pinia / Router
│   ├── App.vue           全局布局：左侧导航（五项 P0）+ 顶部标题栏
│   ├── api/request.ts    统一请求封装（解包契约 1.1 响应、错误码分支提示）
│   ├── router/index.ts   五条 P0 路由 + 标题守卫
│   └── views/            页面骨架：诊断工作台 / 错题本 / 学情画像 / 周计划 / 知识图谱
```

## 运行

```bash
cd zhixue-web
npm install
npm run dev        # http://localhost:5173（/api 自动代理到 localhost:8080）
npm run build      # vue-tsc 类型检查 + 生产构建，产物在 dist/
```

前置：后端 zhixue-server 已在 8080 启动（见 `系统部署与运行方案.md` 第 3 节）。

## 与后端的衔接

- 开发期由 Vite 代理 `/api` → `localhost:8080`；生产建议 Nginx 反代并关闭 `proxy_buffering`（SSE 流式讲解需要）；
- 接口字段与《前后端接口契约文档》一一对应，命名统一 camelCase；
- 契约冻结前可用 Apifox Mock 先行开发，不阻塞后端进度。

## TODO 清单（下一阶段任务）

- OCR 上传后的 taskId 轮询与状态机渲染（P0，契约 2.2 节）
- 启发式讲解 SSE 打字机渲染与引用来源展示（P0，契约 2.4 节）
- 学情画像 ECharts 雷达图与知识图谱 G6 渲染（P0/P1）
- 登录态与角色判断（40100 跳转登录页、教师端导航切换，契约 2.1 节）
