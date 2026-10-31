-- ============================================================
-- 智学领航 V2.3 迁移脚本（仅索引，不改列、不改语义）
--
-- 目标：为 C12（教师查看学生学习记录）与 C3（评阅队列）的查询补齐访问路径。
-- 幂等：执行前用 information_schema.statistics 判断“同列前缀索引”是否已存在；
--       已存在则输出 skip，不会因重复建索引报错（MySQL 8 无 CREATE INDEX IF NOT EXISTS）。
--
-- 执行前置（必须先备份，没有备份不算完成）：
--   1) 备份三张相关表（结构 + 数据）：
--      mysqldump -uroot -p --default-character-set=utf8mb4 --single-transaction ^
--        --no-tablespaces zhixue practice_record student_error_record study_plan ^
--        > backup_v23_YYYYMMDD_HHMM.sql
--   2) 校验备份可用：文件中应出现三张表的 CREATE TABLE 与 INSERT；记录执行前行数。
--   3) 执行（Windows 全路径示例）：
--      "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -uroot -p ^
--        --default-character-set=utf8mb4 zhixue < init-v2.3-migration.sql
--   4) 执行后校验索引：
--      SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index)
--      FROM information_schema.statistics
--      WHERE table_schema = 'zhixue'
--        AND table_name IN ('practice_record', 'student_error_record', 'study_plan')
--      GROUP BY table_name, index_name;
--
-- 回滚（仅当索引确实由本脚本新建，且确认无查询依赖时执行；先备份）：
--   ALTER TABLE practice_record DROP INDEX idx_user_time;
--   ALTER TABLE practice_record DROP INDEX idx_q;
--   ALTER TABLE student_error_record DROP INDEX idx_user_time;
--   ALTER TABLE study_plan DROP INDEX idx_user_plan_date;
--   注意：若索引已存在于 V2.1 基线（init-v2.sql），不要回滚，否则会丢查询路径
--   或 study_plan 的 (user_id, plan_date) 唯一约束（uk_user_date）。
-- ============================================================

-- ------------------------------------------------------------
-- 1. practice_record (user_id, created_at)
-- 对应查询：GET /api/teacher/students 列表的“最近活跃”排序、
--           单学生作答明细按 created_at 倒序分页（C12）。
-- ------------------------------------------------------------
SET @has_user_time := (
  SELECT COUNT(*) FROM (
    SELECT index_name
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'practice_record'
      AND column_name IN ('user_id', 'created_at')
    GROUP BY index_name
    HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index) = 'user_id,created_at'
  ) AS existing_index
);
SET @sql_user_time := IF(@has_user_time = 0,
  'ALTER TABLE practice_record ADD INDEX idx_user_time (user_id, created_at)',
  'SELECT ''skip: practice_record(user_id,created_at) already indexed'' AS migration_note');
PREPARE stmt_user_time FROM @sql_user_time;
EXECUTE stmt_user_time;
DEALLOCATE PREPARE stmt_user_time;

-- ------------------------------------------------------------
-- 2. practice_record (q_id)
-- 对应查询：C12 列表/知识点聚合以课程题库 q_id 集合过滤（aggregateByUser / aggregateByUserKp）。
-- ------------------------------------------------------------
SET @has_q_id := (
  SELECT COUNT(*) FROM (
    SELECT index_name
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'practice_record'
      AND column_name IN ('q_id')
    GROUP BY index_name
    HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index) = 'q_id'
  ) AS existing_index
);
SET @sql_q_id := IF(@has_q_id = 0,
  'ALTER TABLE practice_record ADD INDEX idx_q (q_id)',
  'SELECT ''skip: practice_record(q_id) already indexed'' AS migration_note');
PREPARE stmt_q_id FROM @sql_q_id;
EXECUTE stmt_q_id;
DEALLOCATE PREPARE stmt_q_id;

-- ------------------------------------------------------------
-- 3. student_error_record (user_id, created_at)
-- 对应查询：C12 单学生错题列表按创建时间倒序（loadErrors，LIMIT 20）。
-- ------------------------------------------------------------
SET @has_error_user_time := (
  SELECT COUNT(*) FROM (
    SELECT index_name
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'student_error_record'
      AND column_name IN ('user_id', 'created_at')
    GROUP BY index_name
    HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index) = 'user_id,created_at'
  ) AS existing_index
);
SET @sql_error_user_time := IF(@has_error_user_time = 0,
  'ALTER TABLE student_error_record ADD INDEX idx_user_time (user_id, created_at)',
  'SELECT ''skip: student_error_record(user_id,created_at) already indexed'' AS migration_note');
PREPARE stmt_error_user_time FROM @sql_error_user_time;
EXECUTE stmt_error_user_time;
DEALLOCATE PREPARE stmt_error_user_time;

-- ------------------------------------------------------------
-- 4. study_plan (user_id, plan_date)
-- 对应查询：C12 列表本周计划进度聚合（aggregateProgress，WHERE user_id IN + plan_date BETWEEN）。
-- 基线用 UNIQUE KEY uk_user_date 覆盖同列前缀；旧库若缺失，这里补普通索引，不新增唯一约束语义。
-- ------------------------------------------------------------
SET @has_plan_user_date := (
  SELECT COUNT(*) FROM (
    SELECT index_name
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'study_plan'
      AND column_name IN ('user_id', 'plan_date')
    GROUP BY index_name
    HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index) = 'user_id,plan_date'
  ) AS existing_index
);
SET @sql_plan_user_date := IF(@has_plan_user_date = 0,
  'ALTER TABLE study_plan ADD INDEX idx_user_plan_date (user_id, plan_date)',
  'SELECT ''skip: study_plan(user_id,plan_date) already indexed'' AS migration_note');
PREPARE stmt_plan_user_date FROM @sql_plan_user_date;
EXECUTE stmt_plan_user_date;
DEALLOCATE PREPARE stmt_plan_user_date;

SELECT 'V2.3 migration finished (indexes only)' AS migration_result;
