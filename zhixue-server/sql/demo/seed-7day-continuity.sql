-- 智学领航 · 隔离演示库「7 天连续学习」可重放脚本
-- 目标账号：demo_stu01（user_id=9001，演示库既有账号，不新增账号、不改账号清单）
-- 目标库：只允许 zhixue_demo；脚本内不含 USE zhixue，也不含 DROP DATABASE / 重建。
-- 数据口径：全部相对 CURDATE() 动态计算，不写死 2026-11-03。
-- 幂等：
--   * practice_record 按 (user_id, client_token) upsert，可同日反复执行；
--   * student_error_record 按 log_id / (user_id, client_token) upsert；
--   * study_plan 按 (user_id, plan_date) upsert，明细按 (plan_id, q_id) upsert；
--   * 画像快照不写静态值：删除 demo_stu01 的快照，让 PortraitService 走实时聚合。
-- 执行前置：演示库已执行 scripts/seed-demo.ps1（账号、题库、既有种子已就绪）。
-- 执行方式（只指向隔离库）：
--   $env:MYSQL_PWD='123456'
--   Get-Content -Raw .\zhixue-server\sql\demo\seed-7day-continuity.sql |
--     & 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' -h 127.0.0.1 -P 3306 -u root --default-character-set=utf8mb4 zhixue_demo
--
-- 数据设计（D0 = CURDATE()）：
--   D-7：KP0201 答错（概念混淆）、KP0203 答对、KP0302 答对
--   D-6：KP0201 答对（错→对）、KP0302 答对
--   D-5：KP0305 答错（知识缺口，契约枚举无 KNOWLEDGE_GAP，按 METHOD_MISSING 落库）
--   D-3：KP0404 答错（计算失误）
--   D-1：KP0305 答对（错→对）
--   D0 ：KP0404 答对（错→对）、KP0203 答对
--   覆盖 5 个知识点；3 条答错、3 种不同错因；2 条错→对。
-- 到期复习说明（以代码为准，不改配置）：
--   DueReviewService 真实阈值 = 距上次答对 ≥ mastery.half-life-days（默认 30 天）
--   + 距上次任意作答 ≥ recommend.cooldown-days（默认 7 天）。D-7 答对只满足 7 天冷却，
--   不满足 30 天半衰期，因此不会在 D0 到期；D0 的到期项来自库中既有 KP0202 / KP0205
--   （上次答对约 40 天前），脚本不触碰它们，保证 D0「到期复习」非空。
-- 计划：
--   D-3 计划 2 题（1 完成 1 未完成）→ 读取时派生 OVERDUE（逾期分支）
--   D-1 计划 1 题完成（叠加既有种子后全部完成）
--   D0  计划 1 题完成，并保留既有的 1 题未完成（今日剩余 1 题）
-- 回滚：
--   DELETE FROM practice_record       WHERE user_id=9001 AND client_token LIKE 'demo7d-%';
--   DELETE FROM student_error_record  WHERE user_id=9001 AND log_id LIKE 'EL_7D_9001_%';
--   DELETE FROM study_plan_item WHERE plan_id IN (SELECT plan_id FROM study_plan WHERE user_id=9001 AND plan_date IN (DATE_SUB(CURDATE(), INTERVAL 3 DAY), DATE_SUB(CURDATE(), INTERVAL 1 DAY), CURDATE()));
--   DELETE FROM study_plan      WHERE user_id=9001 AND plan_date IN (DATE_SUB(CURDATE(), INTERVAL 3 DAY), DATE_SUB(CURDATE(), INTERVAL 1 DAY), CURDATE());
--   -- 静态画像快照：重跑 scripts/seed-demo.ps1 会按既有 upsert 恢复。
--   -- 注意：D-1/D0 计划行是既有种子行，回滚时按上面语句会一并删除；如需恢复，重跑 seed-demo.ps1。

SET NAMES utf8mb4;

-- 库名护栏：不在 zhixue_demo 就报错退出，绝不在主库 zhixue 写入任何一行。
SET @target_db := DATABASE();
SET @guard_stmt := IF(@target_db = 'zhixue_demo',
  'SELECT ''guard: zhixue_demo ok'' AS guard_result',
  'SELECT * FROM __seed_7day_continuity_refuses_outside_zhixue_demo__');
PREPARE seed_guard FROM @guard_stmt;
EXECUTE seed_guard;
DEALLOCATE PREPARE seed_guard;

-- ------------------------------------------------------------
-- 1) 六天作答记录（D-7 / D-6 / D-5 / D-3 / D-1 / D0）
-- ------------------------------------------------------------
INSERT INTO practice_record
  (user_id, client_token, q_id, kp_id, source, is_correct, my_answer, error_type, cost_seconds, created_at)
VALUES
-- D-7：第 1 天
(9001, 'demo7d-9001-d7-q0201', 'Q_0201', 'KP0201', 'SELF', 0,
 '[演示数据][7天轨迹] D-7 概念混淆：把关系模型三要素混在一起', 'CONCEPT_CONFUSION', 58,
 DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 7 DAY), INTERVAL 20 HOUR)),
(9001, 'demo7d-9001-d7-q0203', 'Q_0203', 'KP0203', 'SELF', 1,
 '[演示数据][7天轨迹] D-7 正确：候选码与主码判断', NULL, 64,
 DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 7 DAY), INTERVAL 20 HOUR)),
(9001, 'demo7d-9001-d7-q0302', 'Q_0302', 'KP0302', 'SELF', 1,
 '[演示数据][7天轨迹] D-7 正确：模式与基本表辨析', NULL, 71,
 DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 7 DAY), INTERVAL 20 HOUR)),
-- D-6：第 2 天，KP0201 错→对
(9001, 'demo7d-9001-d6-q0201', 'Q_0201', 'KP0201', 'SELF', 1,
 '[演示数据][7天轨迹] D-6 订正：关系模型三要素已能区分', NULL, 52,
 DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 6 DAY), INTERVAL 20 HOUR)),
(9001, 'demo7d-9001-d6-q0303', 'Q_0303', 'KP0302', 'SELF', 1,
 '[演示数据][7天轨迹] D-6 复述：索引与数据字典', NULL, 60,
 DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 6 DAY), INTERVAL 20 HOUR)),
-- D-5：知识缺口
(9001, 'demo7d-9001-d5-q0307', 'Q_0307', 'KP0305', 'SELF', 0,
 '[演示数据][7天轨迹] D-5 知识缺口：相关子查询执行顺序没建立', 'METHOD_MISSING', 96,
 DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 5 DAY), INTERVAL 20 HOUR)),
-- D-3：计算失误
(9001, 'demo7d-9001-d3-q0408', 'Q_0408', 'KP0404', 'SELF', 0,
 '[演示数据][7天轨迹] D-3 计算失误：WITH GRANT OPTION 授权链推错', 'CALCULATION_ERROR', 83,
 DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 3 DAY), INTERVAL 20 HOUR)),
-- D-1：KP0305 错→对
(9001, 'demo7d-9001-dm1-q0307', 'Q_0307', 'KP0305', 'SELF', 1,
 '[演示数据][7天轨迹] D-1 订正：相关子查询按外层行逐行执行', NULL, 74,
 DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 1 DAY), INTERVAL 20 HOUR)),
-- D0：KP0404 错→对 + KP0203 今日复习
(9001, 'demo7d-9001-d0-q0408', 'Q_0408', 'KP0404', 'SELF', 1,
 '[演示数据][7天轨迹] D0 订正：授权链按角色逐级传递', NULL, 69,
 DATE_SUB(NOW(), INTERVAL 10 MINUTE)),
(9001, 'demo7d-9001-d0-q0203', 'Q_0203', 'KP0203', 'SELF', 1,
 '[演示数据][7天轨迹] D0 复习：候选码判断稳定', NULL, 55,
 DATE_SUB(NOW(), INTERVAL 5 MINUTE))
ON DUPLICATE KEY UPDATE
  q_id = VALUES(q_id), kp_id = VALUES(kp_id), source = VALUES(source),
  is_correct = VALUES(is_correct), my_answer = VALUES(my_answer),
  error_type = VALUES(error_type), cost_seconds = VALUES(cost_seconds),
  created_at = VALUES(created_at);

-- ------------------------------------------------------------
-- 2) 三条跨天错题（让错题本也按日期看到 D-7 / D-5 / D-3）
-- ------------------------------------------------------------
INSERT INTO student_error_record
  (log_id, user_id, client_token, q_id, kp_id, image_url, ocr_text, corrected_text,
   ocr_confidence, error_type, student_note, status, confirmed_at, created_at)
VALUES
('EL_7D_9001_1', 9001, 'demo7d-err-9001-1', 'Q_0201', 'KP0201', NULL,
 '[演示数据][7天轨迹] OCR 原文占位', '[演示数据][7天轨迹] 关系模型三要素混淆',
 0.920, 'CONCEPT_CONFUSION',
 '[演示数据][7天轨迹] 第 1 天：概念混淆，已确认。', 'CONFIRMED',
 DATE_ADD(DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 7 DAY), INTERVAL 20 HOUR), INTERVAL 5 MINUTE),
 DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 7 DAY), INTERVAL 20 HOUR)),
('EL_7D_9001_2', 9001, 'demo7d-err-9001-2', 'Q_0307', 'KP0305', NULL,
 '[演示数据][7天轨迹] OCR 原文占位', '[演示数据][7天轨迹] 相关子查询执行顺序缺失',
 0.920, 'METHOD_MISSING',
 '[演示数据][7天轨迹] 第 3 天：知识缺口，按方法缺失落库。', 'CONFIRMED',
 DATE_ADD(DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 5 DAY), INTERVAL 20 HOUR), INTERVAL 5 MINUTE),
 DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 5 DAY), INTERVAL 20 HOUR)),
('EL_7D_9001_3', 9001, 'demo7d-err-9001-3', 'Q_0408', 'KP0404', NULL,
 '[演示数据][7天轨迹] OCR 原文占位', '[演示数据][7天轨迹] 授权链计算失误',
 0.920, 'CALCULATION_ERROR',
 '[演示数据][7天轨迹] 第 5 天：计算失误，已确认。', 'CONFIRMED',
 DATE_ADD(DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 3 DAY), INTERVAL 20 HOUR), INTERVAL 5 MINUTE),
 DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 3 DAY), INTERVAL 20 HOUR))
ON DUPLICATE KEY UPDATE
  q_id = VALUES(q_id), kp_id = VALUES(kp_id), ocr_text = VALUES(ocr_text),
  corrected_text = VALUES(corrected_text), ocr_confidence = VALUES(ocr_confidence),
  error_type = VALUES(error_type), student_note = VALUES(student_note),
  status = VALUES(status), confirmed_at = VALUES(confirmed_at), created_at = VALUES(created_at);

-- ------------------------------------------------------------
-- 3) 计划：D-3（逾期分支）/ D-1 / D0
-- ------------------------------------------------------------
INSERT INTO study_plan (user_id, plan_date, plan_count, done_count, status) VALUES
(9001, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 2, 1, 'PENDING'),
(9001, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 1, 1, 'DONE'),
(9001, CURDATE(), 1, 1, 'PENDING')
ON DUPLICATE KEY UPDATE
  plan_count = VALUES(plan_count), done_count = VALUES(done_count), status = VALUES(status);

INSERT INTO study_plan_item (plan_id, q_id, kp_id, sort_no, is_done, practice_id) VALUES
-- D-3：1 完成 1 未完成 → 读取时派生 OVERDUE
((SELECT plan_id FROM study_plan WHERE user_id = 9001 AND plan_date = DATE_SUB(CURDATE(), INTERVAL 3 DAY)),
 'Q_0408', 'KP0404', 1, 1,
 (SELECT practice_id FROM practice_record WHERE user_id = 9001 AND client_token = 'demo7d-9001-d3-q0408')),
((SELECT plan_id FROM study_plan WHERE user_id = 9001 AND plan_date = DATE_SUB(CURDATE(), INTERVAL 3 DAY)),
 'Q_0409', 'KP0404', 2, 0, NULL),
-- D-1：1 完成（叠加既有种子后全部完成）
((SELECT plan_id FROM study_plan WHERE user_id = 9001 AND plan_date = DATE_SUB(CURDATE(), INTERVAL 1 DAY)),
 'Q_0307', 'KP0305', 1, 1,
 (SELECT practice_id FROM practice_record WHERE user_id = 9001 AND client_token = 'demo7d-9001-dm1-q0307')),
-- D0：1 完成；既有种子的 1 题未完成保留
((SELECT plan_id FROM study_plan WHERE user_id = 9001 AND plan_date = CURDATE()),
 'Q_0408', 'KP0404', 4, 1,
 (SELECT practice_id FROM practice_record WHERE user_id = 9001 AND client_token = 'demo7d-9001-d0-q0408'))
ON DUPLICATE KEY UPDATE
  kp_id = VALUES(kp_id), sort_no = VALUES(sort_no),
  is_done = VALUES(is_done), practice_id = VALUES(practice_id);

-- 按真实明细重算三个计划的数量与状态（不写死数字）
UPDATE study_plan p
LEFT JOIN (
  SELECT plan_id, COUNT(*) AS item_count, SUM(is_done = 1) AS done_count
  FROM study_plan_item GROUP BY plan_id
) x ON p.plan_id = x.plan_id
SET p.plan_count = COALESCE(x.item_count, 0),
    p.done_count = COALESCE(x.done_count, 0),
    p.status = CASE
      WHEN COALESCE(x.done_count, 0) >= COALESCE(x.item_count, 0) THEN 'DONE'
      WHEN p.plan_date < CURDATE() THEN 'OVERDUE'
      ELSE 'PENDING'
    END
WHERE p.user_id = 9001
  AND p.plan_date IN (DATE_SUB(CURDATE(), INTERVAL 3 DAY),
                      DATE_SUB(CURDATE(), INTERVAL 1 DAY),
                      CURDATE());

-- ------------------------------------------------------------
-- 4) 画像：不写静态快照；删除该账号既有静态快照，交给 PortraitService 实时聚合
--    （PortraitService 当前无快照写入方，代码注释“预留未启用”；此处不伪造快照）
-- ------------------------------------------------------------
DELETE FROM learning_portrait_snapshot WHERE user_id = 9001;

-- ------------------------------------------------------------
-- 5) 执行后自检（输出给执行日志）
-- ------------------------------------------------------------
SELECT '本脚本写入的按日分布' AS check_item;
SELECT DATE(created_at) AS d, COUNT(*) AS cnt, SUM(is_correct = 0) AS wrong
FROM practice_record
WHERE user_id = 9001 AND client_token LIKE 'demo7d-%'
GROUP BY DATE(created_at)
ORDER BY d;

SELECT '该账号全量按日分布' AS check_item;
SELECT DATE(created_at) AS d, COUNT(*) AS cnt, SUM(is_correct = 0) AS wrong
FROM practice_record
WHERE user_id = 9001
GROUP BY DATE(created_at)
ORDER BY d;

SELECT '计划状态' AS check_item;
SELECT plan_id, user_id, plan_date, plan_count, done_count, status
FROM study_plan
WHERE user_id = 9001
  AND plan_date IN (DATE_SUB(CURDATE(), INTERVAL 3 DAY),
                    DATE_SUB(CURDATE(), INTERVAL 1 DAY),
                    CURDATE())
ORDER BY plan_date;

SELECT '纯 SQL 预演到期候选（口径：上次答对≥30天 且 上次作答≥7天）' AS check_item;
SELECT c.kp_id,
       MAX(CASE WHEN c.is_correct = 1 THEN c.created_at END) AS last_correct_at,
       MAX(c.created_at) AS last_practiced_at,
       SUM(c.is_correct = 0) AS wrong_count,
       DATEDIFF(CURDATE(), DATE(MAX(CASE WHEN c.is_correct = 1 THEN c.created_at END))) AS days_since_correct,
       DATEDIFF(CURDATE(), DATE(MAX(c.created_at))) AS days_since_any
FROM practice_record c
WHERE c.user_id = 9001
GROUP BY c.kp_id
HAVING last_correct_at IS NOT NULL
   AND days_since_correct >= 30
   AND days_since_any >= 7
ORDER BY days_since_correct DESC;
