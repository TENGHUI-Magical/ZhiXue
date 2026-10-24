-- 智学领航 · 数据库初始化脚本 V1（历史快照）
-- 注意：本文件仅保留给期中佐证材料/旧文档引用；执行环境一律使用 init-v2.sql（16 表）。
-- 与 docker-compose.yml 配套：MySQL 8，库名 zhixue（单实例 schema 隔离，规划书 2.2 节）
-- 本脚本幂等，可重复执行。

CREATE DATABASE IF NOT EXISTS zhixue
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE zhixue;

-- ===== 1. 用户表（学生/教师双角色） =====
CREATE TABLE IF NOT EXISTS sys_user (
  user_id     BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
  username    VARCHAR(50)  NOT NULL COMMENT '学号/工号',
  password    VARCHAR(100) NOT NULL COMMENT 'Sa-Token 加密密码',
  real_name   VARCHAR(50)  NOT NULL COMMENT '姓名',
  role        VARCHAR(20)  NOT NULL COMMENT '角色: STUDENT/TEACHER',
  class_name  VARCHAR(50)  DEFAULT NULL COMMENT '班级',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_username (username)
) COMMENT '用户表';

-- ===== 2. 课程表 =====
CREATE TABLE IF NOT EXISTS course (
  course_id   BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '课程ID',
  course_name VARCHAR(100) NOT NULL COMMENT '课程名称（试点: 数据结构）',
  teacher_id  BIGINT       NOT NULL COMMENT '负责教师 user_id',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '课程表';

-- ===== 3. 知识点表（章→节→知识点三级树形） =====
CREATE TABLE IF NOT EXISTS knowledge_point (
  kp_id        VARCHAR(20) PRIMARY KEY COMMENT '知识点ID, 如 KP0301',
  course_id    BIGINT       NOT NULL COMMENT '所属课程',
  chapter      VARCHAR(50)  NOT NULL COMMENT '章节, 如 第3章: 栈与队列',
  name         VARCHAR(100) NOT NULL COMMENT '专有名词命名',
  concept_text TEXT COMMENT '核心概念文本切片(300-500字), 供RAG检索',
  created_by   BIGINT       NOT NULL COMMENT '录入人 user_id',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_course (course_id)
) COMMENT '知识点表';

-- ===== 4. 先修关系表（知识图谱边，取代图数据库） =====
CREATE TABLE IF NOT EXISTS knowledge_prereq (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  kp_id        VARCHAR(20) NOT NULL COMMENT '知识点',
  prereq_kp_id VARCHAR(20) NOT NULL COMMENT '先修知识点',
  UNIQUE KEY uk_edge (kp_id, prereq_kp_id)
) COMMENT '知识点先修关系表';

-- ===== 5. 题库表 =====
CREATE TABLE IF NOT EXISTS question_bank (
  q_id          VARCHAR(20) PRIMARY KEY COMMENT '题目ID, 如 Q_001',
  course_id     BIGINT       NOT NULL COMMENT '所属课程',
  kp_id         VARCHAR(20)  NOT NULL COMMENT '关联知识点(可多个, 逗号分隔)',
  question_text TEXT         NOT NULL COMMENT '题目文本(公式统一LaTeX)',
  answer_text   TEXT COMMENT '标准答案/解析',
  difficulty    VARCHAR(10)  DEFAULT NULL COMMENT '难度: 简单/中等/困难',
  variant_hint  VARCHAR(200) DEFAULT NULL COMMENT '变式题建议方向',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_kp (kp_id)
) COMMENT '题库表';

-- ===== 6. 错题记录表（OCR双通道） =====
CREATE TABLE IF NOT EXISTS student_error_record (
  log_id         VARCHAR(20) PRIMARY KEY COMMENT '错题记录ID, 如 EL_101',
  user_id        BIGINT       NOT NULL COMMENT '学生 user_id',
  q_id           VARCHAR(20)  DEFAULT NULL COMMENT '关联题库(无则NULL)',
  kp_id          VARCHAR(20)  DEFAULT NULL COMMENT '学生确认的知识点',
  image_url      VARCHAR(200) DEFAULT NULL COMMENT '原始错题图片',
  ocr_text       TEXT COMMENT 'OCR识别原文',
  corrected_text TEXT COMMENT '学生人工校正后文本',
  ocr_confidence DECIMAL(4,3) DEFAULT NULL COMMENT '识别置信度',
  error_type     VARCHAR(30)  NOT NULL COMMENT '错因: 概念混淆/步骤遗漏/边界条件忽略/计算失误/方法缺失',
  student_note   VARCHAR(500) DEFAULT NULL COMMENT '学生备注/错因自述',
  status         VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/CONFIRMED',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user (user_id),
  KEY idx_kp_time (kp_id, created_at)
) COMMENT '错题记录表';

-- ===== 7. 学情画像快照表 =====
CREATE TABLE IF NOT EXISTS learning_portrait_snapshot (
  snapshot_id   BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id       BIGINT   NOT NULL COMMENT '学生 user_id',
  dim_score     JSON     NOT NULL COMMENT '五维得分 {concept,structure,algorithm,complexity,boundary}',
  weakest_kps   JSON COMMENT '薄弱知识点TOP-N(可解释口径)',
  error_dist    JSON COMMENT '错因分布统计',
  total_train   INT      NOT NULL DEFAULT 0 COMMENT '总训练量(题次)',
  stat_date     DATE     NOT NULL COMMENT '统计日期',
  UNIQUE KEY uk_user_date (user_id, stat_date)
) COMMENT '学情画像快照表';

-- ===== 8. AI交互日志与教师评阅表 =====
CREATE TABLE IF NOT EXISTS ai_generation_log (
  trace_id        VARCHAR(40) PRIMARY KEY COMMENT '链路追踪ID',
  user_id         BIGINT       NOT NULL,
  log_id          VARCHAR(20)  DEFAULT NULL COMMENT '关联错题记录',
  scene           VARCHAR(20)  NOT NULL COMMENT '场景: OCR/RAG/LLM/RECOMMEND',
  model           VARCHAR(50)  DEFAULT NULL COMMENT '实际调用模型',
  prompt_tokens   INT DEFAULT 0, completion_tokens INT DEFAULT 0,
  rag_sources     JSON COMMENT '引用来源(教材页码/讲义段落/切片kp)',
  block_flag      TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否触发合规拦截',
  teacher_score   TINYINT DEFAULT NULL COMMENT '教师评阅分(1-5)',
  teacher_comment VARCHAR(500) DEFAULT NULL COMMENT '教师评语',
  reviewed_at     DATETIME DEFAULT NULL,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_scene_time (scene, created_at),
  KEY idx_pending_review (reviewed_at)
) COMMENT 'AI交互日志与教师评阅表';

-- ===== 9. 学习计划与打卡表 =====
CREATE TABLE IF NOT EXISTS study_plan (
  plan_id     BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id     BIGINT       NOT NULL COMMENT '学生 user_id',
  plan_date   DATE         NOT NULL COMMENT '计划日期',
  plan_count  INT          NOT NULL DEFAULT 3 COMMENT '当日题量(每天限3题)',
  done_count  INT          NOT NULL DEFAULT 0 COMMENT '完成数',
  status      VARCHAR(10)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DONE/OVERDUE',
  UNIQUE KEY uk_user_date (user_id, plan_date)
) COMMENT '学习计划与打卡表';

-- ===== 种子数据（数据标注规范 V1.0 的 Sheet1 模板实例） =====
INSERT INTO course (course_id, course_name, teacher_id)
VALUES (1, '数据结构', 1)
ON DUPLICATE KEY UPDATE course_name = VALUES(course_name);

INSERT INTO knowledge_point (kp_id, course_id, chapter, name, concept_text, created_by) VALUES
('KP0301', 1, '第 3 章：栈与队列', '栈的定义与特性',
 '栈是一种只能在一端进行插入或删除操作的线性表，该端称为栈顶，另一端称为栈底。栈遵循后进先出（LIFO）原则……',
 1),
('KP0302', 1, '第 3 章：栈与队列', '栈的应用-括号匹配', '在括号匹配算法中，遇到左括号入栈，遇到右括号则与栈顶元素配对；匹配成功出栈，失败判定非法；扫描结束后栈为空则序列合法……', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name);
