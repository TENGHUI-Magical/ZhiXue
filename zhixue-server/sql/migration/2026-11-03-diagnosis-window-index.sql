-- 诊断 180 天窗口物理收敛：按 (user_id, created_at) 前缀的覆盖索引
-- 关联：fix/diagnosis-consistency-20261103（D 线）
--
-- 背景：aggregateByUserKp（掌握度聚合，含同日同题去重）与
-- aggregateByUser / aggregateErrorType（带 user 过滤时）此前只能走
-- idx_user_time / idx_q_time 的范围扫描并逐行回表。
-- 本索引把 created_at 放在 user_id 之后，既能按 180 天窗口物理裁剪行，
-- 又覆盖聚合所需列（kp_id/q_id/is_correct/error_type/cost_seconds；practice_id 为
-- InnoDB 二级索引隐式主键），走覆盖扫描不回表。
--
-- 200k 行合成数据实测（MySQL 8.0.44，EXPLAIN ANALYZE，1/3 行在 180 天窗口内）：
--   aggregateByUserKp（102 个学生 × 233 道题）
--     BEFORE 无本索引：idx_user_time 范围扫描 + 回表 ≈435ms
--     AFTER  本索引 + optimizer hint：覆盖范围扫描 ≈156ms（2.8x）
--   注：该表已有索引已能把窗口裁剪到 32,859 行（并非全表扫描），
--   本索引消除的是回表成本；若数据全部落在窗口内（如仅 90 天历史），
--   行数无法再裁剪，收益只剩省回表。
--
-- 处置 2026-11-03-add-performance-indexes.sql 的 idx_user_agg：
-- 后者列序为 (user_id, kp_id, ..., created_at, ...)，created_at 在第 6 位，
-- 无法按窗口裁剪同一学生的窗外历史；两条索引同为 7 列、覆盖列相同，仅列序不同。
-- 200k 行实测（两条索引并存，EXPLAIN FORMAT=TREE）：
--   班级 aggregateByUser（qIds 非空，userIds=null）        → idx_q
--   单学生 aggregateByUser                                → idx_course_agg
--   班级 aggregateByUserKp / aggregateErrorType            → idx_course_agg
--   单学生 aggregateByUserKp / 冷却 selectPracticedQids    → idx_user_window（覆盖范围扫描）
-- 唯一选中 idx_user_agg 的是 aggregateByUser(qIds=null, userIds=null) 退化查询，
-- 而 PortraitService.classPortrait 与 TeacherStudentService.aggregateAll 均在
-- qIds.isEmpty() 时提前返回，该调用实际不可达。
-- 结论：删除 idx_user_agg（被 idx_user_window 取代；窗口聚合 430ms→150ms，
-- 且原索引无法裁剪 180 天窗口）。热表不再付两份 7 列索引的写入/空间成本。
--
-- 幂等：MySQL 8 无 ADD INDEX IF NOT EXISTS，重复执行报 1061 可忽略；
-- DROP 前按 information_schema 判存，索引不存在时执行 DO 0（不报 1091）。

ALTER TABLE practice_record
    ADD INDEX idx_user_window (user_id, created_at, kp_id, q_id, is_correct, error_type, cost_seconds),
    ALGORITHM=INPLACE, LOCK=NONE;

SET @drop_agg := (
    SELECT IF(COUNT(*) > 0,
              'ALTER TABLE practice_record DROP INDEX idx_user_agg, ALGORITHM=INPLACE, LOCK=NONE',
              'DO 0')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'practice_record'
      AND index_name = 'idx_user_agg'
);
PREPARE stmt_drop_agg FROM @drop_agg;
EXECUTE stmt_drop_agg;
DEALLOCATE PREPARE stmt_drop_agg;
