# perf/bench

## 1. 确定性体积棘轮（已接入 CI）

```bash
npm run build          # vue-tsc + vite build + 体积检查
npm run perf:bundle    # 只检查 dist/assets
node perf/bench/bundle-budget.mjs --update  # 优化后收紧上限
```

`ceilings.json` 是当前上限；只允许通过 `--update` 下调，不允许为了过 CI 上调。

## 2. 真实浏览器计时（已安装 Playwright）

`bench.py` / `ratchet.py` 改编自花叔 `huashu-flash`（MIT，https://github.com/alchaincyf/huashu-flash）。**2026-10-29 改动**：为通过安全扫描门禁，写盘路径已常量化——bench 结果固定写 `perf/bench/last_result.json`（已 gitignore），ratchet 上限只允许 `ceilings.json` / `timing-ceilings.json` 两个常量文件，`--out` / `--ceilings` 仅作提示或选择器。

```powershell
D:\Python3.12.10\python.exe -m pip install playwright
D:\Python3.12.10\python.exe -m playwright install chromium
```

> 2026-10-28 已在本机验证：Playwright 1.64.1 + Chromium 156。官方源下载慢时用清华镜像：
> `pip install -i https://pypi.tuna.tsinghua.edu.cn/simple playwright`；
> 浏览器走 npmmirror：
> `$env:PLAYWRIGHT_BROWSERS_PATH='C:\Users\TENGHUI\Library\Caches\ms-playwright'; $env:PLAYWRIGHT_DOWNLOAD_HOST='https://cdn.npmmirror.com/binaries/playwright'; python -m playwright install chromium`

登录页基线（生产构建 preview，2026-10-28）：

```powershell
D:\Python3.12.10\python.exe perf\bench\bench.py `
  --url http://127.0.0.1:4173/login `
  --ready "document.querySelector('input[autocomplete=\"username\"]')?.offsetParent !== null" `
  --type-check "input[autocomplete=\"username\"]" `
  --runs 10 --profile fast4g
# 结果固定写入 perf\bench\last_result.json；要留档就改名保存：
Move-Item perf\bench\last_result.json perf\bench\login-base.json
```

结果：10/10 成功，能用 p50/p75/p95 = **123.5 / 127.8 / 131.1 ms**；原始数据 `perf/bench/login-base.json`，上限 `perf/bench/timing-ceilings.json`（本机基线，未接入 CI）。

诊断台基线需要先登录并写入 `localStorage.satoken`，或用真实账号在脚本外完成登录；判定表达式用：

```js
document.querySelector('textarea')?.offsetParent !== null
```

A/B 配对测量：

```powershell
D:\Python3.12.10\python.exe perf\bench\bench.py `
  --url http://127.0.0.1:5173/login --url-b http://127.0.0.1:5174/login `
  --ready "<同一个判定表达式>" --runs 10 --profile fast4g
# 结果固定写入 perf\bench\last_result.json
```

棘轮：

```powershell
D:\Python3.12.10\python.exe perf\bench\ratchet.py init `
  --result perf\bench\login-base.json --ceilings timing `
  --label "login-4173"
```

> 已执行，当前上限 `login-4173::ready_p75 = 127.8`。

## 3. 口径

- Fast 4G：往返 20ms，下行 4Mbps，上行 3Mbps；冷缓存；CPU 1x。
- 每组至少 10 次；报 p50/p75/p95，不报平均数。
- 最终对比必须 A/B/A/B 交替，两个版本同时起服务。
- 本机数字只作过程记录；正式结论用 JMeter/预发 + CrUX。
