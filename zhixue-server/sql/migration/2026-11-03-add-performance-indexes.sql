-- ============================================================
-- 智学领航 · 性能索引迁移（2026-11-03）
-- 依据：spark-output/review/db-audit-2026-11-03.md「实测」章节
--   临时库 zhixue_audit_tmp（MySQL 8.0.44）实测：
--   - idx_user_q：答错热路径 countByUserAndQid 3.27ms → 0.30ms（约 11 倍）
--   - idx_user_agg 终局复核（zhixue_user_agg_verify，20 万合成行）：
--     仅 Q1 aggregateByUser 实测受益，P95 731.124→355.608ms；Q2/Q3 未选用该索引，
--     不得引用早期临时库数字宣称“三聚合全面提速”。
--   - 写入代价约 +23%（P95 +23.60%、P50 +26.13%、均值 +22.14%）；
--     额外磁盘 17.27MiB/20 万行。189 行演示规模无一致可测收益，演示库不建 idx_user_agg。
--   - idx_user_agg 仅当两条同时满足才可建：practice_record >222,776 行，
--     且 Q1 P95/(Q1+Q2+Q3 P95)>40.72%。N=200,000×2,000/(731.124+910.574+153.827)，
--     X=731.124/(731.124+910.574+153.827)；执行前另查写入 SLO 与磁盘余量（runbook §6.1）。
--   - 必须保持 user_id 前缀（为 GROUP BY user_id[,kp_id] 提供有序输入）
--   - student_error_record(kp_id,log_id) 实测无收益（log_id 是主键，EXISTS 本就走主键），不建
--   - ai_generation_log 评阅索引升级为可选项（当前 1 万行规模无差异，见文件末）
--
-- 线上执行注意：
--   1) 本脚本未在主库执行过；执行前先备份（mysqldump 含三张目标表）。
--   2) MySQL 8.0 二级索引 ADD INDEX 默认 INPLACE/ONLINE，不阻塞读写；
--      但为防老版本/意外降级，显式写 ALGORITHM=INPLACE, LOCK=NONE，
--      若版本不支持该组合会直接报错而不是静默锁表。
--   3) 锁表风险：INPLACE 开始/结束仍有短暂元数据锁（MDL），
--      长事务未提交会排队阻塞后续读写；务必在低峰、无长事务窗口执行。
--   4) 大表（>500 万行）建议改用 pt-online-schema-change 或 gh-ost，
--      本脚本只面向当前学期量级（<100 万行，实测建索引 1-2 秒）。
--   5) idx_user_agg 为 7 列宽索引（实测 55.8 万行时索引总体积约 200MB 量级），
--      小内存实例先评估 innodb_buffer_pool_size，避免索引挤压数据页缓存。
--   6) 每条索引均已按 information_schema 判存，可重复执行；
--      回滚语句见文件末尾（仅DROP，不影响数据）。
-- ============================================================

USE zhixue;

-- ---------- 1) 答错热路径：practice_record(user_id, q_id) ----------
-- 对应查询：PracticeRecordMapper.countByUserAndQid（每次答错的错因推断）
SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE() AND table_name = 'practice_record'
     AND index_name = 'idx_user_q') = 0,
  'ALTER TABLE practice_record ADD INDEX idx_user_q (user_id, q_id), ALGORITHM=INPLACE, LOCK=NONE',
  'SELECT ''skip idx_user_q'''));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- 2) 教师端课程聚合覆盖索引 ----------
-- 对应查询：aggregateByUser / aggregateByUserKp / aggregateErrorTypeByCourse
-- user_id 前缀保证 GROUP BY user_id[,kp_id] 有序输入；7 列含 cost_seconds 才能全覆盖 Q1
SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE() AND table_name = 'practice_record'
     AND index_name = 'idx_user_agg') = 0,
  'ALTER TABLE practice_record ADD INDEX idx_user_agg (user_id, kp_id, q_id, is_correct, error_type, created_at, cost_seconds), ALGORITHM=INPLACE, LOCK=NONE',
  'SELECT ''skip idx_user_agg'''));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- 3) OCR 回收器：ocr_task(status, updated_at) ----------
-- 对应查询：OcrService.reclaimStaleTasks（PENDING/RUNNING 积压时生效，小表低优先）
SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE() AND table_name = 'ocr_task'
     AND index_name = 'idx_status_updated') = 0,
  'ALTER TABLE ocr_task ADD INDEX idx_status_updated (status, updated_at), ALGORITHM=INPLACE, LOCK=NONE',
  'SELECT ''skip idx_status_updated'''));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- 4) 可选：评阅队列索引升级（当前规模实测无收益，暂缓） ----------
-- 仅当"待评阅"积压超过 10 万行、且出现已评阅/待评阅混合筛选时再执行。
-- 注意：先 DROP 旧索引再建同名 3 列索引，属原位升级。
-- ALTER TABLE ai_generation_log DROP INDEX idx_pending_review;
-- ALTER TABLE ai_generation_log ADD INDEX idx_pending_review (scene, reviewed_at, created_at), ALGORITHM=INPLACE, LOCK=NONE;

-- ============================================================
-- 回滚（只删索引，不动数据）：
--   ALTER TABLE practice_record DROP INDEX idx_user_q;
--   ALTER TABLE practice_record DROP INDEX idx_user_agg;
--   ALTER TABLE ocr_task DROP INDEX idx_status_updated;
-- 回滚影响：教师端聚合退回全索引扫描+回表（55.8 万行实测 Q1 47s），
--   非必要不回滚 idx_user_agg。
-- ============================================================
