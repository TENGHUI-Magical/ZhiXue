-- 智学领航 · 第8章先修边（知识图谱边，草稿）
-- 日期：2026-11-03　产出：Trae AI（按教材第8章知识结构编排，待内容复核人签核）
-- 语义：kp_id 的先修是 prereq_kp_id，边方向为“先修 → 后继”。
-- 幂等：uk_edge(kp_id, prereq_kp_id)，可重复执行。
-- 前置：先导入 seed-kp-ch08.sql；未签核前不要执行本文件，避免悬空边。
USE zhixue;
SET NAMES utf8mb4;

INSERT INTO knowledge_prereq (kp_id, prereq_kp_id) VALUES
-- 8.1 概述是本章根：能力限制 → WITH / 内置函数 / 宿主语言两条扩展路径
('KP0802', 'KP0801'),  -- WITH子句是扩展SQL功能的第一种做法
('KP0803', 'KP0802'),  -- WITH RECURSIVE是WITH子句的特殊形式
('KP0804', 'KP0801'),  -- 内置函数与WITH子句并列，都是扩展SQL自身功能
('KP0805', 'KP0801'),  -- 嵌入式SQL/ODBC/JDBC是通过高级语言实现复杂应用
('KP0803', 'KP0304'),  -- 递归查询中的直接先修课由Course自身连接求得（跨章）
-- 8.2 过程化SQL：块 → 流程控制 → 游标 → 存储过程/存储函数
('KP0806', 'KP0801'),  -- 过程化SQL是SQL自身的过程控制扩展，不依赖嵌入式SQL
('KP0807', 'KP0806'),  -- 流程控制写在过程化SQL块内
('KP0808', 'KP0807'),  -- 游标遍历依赖LOOP与EXIT等流程控制
('KP0809', 'KP0808'),  -- 存储过程例8.5用游标逐门课程计算GPA
-- 8.3 JDBC 编程
('KP0810', 'KP0801')   -- JDBC是Java访问数据库的标准接口，基础可独立于存储过程
ON DUPLICATE KEY UPDATE kp_id = VALUES(kp_id);
