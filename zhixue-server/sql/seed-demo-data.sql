-- 智学领航 · 演示数据（全部为构造数据，不是真实试用结果）
-- 用途：答辩演示库的教师端多行数据；覆盖 12 名学生、180 条练习、24 条错题、12 条讲解留痕。
-- 标注：姓名前缀“演示学生”，班级“演示数据-23软件工程1班”，作答/错题/讲解正文均带“[演示数据]”。
-- 幂等：练习/错题/计划按固定幂等键 upsert；讲解留痕按 explain_id 判重，不覆盖教师已提交的评阅。
-- 前置：init-v2.sql + 第1—4章知识点与题库 + seed-demo-users.sql。
USE zhixue;
SET NAMES utf8mb4;

-- 12 名学生 × 5 个知识点 × 3 次练习 = 180 条；低/中/高三种作答画像。
INSERT INTO practice_record (user_id, client_token, q_id, kp_id, source, is_correct, my_answer, error_type, cost_seconds, created_at) VALUES
(2, 'demo-prac-2-1-1', 'Q_0201', 'KP0201', 'PLAN', 0, '[演示数据] 错误选项占位', 'METHOD_MISSING', 57, DATE_SUB(NOW(), INTERVAL 12 HOUR)),
(2, 'demo-prac-2-1-2', 'Q_0213', 'KP0201', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 68, DATE_SUB(NOW(), INTERVAL 13 HOUR)),
(2, 'demo-prac-2-1-3', 'Q_0201', 'KP0201', 'SELF', 0, '[演示数据] 错误选项占位', 'CALCULATION_ERROR', 79, DATE_SUB(NOW(), INTERVAL 14 HOUR)),
(2, 'demo-prac-2-2-1', 'Q_0203', 'KP0203', 'SELF', 0, '[演示数据] 错误选项占位', 'STEP_OMISSION', 80, DATE_SUB(NOW(), INTERVAL 15 HOUR)),
(2, 'demo-prac-2-2-2', 'Q_0220', 'KP0203', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 91, DATE_SUB(NOW(), INTERVAL 16 HOUR)),
(2, 'demo-prac-2-2-3', 'Q_0203', 'KP0203', 'PLAN', 0, '[演示数据] 错误选项占位', 'BOUNDARY_ERROR', 102, DATE_SUB(NOW(), INTERVAL 17 HOUR)),
(2, 'demo-prac-2-3-1', 'Q_0302', 'KP0302', 'SELF', 0, '[演示数据] 错误选项占位', 'CALCULATION_ERROR', 103, DATE_SUB(NOW(), INTERVAL 18 HOUR)),
(2, 'demo-prac-2-3-2', 'Q_0303', 'KP0302', 'PLAN', 1, '[演示数据] 正确选项占位', NULL, 114, DATE_SUB(NOW(), INTERVAL 19 HOUR)),
(2, 'demo-prac-2-3-3', 'Q_0302', 'KP0302', 'SELF', 0, '[演示数据] 错误选项占位', 'CONCEPT_CONFUSION', 125, DATE_SUB(NOW(), INTERVAL 20 HOUR)),
(2, 'demo-prac-2-4-1', 'Q_0307', 'KP0305', 'PLAN', 0, '[演示数据] 错误选项占位', 'BOUNDARY_ERROR', 126, DATE_SUB(NOW(), INTERVAL 21 HOUR)),
(2, 'demo-prac-2-4-2', 'Q_0319', 'KP0305', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 137, DATE_SUB(NOW(), INTERVAL 22 HOUR)),
(2, 'demo-prac-2-4-3', 'Q_0325', 'KP0305', 'SELF', 0, '[演示数据] 错误选项占位', 'METHOD_MISSING', 148, DATE_SUB(NOW(), INTERVAL 23 HOUR)),
(2, 'demo-prac-2-5-1', 'Q_0408', 'KP0404', 'SELF', 0, '[演示数据] 错误选项占位', 'CONCEPT_CONFUSION', 149, DATE_SUB(NOW(), INTERVAL 24 HOUR)),
(2, 'demo-prac-2-5-2', 'Q_0409', 'KP0404', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 160, DATE_SUB(NOW(), INTERVAL 25 HOUR)),
(2, 'demo-prac-2-5-3', 'Q_0410', 'KP0404', 'PLAN', 0, '[演示数据] 错误选项占位', 'STEP_OMISSION', 171, DATE_SUB(NOW(), INTERVAL 26 HOUR)),
(9001, 'demo-prac-9001-1-1', 'Q_0201', 'KP0201', 'SELF', 0, '[演示数据] 错误选项占位', 'STEP_OMISSION', 74, DATE_SUB(NOW(), INTERVAL 24 HOUR)),
(9001, 'demo-prac-9001-1-2', 'Q_0213', 'KP0201', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 85, DATE_SUB(NOW(), INTERVAL 25 HOUR)),
(9001, 'demo-prac-9001-1-3', 'Q_0201', 'KP0201', 'PLAN', 0, '[演示数据] 错误选项占位', 'BOUNDARY_ERROR', 96, DATE_SUB(NOW(), INTERVAL 26 HOUR)),
(9001, 'demo-prac-9001-2-1', 'Q_0203', 'KP0203', 'SELF', 0, '[演示数据] 错误选项占位', 'CALCULATION_ERROR', 97, DATE_SUB(NOW(), INTERVAL 27 HOUR)),
(9001, 'demo-prac-9001-2-2', 'Q_0220', 'KP0203', 'PLAN', 1, '[演示数据] 正确选项占位', NULL, 108, DATE_SUB(NOW(), INTERVAL 28 HOUR)),
(9001, 'demo-prac-9001-2-3', 'Q_0203', 'KP0203', 'SELF', 0, '[演示数据] 错误选项占位', 'CONCEPT_CONFUSION', 119, DATE_SUB(NOW(), INTERVAL 29 HOUR)),
(9001, 'demo-prac-9001-3-1', 'Q_0302', 'KP0302', 'PLAN', 0, '[演示数据] 错误选项占位', 'BOUNDARY_ERROR', 120, DATE_SUB(NOW(), INTERVAL 30 HOUR)),
(9001, 'demo-prac-9001-3-2', 'Q_0303', 'KP0302', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 131, DATE_SUB(NOW(), INTERVAL 31 HOUR)),
(9001, 'demo-prac-9001-3-3', 'Q_0302', 'KP0302', 'SELF', 0, '[演示数据] 错误选项占位', 'METHOD_MISSING', 142, DATE_SUB(NOW(), INTERVAL 32 HOUR)),
(9001, 'demo-prac-9001-4-1', 'Q_0307', 'KP0305', 'SELF', 0, '[演示数据] 错误选项占位', 'CONCEPT_CONFUSION', 143, DATE_SUB(NOW(), INTERVAL 33 HOUR)),
(9001, 'demo-prac-9001-4-2', 'Q_0319', 'KP0305', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 154, DATE_SUB(NOW(), INTERVAL 34 HOUR)),
(9001, 'demo-prac-9001-4-3', 'Q_0325', 'KP0305', 'PLAN', 0, '[演示数据] 错误选项占位', 'STEP_OMISSION', 165, DATE_SUB(NOW(), INTERVAL 35 HOUR)),
(9001, 'demo-prac-9001-5-1', 'Q_0408', 'KP0404', 'SELF', 0, '[演示数据] 错误选项占位', 'METHOD_MISSING', 166, DATE_SUB(NOW(), INTERVAL 36 HOUR)),
(9001, 'demo-prac-9001-5-2', 'Q_0409', 'KP0404', 'PLAN', 1, '[演示数据] 正确选项占位', NULL, 177, DATE_SUB(NOW(), INTERVAL 37 HOUR)),
(9001, 'demo-prac-9001-5-3', 'Q_0410', 'KP0404', 'SELF', 0, '[演示数据] 错误选项占位', 'CALCULATION_ERROR', 188, DATE_SUB(NOW(), INTERVAL 38 HOUR)),
(9002, 'demo-prac-9002-1-1', 'Q_0201', 'KP0201', 'SELF', 0, '[演示数据] 错误选项占位', 'CALCULATION_ERROR', 91, DATE_SUB(NOW(), INTERVAL 36 HOUR)),
(9002, 'demo-prac-9002-1-2', 'Q_0213', 'KP0201', 'PLAN', 1, '[演示数据] 正确选项占位', NULL, 102, DATE_SUB(NOW(), INTERVAL 37 HOUR)),
(9002, 'demo-prac-9002-1-3', 'Q_0201', 'KP0201', 'SELF', 0, '[演示数据] 错误选项占位', 'CONCEPT_CONFUSION', 113, DATE_SUB(NOW(), INTERVAL 38 HOUR)),
(9002, 'demo-prac-9002-2-1', 'Q_0203', 'KP0203', 'PLAN', 0, '[演示数据] 错误选项占位', 'BOUNDARY_ERROR', 114, DATE_SUB(NOW(), INTERVAL 39 HOUR)),
(9002, 'demo-prac-9002-2-2', 'Q_0220', 'KP0203', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 125, DATE_SUB(NOW(), INTERVAL 40 HOUR)),
(9002, 'demo-prac-9002-2-3', 'Q_0203', 'KP0203', 'SELF', 0, '[演示数据] 错误选项占位', 'METHOD_MISSING', 136, DATE_SUB(NOW(), INTERVAL 41 HOUR)),
(9002, 'demo-prac-9002-3-1', 'Q_0302', 'KP0302', 'SELF', 0, '[演示数据] 错误选项占位', 'CONCEPT_CONFUSION', 137, DATE_SUB(NOW(), INTERVAL 42 HOUR)),
(9002, 'demo-prac-9002-3-2', 'Q_0303', 'KP0302', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 148, DATE_SUB(NOW(), INTERVAL 43 HOUR)),
(9002, 'demo-prac-9002-3-3', 'Q_0302', 'KP0302', 'PLAN', 0, '[演示数据] 错误选项占位', 'STEP_OMISSION', 159, DATE_SUB(NOW(), INTERVAL 44 HOUR)),
(9002, 'demo-prac-9002-4-1', 'Q_0307', 'KP0305', 'SELF', 0, '[演示数据] 错误选项占位', 'METHOD_MISSING', 160, DATE_SUB(NOW(), INTERVAL 45 HOUR)),
(9002, 'demo-prac-9002-4-2', 'Q_0319', 'KP0305', 'PLAN', 1, '[演示数据] 正确选项占位', NULL, 171, DATE_SUB(NOW(), INTERVAL 46 HOUR)),
(9002, 'demo-prac-9002-4-3', 'Q_0325', 'KP0305', 'SELF', 0, '[演示数据] 错误选项占位', 'CALCULATION_ERROR', 182, DATE_SUB(NOW(), INTERVAL 47 HOUR)),
(9002, 'demo-prac-9002-5-1', 'Q_0408', 'KP0404', 'PLAN', 0, '[演示数据] 错误选项占位', 'STEP_OMISSION', 183, DATE_SUB(NOW(), INTERVAL 48 HOUR)),
(9002, 'demo-prac-9002-5-2', 'Q_0409', 'KP0404', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 194, DATE_SUB(NOW(), INTERVAL 49 HOUR)),
(9002, 'demo-prac-9002-5-3', 'Q_0410', 'KP0404', 'SELF', 0, '[演示数据] 错误选项占位', 'BOUNDARY_ERROR', 205, DATE_SUB(NOW(), INTERVAL 50 HOUR)),
(9003, 'demo-prac-9003-1-1', 'Q_0201', 'KP0201', 'PLAN', 0, '[演示数据] 错误选项占位', 'BOUNDARY_ERROR', 108, DATE_SUB(NOW(), INTERVAL 48 HOUR)),
(9003, 'demo-prac-9003-1-2', 'Q_0213', 'KP0201', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 119, DATE_SUB(NOW(), INTERVAL 49 HOUR)),
(9003, 'demo-prac-9003-1-3', 'Q_0201', 'KP0201', 'SELF', 0, '[演示数据] 错误选项占位', 'METHOD_MISSING', 130, DATE_SUB(NOW(), INTERVAL 50 HOUR)),
(9003, 'demo-prac-9003-2-1', 'Q_0203', 'KP0203', 'SELF', 0, '[演示数据] 错误选项占位', 'CONCEPT_CONFUSION', 131, DATE_SUB(NOW(), INTERVAL 51 HOUR)),
(9003, 'demo-prac-9003-2-2', 'Q_0220', 'KP0203', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 142, DATE_SUB(NOW(), INTERVAL 52 HOUR)),
(9003, 'demo-prac-9003-2-3', 'Q_0203', 'KP0203', 'PLAN', 0, '[演示数据] 错误选项占位', 'STEP_OMISSION', 153, DATE_SUB(NOW(), INTERVAL 53 HOUR)),
(9003, 'demo-prac-9003-3-1', 'Q_0302', 'KP0302', 'SELF', 0, '[演示数据] 错误选项占位', 'METHOD_MISSING', 154, DATE_SUB(NOW(), INTERVAL 54 HOUR)),
(9003, 'demo-prac-9003-3-2', 'Q_0303', 'KP0302', 'PLAN', 1, '[演示数据] 正确选项占位', NULL, 165, DATE_SUB(NOW(), INTERVAL 55 HOUR)),
(9003, 'demo-prac-9003-3-3', 'Q_0302', 'KP0302', 'SELF', 0, '[演示数据] 错误选项占位', 'CALCULATION_ERROR', 176, DATE_SUB(NOW(), INTERVAL 56 HOUR)),
(9003, 'demo-prac-9003-4-1', 'Q_0307', 'KP0305', 'PLAN', 0, '[演示数据] 错误选项占位', 'STEP_OMISSION', 177, DATE_SUB(NOW(), INTERVAL 57 HOUR)),
(9003, 'demo-prac-9003-4-2', 'Q_0319', 'KP0305', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 188, DATE_SUB(NOW(), INTERVAL 58 HOUR)),
(9003, 'demo-prac-9003-4-3', 'Q_0325', 'KP0305', 'SELF', 0, '[演示数据] 错误选项占位', 'BOUNDARY_ERROR', 199, DATE_SUB(NOW(), INTERVAL 59 HOUR)),
(9003, 'demo-prac-9003-5-1', 'Q_0408', 'KP0404', 'SELF', 0, '[演示数据] 错误选项占位', 'CALCULATION_ERROR', 200, DATE_SUB(NOW(), INTERVAL 60 HOUR)),
(9003, 'demo-prac-9003-5-2', 'Q_0409', 'KP0404', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 211, DATE_SUB(NOW(), INTERVAL 61 HOUR)),
(9003, 'demo-prac-9003-5-3', 'Q_0410', 'KP0404', 'PLAN', 0, '[演示数据] 错误选项占位', 'CONCEPT_CONFUSION', 222, DATE_SUB(NOW(), INTERVAL 62 HOUR)),
(9004, 'demo-prac-9004-1-1', 'Q_0202', 'KP0202', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 125, DATE_SUB(NOW(), INTERVAL 60 HOUR)),
(9004, 'demo-prac-9004-1-2', 'Q_0218', 'KP0202', 'SELF', 0, '[演示数据] 错误选项占位', 'METHOD_MISSING', 136, DATE_SUB(NOW(), INTERVAL 61 HOUR)),
(9004, 'demo-prac-9004-1-3', 'Q_0219', 'KP0202', 'PLAN', 1, '[演示数据] 正确选项占位', NULL, 147, DATE_SUB(NOW(), INTERVAL 62 HOUR)),
(9004, 'demo-prac-9004-2-1', 'Q_0206', 'KP0206', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 148, DATE_SUB(NOW(), INTERVAL 63 HOUR)),
(9004, 'demo-prac-9004-2-2', 'Q_0215', 'KP0206', 'PLAN', 0, '[演示数据] 错误选项占位', 'STEP_OMISSION', 159, DATE_SUB(NOW(), INTERVAL 64 HOUR)),
(9004, 'demo-prac-9004-2-3', 'Q_0222', 'KP0206', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 170, DATE_SUB(NOW(), INTERVAL 65 HOUR)),
(9004, 'demo-prac-9004-3-1', 'Q_0311', 'KP0303', 'PLAN', 1, '[演示数据] 正确选项占位', NULL, 171, DATE_SUB(NOW(), INTERVAL 66 HOUR)),
(9004, 'demo-prac-9004-3-2', 'Q_0321', 'KP0303', 'SELF', 0, '[演示数据] 错误选项占位', 'CALCULATION_ERROR', 182, DATE_SUB(NOW(), INTERVAL 67 HOUR)),
(9004, 'demo-prac-9004-3-3', 'Q_0322', 'KP0303', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 193, DATE_SUB(NOW(), INTERVAL 68 HOUR)),
(9004, 'demo-prac-9004-4-1', 'Q_0404', 'KP0402', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 194, DATE_SUB(NOW(), INTERVAL 69 HOUR)),
(9004, 'demo-prac-9004-4-2', 'Q_0405', 'KP0402', 'SELF', 0, '[演示数据] 错误选项占位', 'BOUNDARY_ERROR', 205, DATE_SUB(NOW(), INTERVAL 70 HOUR)),
(9004, 'demo-prac-9004-4-3', 'Q_0404', 'KP0402', 'PLAN', 1, '[演示数据] 正确选项占位', NULL, 216, DATE_SUB(NOW(), INTERVAL 71 HOUR)),
(9004, 'demo-prac-9004-5-1', 'Q_0413', 'KP0406', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 217, DATE_SUB(NOW(), INTERVAL 72 HOUR)),
(9004, 'demo-prac-9004-5-2', 'Q_0414', 'KP0406', 'PLAN', 0, '[演示数据] 错误选项占位', 'CONCEPT_CONFUSION', 228, DATE_SUB(NOW(), INTERVAL 73 HOUR)),
(9004, 'demo-prac-9004-5-3', 'Q_0415', 'KP0406', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 239, DATE_SUB(NOW(), INTERVAL 74 HOUR)),
(9005, 'demo-prac-9005-1-1', 'Q_0202', 'KP0202', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 142, DATE_SUB(NOW(), INTERVAL 72 HOUR)),
(9005, 'demo-prac-9005-1-2', 'Q_0218', 'KP0202', 'PLAN', 0, '[演示数据] 错误选项占位', 'STEP_OMISSION', 153, DATE_SUB(NOW(), INTERVAL 73 HOUR)),
(9005, 'demo-prac-9005-1-3', 'Q_0219', 'KP0202', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 164, DATE_SUB(NOW(), INTERVAL 74 HOUR)),
(9005, 'demo-prac-9005-2-1', 'Q_0206', 'KP0206', 'PLAN', 1, '[演示数据] 正确选项占位', NULL, 165, DATE_SUB(NOW(), INTERVAL 75 HOUR)),
(9005, 'demo-prac-9005-2-2', 'Q_0215', 'KP0206', 'SELF', 0, '[演示数据] 错误选项占位', 'CALCULATION_ERROR', 176, DATE_SUB(NOW(), INTERVAL 76 HOUR)),
(9005, 'demo-prac-9005-2-3', 'Q_0222', 'KP0206', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 187, DATE_SUB(NOW(), INTERVAL 77 HOUR)),
(9005, 'demo-prac-9005-3-1', 'Q_0311', 'KP0303', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 188, DATE_SUB(NOW(), INTERVAL 78 HOUR)),
(9005, 'demo-prac-9005-3-2', 'Q_0321', 'KP0303', 'SELF', 0, '[演示数据] 错误选项占位', 'BOUNDARY_ERROR', 199, DATE_SUB(NOW(), INTERVAL 79 HOUR)),
(9005, 'demo-prac-9005-3-3', 'Q_0322', 'KP0303', 'PLAN', 1, '[演示数据] 正确选项占位', NULL, 210, DATE_SUB(NOW(), INTERVAL 80 HOUR)),
(9005, 'demo-prac-9005-4-1', 'Q_0404', 'KP0402', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 211, DATE_SUB(NOW(), INTERVAL 81 HOUR)),
(9005, 'demo-prac-9005-4-2', 'Q_0405', 'KP0402', 'PLAN', 0, '[演示数据] 错误选项占位', 'CONCEPT_CONFUSION', 222, DATE_SUB(NOW(), INTERVAL 82 HOUR)),
(9005, 'demo-prac-9005-4-3', 'Q_0404', 'KP0402', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 233, DATE_SUB(NOW(), INTERVAL 83 HOUR)),
(9005, 'demo-prac-9005-5-1', 'Q_0413', 'KP0406', 'PLAN', 1, '[演示数据] 正确选项占位', NULL, 234, DATE_SUB(NOW(), INTERVAL 84 HOUR)),
(9005, 'demo-prac-9005-5-2', 'Q_0414', 'KP0406', 'SELF', 0, '[演示数据] 错误选项占位', 'METHOD_MISSING', 45, DATE_SUB(NOW(), INTERVAL 85 HOUR)),
(9005, 'demo-prac-9005-5-3', 'Q_0415', 'KP0406', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 56, DATE_SUB(NOW(), INTERVAL 86 HOUR)),
(9006, 'demo-prac-9006-1-1', 'Q_0202', 'KP0202', 'PLAN', 1, '[演示数据] 正确选项占位', NULL, 159, DATE_SUB(NOW(), INTERVAL 84 HOUR)),
(9006, 'demo-prac-9006-1-2', 'Q_0218', 'KP0202', 'SELF', 0, '[演示数据] 错误选项占位', 'CALCULATION_ERROR', 170, DATE_SUB(NOW(), INTERVAL 85 HOUR)),
(9006, 'demo-prac-9006-1-3', 'Q_0219', 'KP0202', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 181, DATE_SUB(NOW(), INTERVAL 86 HOUR)),
(9006, 'demo-prac-9006-2-1', 'Q_0206', 'KP0206', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 182, DATE_SUB(NOW(), INTERVAL 87 HOUR)),
(9006, 'demo-prac-9006-2-2', 'Q_0215', 'KP0206', 'SELF', 0, '[演示数据] 错误选项占位', 'BOUNDARY_ERROR', 193, DATE_SUB(NOW(), INTERVAL 88 HOUR)),
(9006, 'demo-prac-9006-2-3', 'Q_0222', 'KP0206', 'PLAN', 1, '[演示数据] 正确选项占位', NULL, 204, DATE_SUB(NOW(), INTERVAL 89 HOUR)),
(9006, 'demo-prac-9006-3-1', 'Q_0311', 'KP0303', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 205, DATE_SUB(NOW(), INTERVAL 90 HOUR)),
(9006, 'demo-prac-9006-3-2', 'Q_0321', 'KP0303', 'PLAN', 0, '[演示数据] 错误选项占位', 'CONCEPT_CONFUSION', 216, DATE_SUB(NOW(), INTERVAL 91 HOUR)),
(9006, 'demo-prac-9006-3-3', 'Q_0322', 'KP0303', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 227, DATE_SUB(NOW(), INTERVAL 92 HOUR)),
(9006, 'demo-prac-9006-4-1', 'Q_0404', 'KP0402', 'PLAN', 1, '[演示数据] 正确选项占位', NULL, 228, DATE_SUB(NOW(), INTERVAL 93 HOUR)),
(9006, 'demo-prac-9006-4-2', 'Q_0405', 'KP0402', 'SELF', 0, '[演示数据] 错误选项占位', 'METHOD_MISSING', 239, DATE_SUB(NOW(), INTERVAL 94 HOUR)),
(9006, 'demo-prac-9006-4-3', 'Q_0404', 'KP0402', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 50, DATE_SUB(NOW(), INTERVAL 95 HOUR)),
(9006, 'demo-prac-9006-5-1', 'Q_0413', 'KP0406', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 51, DATE_SUB(NOW(), INTERVAL 96 HOUR)),
(9006, 'demo-prac-9006-5-2', 'Q_0414', 'KP0406', 'SELF', 0, '[演示数据] 错误选项占位', 'STEP_OMISSION', 62, DATE_SUB(NOW(), INTERVAL 97 HOUR)),
(9006, 'demo-prac-9006-5-3', 'Q_0415', 'KP0406', 'PLAN', 1, '[演示数据] 正确选项占位', NULL, 73, DATE_SUB(NOW(), INTERVAL 98 HOUR)),
(9007, 'demo-prac-9007-1-1', 'Q_0202', 'KP0202', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 176, DATE_SUB(NOW(), INTERVAL 96 HOUR)),
(9007, 'demo-prac-9007-1-2', 'Q_0218', 'KP0202', 'SELF', 0, '[演示数据] 错误选项占位', 'BOUNDARY_ERROR', 187, DATE_SUB(NOW(), INTERVAL 97 HOUR)),
(9007, 'demo-prac-9007-1-3', 'Q_0219', 'KP0202', 'PLAN', 1, '[演示数据] 正确选项占位', NULL, 198, DATE_SUB(NOW(), INTERVAL 98 HOUR)),
(9007, 'demo-prac-9007-2-1', 'Q_0206', 'KP0206', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 199, DATE_SUB(NOW(), INTERVAL 99 HOUR)),
(9007, 'demo-prac-9007-2-2', 'Q_0215', 'KP0206', 'PLAN', 0, '[演示数据] 错误选项占位', 'CONCEPT_CONFUSION', 210, DATE_SUB(NOW(), INTERVAL 100 HOUR)),
(9007, 'demo-prac-9007-2-3', 'Q_0222', 'KP0206', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 221, DATE_SUB(NOW(), INTERVAL 101 HOUR)),
(9007, 'demo-prac-9007-3-1', 'Q_0311', 'KP0303', 'PLAN', 1, '[演示数据] 正确选项占位', NULL, 222, DATE_SUB(NOW(), INTERVAL 102 HOUR)),
(9007, 'demo-prac-9007-3-2', 'Q_0321', 'KP0303', 'SELF', 0, '[演示数据] 错误选项占位', 'METHOD_MISSING', 233, DATE_SUB(NOW(), INTERVAL 103 HOUR)),
(9007, 'demo-prac-9007-3-3', 'Q_0322', 'KP0303', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 44, DATE_SUB(NOW(), INTERVAL 104 HOUR)),
(9007, 'demo-prac-9007-4-1', 'Q_0404', 'KP0402', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 45, DATE_SUB(NOW(), INTERVAL 105 HOUR)),
(9007, 'demo-prac-9007-4-2', 'Q_0405', 'KP0402', 'SELF', 0, '[演示数据] 错误选项占位', 'STEP_OMISSION', 56, DATE_SUB(NOW(), INTERVAL 106 HOUR)),
(9007, 'demo-prac-9007-4-3', 'Q_0404', 'KP0402', 'PLAN', 1, '[演示数据] 正确选项占位', NULL, 67, DATE_SUB(NOW(), INTERVAL 107 HOUR)),
(9007, 'demo-prac-9007-5-1', 'Q_0413', 'KP0406', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 68, DATE_SUB(NOW(), INTERVAL 108 HOUR)),
(9007, 'demo-prac-9007-5-2', 'Q_0414', 'KP0406', 'PLAN', 0, '[演示数据] 错误选项占位', 'CALCULATION_ERROR', 79, DATE_SUB(NOW(), INTERVAL 109 HOUR)),
(9007, 'demo-prac-9007-5-3', 'Q_0415', 'KP0406', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 90, DATE_SUB(NOW(), INTERVAL 110 HOUR)),
(9008, 'demo-prac-9008-1-1', 'Q_0116', 'KP0107', 'SELF', 0, '[演示数据] 错误选项占位', 'BOUNDARY_ERROR', 193, DATE_SUB(NOW(), INTERVAL 108 HOUR)),
(9008, 'demo-prac-9008-1-2', 'Q_0117', 'KP0107', 'PLAN', 1, '[演示数据] 正确选项占位', NULL, 204, DATE_SUB(NOW(), INTERVAL 109 HOUR)),
(9008, 'demo-prac-9008-1-3', 'Q_0118', 'KP0107', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 215, DATE_SUB(NOW(), INTERVAL 110 HOUR)),
(9008, 'demo-prac-9008-2-1', 'Q_0205', 'KP0205', 'PLAN', 1, '[演示数据] 正确选项占位', NULL, 216, DATE_SUB(NOW(), INTERVAL 111 HOUR)),
(9008, 'demo-prac-9008-2-2', 'Q_0214', 'KP0205', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 227, DATE_SUB(NOW(), INTERVAL 112 HOUR)),
(9008, 'demo-prac-9008-2-3', 'Q_0205', 'KP0205', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 238, DATE_SUB(NOW(), INTERVAL 113 HOUR)),
(9008, 'demo-prac-9008-3-1', 'Q_0309', 'KP0306', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 239, DATE_SUB(NOW(), INTERVAL 114 HOUR)),
(9008, 'demo-prac-9008-3-2', 'Q_0312', 'KP0306', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 50, DATE_SUB(NOW(), INTERVAL 115 HOUR)),
(9008, 'demo-prac-9008-3-3', 'Q_0309', 'KP0306', 'PLAN', 1, '[演示数据] 正确选项占位', NULL, 61, DATE_SUB(NOW(), INTERVAL 116 HOUR)),
(9008, 'demo-prac-9008-4-1', 'Q_0401', 'KP0401', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 62, DATE_SUB(NOW(), INTERVAL 117 HOUR)),
(9008, 'demo-prac-9008-4-2', 'Q_0402', 'KP0401', 'PLAN', 1, '[演示数据] 正确选项占位', NULL, 73, DATE_SUB(NOW(), INTERVAL 118 HOUR)),
(9008, 'demo-prac-9008-4-3', 'Q_0403', 'KP0401', 'SELF', 0, '[演示数据] 错误选项占位', 'BOUNDARY_ERROR', 84, DATE_SUB(NOW(), INTERVAL 119 HOUR)),
(9008, 'demo-prac-9008-5-1', 'Q_0419', 'KP0408', 'PLAN', 1, '[演示数据] 正确选项占位', NULL, 85, DATE_SUB(NOW(), INTERVAL 120 HOUR)),
(9008, 'demo-prac-9008-5-2', 'Q_0420', 'KP0408', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 96, DATE_SUB(NOW(), INTERVAL 121 HOUR)),
(9008, 'demo-prac-9008-5-3', 'Q_0419', 'KP0408', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 107, DATE_SUB(NOW(), INTERVAL 122 HOUR)),
(9009, 'demo-prac-9009-1-1', 'Q_0116', 'KP0107', 'PLAN', 0, '[演示数据] 错误选项占位', 'CONCEPT_CONFUSION', 210, DATE_SUB(NOW(), INTERVAL 120 HOUR)),
(9009, 'demo-prac-9009-1-2', 'Q_0117', 'KP0107', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 221, DATE_SUB(NOW(), INTERVAL 121 HOUR)),
(9009, 'demo-prac-9009-1-3', 'Q_0118', 'KP0107', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 232, DATE_SUB(NOW(), INTERVAL 122 HOUR)),
(9009, 'demo-prac-9009-2-1', 'Q_0205', 'KP0205', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 233, DATE_SUB(NOW(), INTERVAL 123 HOUR)),
(9009, 'demo-prac-9009-2-2', 'Q_0214', 'KP0205', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 44, DATE_SUB(NOW(), INTERVAL 124 HOUR)),
(9009, 'demo-prac-9009-2-3', 'Q_0205', 'KP0205', 'PLAN', 1, '[演示数据] 正确选项占位', NULL, 55, DATE_SUB(NOW(), INTERVAL 125 HOUR)),
(9009, 'demo-prac-9009-3-1', 'Q_0309', 'KP0306', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 56, DATE_SUB(NOW(), INTERVAL 126 HOUR)),
(9009, 'demo-prac-9009-3-2', 'Q_0312', 'KP0306', 'PLAN', 1, '[演示数据] 正确选项占位', NULL, 67, DATE_SUB(NOW(), INTERVAL 127 HOUR)),
(9009, 'demo-prac-9009-3-3', 'Q_0309', 'KP0306', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 78, DATE_SUB(NOW(), INTERVAL 128 HOUR)),
(9009, 'demo-prac-9009-4-1', 'Q_0401', 'KP0401', 'PLAN', 1, '[演示数据] 正确选项占位', NULL, 79, DATE_SUB(NOW(), INTERVAL 129 HOUR)),
(9009, 'demo-prac-9009-4-2', 'Q_0402', 'KP0401', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 90, DATE_SUB(NOW(), INTERVAL 130 HOUR)),
(9009, 'demo-prac-9009-4-3', 'Q_0403', 'KP0401', 'SELF', 0, '[演示数据] 错误选项占位', 'CONCEPT_CONFUSION', 101, DATE_SUB(NOW(), INTERVAL 131 HOUR)),
(9009, 'demo-prac-9009-5-1', 'Q_0419', 'KP0408', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 102, DATE_SUB(NOW(), INTERVAL 132 HOUR)),
(9009, 'demo-prac-9009-5-2', 'Q_0420', 'KP0408', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 113, DATE_SUB(NOW(), INTERVAL 133 HOUR)),
(9009, 'demo-prac-9009-5-3', 'Q_0419', 'KP0408', 'PLAN', 1, '[演示数据] 正确选项占位', NULL, 124, DATE_SUB(NOW(), INTERVAL 134 HOUR)),
(9010, 'demo-prac-9010-1-1', 'Q_0116', 'KP0107', 'SELF', 0, '[演示数据] 错误选项占位', 'METHOD_MISSING', 227, DATE_SUB(NOW(), INTERVAL 200 HOUR)),
(9010, 'demo-prac-9010-1-2', 'Q_0117', 'KP0107', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 238, DATE_SUB(NOW(), INTERVAL 201 HOUR)),
(9010, 'demo-prac-9010-1-3', 'Q_0118', 'KP0107', 'PLAN', 1, '[演示数据] 正确选项占位', NULL, 49, DATE_SUB(NOW(), INTERVAL 202 HOUR)),
(9010, 'demo-prac-9010-2-1', 'Q_0205', 'KP0205', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 50, DATE_SUB(NOW(), INTERVAL 202 HOUR)),
(9010, 'demo-prac-9010-2-2', 'Q_0214', 'KP0205', 'PLAN', 1, '[演示数据] 正确选项占位', NULL, 61, DATE_SUB(NOW(), INTERVAL 203 HOUR)),
(9010, 'demo-prac-9010-2-3', 'Q_0205', 'KP0205', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 72, DATE_SUB(NOW(), INTERVAL 204 HOUR)),
(9010, 'demo-prac-9010-3-1', 'Q_0309', 'KP0306', 'PLAN', 1, '[演示数据] 正确选项占位', NULL, 73, DATE_SUB(NOW(), INTERVAL 204 HOUR)),
(9010, 'demo-prac-9010-3-2', 'Q_0312', 'KP0306', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 84, DATE_SUB(NOW(), INTERVAL 205 HOUR)),
(9010, 'demo-prac-9010-3-3', 'Q_0309', 'KP0306', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 95, DATE_SUB(NOW(), INTERVAL 206 HOUR)),
(9010, 'demo-prac-9010-4-1', 'Q_0401', 'KP0401', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 96, DATE_SUB(NOW(), INTERVAL 206 HOUR)),
(9010, 'demo-prac-9010-4-2', 'Q_0402', 'KP0401', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 107, DATE_SUB(NOW(), INTERVAL 207 HOUR)),
(9010, 'demo-prac-9010-4-3', 'Q_0403', 'KP0401', 'PLAN', 0, '[演示数据] 错误选项占位', 'METHOD_MISSING', 118, DATE_SUB(NOW(), INTERVAL 208 HOUR)),
(9010, 'demo-prac-9010-5-1', 'Q_0419', 'KP0408', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 119, DATE_SUB(NOW(), INTERVAL 208 HOUR)),
(9010, 'demo-prac-9010-5-2', 'Q_0420', 'KP0408', 'PLAN', 1, '[演示数据] 正确选项占位', NULL, 130, DATE_SUB(NOW(), INTERVAL 209 HOUR)),
(9010, 'demo-prac-9010-5-3', 'Q_0419', 'KP0408', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 141, DATE_SUB(NOW(), INTERVAL 210 HOUR)),
(9011, 'demo-prac-9011-1-1', 'Q_0116', 'KP0107', 'SELF', 0, '[演示数据] 错误选项占位', 'STEP_OMISSION', 44, DATE_SUB(NOW(), INTERVAL 224 HOUR)),
(9011, 'demo-prac-9011-1-2', 'Q_0117', 'KP0107', 'PLAN', 1, '[演示数据] 正确选项占位', NULL, 55, DATE_SUB(NOW(), INTERVAL 225 HOUR)),
(9011, 'demo-prac-9011-1-3', 'Q_0118', 'KP0107', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 66, DATE_SUB(NOW(), INTERVAL 226 HOUR)),
(9011, 'demo-prac-9011-2-1', 'Q_0205', 'KP0205', 'PLAN', 1, '[演示数据] 正确选项占位', NULL, 67, DATE_SUB(NOW(), INTERVAL 226 HOUR)),
(9011, 'demo-prac-9011-2-2', 'Q_0214', 'KP0205', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 78, DATE_SUB(NOW(), INTERVAL 227 HOUR)),
(9011, 'demo-prac-9011-2-3', 'Q_0205', 'KP0205', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 89, DATE_SUB(NOW(), INTERVAL 228 HOUR)),
(9011, 'demo-prac-9011-3-1', 'Q_0309', 'KP0306', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 90, DATE_SUB(NOW(), INTERVAL 228 HOUR)),
(9011, 'demo-prac-9011-3-2', 'Q_0312', 'KP0306', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 101, DATE_SUB(NOW(), INTERVAL 229 HOUR)),
(9011, 'demo-prac-9011-3-3', 'Q_0309', 'KP0306', 'PLAN', 1, '[演示数据] 正确选项占位', NULL, 112, DATE_SUB(NOW(), INTERVAL 230 HOUR)),
(9011, 'demo-prac-9011-4-1', 'Q_0401', 'KP0401', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 113, DATE_SUB(NOW(), INTERVAL 230 HOUR)),
(9011, 'demo-prac-9011-4-2', 'Q_0402', 'KP0401', 'PLAN', 1, '[演示数据] 正确选项占位', NULL, 124, DATE_SUB(NOW(), INTERVAL 231 HOUR)),
(9011, 'demo-prac-9011-4-3', 'Q_0403', 'KP0401', 'SELF', 0, '[演示数据] 错误选项占位', 'STEP_OMISSION', 135, DATE_SUB(NOW(), INTERVAL 232 HOUR)),
(9011, 'demo-prac-9011-5-1', 'Q_0419', 'KP0408', 'PLAN', 1, '[演示数据] 正确选项占位', NULL, 136, DATE_SUB(NOW(), INTERVAL 232 HOUR)),
(9011, 'demo-prac-9011-5-2', 'Q_0420', 'KP0408', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 147, DATE_SUB(NOW(), INTERVAL 233 HOUR)),
(9011, 'demo-prac-9011-5-3', 'Q_0419', 'KP0408', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 158, DATE_SUB(NOW(), INTERVAL 234 HOUR))
ON DUPLICATE KEY UPDATE q_id = VALUES(q_id), kp_id = VALUES(kp_id), source = VALUES(source), is_correct = VALUES(is_correct), my_answer = VALUES(my_answer), error_type = VALUES(error_type), cost_seconds = VALUES(cost_seconds), created_at = VALUES(created_at);

-- 每名学生 2 条已确认错题，供教师下钻与评阅证据链使用。
INSERT INTO student_error_record (log_id, user_id, client_token, q_id, kp_id, image_url, ocr_text, corrected_text, ocr_confidence, error_type, student_note, status, confirmed_at, created_at) VALUES
('EL_DEMO_2_1', 2, 'demo-err-2-1', 'Q_0201', 'KP0201', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'METHOD_MISSING', '[演示数据] 该记录由种子脚本构造，用于答辩演示，不是真实学生数据。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 12 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 12 HOUR)),
('EL_DEMO_2_2', 2, 'demo-err-2-2', 'Q_0302', 'KP0302', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'CALCULATION_ERROR', '[演示数据] 该记录由种子脚本构造，用于答辩演示，不是真实学生数据。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 18 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 18 HOUR)),
('EL_DEMO_9001_1', 9001, 'demo-err-9001-1', 'Q_0201', 'KP0201', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'STEP_OMISSION', '[演示数据] 该记录由种子脚本构造，用于答辩演示，不是真实学生数据。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 24 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 24 HOUR)),
('EL_DEMO_9001_2', 9001, 'demo-err-9001-2', 'Q_0302', 'KP0302', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'BOUNDARY_ERROR', '[演示数据] 该记录由种子脚本构造，用于答辩演示，不是真实学生数据。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 30 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 30 HOUR)),
('EL_DEMO_9002_1', 9002, 'demo-err-9002-1', 'Q_0201', 'KP0201', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'CALCULATION_ERROR', '[演示数据] 该记录由种子脚本构造，用于答辩演示，不是真实学生数据。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 36 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 36 HOUR)),
('EL_DEMO_9002_2', 9002, 'demo-err-9002-2', 'Q_0302', 'KP0302', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'CONCEPT_CONFUSION', '[演示数据] 该记录由种子脚本构造，用于答辩演示，不是真实学生数据。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 42 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 42 HOUR)),
('EL_DEMO_9003_1', 9003, 'demo-err-9003-1', 'Q_0201', 'KP0201', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'BOUNDARY_ERROR', '[演示数据] 该记录由种子脚本构造，用于答辩演示，不是真实学生数据。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 48 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 48 HOUR)),
('EL_DEMO_9003_2', 9003, 'demo-err-9003-2', 'Q_0302', 'KP0302', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'METHOD_MISSING', '[演示数据] 该记录由种子脚本构造，用于答辩演示，不是真实学生数据。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 54 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 54 HOUR)),
('EL_DEMO_9004_1', 9004, 'demo-err-9004-1', 'Q_0218', 'KP0202', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'METHOD_MISSING', '[演示数据] 该记录由种子脚本构造，用于答辩演示，不是真实学生数据。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 61 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 61 HOUR)),
('EL_DEMO_9004_2', 9004, 'demo-err-9004-2', 'Q_0405', 'KP0402', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'BOUNDARY_ERROR', '[演示数据] 该记录由种子脚本构造，用于答辩演示，不是真实学生数据。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 70 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 70 HOUR)),
('EL_DEMO_9005_1', 9005, 'demo-err-9005-1', 'Q_0218', 'KP0202', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'STEP_OMISSION', '[演示数据] 该记录由种子脚本构造，用于答辩演示，不是真实学生数据。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 73 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 73 HOUR)),
('EL_DEMO_9005_2', 9005, 'demo-err-9005-2', 'Q_0405', 'KP0402', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'CONCEPT_CONFUSION', '[演示数据] 该记录由种子脚本构造，用于答辩演示，不是真实学生数据。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 82 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 82 HOUR)),
('EL_DEMO_9006_1', 9006, 'demo-err-9006-1', 'Q_0218', 'KP0202', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'CALCULATION_ERROR', '[演示数据] 该记录由种子脚本构造，用于答辩演示，不是真实学生数据。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 85 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 85 HOUR)),
('EL_DEMO_9006_2', 9006, 'demo-err-9006-2', 'Q_0405', 'KP0402', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'METHOD_MISSING', '[演示数据] 该记录由种子脚本构造，用于答辩演示，不是真实学生数据。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 94 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 94 HOUR)),
('EL_DEMO_9007_1', 9007, 'demo-err-9007-1', 'Q_0218', 'KP0202', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'BOUNDARY_ERROR', '[演示数据] 该记录由种子脚本构造，用于答辩演示，不是真实学生数据。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 97 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 97 HOUR)),
('EL_DEMO_9007_2', 9007, 'demo-err-9007-2', 'Q_0405', 'KP0402', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'STEP_OMISSION', '[演示数据] 该记录由种子脚本构造，用于答辩演示，不是真实学生数据。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 106 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 106 HOUR)),
('EL_DEMO_9008_1', 9008, 'demo-err-9008-1', 'Q_0116', 'KP0107', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'BOUNDARY_ERROR', '[演示数据] 该记录由种子脚本构造，用于答辩演示，不是真实学生数据。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 108 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 108 HOUR)),
('EL_DEMO_9008_2', 9008, 'demo-err-9008-2', 'Q_0403', 'KP0401', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'BOUNDARY_ERROR', '[演示数据] 该记录由种子脚本构造，用于答辩演示，不是真实学生数据。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 119 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 119 HOUR)),
('EL_DEMO_9009_1', 9009, 'demo-err-9009-1', 'Q_0116', 'KP0107', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'CONCEPT_CONFUSION', '[演示数据] 该记录由种子脚本构造，用于答辩演示，不是真实学生数据。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 120 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 120 HOUR)),
('EL_DEMO_9009_2', 9009, 'demo-err-9009-2', 'Q_0403', 'KP0401', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'CONCEPT_CONFUSION', '[演示数据] 该记录由种子脚本构造，用于答辩演示，不是真实学生数据。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 131 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 131 HOUR)),
('EL_DEMO_9010_1', 9010, 'demo-err-9010-1', 'Q_0116', 'KP0107', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'METHOD_MISSING', '[演示数据] 该记录由种子脚本构造，用于答辩演示，不是真实学生数据。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 200 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 200 HOUR)),
('EL_DEMO_9010_2', 9010, 'demo-err-9010-2', 'Q_0403', 'KP0401', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'METHOD_MISSING', '[演示数据] 该记录由种子脚本构造，用于答辩演示，不是真实学生数据。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 208 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 208 HOUR)),
('EL_DEMO_9011_1', 9011, 'demo-err-9011-1', 'Q_0116', 'KP0107', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'STEP_OMISSION', '[演示数据] 该记录由种子脚本构造，用于答辩演示，不是真实学生数据。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 224 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 224 HOUR)),
('EL_DEMO_9011_2', 9011, 'demo-err-9011-2', 'Q_0403', 'KP0401', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'STEP_OMISSION', '[演示数据] 该记录由种子脚本构造，用于答辩演示，不是真实学生数据。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 232 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 232 HOUR))
ON DUPLICATE KEY UPDATE q_id = VALUES(q_id), kp_id = VALUES(kp_id), ocr_confidence = VALUES(ocr_confidence), error_type = VALUES(error_type), student_note = VALUES(student_note), status = VALUES(status), confirmed_at = VALUES(confirmed_at), created_at = VALUES(created_at);

-- 12 条 RAG 讲解留痕：9 条待评阅、3 条已评阅；其中 3 条为 L3 本地模板降级。
INSERT INTO ai_generation_log (explain_id, trace_id, user_id, log_id, scene, model, prompt_version, is_degraded, degrade_level, latency_ms, prompt_tokens, completion_tokens, source_ids, content_text, is_blocked, teacher_score, teacher_comment, review_result, reviewed_at, created_at)
SELECT 'DEMO_EX_2', 'DEMO_TRACE_2', 2, 'EL_DEMO_2_1', 'RAG', 'deepseek-chat', 'demo-v1', 0, NULL, 1290, 620, 380, JSON_ARRAY('KP0201'), '[演示数据] 本段为答辩演示占位讲解，不是真实模型输出。知识点 KP0201 的演示讲解：先给出定义，再说明适用条件，最后用一个反例说明边界。', 0, NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 15 HOUR) FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ai_generation_log WHERE explain_id = 'DEMO_EX_2');

INSERT INTO ai_generation_log (explain_id, trace_id, user_id, log_id, scene, model, prompt_version, is_degraded, degrade_level, latency_ms, prompt_tokens, completion_tokens, source_ids, content_text, is_blocked, teacher_score, teacher_comment, review_result, reviewed_at, created_at)
SELECT 'DEMO_EX_9001', 'DEMO_TRACE_9001', 9001, 'EL_DEMO_9001_1', 'RAG', 'deepseek-chat', 'demo-v1', 0, NULL, 1380, 620, 380, JSON_ARRAY('KP0201'), '[演示数据] 本段为答辩演示占位讲解，不是真实模型输出。知识点 KP0201 的演示讲解：先给出定义，再说明适用条件，最后用一个反例说明边界。', 0, NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 18 HOUR) FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ai_generation_log WHERE explain_id = 'DEMO_EX_9001');

INSERT INTO ai_generation_log (explain_id, trace_id, user_id, log_id, scene, model, prompt_version, is_degraded, degrade_level, latency_ms, prompt_tokens, completion_tokens, source_ids, content_text, is_blocked, teacher_score, teacher_comment, review_result, reviewed_at, created_at)
SELECT 'DEMO_EX_9002', 'DEMO_TRACE_9002', 9002, 'EL_DEMO_9002_1', 'RAG', 'local-template', 'demo-v1', 1, 'L3', 35, 0, 0, JSON_ARRAY('KP0201'), '[演示数据] 本段为答辩演示占位讲解，不是真实模型输出。知识点 KP0201 的演示讲解：先给出定义，再说明适用条件，最后用一个反例说明边界。', 0, NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 21 HOUR) FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ai_generation_log WHERE explain_id = 'DEMO_EX_9002');

INSERT INTO ai_generation_log (explain_id, trace_id, user_id, log_id, scene, model, prompt_version, is_degraded, degrade_level, latency_ms, prompt_tokens, completion_tokens, source_ids, content_text, is_blocked, teacher_score, teacher_comment, review_result, reviewed_at, created_at)
SELECT 'DEMO_EX_9003', 'DEMO_TRACE_9003', 9003, 'EL_DEMO_9003_1', 'RAG', 'deepseek-chat', 'demo-v1', 0, NULL, 1560, 620, 380, JSON_ARRAY('KP0201'), '[演示数据] 本段为答辩演示占位讲解，不是真实模型输出。知识点 KP0201 的演示讲解：先给出定义，再说明适用条件，最后用一个反例说明边界。', 0, NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 24 HOUR) FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ai_generation_log WHERE explain_id = 'DEMO_EX_9003');

INSERT INTO ai_generation_log (explain_id, trace_id, user_id, log_id, scene, model, prompt_version, is_degraded, degrade_level, latency_ms, prompt_tokens, completion_tokens, source_ids, content_text, is_blocked, teacher_score, teacher_comment, review_result, reviewed_at, created_at)
SELECT 'DEMO_EX_9004', 'DEMO_TRACE_9004', 9004, 'EL_DEMO_9004_1', 'RAG', 'deepseek-chat', 'demo-v1', 0, NULL, 1650, 620, 380, JSON_ARRAY('KP0202'), '[演示数据] 本段为答辩演示占位讲解，不是真实模型输出。知识点 KP0202 的演示讲解：先给出定义，再说明适用条件，最后用一个反例说明边界。', 0, NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 27 HOUR) FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ai_generation_log WHERE explain_id = 'DEMO_EX_9004');

INSERT INTO ai_generation_log (explain_id, trace_id, user_id, log_id, scene, model, prompt_version, is_degraded, degrade_level, latency_ms, prompt_tokens, completion_tokens, source_ids, content_text, is_blocked, teacher_score, teacher_comment, review_result, reviewed_at, created_at)
SELECT 'DEMO_EX_9005', 'DEMO_TRACE_9005', 9005, 'EL_DEMO_9005_1', 'RAG', 'deepseek-chat', 'demo-v1', 0, NULL, 1740, 620, 380, JSON_ARRAY('KP0202'), '[演示数据] 本段为答辩演示占位讲解，不是真实模型输出。知识点 KP0202 的演示讲解：先给出定义，再说明适用条件，最后用一个反例说明边界。', 0, NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 30 HOUR) FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ai_generation_log WHERE explain_id = 'DEMO_EX_9005');

INSERT INTO ai_generation_log (explain_id, trace_id, user_id, log_id, scene, model, prompt_version, is_degraded, degrade_level, latency_ms, prompt_tokens, completion_tokens, source_ids, content_text, is_blocked, teacher_score, teacher_comment, review_result, reviewed_at, created_at)
SELECT 'DEMO_EX_9006', 'DEMO_TRACE_9006', 9006, 'EL_DEMO_9006_1', 'RAG', 'local-template', 'demo-v1', 1, 'L3', 35, 0, 0, JSON_ARRAY('KP0202'), '[演示数据] 本段为答辩演示占位讲解，不是真实模型输出。知识点 KP0202 的演示讲解：先给出定义，再说明适用条件，最后用一个反例说明边界。', 0, NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 33 HOUR) FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ai_generation_log WHERE explain_id = 'DEMO_EX_9006');

INSERT INTO ai_generation_log (explain_id, trace_id, user_id, log_id, scene, model, prompt_version, is_degraded, degrade_level, latency_ms, prompt_tokens, completion_tokens, source_ids, content_text, is_blocked, teacher_score, teacher_comment, review_result, reviewed_at, created_at)
SELECT 'DEMO_EX_9007', 'DEMO_TRACE_9007', 9007, 'EL_DEMO_9007_1', 'RAG', 'deepseek-chat', 'demo-v1', 0, NULL, 1920, 620, 380, JSON_ARRAY('KP0202'), '[演示数据] 本段为答辩演示占位讲解，不是真实模型输出。知识点 KP0202 的演示讲解：先给出定义，再说明适用条件，最后用一个反例说明边界。', 0, NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 36 HOUR) FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ai_generation_log WHERE explain_id = 'DEMO_EX_9007');

INSERT INTO ai_generation_log (explain_id, trace_id, user_id, log_id, scene, model, prompt_version, is_degraded, degrade_level, latency_ms, prompt_tokens, completion_tokens, source_ids, content_text, is_blocked, teacher_score, teacher_comment, review_result, reviewed_at, created_at)
SELECT 'DEMO_EX_9008', 'DEMO_TRACE_9008', 9008, 'EL_DEMO_9008_1', 'RAG', 'deepseek-chat', 'demo-v1', 0, NULL, 2010, 620, 380, JSON_ARRAY('KP0107'), '[演示数据] 本段为答辩演示占位讲解，不是真实模型输出。知识点 KP0107 的演示讲解：先给出定义，再说明适用条件，最后用一个反例说明边界。', 0, NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 39 HOUR) FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ai_generation_log WHERE explain_id = 'DEMO_EX_9008');

INSERT INTO ai_generation_log (explain_id, trace_id, user_id, log_id, scene, model, prompt_version, is_degraded, degrade_level, latency_ms, prompt_tokens, completion_tokens, source_ids, content_text, is_blocked, teacher_score, teacher_comment, review_result, reviewed_at, created_at)
SELECT 'DEMO_EX_9009', 'DEMO_TRACE_9009', 9009, 'EL_DEMO_9009_1', 'RAG', 'deepseek-chat', 'demo-v1', 0, NULL, 2100, 620, 380, JSON_ARRAY('KP0107'), '[演示数据] 本段为答辩演示占位讲解，不是真实模型输出。知识点 KP0107 的演示讲解：先给出定义，再说明适用条件，最后用一个反例说明边界。', 0, 4, '[演示数据] 结构完整，建议补充一个反例。', 'PASS', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 42 HOUR) FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ai_generation_log WHERE explain_id = 'DEMO_EX_9009');

INSERT INTO ai_generation_log (explain_id, trace_id, user_id, log_id, scene, model, prompt_version, is_degraded, degrade_level, latency_ms, prompt_tokens, completion_tokens, source_ids, content_text, is_blocked, teacher_score, teacher_comment, review_result, reviewed_at, created_at)
SELECT 'DEMO_EX_9010', 'DEMO_TRACE_9010', 9010, 'EL_DEMO_9010_1', 'RAG', 'local-template', 'demo-v1', 1, 'L3', 35, 0, 0, JSON_ARRAY('KP0107'), '[演示数据] 本段为答辩演示占位讲解，不是真实模型输出。知识点 KP0107 的演示讲解：先给出定义，再说明适用条件，最后用一个反例说明边界。', 0, 4, '[演示数据] 结构完整，建议补充一个反例。', 'PASS', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 45 HOUR) FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ai_generation_log WHERE explain_id = 'DEMO_EX_9010');

INSERT INTO ai_generation_log (explain_id, trace_id, user_id, log_id, scene, model, prompt_version, is_degraded, degrade_level, latency_ms, prompt_tokens, completion_tokens, source_ids, content_text, is_blocked, teacher_score, teacher_comment, review_result, reviewed_at, created_at)
SELECT 'DEMO_EX_9011', 'DEMO_TRACE_9011', 9011, 'EL_DEMO_9011_1', 'RAG', 'deepseek-chat', 'demo-v1', 0, NULL, 2280, 620, 380, JSON_ARRAY('KP0107'), '[演示数据] 本段为答辩演示占位讲解，不是真实模型输出。知识点 KP0107 的演示讲解：先给出定义，再说明适用条件，最后用一个反例说明边界。', 0, 4, '[演示数据] 结构完整，建议补充一个反例。', 'PASS', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 48 HOUR) FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ai_generation_log WHERE explain_id = 'DEMO_EX_9011');

-- 每名学生 2 条学习计划（今日 + 昨日），让教师列表的计划进度列有数据。
INSERT INTO study_plan (user_id, plan_date, plan_count, done_count, status) VALUES
(2, CURDATE(), 3, 2, 'PENDING'),
(2, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 3, 3, 'DONE'),
(9001, CURDATE(), 3, 2, 'PENDING'),
(9001, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 3, 3, 'DONE'),
(9002, CURDATE(), 3, 2, 'PENDING'),
(9002, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 3, 3, 'DONE'),
(9003, CURDATE(), 3, 2, 'PENDING'),
(9003, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 3, 3, 'DONE'),
(9004, CURDATE(), 3, 2, 'PENDING'),
(9004, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 3, 3, 'DONE'),
(9005, CURDATE(), 3, 2, 'PENDING'),
(9005, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 3, 3, 'DONE'),
(9006, CURDATE(), 3, 2, 'PENDING'),
(9006, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 3, 3, 'DONE'),
(9007, CURDATE(), 3, 2, 'PENDING'),
(9007, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 3, 3, 'DONE'),
(9008, CURDATE(), 3, 2, 'PENDING'),
(9008, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 3, 3, 'DONE'),
(9009, CURDATE(), 3, 2, 'PENDING'),
(9009, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 3, 3, 'DONE'),
(9010, CURDATE(), 3, 2, 'PENDING'),
(9010, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 3, 3, 'DONE'),
(9011, CURDATE(), 3, 2, 'PENDING'),
(9011, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 3, 3, 'DONE')
ON DUPLICATE KEY UPDATE plan_count = VALUES(plan_count), done_count = VALUES(done_count), status = VALUES(status);
