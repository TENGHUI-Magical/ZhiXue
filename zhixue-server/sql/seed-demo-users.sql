-- 智学领航 · 演示库账号（仅演示库使用）
-- 用途：全新环境重建答辩演示库，保证账号可登录、教师端多行数据可见。
-- 口令：Demo@2026（仅演示库；生产/真实试用环境严禁使用本文件）
-- 幂等：按 user_id 插入；已存在的账号只更新角色/姓名/班级，不重置已有口令。
-- 前置：先执行 init-v2.sql（建表）。
USE zhixue;
SET NAMES utf8mb4;

SET @demo_password = '$2a$10$B5XbzrDy2S2IrgpbVead.eJ1G7RRpnY4CHJrHYg8Z5QaVVo3N1UjO';

INSERT INTO sys_user (user_id, username, password, real_name, role, class_name) VALUES
(1,    'teacher01', @demo_password, '演示教师',   'TEACHER', NULL),
(2,    'student01', @demo_password, '演示学生01', 'STUDENT', '演示数据-23软件工程1班'),
(9001, 'demo_stu01', @demo_password, '演示学生02', 'STUDENT', '演示数据-23软件工程1班'),
(9002, 'demo_stu02', @demo_password, '演示学生03', 'STUDENT', '演示数据-23软件工程1班'),
(9003, 'demo_stu03', @demo_password, '演示学生04', 'STUDENT', '演示数据-23软件工程1班'),
(9004, 'demo_stu04', @demo_password, '演示学生05', 'STUDENT', '演示数据-23软件工程1班'),
(9005, 'demo_stu05', @demo_password, '演示学生06', 'STUDENT', '演示数据-23软件工程1班'),
(9006, 'demo_stu06', @demo_password, '演示学生07', 'STUDENT', '演示数据-23软件工程1班'),
(9007, 'demo_stu07', @demo_password, '演示学生08', 'STUDENT', '演示数据-23软件工程1班'),
(9008, 'demo_stu08', @demo_password, '演示学生09', 'STUDENT', '演示数据-23软件工程1班'),
(9009, 'demo_stu09', @demo_password, '演示学生10', 'STUDENT', '演示数据-23软件工程1班'),
(9010, 'demo_stu10', @demo_password, '演示学生11', 'STUDENT', '演示数据-23软件工程1班'),
(9011, 'demo_stu11', @demo_password, '演示学生12', 'STUDENT', '演示数据-23软件工程1班')
ON DUPLICATE KEY UPDATE
  real_name = VALUES(real_name),
  role = VALUES(role),
  class_name = VALUES(class_name),
  is_active = 1;
