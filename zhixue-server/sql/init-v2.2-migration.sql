-- ============================================================
-- 智学领航 V2.2 迁移脚本（仅用于「已按旧版 init-v2 建库」的环境）
--
-- 变更：教师评阅增加结论（PASS 通过 / RETURN 退回）。
-- 全新环境：init-v2.sql 已含 review_result，不要再执行本脚本。
-- 执行前置：已存在 ai_generation_log 且无 review_result 列。
-- 执行方式：mysql -u root -p --default-character-set=utf8mb4 zhixue < init-v2.2-migration.sql
--
-- 执行前置（必须先备份，没有备份不算完成）：
--   1) 备份 ai_generation_log（结构 + 数据）：
--      mysqldump -uroot -p --default-character-set=utf8mb4 --single-transaction ^
--        --no-tablespaces zhixue ai_generation_log > backup_v22_YYYYMMDD_HHMM.sql
--   2) 记录执行前行数：SELECT COUNT(*) FROM ai_generation_log;
--   3) 执行本脚本；执行后再查一次行数，应保持不变。
--   4) 校验列已存在：
--      SELECT column_name, column_type FROM information_schema.columns
--      WHERE table_schema='zhixue' AND table_name='ai_generation_log'
--        AND column_name='review_result';  -- 期望 varchar(10)
--
-- 回滚（如需）：ALTER TABLE ai_generation_log DROP COLUMN review_result;
--   回滚前同样先备份；若已有 PASS/RETURN 评阅数据，回滚会丢失该列历史值。
-- ============================================================

ALTER TABLE ai_generation_log
  ADD COLUMN review_result VARCHAR(10) DEFAULT NULL COMMENT '评阅结论：PASS / RETURN' AFTER teacher_comment;
