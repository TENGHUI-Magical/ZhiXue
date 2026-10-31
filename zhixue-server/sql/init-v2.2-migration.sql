-- ============================================================
-- 智学领航 V2.2 迁移脚本（仅用于「已按旧版 init-v2 建库」的环境）
--
-- 变更：教师评阅增加结论（PASS 通过 / RETURN 退回）。
-- 全新环境：init-v2.sql 已含 review_result，不要再执行本脚本。
-- 执行前置：已存在 ai_generation_log 且无 review_result 列。
-- 执行方式：mysql -u root -p --default-character-set=utf8mb4 zhixue < init-v2.2-migration.sql
--
-- 回滚（如需）：ALTER TABLE ai_generation_log DROP COLUMN review_result;
-- 备份建议：执行前 mysqldump 备份 ai_generation_log。
-- ============================================================

ALTER TABLE ai_generation_log
  ADD COLUMN review_result VARCHAR(10) DEFAULT NULL COMMENT '评阅结论：PASS / RETURN' AFTER teacher_comment;
