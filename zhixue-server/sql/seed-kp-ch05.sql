-- 智学领航 · 第5章知识点种子（由 csv_to_sql.py 生成）
-- 前置：init-v2.sql；可重复执行（upsert）。
USE zhixue;
SET NAMES utf8mb4;

INSERT INTO course_chapter (course_id, chapter_no, title, sort_no) VALUES
(1, 5, '数据库完整性', 4)
ON DUPLICATE KEY UPDATE title = VALUES(title), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0501', 1, chapter_id, '数据库完整性概述与完整性控制机制', '数据库的完整性是指数据的正确性和相容性。正确性指数据符合现实世界语义并反映当前实际状况；相容性指同一对象在不同关系表中的数据是相同、一致的。完整性防范的是不合语义、不正确的数据，安全性防范的是非法用户和非法操作，二者既有联系又不同。关系数据库管理系统必须提供三方面功能：定义完整性约束的机制、检查完整性约束的方法、违约处理的方法。完整性约束作为数据库模式的一部分存入数据字典，一般由 SQL 的 DDL 定义；检查可在 INSERT、UPDATE、DELETE 后或事务提交时进行；违约时可拒绝（NO ACTION）或级联（CASCADE）处理。实体完整性和参照完整性是关系模型的两个不变性，其他约束归入用户定义的完整性。', '①把完整性等同于安全性\\n②认为完整性只靠应用程序保证\\n③认为完整性检查只在查询时进行\\n④忽略违约处理策略，认为违约一律只报错', 'P151', 'concept', '1', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 5
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0502', 1, chapter_id, '实体完整性', '实体完整性在 CREATE TABLE 中用 PRIMARY KEY 定义。单属性主码可定义为列级约束或表级约束；多属性主码只能在表级定义，如 SC(Sno,Cno) 的 PRIMARY KEY(Sno,Cno)。定义主码后，每次插入记录或更新主码列，DBMS 自动检查：主码值是否唯一，不唯一则拒绝插入或修改；主码各属性是否为空，只要有一个为空就拒绝。检查唯一性可全表扫描，但 DBMS 通常在主码上自动建立索引（如 B+ 树），通过索引查找提高效率。实体完整性是关系模型必须满足的不变性之一，不能由应用程序替代。', '①认为主码可以取空值\\n②认为多属性主码可以在列级定义\\n③认为实体完整性靠应用校验即可\\n④认为主码重复只给出警告而不拒绝', 'P153', 'sql', '2', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 5
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0503', 1, chapter_id, '参照完整性定义', '参照完整性在 CREATE TABLE 中用 FOREIGN KEY 定义外码，用 REFERENCES 指明外码参照哪个表的主码。例如 SC 表的 Sno 参照 Student 的 Sno，Cno 参照 Course 的 Cno；Sno、Cno 是外码，Student、Course 是被参照表。外码列是否允许空值要由应用语义决定：学生“主修专业”未确定时可为空；SC 的 Sno、Cno 是主属性，按实体完整性不能为空。定义参照完整性一般在表级写 FOREIGN KEY ... REFERENCES ...。参照完整性把两个表的相应元组联系起来，是关系模型的另一个不变性。', '①认为外码必须非空\\n②认为外码可以参照非主码属性\\n③认为参照完整性只靠应用保证\\n④忽略外码列的空值语义，随意允许为空', 'P155', 'schema', '3', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 5
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0504', 1, chapter_id, '参照完整性违约处理', '对被参照表和参照表进行增删改时可能破坏参照完整性，共有四种情况：参照表插入元组、参照表修改外码值、被参照表删除元组、被参照表修改主码值。违约处理有三种策略：拒绝执行（NO ACTION，一般为默认）、级联操作（CASCADE，删除或修改参照表中所有不一致元组）、设置为空值（SET NULL，把参照表对应属性置空，前提是外码允许空值）。对 DELETE 和 UPDATE 可采用不同策略，如例 5.4 中 SC 对 Student 删除/更新都级联，Course 删除拒绝、更新级联。具体选哪种策略由应用语义决定。', '①认为默认策略是级联\\n②认为删除和更新必须使用同一策略\\n③对外码非空的列也设置 SET NULL\\n④认为级联只影响参照表的一行', 'P156', 'boundary', '4', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 5
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0505', 1, chapter_id, '用户定义的完整性（属性与元组约束）', '用户定义的完整性是某一具体应用的数据必须满足的语义要求，RDBMS 提供与实体、参照完整性相同的定义和检查机制。属性上的约束在 CREATE TABLE 中定义，包括列值非空 NOT NULL、列值唯一 UNIQUE、检查列值满足条件表达式 CHECK。元组上的约束也用 CHECK，但可设置不同属性之间取值的相互约束，如“性别为男时姓名不能以 Ms. 开头”。插入元组或修改属性值时，DBMS 检查约束是否满足，不满足则拒绝执行。例：Ssex CHECK(Ssex IN (''男'',''女''))、Grade CHECK(Grade>=0 AND Grade<=100)。', '①把用户定义完整性等同于实体/参照完整性\\n②认为元组 CHECK 不能跨属性\\n③认为 CHECK 只在查询时检查\\n④把 NOT NULL 与 UNIQUE 当成同一约束', 'P158', 'schema', '5', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 5
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0506', 1, chapter_id, '完整性约束命名子句', 'SQL 在 CREATE TABLE 中提供 CONSTRAINT <约束名> <完整性约束> 子句，用来给完整性约束命名，约束包括 NOT NULL、UNIQUE、PRIMARY KEY、FOREIGN KEY、CHECK 等。命名后可灵活地增加、删除或修改约束：ALTER TABLE ... DROP CONSTRAINT 删除，ALTER TABLE ... ADD CONSTRAINT 增加。例 5.10 在 Student 表上建立 StudentKey 主码约束和 C1—C4 四个列级约束；例 5.11 为 Teacher 的 Schoolno 外码命名 TeacherFKey。命名使约束可维护、报错可定位；修改约束时通常先删除旧约束再增加新约束。', '①认为约束命名只为好看\\n②认为命名约束不能删除\\n③认为 ALTER TABLE 只能加列不能加约束\\n④修改约束时直接改原定义而不先删除', 'P160', 'sql', '6', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 5
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0507', 1, chapter_id, '域的完整性限制', '域是一组具有相同数据类型的值的集合。SQL 用 CREATE DOMAIN 建立域并指明域应满足的完整性约束，再用域定义属性；不同属性可来自同一域，域约束改变时只改域定义，不必逐列修改。例 5.14：CREATE DOMAIN GenderDomain CHAR(6) CHECK(VALUE IN (''男'',''女''))；例 5.15 用 CONSTRAINT GD 给域约束命名。ALTER DOMAIN ... DROP CONSTRAINT / ADD CONSTRAINT 可删除或增加域约束。域约束把列级 CHECK 提升为可复用的类型级约束，适合多个属性共享同一取值范围的场景。', '①把域等同于数据类型\\n②认为域约束只作用于一个列\\n③修改域约束必须逐列修改\\n④认为域约束不能命名', 'P161', 'concept', '7', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 5
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0508', 1, chapter_id, 'SQL 断言', 'SQL 标准支持数据库完整性断言 ASSERTION。用户用 DDL 的 CREATE ASSERTION <断言名> CHECK (<条件>) 声明性指定更一般的完整性约束，条件可涉及多个表或聚集操作，比单表 CHECK 更通用。例：先建 Male、Female 两个关系，再 CREATE ASSERTION Party CHECK((SELECT COUNT(*) FROM Male)+(SELECT COUNT(*) FROM Female)<=50)，把联谊会来宾人数限制在 50 人以内。断言由 DBMS 在数据变化时检查，不满足则拒绝。教材把断言放在扩展阅读；SQL 标准支持，但并非所有 DBMS 产品都实现。', '①把断言等同于表级 CHECK\\n②认为断言只能约束单表\\n③把断言当成触发器\\n④认为所有 DBMS 都支持断言', 'P162', 'boundary', '8', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 5
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0509', 1, chapter_id, '触发器', '触发器是定义在关系表上、由事件驱动的特殊过程，又称事件-条件-动作规则（ECA）。SQL 用 CREATE TRIGGER <名> BEFORE|AFTER <事件> ON <表> REFERENCING NEW|OLD AS <变量> FOR EACH ROW|STATEMENT [WHEN <条件>] <动作体> 定义。触发事件可为 INSERT、DELETE、UPDATE 或 UPDATE OF 列；BEFORE 在操作前、AFTER 在操作后激活；行级触发器每影响一行执行一次，可用 OLD/NEW 引用旧值/新值；语句级每条语句执行一次，不能用 OLD/NEW。触发器只能定义在基本表上，不能定义在视图上；执行顺序为 BEFORE→SQL→AFTER，同表多个同类触发器按创建顺序执行。DROP TRIGGER 删除；触发器强大但会增加访问开销，触发链还可能难以预见。', '①认为触发器可以定义在视图上\\n②认为语句级触发器也能用 OLD/NEW\\n③认为 AFTER 触发器先于 SQL 执行\\n④认为触发器越多越好，忽略性能与触发链风险', 'P163', 'sql', '9', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 5
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

