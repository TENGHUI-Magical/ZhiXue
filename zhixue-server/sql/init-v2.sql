-- ============================================================
-- 智学领航：高校课程学情诊断系统
-- 数据库 V2 · MySQL 8.0+
-- 本文件是表结构的唯一权威来源，设计说明见 03-数据库设计-V2.md
-- 约定：不用外键（关联关系在应用层维护）、表名小写下划线、单数、字段带注释
-- ============================================================

-- 时间戳口径（2026-11-03）：业务时间只由应用写（Java 侧 LocalDateTime.now(Asia/Shanghai)）。
-- 本文件保留 DEFAULT CURRENT_TIMESTAMP 仅作手工/种子数据/漏写字段的兜底；CI 或服务器默认
-- 时区（UTC）不得作为业务时间来源——CI MySQL 默认 UTC，直连写入得到的“当前时间”比上海
-- 时间少 8 小时，禁止用于验收断言/对账。
-- 不删默认值的理由（路径 B）：删除会让所有未显式赋值的插入点在严格模式下直接失败，
-- 涉及 user/course/diagnosis/plan 多个模块，属跨模块 schema 大改，超出本轮范围；
-- 口径约束改由应用侧保证，并在 docs/13 规则 8 声明。

-- Windows 下 mysql 客户端默认字符集可能是 GBK，显式声明避免中文乱码/1366 错误
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS zhixue
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE zhixue;

-- ------------------------------------------------------------
-- 1. sys_user 用户（学生 / 教师双角色）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user (
  user_id      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  username     VARCHAR(50)  NOT NULL                COMMENT '学号或工号，登录名',
  password     VARCHAR(100) NOT NULL                COMMENT 'BCrypt 哈希，禁止存明文',
  real_name    VARCHAR(50)  NOT NULL                COMMENT '姓名',
  role         VARCHAR(20)  NOT NULL                COMMENT '角色：STUDENT / TEACHER',
  class_name   VARCHAR(50)  DEFAULT NULL            COMMENT '班级名称，教师端按此聚合',
  is_active    TINYINT UNSIGNED NOT NULL DEFAULT 1  COMMENT '是否启用：1 启用，0 停用',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (user_id),
  UNIQUE KEY uk_username (username),
  KEY idx_role_class (role, class_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ------------------------------------------------------------
-- 2. course 课程
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS course (
  course_id    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '课程ID',
  course_name  VARCHAR(100) NOT NULL                COMMENT '课程名称，如 数据库原理',
  teacher_id   BIGINT       NOT NULL                COMMENT '负责教师 user_id',
  textbook     VARCHAR(200) DEFAULT NULL            COMMENT '教材名称与版本，RAG 来源追溯依据',
  description  VARCHAR(500) DEFAULT NULL            COMMENT '课程简介',
  dimension_json JSON      DEFAULT NULL            COMMENT '五维能力配置，按课程定义；前端雷达图读取渲染',
  is_active    TINYINT UNSIGNED NOT NULL DEFAULT 1  COMMENT '是否启用',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (course_id),
  KEY idx_teacher (teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程表';

-- ------------------------------------------------------------
-- 3. course_chapter 章节（替代字符串章节名，解决"第10章"排序错乱）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS course_chapter (
  chapter_id   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '章节ID',
  course_id    BIGINT       NOT NULL                COMMENT '所属课程',
  chapter_no   INT          NOT NULL                COMMENT '章序号，用于正确排序（10 排在 2 之后）',
  title        VARCHAR(100) NOT NULL                COMMENT '章节标题，如 关系数据库基础',
  sort_no      INT          NOT NULL DEFAULT 0      COMMENT '章内排序，预留节级扩展',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (chapter_id),
  UNIQUE KEY uk_course_chapter_no (course_id, chapter_no),
  KEY idx_course_sort (course_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程章节表';

-- ------------------------------------------------------------
-- 4. knowledge_point 知识点
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS knowledge_point (
  kp_id          VARCHAR(20)  NOT NULL            COMMENT '知识点编号，如 KP0201（教材章号+序号，业务主键）',
  course_id      BIGINT       NOT NULL            COMMENT '所属课程',
  chapter_id     BIGINT       NOT NULL            COMMENT '所属章节',
  name           VARCHAR(100) NOT NULL            COMMENT '知识点名称，专有名词，如 关系模型的基本概念',
  concept_text   TEXT         DEFAULT NULL        COMMENT '核心概念切片 300-500 字，RAG 检索与模板降级共用',
  misconception  TEXT         DEFAULT NULL        COMMENT '常见误区 1-3 条，换行分隔，讲解的误区段直接引用',
  textbook_page  VARCHAR(50)  DEFAULT NULL        COMMENT '教材页码，如 P34，来源追溯展示',
  trial_hint     VARCHAR(200) DEFAULT NULL        COMMENT '自检题提示，讲解末段用',
  dimension_key  VARCHAR(30)  DEFAULT NULL        COMMENT '五维归属键，须存在于 course.dimension_json',
  sort_no        INT          NOT NULL DEFAULT 0  COMMENT '章节内排序',
  created_by     BIGINT       NOT NULL            COMMENT '录入人 user_id',
  is_active      TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '是否启用，停用代替删除',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (kp_id),
  KEY idx_course_chapter (course_id, chapter_id, sort_no),
  KEY idx_course_dimension (course_id, dimension_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识点表';

-- ------------------------------------------------------------
-- 5. knowledge_prereq 先修关系（知识图谱的边）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS knowledge_prereq (
  id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  kp_id         VARCHAR(20) NOT NULL                COMMENT '知识点',
  prereq_kp_id  VARCHAR(20) NOT NULL                COMMENT '其先修知识点',
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_edge (kp_id, prereq_kp_id),
  KEY idx_prereq (prereq_kp_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识点先修关系表';

-- ------------------------------------------------------------
-- 6. kp_keyword 知识点关键词（映射通道一的数据源）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS kp_keyword (
  id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  kp_id      VARCHAR(20) NOT NULL                COMMENT '所属知识点',
  keyword    VARCHAR(50) NOT NULL                COMMENT '关键词或同义词，如 LIFO / 后进先出',
  weight     DECIMAL(3,2) NOT NULL DEFAULT 1.00  COMMENT '权重，区分核心术语与辅助词',
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_kp_keyword (kp_id, keyword),
  KEY idx_keyword (keyword)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识点关键词表';

-- ------------------------------------------------------------
-- 7. question_bank 题库
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS question_bank (
  q_id           VARCHAR(20)  NOT NULL            COMMENT '题目编号，如 Q_001（业务主键）',
  course_id      BIGINT       NOT NULL            COMMENT '所属课程',
  primary_kp_id  VARCHAR(20)  NOT NULL            COMMENT '主知识点，用于展示与默认推荐；多知识点见 question_kp',
  question_type  VARCHAR(20)  NOT NULL DEFAULT '单选' COMMENT '题型：单选/多选/填空/简答/编程',
  question_text  TEXT         NOT NULL            COMMENT '题干，公式统一 LaTeX',
  options_json   JSON         DEFAULT NULL        COMMENT '选择题选项数组，非选择题为 NULL',
  answer_text    TEXT         DEFAULT NULL        COMMENT '标准答案',
  analysis_text  TEXT         DEFAULT NULL        COMMENT '解析',
  difficulty     TINYINT UNSIGNED NOT NULL DEFAULT 2 COMMENT '难度：1 简单，2 中等，3 困难',
  source         VARCHAR(50)  DEFAULT NULL        COMMENT '来源：教材/习题册/历年真题/自编',
  variant_hint   VARCHAR(200) DEFAULT NULL        COMMENT '变式题建议方向',
  is_active      TINYINT UNSIGNED NOT NULL DEFAULT 1,
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (q_id),
  KEY idx_course_kp (course_id, primary_kp_id),
  KEY idx_difficulty (difficulty)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题库表';

-- ------------------------------------------------------------
-- 8. question_kp 题目与知识点关联（替代逗号分隔多值字段）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS question_kp (
  id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  q_id       VARCHAR(20) NOT NULL                COMMENT '题目编号',
  kp_id      VARCHAR(20) NOT NULL                COMMENT '关联知识点',
  is_primary TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否主知识点：1 是，0 否',
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_q_kp (q_id, kp_id),
  KEY idx_kp (kp_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目知识点关联表';

-- ------------------------------------------------------------
-- 9. student_error_record 错题记录（学生上传的错题，系统输入）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS student_error_record (
  log_id           VARCHAR(20)  NOT NULL            COMMENT '错题记录编号，如 EL_101（业务主键）',
  user_id          BIGINT       NOT NULL            COMMENT '学生 user_id',
  client_token     VARCHAR(64)  DEFAULT NULL        COMMENT '客户端幂等键，防止重复提交',
  q_id             VARCHAR(20)  DEFAULT NULL        COMMENT '匹配到的题库题目，无则为 NULL',
  kp_id            VARCHAR(20)  DEFAULT NULL        COMMENT '学生确认的知识点，DRAFT 阶段可为空',
  image_url        VARCHAR(300) DEFAULT NULL        COMMENT '原始错题图片路径',
  ocr_text         TEXT         DEFAULT NULL        COMMENT 'OCR 识别原文，保留以支撑校正追溯',
  corrected_text   TEXT         DEFAULT NULL        COMMENT '学生校正后文本',
  ocr_confidence   DECIMAL(4,3) DEFAULT NULL        COMMENT '识别置信度 0-1，低于 0.75 强制人工确认',
  error_type       VARCHAR(30)  DEFAULT NULL        COMMENT '错因英文 Key，见数据标注规范；DRAFT 可为空',
  student_note     VARCHAR(500) DEFAULT NULL        COMMENT '学生错因自述',
  status           VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT / CONFIRMED',
  confirmed_at     DATETIME     DEFAULT NULL        COMMENT '确认时间，确认后才参与画像聚合',
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (log_id),
  UNIQUE KEY uk_user_client (user_id, client_token),
  KEY idx_user_status (user_id, status),
  KEY idx_user_time (user_id, created_at),
  KEY idx_kp_time (kp_id, confirmed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='错题记录表';

-- ------------------------------------------------------------
-- 10. ocr_task OCR 任务（契约 2.2 轮询的权威状态）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ocr_task (
  task_id      CHAR(32)     NOT NULL            COMMENT '任务ID，uuid hex',
  user_id      BIGINT       NOT NULL            COMMENT '提交人',
  client_token VARCHAR(64)  DEFAULT NULL        COMMENT '客户端幂等键，防止重复上传',
  image_path   VARCHAR(300) NOT NULL            COMMENT '图片落盘路径',
  status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / RUNNING / SUCCESS / FAILED',
  confidence   DECIMAL(4,3) DEFAULT NULL        COMMENT '整体识别置信度',
  result_json  JSON         DEFAULT NULL        COMMENT '结构化结果：题干/选项/答案/题型/难度',
  error_code   VARCHAR(20)  DEFAULT NULL        COMMENT '失败错误码，如 60002',
  error_msg    VARCHAR(200) DEFAULT NULL        COMMENT '失败原因，面向用户展示',
  retry_count  TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '重试次数',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (task_id),
  UNIQUE KEY uk_user_client (user_id, client_token),
  KEY idx_user_status (user_id, status),
  KEY idx_status_time (status, created_at),
  KEY idx_status_updated (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OCR任务表';

-- ------------------------------------------------------------
-- 11. practice_record 练习作答记录（掌握度的分母来源）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS practice_record (
  practice_id       BIGINT   NOT NULL AUTO_INCREMENT COMMENT '作答ID',
  user_id           BIGINT   NOT NULL                COMMENT '学生 user_id',
  client_token      VARCHAR(64) DEFAULT NULL         COMMENT '客户端幂等键，防止重复提交',
  q_id              VARCHAR(20) NOT NULL             COMMENT '题目编号',
  kp_id             VARCHAR(20) NOT NULL             COMMENT '冗余主知识点，避免画像聚合时关联查询',
  source            VARCHAR(20) NOT NULL             COMMENT '来源：RECOMMEND / PLAN / SELF',
  is_correct        TINYINT UNSIGNED NOT NULL        COMMENT '是否答对：1 对，0 错',
  my_answer         TEXT     DEFAULT NULL            COMMENT '学生作答内容',
  error_type        VARCHAR(30) DEFAULT NULL         COMMENT '答错时的错因英文 Key',
  cost_seconds      INT      DEFAULT NULL            COMMENT '作答耗时秒数',
  recommendation_id BIGINT   DEFAULT NULL            COMMENT '若来自推荐，关联 recommendation',
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (practice_id),
  UNIQUE KEY uk_user_client (user_id, client_token),
  KEY idx_user_kp (user_id, kp_id),
  KEY idx_user_time (user_id, created_at),
  KEY idx_q (q_id),
  KEY idx_user_q (user_id, q_id),
  -- 窗口聚合覆盖索引：空库建完即等于 migration/2026-11-03-diagnosis-window-index.sql 的终态，
  -- 否则 aggregateByUserKp 等查询的 /*+ INDEX(practice_record idx_user_window) */ 会被静默忽略。
  KEY idx_user_window (user_id, created_at, kp_id, q_id, is_correct, error_type, cost_seconds),
  -- idx_course_agg 保留：班级 aggregateByUserKp / aggregateErrorType 与单学生 aggregateByUser
  -- 实际选中它（docs/05 §2.5 逐条 EXPLAIN）；被 idx_user_window 取代的 idx_user_agg 不在此建。
  KEY idx_course_agg (q_id, user_id, kp_id, is_correct, error_type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='练习作答记录表';

-- ------------------------------------------------------------
-- 12. recommendation 推荐记录（评估推荐效果的数据源）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS recommendation (
  recommendation_id BIGINT      NOT NULL AUTO_INCREMENT COMMENT '推荐ID',
  user_id           BIGINT      NOT NULL                COMMENT '学生 user_id',
  log_id            VARCHAR(20) DEFAULT NULL            COMMENT '基于哪条错题推荐',
  q_id              VARCHAR(20) NOT NULL                COMMENT '推荐题目',
  layer             VARCHAR(20) NOT NULL                COMMENT '层次：CONSOLIDATE / VARIANT / ADVANCE',
  reason            VARCHAR(50) NOT NULL                COMMENT '推荐理由，≤50 字，补漏评分组合模板',
  rank_no           TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '层内序号',
  is_adopted        TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '学生是否做了这道题',
  created_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (recommendation_id),
  UNIQUE KEY uk_user_log_q (user_id, log_id, q_id),
  KEY idx_user_log (user_id, log_id),
  KEY idx_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='练习推荐记录表';

-- ------------------------------------------------------------
-- 13. study_plan 学习计划（日粒度）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS study_plan (
  plan_id     BIGINT   NOT NULL AUTO_INCREMENT COMMENT '计划ID',
  user_id     BIGINT   NOT NULL                COMMENT '学生 user_id',
  plan_date   DATE     NOT NULL                COMMENT '计划日期',
  plan_count  INT      NOT NULL DEFAULT 3      COMMENT '当日计划题量',
  done_count  INT      NOT NULL DEFAULT 0      COMMENT '当日完成题量',
  status      VARCHAR(10) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / DONE / OVERDUE',
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (plan_id),
  UNIQUE KEY uk_user_date (user_id, plan_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习计划表';

-- ------------------------------------------------------------
-- 14. study_plan_item 计划明细（今天具体做哪几道）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS study_plan_item (
  item_id     BIGINT      NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  plan_id     BIGINT      NOT NULL                COMMENT '所属计划',
  q_id        VARCHAR(20) NOT NULL                COMMENT '题目编号',
  kp_id       VARCHAR(20) NOT NULL                COMMENT '所属知识点，冗余便于统计',
  sort_no     TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '做题顺序',
  is_done     TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否完成',
  practice_id BIGINT      DEFAULT NULL            COMMENT '完成后关联的作答记录',
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (item_id),
  UNIQUE KEY uk_plan_q (plan_id, q_id),
  KEY idx_plan (plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习计划明细表';

-- ------------------------------------------------------------
-- 15. learning_portrait_snapshot 学情画像快照（按日，不实时算）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS learning_portrait_snapshot (
  snapshot_id      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '快照ID',
  user_id          BIGINT       NOT NULL                COMMENT '学生 user_id',
  stat_date        DATE         NOT NULL                COMMENT '统计日期',
  dimension_scores JSON         DEFAULT NULL            COMMENT '维度得分对象，键与 course.dimension_json.key 一致',
  weakest_kps      JSON         DEFAULT NULL            COMMENT '薄弱知识点 TOP-N 数组',
  error_dist       JSON         DEFAULT NULL            COMMENT '五类错因计数对象',
  total_practice   INT          NOT NULL DEFAULT 0      COMMENT '累计作答次数',
  total_error      INT          NOT NULL DEFAULT 0      COMMENT '累计错误次数',
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (snapshot_id),
  UNIQUE KEY uk_user_date (user_id, stat_date),
  KEY idx_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学情画像快照表';

-- ------------------------------------------------------------
-- 16. ai_generation_log AI 调用与教师评阅（论文实验数据源）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_generation_log (
  log_pk           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志主键',
  explain_id       VARCHAR(40)  NOT NULL            COMMENT '一次讲解的业务ID，多次重试共用',
  trace_id         VARCHAR(40)  NOT NULL            COMMENT '单次模型调用链ID',
  user_id          BIGINT       NOT NULL            COMMENT '学生 user_id',
  log_id           VARCHAR(20)  DEFAULT NULL        COMMENT '关联错题记录',
  scene            VARCHAR(20)  NOT NULL            COMMENT '场景：OCR / RAG / LLM / RECOMMEND',
  model            VARCHAR(50)  DEFAULT NULL        COMMENT '实际调用的模型名',
  prompt_version   VARCHAR(20)  DEFAULT NULL        COMMENT 'Prompt 模板版本号',
  is_degraded      TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否发生降级',
  degrade_level    VARCHAR(10)  DEFAULT NULL        COMMENT '降级级别：L1 / L2 / L3 / L4',
  latency_ms       INT          DEFAULT NULL        COMMENT '调用耗时毫秒',
  prompt_tokens    INT          NOT NULL DEFAULT 0  COMMENT '输入 token 数',
  completion_tokens INT         NOT NULL DEFAULT 0  COMMENT '输出 token 数',
  source_ids       JSON         DEFAULT NULL        COMMENT '引用的知识切片 kp_id 数组',
  content_text     TEXT         DEFAULT NULL        COMMENT '生成的讲解正文快照，供教师评阅',
  is_blocked       TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否触发合规拦截',
  teacher_score    TINYINT      DEFAULT NULL        COMMENT '教师评阅分 1-5',
  teacher_comment  VARCHAR(500) DEFAULT NULL        COMMENT '教师评语',
  review_result    VARCHAR(10)  DEFAULT NULL        COMMENT '评阅结论：PASS / RETURN',
  reviewed_at      DATETIME     DEFAULT NULL        COMMENT '评阅时间，NULL 表示待评阅',
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (log_pk),
  KEY idx_explain (explain_id),
  KEY idx_scene_time (scene, created_at),
  KEY idx_pending_review (scene, reviewed_at, created_at),
  KEY idx_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI调用日志与教师评阅表';

-- ============================================================
-- 种子数据：一门课 + 一章 + 知识点（不含账号）
-- 本地开发账号单独放在 seed-dev-users.sql（仅本地；生产环境严禁执行）
-- ============================================================
INSERT INTO course (course_id, course_name, teacher_id, textbook, description, dimension_json)
VALUES (1, '数据库原理', 1, '《数据库系统概论（第6版）》王珊', '试点课程，覆盖 8 个核心章节',
        '[{"key":"concept","name":"概念理解"},{"key":"sql","name":"SQL 编写"},{"key":"schema","name":"模式设计"},{"key":"transaction","name":"事务与并发"},{"key":"boundary","name":"边界与异常"}]')
ON DUPLICATE KEY UPDATE course_name = VALUES(course_name), textbook = VALUES(textbook), dimension_json = VALUES(dimension_json);

INSERT INTO course_chapter (course_id, chapter_no, title, sort_no) VALUES
(1, 2, '关系模型', 1)
ON DUPLICATE KEY UPDATE title = VALUES(title);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0201', 1, chapter_id, '关系模型的基本概念',
       '关系模型用二维表表示实体及其联系：行称为元组，列称为属性，属性的取值范围称为域；关系模式是对关系的结构描述。关系模型的三要素是关系数据结构、关系操作集合和关系完整性约束。',
       '把关系等同于普通文件\n混淆元组与属性',
       'P34', 'concept', 1, 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 2
ON DUPLICATE KEY UPDATE name = VALUES(name), dimension_key = VALUES(dimension_key);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0202', 1, chapter_id, '主键与外键',
       '主键是能唯一标识关系中元组的属性或属性组，取值不能重复且不能为空；外键是关系中的一个属性或属性组，其取值必须参照另一关系的主键，用于保证参照完整性。',
       '认为主键可以为空\n忽略外键的参照完整性',
       'P34,P38', 'schema', 2, 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 2
ON DUPLICATE KEY UPDATE name = VALUES(name), dimension_key = VALUES(dimension_key);

INSERT INTO knowledge_prereq (kp_id, prereq_kp_id)
VALUES ('KP0202', 'KP0201')
ON DUPLICATE KEY UPDATE kp_id = VALUES(kp_id);

INSERT INTO kp_keyword (kp_id, keyword, weight) VALUES
('KP0201', '关系', 1.00), ('KP0201', '元组', 0.90), ('KP0201', '属性', 0.90), ('KP0201', '域', 0.70), ('KP0201', '关系模式', 0.80),
('KP0202', '主键', 1.00), ('KP0202', '外键', 0.90), ('KP0202', '参照完整性', 1.00), ('KP0202', '唯一标识', 0.70), ('KP0202', '约束', 0.60)
ON DUPLICATE KEY UPDATE weight = VALUES(weight);
