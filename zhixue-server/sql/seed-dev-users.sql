-- ============================================================
-- 智学领航 · 本地开发种子账号（仅本地开发环境，生产环境严禁执行）
-- 口令统一为 ChangeMe@2026（BCrypt），首次登录后必须修改。
-- 生产环境：账号由管理员按需创建，口令通过环境变量/首登改密注入，禁止固定口令。
-- 可重复执行：按 username 幂等更新。
-- ============================================================

USE zhixue;
SET NAMES utf8mb4;

INSERT INTO sys_user (user_id, username, password, real_name, role, class_name)
VALUES (1, 'teacher01', '$2a$10$7gTMsmEghWWChdEa83pnFu2CcL7s08WGi12XK4xwvN2N8fSFssvIe', '教师A', 'TEACHER', NULL)
ON DUPLICATE KEY UPDATE password = VALUES(password), real_name = VALUES(real_name),
                        role = VALUES(role), class_name = VALUES(class_name), is_active = 1;

INSERT INTO sys_user (user_id, username, password, real_name, role, class_name)
VALUES (2, 'student01', '$2a$10$7gTMsmEghWWChdEa83pnFu2CcL7s08WGi12XK4xwvN2N8fSFssvIe', '学生A', 'STUDENT', '23软件工程1班')
ON DUPLICATE KEY UPDATE password = VALUES(password), real_name = VALUES(real_name),
                        role = VALUES(role), class_name = VALUES(class_name), is_active = 1;
