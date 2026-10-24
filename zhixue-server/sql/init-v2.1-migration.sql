-- ============================================================
-- 智学领航 V2.1 迁移脚本（仅用于「已按旧版 init-v2 建库」的环境）
--
-- 重要：init-v2.sql 已更新为 V2.1 基线（含 dimension_scores、dimension_key、
--       client_token 与推荐唯一键）。全新环境直接执行 init-v2.sql 即可，
--       **不要**再执行本脚本，否则会因列已存在而报错。
--
-- 执行前置：旧库（五维固定列 + 无幂等键）已存在
-- 执行方式：docker exec -i zhixue-mysql mysql -uroot -p zhixue < init-v2.1-migration.sql
-- ============================================================

-- 1. 画像快照：五维固定列改为动态 JSON
ALTER TABLE learning_portrait_snapshot
  ADD COLUMN dimension_scores JSON NULL COMMENT '维度得分对象，键与 course.dimension_json.key 一致' AFTER stat_date;

UPDATE learning_portrait_snapshot
SET dimension_scores = JSON_OBJECT(
  'concept',      concept_score,
  'sql',          structure_score,
  'schema',       algorithm_score,
  'transaction',  complexity_score,
  'boundary',     boundary_score
)
WHERE dimension_scores IS NULL
  AND (concept_score IS NOT NULL OR structure_score IS NOT NULL
       OR algorithm_score IS NOT NULL OR complexity_score IS NOT NULL
       OR boundary_score IS NOT NULL);

ALTER TABLE learning_portrait_snapshot
  DROP COLUMN concept_score,
  DROP COLUMN structure_score,
  DROP COLUMN algorithm_score,
  DROP COLUMN complexity_score,
  DROP COLUMN boundary_score;

-- 2. 知识点增加维度归属（一个知识点只归属一个维度）
ALTER TABLE knowledge_point
  ADD COLUMN dimension_key VARCHAR(30) NULL COMMENT '五维归属键，须存在于 course.dimension_json' AFTER trial_hint,
  ADD KEY idx_course_dimension (course_id, dimension_key);

-- 3. 幂等键：上传 / 创建错题 / 作答
ALTER TABLE ocr_task
  ADD COLUMN client_token VARCHAR(64) NULL COMMENT '客户端幂等键' AFTER user_id,
  ADD UNIQUE KEY uk_user_client (user_id, client_token);

ALTER TABLE student_error_record
  ADD COLUMN client_token VARCHAR(64) NULL COMMENT '客户端幂等键' AFTER user_id,
  ADD UNIQUE KEY uk_user_client (user_id, client_token);

ALTER TABLE practice_record
  ADD COLUMN client_token VARCHAR(64) NULL COMMENT '客户端幂等键' AFTER user_id,
  ADD UNIQUE KEY uk_user_client (user_id, client_token);

-- 4. 推荐去重：同一学生同一错题同一题只推荐一次
ALTER TABLE recommendation
  ADD UNIQUE KEY uk_user_log_q (user_id, log_id, q_id);

-- 5. 维度归属与课程配置回填（按当前种子的两个知识点）
UPDATE knowledge_point SET dimension_key = 'concept' WHERE kp_id = 'KP0201';
UPDATE knowledge_point SET dimension_key = 'schema'  WHERE kp_id = 'KP0202';

UPDATE course SET dimension_json = JSON_ARRAY(
  JSON_OBJECT('key','concept','name','概念理解'),
  JSON_OBJECT('key','sql','name','SQL 编写'),
  JSON_OBJECT('key','schema','name','模式设计'),
  JSON_OBJECT('key','transaction','name','事务与并发'),
  JSON_OBJECT('key','boundary','name','边界与异常')
) WHERE course_id = 1;

-- 6. 回滚说明（仅开发环境）
--    本脚本为破坏性变更：已 DROP 的五个维度列无法自动恢复。
--    回滚需按新版 init-v2.sql 重建快照表并重跑当日快照任务。
