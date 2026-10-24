-- 智学领航 · 第2—3章知识点种子（竖切范围）
-- 数据来源：技术学习/11-第2章概念切片-核对版.md、12-第3章概念切片-核对版.md
-- 前置：先执行 init-v2.sql（建库建表）。本文件可重复执行，全部为 upsert。
-- 页码为教材印刷页；教材：《数据库系统概论（第6版）》王珊、杜小勇、陈红。

USE zhixue;
-- Windows 下 mysql 客户端默认字符集可能是 GBK，显式声明避免中文乱码/1366 错误
SET NAMES utf8mb4;

INSERT INTO course_chapter (course_id, chapter_no, title, sort_no) VALUES
(1, 2, '关系模型', 1),
(1, 3, '关系数据库标准语言SQL', 2)
ON DUPLICATE KEY UPDATE title = VALUES(title), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0201', 1, chapter_id, '关系模型的基本概念与三要素',
       '关系模型是用单一结构——关系——描述现实世界实体及其联系的模型。用户看到的关系数据结构是一张二维表：每一行是一个元组，每一列是一个属性，列的取值范围是域，元组中的每一个值是分量。关系模型由三部分组成：关系数据结构、关系操作集合、关系完整性约束。数据结构回答“数据如何表示”，关系操作回答“能施加哪些查询与更新”，完整性约束回答“数据必须满足哪些语义条件”。关系模型建立在集合代数之上，本章用集合论给出关系、关系模式等形式化定义。还要区分型与值：关系模式是对关系的描述，属于型；关系是某一时刻的元组集合，属于值。关系模型的数据结构虽然简单，却能表达丰富的语义，描述现实世界的实体以及实体间的各种联系。与层次模型、网状模型相比，它把实体和联系统一用关系表示，用户只需面对二维表。',
       '把关系模型只当成二维表，漏掉关系操作与完整性约束
认为关系模型的结构与层次、网状模型一样复杂
把“关系”与“关系模式”当作同一概念',
       'P34', 'concept', 1, 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 2
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0202', 1, chapter_id, '关系的形式化定义（域、笛卡儿积、关系）',
       '域是一组具有相同数据类型的值的集合。给定一组域 D1,D2,…,Dn，允许其中某些域相同，其笛卡儿积定义为 D1×D2×…×Dn={(d1,d2,…,dn) | di∈Di, i=1,2,…,n}。每个元素 (d1,d2,…,dn) 是一个 n 元组，元组中的每个 di 是分量。若 Di 为有限集、基数为 mi，则笛卡儿积的基数为 M=∏(i=1…n) mi。笛卡儿积可列成二维表：一行对应一个元组，一列的值来自一个域。关系是笛卡儿积的有限子集：D1×D2×…×Dn 的子集称为这组域上的关系，记作 R(D1,D2,…,Dn)，R 为关系名，n 为目或度；n=1 为一元关系，n=2 为二元关系。因域可相同，必须给每一列起属性名，n 目关系必有 n 个属性。笛卡儿积中的元组并不都具有实际语义，只有它的某个真子集才可能有实际含义，关系正是取这样的子集。',
       '把关系的“目”误当成元组个数
认为笛卡儿积的每个元组都有实际语义
把域与属性混为一谈，认为属性值可跨域取值',
       'P34—36', 'concept', 2, 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 2
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0203', 1, chapter_id, '候选码、主码、主属性与非主属性',
       '在关系模式中，若某一个属性或一组属性的值能唯一标识一个元组，而它的真子集不能唯一标识一个元组，则称该属性或属性组为候选码。一个关系可以有多个候选码，选定其中一个作为主码（主键），并在其名称下加下划线标识。候选码所包含的属性称为主属性；不包含在任何候选码中的属性称为非主属性（非码属性）。最简单的情形是候选码只含一个属性；最极端的情形是关系模式的所有属性共同构成候选码，称为全码。候选码强调“最小性”：属性组整体能唯一标识元组，去掉任一属性后便不能唯一标识。主码则是在多个候选码中选定的一个，用于实体完整性约束。码是能唯一标识元组的属性或属性组，理解时应把“唯一标识”与“最小性”同时抓住。候选码的定义以“能唯一标识元组”和“真子集不能唯一标识”为两个条件。',
       '认为候选码只能由一个属性组成
认为主属性可以取空值
把主码与候选码等同，认为一个关系只有一个码',
       'P38', 'schema', 3, 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 2
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0204', 1, chapter_id, '关系模式与关系数据库',
       '关系模式是对关系的描述，关系模式是型，关系是值；关系是元组集合，是关系模式在某一时刻的状态或内容。关系模式可形式化表示为 R(U,D,DOM,F)：R 为关系名，U 为组成该关系的属性名集合，D 为 U 中属性所来自的域，DOM 为属性向域的映像集合，F 为属性间数据依赖关系的集合。本章只涉及 R(U,D,DOM)，数据依赖留到第 6 章。关系模式常简记为 R(U) 或 R(A1,A2,…,An)，域名与映像常直接说明为属性的类型、长度。关系模式是静态、稳定的；关系随时间不断变化。某一时刻所有关系模式对应的关系的集合构成一个关系数据库；所有关系模式的集合是关系数据库模式，是型；其对应的关系集合是关系数据库的值。实际工作中，人们常把关系模式和关系都笼统地称为“关系”，需要根据上下文加以区别。',
       '认为关系模式会随元组增删而变化
把关系数据库模式与某一时刻的关系数据库值混同
认为关系模式只描述属性名，不含域名与映像',
       'P37—39', 'schema', 4, 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 2
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0205', 1, chapter_id, '关系操作与关系数据语言分类',
       '关系模型中常用的关系操作包括查询和更新两大部分，更新又分为插入、删除、修改。查询操作可进一步分为选择、投影、连接、除、并、差、交、笛卡儿积等；其中选择、投影、并、差、笛卡儿积是 5 种基本操作，其他操作可由基本操作定义和导出，就像乘法可由加法定义一样。关系操作的特点是集合操作方式，即操作对象和结果都是集合，也称为一次一个集合的处理方式；所有输入、输出以及中间结果都是关系。关系数据语言分为关系代数、关系演算和结构化查询语言 SQL。关系演算按谓词变元的基本对象是元组变量还是域变量，分为元组关系演算和域关系演算。关系代数、元组关系演算、域关系演算表达能力等价，都具有关系完备性。SQL 集数据查询、数据定义、数据操纵和数据控制于一体。',
       '认为查询不属于关系操作，只把插入删除修改当操作
认为交、连接、除是基本运算
认为关系代数与关系演算表达能力不等价',
       'P40—42', 'concept', 5, 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 2
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0206', 1, chapter_id, '实体完整性',
       '关系模型有三类完整性约束：实体完整性、参照完整性和用户定义的完整性。其中实体完整性和参照完整性是关系模型必须满足的完整性约束，被称为关系的两个不变性，应由关系系统自动支持。实体完整性规则：若属性 A（一个或一组属性）是基本关系 R 的主属性，则 A 不能取空值。空值是“不知道”“不存在”或“无意义”的值。按照该规则，如果主码由若干属性组成，则所有这些属性都不能取空值。例如学生关系中“学号”为主码，学号取值唯一且不能为空；学生选课关系中主码为（学号,课程号），两个属性都不能为空。实体完整性针对基本关系而言，基本表通常对应现实世界的一个实体集；现实世界实体可区分，关系模型以主码作为唯一性标识。主码若取空值，就会出现不可标识的实体，与实体可区分性矛盾。',
       '认为主码只要唯一即可，允许取空值
认为实体完整性约束所有属性都不能为空
认为主码由多属性组成时只需其中一个非空',
       'P42—43', 'schema', 6, 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 2
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0207', 1, chapter_id, '参照完整性与外码（违约处理见第5章）',
       '参照完整性约束定义外码与主码之间的引用规则。设 F 是基本关系 R 的一个或一组属性，但不是 R 的码；K_s 是基本关系 S 的主码。若 F 与 K_s 相对应，则称 F 是 R 的外码，R 为参照关系，S 为被参照关系（目标关系）。R 与 S 不一定是不同的关系，F 与 K_s 必须定义在同一（或同一组）域上。参照完整性规则：对于 R 中每个元组在 F 上的值，或者取空值（F 的每个属性值均为空值），或者等于 S 中某个元组的主码值。外码不一定要与主码同名，例如“主修专业”引用“专业名”、“先修课”引用“课程号”。同一关系的内部属性间也可存在引用，如课程关系中的“先修课”引用本关系的“课程号”。参照完整性刻画的是关系与关系之间、或同一关系内部属性之间的引用约束。教材 P43—45 只给出上述规则与示例，未展开违约处理。',
       '认为外码必须与对应主码同名
认为数据库只有一个关系就不存在参照完整性
认为外码在任何情况下都不能取空值',
       'P43—45', 'schema', 7, 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 2
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0208', 1, chapter_id, '用户定义的完整性',
       '任何关系数据库系统都应支持实体完整性和参照完整性，这是关系模型本身的要求。除此之外，不同关系数据库根据应用场景不同，往往还需要一些特殊的约束条件。用户定义的完整性就是针对某一具体关系数据库的约束条件，它反映某一具体应用所涉及的数据必须满足的语义要求。例如，某个属性必须取唯一值，某个非主属性不能取空值；在学生关系中若要求学生必须有姓名，可定义“姓名”属性不能取空值；在学生选课关系中可定义“成绩”的取值范围为 0～100。用户定义的完整性是应用领域需要遵循的约束条件，体现了具体领域中的语义约束。关系模型应提供定义和检验这类完整性约束的机制，以便用统一的、系统的方法处理，而不需要由应用程序承担这一功能。教材本节未给出编号规则。',
       '把用户定义完整性当成关系模型的两个不变性之一
认为“成绩 0～100”属于实体完整性
认为完整性约束只能由应用程序检查',
       'P45', 'schema', 8, 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 2
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0209', 1, chapter_id, '传统集合运算（并、差、交、笛卡儿积）',
       '传统的集合运算是二目运算，包括并、差、交、笛卡儿积 4 种。设关系 R 和关系 S 具有相同的目 n，即两个关系都有 n 个属性，且相应属性取自同一个域；t 是元组变量。并：R∪S={t | t∈R∨t∈S}，结果仍为 n 目关系，由属于 R 或属于 S 的元组组成。差：R−S={t | t∈R∧t∉S}，由属于 R 而不属于 S 的元组组成。交：R∩S={t | t∈R∧t∈S}，由既属于 R 又属于 S 的元组组成；交可用差表示，R∩S=R−(R−S)。笛卡儿积是广义笛卡儿积：若 R 为 n 目、S 为 m 目，R×S 是 n+m 列的元组集合，前 n 列来自 R 的一个元组，后 m 列来自 S 的一个元组；若 R 有 k1 个元组、S 有 k2 个元组，则 R×S 有 k1×k2 个元组，记作 R×S={t_r t_s | t_r∈R∧t_s∈S}。',
       '认为交的结果元组数最多为两关系元组数之和
认为差运算会减少结果关系的列数
把笛卡儿积的列数误算为 n+m−公共属性数',
       'P46', 'concept', 9, 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 2
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0210', 1, chapter_id, '专门关系运算（选择、投影、连接、除）',
       '专门的关系运算包括选择、投影、连接、除。选择又称限制，σ_F(R)={t | t∈R∧F(t)=''真''}，F 是逻辑表达式，基本形式为 X1θY1，θ 为 >、≥、<、≤、=、<> 等比较运算符，并可用 ¬、∧、∨ 组合；选择从行的角度进行。投影 π_A(R)={t[A] | t∈R}，从列的角度进行；取消某些属性列后可能出现重复行，应取消重复元组。连接从两个关系的笛卡儿积中选取属性间满足条件的元组：R⋈S={t_r t_s | t_r∈R∧t_s∈S∧t_r[A]θt_s[B]}；θ 为“=”时是等值连接；自然连接要求比较分量是同名属性列，并在结果中去掉重复属性列。自然连接舍弃的元组称悬浮元组，保留悬浮元组则构成外连接（左外、右外）。除运算 R(X,Y)÷S(Y,Z) 的结果 P(X) 由满足“x 的象集 Y_x 包含 S 在 Y 上投影”的 x 组成，即 R÷S={t_r[X] | t_r∈R∧Π_Y(S)⊆Y_x}。8 种运算中并、差、笛卡儿积、选择、投影为基本运算，交、连接、除可由其导出。',
       '认为选择会减少属性列，投影会减少元组数
认为投影不必取消重复元组
把等值连接与自然连接等同，忽略去掉重复属性列',
       'P46—53', 'concept', 10, 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 2
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0301', 1, chapter_id, 'SQL 概述、特点与三级模式对应',
       'SQL（结构化查询语言）是关系数据库的标准语言，包括数据查询、数据库模式创建、数据的增删改、安全性和完整性定义与控制等功能。1974 年由 Boyce 和 Chamberlin 提出，原名 SEQUEL，在 IBM 的 System R 上实现后改名为 SQL；1986 年 ANSI 批准 SQL 86 为美国标准，1987 年 ISO 通过。SQL 是非过程化语言，用户只需说明"要什么"，无须指明"怎么干"，存取路径由 DBMS 自动选择。特点有五：功能综合且风格统一，集数据定义、数据操纵、数据控制于一体，核心功能只用 9 个动词（CREATE、DROP、ALTER、SELECT、INSERT、UPDATE、DELETE、GRANT、REVOKE）；数据操纵高度非过程化；面向集合的操作方式；统一语法结构支持独立与嵌入式两种使用方式；语言简洁易学。SQL 支持三级模式：外模式对应视图和部分基本表，模式对应基本表，内模式对应存储文件。',
       '误认为 SQL 是过程化语言，用户必须指定存取路径
误认为 SQL 只含查询功能，不含数据控制
误认为嵌入式 SQL 与独立 SQL 语法差别很大',
       'P67—71', 'concept', 1, 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 3
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0302', 1, chapter_id, '数据定义：模式、基本表、索引、数据字典',
       '数据定义包括数据库模式、表、视图和索引的定义。模式：CREATE SCHEMA [<模式名>] AUTHORIZATION <用户名>；DROP SCHEMA <模式名><CASCADE|RESTRICT>，CASCADE 级联删除模式中全部对象，RESTRICT 在模式已有对象时拒绝删除。基本表：CREATE TABLE <表名>(<列名><数据类型>[列级完整性约束][，…][，<表级完整性约束>])，涉及多个属性列的约束必须定义在表级；主码加下划线，SC 的主码 (Sno,Cno) 必须用表级完整性约束定义。所属模式可由表名前缀或搜索路径（SHOW SEARCH_PATH）确定。修改用 ALTER TABLE 的 ADD、DROP、ALTER、RENAME 子句；删除用 DROP TABLE <表名>[RESTRICT|CASCADE]，默认 RESTRICT。索引：CREATE [UNIQUE][CLUSTER] INDEX <索引名> ON <表名>(<列名>[<次序>][,…])，默认 ASC；删除用 DROP INDEX。数据字典记录模式、视图、索引、约束与权限等定义。',
       '误以为 DROP 用于删除表中数据（删数据应用 DELETE）
误认为 SQL 标准有修改模式语句（只能先删再重建）
误把涉及多列的完整性约束定义在列级',
       'P71—80', 'sql', 2, 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 3
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0303', 1, chapter_id, '单表查询（WHERE、ORDER BY、聚合、分组）',
       '单表查询指仅涉及一个表的查询。SELECT 语句一般格式：SELECT [ALL|DISTINCT] <目标列表达式>[别名][,…] FROM <表名或视图名>[别名][,…] [WHERE <条件表达式>] [GROUP BY <列名1>[HAVING <条件表达式>]] [ORDER BY <列名2>[ASC|DESC]]。未指定 DISTINCT 默认 ALL。WHERE 常用条件：比较运算符 =、>、<、>=、<=、!=、<>；BETWEEN…AND…；IN；LIKE，其中 % 代表任意长度的字符串、_ 代表任意单个字符，匹配串含 % 或 _ 时用 ESCAPE 转义；IS NULL；AND、OR、NOT，AND 优先级高于 OR。ORDER BY 按列升序（默认 ASC）或降序（DESC）排列，不改变基本表存储。聚集函数 COUNT、SUM、AVG、MAX、MIN 可加 DISTINCT，除 COUNT(*) 外跳过空值。GROUP BY 按列值分组，分组后可用 HAVING 筛选组；WHERE 作用于元组，HAVING 作用于组，WHERE 中不能直接用聚集函数。',
       '在 WHERE 中直接用聚集函数作条件，如 AVG(Grade)>=90
用 = NULL 判断空值，正确写法是 IS NULL
误认为 AND 与 OR 优先级相同，漏加括号',
       'P81—91', 'sql', 3, 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 3
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0304', 1, chapter_id, '连接查询（等值、自然、外连接）',
       '若一个查询同时涉及两个以上的表，称为连接查询，包括等值、非等值、自然、自身、外连接、复合条件连接和多表连接查询。连接条件（连接谓词）格式为 [<表名1>.]<列名1><比较运算符>[<表名2>.]<列名2>，运算符主要有 =、>、<、>=、<=、!=(或<>)；连接字段类型必须可比，名字不必相同。比较运算符为"="的是等值连接；去掉结果表中重复属性列的等值连接为自然连接，如例 3.52 去掉 SC 的 Sno，两表同名的 Sno 引用时必须加表名前缀。一个表与其自身连接为自身连接，须取不同别名，如例 3.54 求间接先修课：SELECT FIRST.Cno,SECOND.Cpno FROM Course FIRST,Course SECOND WHERE FIRST.Cpno=SECOND.Cno AND SECOND.Cpno IS NOT NULL。外连接以空值填补未匹配元组：SELECT … FROM Student LEFT OUTER JOIN SC ON (Student.Sno=SC.Sno)；左外连接列出左边关系全部元组，右外连接列出右边关系全部元组。',
       '自然连接忘记去掉重复的连接属性列
自身连接未取两个别名，无法区分两个副本
误认为外连接只输出匹配成功的元组',
       'P91—96', 'sql', 4, 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 3
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0305', 1, chapter_id, '嵌套查询与相关子查询',
       '一个 SELECT-FROM-WHERE 语句称为一个查询块，将一个查询块嵌套在另一个查询块的 WHERE 或 HAVING 条件中称为嵌套查询；上层为父查询，下层为子查询。子查询的 SELECT 中不能使用 ORDER BY，只能对最终结果排序。子查询条件不依赖父查询的称为不相关子查询，由里向外分步求解；依赖父查询的称为相关子查询，整个语句称相关嵌套查询，必须反复求值，如例 3.59 求每个学生超过本人平均成绩的课程号，SC 的别名 x、y 称元组变量。带 IN 谓词的子查询最常用；内层返回单值时可用 >、<、=、>=、<=、!= 或 <>；返回多值须用 ANY（或 SOME）或 ALL，且必须同时使用比较运算符：=ANY 等价于 IN，<>ALL 等价于 NOT IN，<ANY 等价于 <MAX，<ALL 等价于 <MIN。EXISTS 代表存在量词 ∃，只产生 true/false，目标列通常用 *；NOT EXISTS 在内层为空时返回真。SQL 无全称量词，用 (∀x)P≡¬(∃x(¬P)) 转换，例 3.64 据此求选修全部课程的学生；蕴涵 p→q 等价于 ¬p∨q。',
       '在子查询的 SELECT 语句中写 ORDER BY
ANY/ALL 未与比较运算符同时使用
求“全部课程”时不会用 NOT EXISTS 双重否定转换',
       'P96—105', 'sql', 5, 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 3
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0306', 1, chapter_id, '集合查询与基于派生表的查询',
       'SELECT 的查询结果是元组的集合，可对多个 SELECT 结果做集合操作：并 UNION、交 INTERSECT、差 EXCEPT。参加操作的各结果列数必须相同，对应项数据类型也必须相同。UNION 合并时系统自动去掉重复元组，要保留重复元组则用 UNION ALL。例 3.70 用 EXCEPT 求计算机科学与技术专业学生与年龄不大于 19 岁学生的差集。子查询还可出现在 FROM 子句中，生成的临时派生表成为主查询的查询对象，例如：SELECT Sno,Cno FROM SC,(SELECT Sno,AVG(Grade) FROM SC GROUP BY Sno) AS Avg_SC(Avg_sno,Avg_grade) WHERE SC.Sno=Avg_SC.Avg_sno AND SC.Grade>=Avg_SC.Avg_grade。含聚集函数时必须指定派生表属性列名，否则以子查询 SELECT 的列名为默认属性。生成派生表时 AS 可省略，但必须指定别名；基本表别名可选。派生表是中间结果表，查询完成后由系统自动清除。',
       '集合操作两侧列数或对应列类型不一致
以为 UNION 会保留重复元组（应写 UNION ALL）
派生表未指定别名，或含聚集函数却省略列名',
       'P105—108', 'sql', 6, 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 3
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0307', 1, chapter_id, '数据更新（INSERT、UPDATE、DELETE）',
       'SQL 数据更新包括插入、修改、删除三类语句。插入一个元组：INSERT INTO <表名>[(<属性列1>[，<属性列2>]…)] VALUES(<常量1>[，<常量2>]…)；INTO 中未出现的属性列取空值（有默认值则取默认值），但 NOT NULL 列不能取空值；INTO 未指明属性列时，VALUES 必须在每个属性列上均有值，次序同 CREATE TABLE。插入子查询结果可一次插入多个元组：INSERT INTO <表名>[(<属性列1>[，…])] 子查询，例 3.74 按专业分组求平均年龄存入新表。修改：UPDATE <表名> SET <列名>=<表达式>[，<列名>=<表达式>]… [WHERE <条件>]，省略 WHERE 表示修改所有元组；例 3.76 将某学期某课程成绩减 5 分。删除：DELETE FROM <表名> [WHERE <条件>]，省略 WHERE 删除全部元组，但表定义仍在字典中，即删除的是数据而不是表的定义。增删改可能破坏参照完整性。',
       'INTO 未列出属性列，却漏给某些列指定值
UPDATE/DELETE 漏写 WHERE，误改误删全部元组
误以为 DELETE 会连同表定义一起删除',
       'P108—111', 'sql', 7, 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 3
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0308', 1, chapter_id, '空值处理与三值逻辑',
       '空值是"不知道""不存在"或"无意义"的值，产生于插入未赋值、更新置 NULL、外连接和空值参与运算。判断空值用 IS NULL 或 IS NOT NULL，不能用等号（=）代替。约束方面：NOT NULL 属性不能取空值，主码不能取空值；UNIQUE 属性可以取空值，能否有多条记录取空值 SQL 标准未规定，Oracle、KingbaseES、PostgreSQL 允许，SQL Server 不允许。运算方面：空值与另一个值（包括空值）的算术运算结果为空值，比较运算结果为 UNKNOWN。二值逻辑因此扩展为三值逻辑（表 3.7）：NULL AND FALSE 为 FALSE，NULL AND TRUE 为 UNKNOWN，NULL OR TRUE 为 TRUE，NULL OR FALSE 为 UNKNOWN，NOT UNKNOWN 为 UNKNOWN。查询中只有 WHERE、HAVING 条件为 TRUE 的元组才被选出，所以 Grade<60 不会选出缺考（Grade 为空值）的学生。',
       '用 WHERE X = NULL 判断空值（应为 IS NULL）
误认为 NULL 与 NULL 比较的结果为 TRUE
忽略 UNKNOWN，把三值逻辑当作二值逻辑',
       'P111—114', 'boundary', 8, 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 3
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0309', 1, chapter_id, '视图的定义、查询与更新',
       '视图是从一个或几个基本表（或视图）导出的表，是一个虚表；数据库中只存放视图定义，不存放对应数据。建立视图：CREATE VIEW <视图名>[(<列名>[，<列名>]…)] AS <子查询> [WITH CHECK OPTION]；子查询是否含 ORDER BY、DISTINCT 取决于系统实现；WITH CHECK OPTION 保证更新、插入、删除的行满足视图定义的谓词条件。组成视图的列名或全部省略或全部指定，必须指定列名的三种情况：目标列为聚集函数或表达式、多表连接出现同名列、需要启用新列名。例 3.86 的 IS_Student 是保留主码的行列子集视图；例 3.91 的 S_GradeAVG 是分组视图，必须指定列名。删除视图：DROP VIEW <视图名>[CASCADE]；基本表删除后视图定义仍在字典中，需显式删除。查询视图时系统从数据字典取出定义，与用户查询结合转换成等价的基本表查询，称视图消解；行列子集视图一般能正确转换，非行列子集视图（例 3.96）不一定。更新视图最终转换为对基本表的更新，一般地行列子集视图可更新；不可更新与不允许更新是两个概念。',
       '认为视图中存放了数据（实际只存视图定义）
认为所有视图都可更新（只有行列子集视图一般可更新）
对非行列子集视图直接消解，未改用 HAVING',
       'P114—121', 'sql', 9, 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 3
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0310', 1, chapter_id, '视图的作用',
       '视图定义在基本表之上，对视图的操作最终转换为对基本表的操作，非行列子集视图查询或更新时还可能出问题，但合理使用视图好处很多。作用有四：① 对机密数据提供安全保护。对不同用户定义不同视图，使机密数据不出现在不应看到这些数据的用户视图上；如 Student 表涉及全校 30 个院系，可定义 30 个视图，每个只含一个院系数据，只允许该院系主任查询和修改。② 对重构数据库提供一定程度的逻辑独立性。逻辑独立性指数据库重构时（如增加新关系或新字段）应用程序不受影响；把 Student 垂直分成 SX(Sno,Sname,Sbirthdate) 和 SY(Sno,Ssex,Smajor)，再定义视图 Student 为二者自然连接结果，逻辑结构改变而不必修改程序。③ 简化用户操作，把多表连接对用户隐蔽起来。④ 使用户能以多种角度看待同一数据。',
       '误认为视图能提供完全的逻辑独立性
把视图当作独立存储的表，忽视其虚表性质
只答“方便查询”，漏掉安全保护与多角度两点',
       'P121—122', 'sql', 10, 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 3
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_prereq (kp_id, prereq_kp_id) VALUES
('KP0202', 'KP0201'),
('KP0203', 'KP0202'),
('KP0204', 'KP0203'),
('KP0205', 'KP0204'),
('KP0206', 'KP0203'),
('KP0207', 'KP0203'),
('KP0208', 'KP0207'),
('KP0209', 'KP0205'),
('KP0210', 'KP0209'),
('KP0301', 'KP0205'),
('KP0302', 'KP0301'),
('KP0303', 'KP0302'),
('KP0303', 'KP0210'),
('KP0304', 'KP0303'),
('KP0305', 'KP0304'),
('KP0306', 'KP0305'),
('KP0307', 'KP0303'),
('KP0308', 'KP0303'),
('KP0309', 'KP0303'),
('KP0310', 'KP0309')
ON DUPLICATE KEY UPDATE kp_id = VALUES(kp_id);

-- 关键词以本文件为准：先清掉这 20 个 KP 的旧关键词，避免 init-v2 冒烟种子残留导致映射串味
DELETE FROM kp_keyword WHERE kp_id IN (
'KP0201','KP0202','KP0203','KP0204','KP0205','KP0206','KP0207','KP0208','KP0209','KP0210',
'KP0301','KP0302','KP0303','KP0304','KP0305','KP0306','KP0307','KP0308','KP0309','KP0310');

INSERT INTO kp_keyword (kp_id, keyword, weight) VALUES
('KP0201', '关系模型', 1.00),
('KP0201', '关系', 0.90),
('KP0201', '元组', 0.80),
('KP0201', '属性', 0.70),
('KP0201', '域', 0.60),
('KP0201', '分量', 0.50),
('KP0201', '二维表', 0.50),
('KP0201', '三要素', 0.50),
('KP0202', '域', 1.00),
('KP0202', '笛卡儿积', 0.90),
('KP0202', '元组', 0.80),
('KP0202', '分量', 0.70),
('KP0202', '基数', 0.60),
('KP0202', '目（度）', 0.50),
('KP0202', '关系', 0.50),
('KP0202', '子集', 0.50),
('KP0203', '候选码', 1.00),
('KP0203', '主码', 0.90),
('KP0203', '主属性', 0.80),
('KP0203', '非主属性', 0.70),
('KP0203', '全码', 0.60),
('KP0203', '唯一标识', 0.50),
('KP0203', '真子集', 0.50),
('KP0204', '关系模式', 1.00),
('KP0204', '型', 0.90),
('KP0204', '值', 0.80),
('KP0204', 'R(U,D,DOM,F)', 0.70),
('KP0204', '关系数据库', 0.60),
('KP0204', '关系数据库模式', 0.50),
('KP0204', '静态', 0.50),
('KP0204', '动态', 0.50),
('KP0205', '关系操作', 1.00),
('KP0205', '查询', 0.90),
('KP0205', '更新', 0.80),
('KP0205', '基本操作', 0.70),
('KP0205', '关系代数', 0.60),
('KP0205', '关系演算', 0.50),
('KP0205', '关系完备性', 0.50),
('KP0205', 'SQL', 0.50),
('KP0206', '实体完整性', 1.00),
('KP0206', '主码', 0.90),
('KP0206', '主属性', 0.80),
('KP0206', '空值', 0.70),
('KP0206', '两个不变性', 0.60),
('KP0206', '唯一标识', 0.50),
('KP0207', '参照完整性', 1.00),
('KP0207', '外码', 0.90),
('KP0207', '参照关系', 0.80),
('KP0207', '被参照关系', 0.70),
('KP0207', '目标关系', 0.60),
('KP0207', '主码', 0.50),
('KP0207', '空值', 0.50),
('KP0207', '同一域', 0.50),
('KP0208', '用户定义的完整性', 1.00),
('KP0208', '语义约束', 0.90),
('KP0208', '取值范围', 0.80),
('KP0208', '非空', 0.70),
('KP0208', '唯一', 0.60),
('KP0208', '应用领域', 0.50),
('KP0209', '并', 1.00),
('KP0209', '差', 0.90),
('KP0209', '交', 0.80),
('KP0209', '笛卡儿积', 0.70),
('KP0209', '二目运算', 0.60),
('KP0209', '同目', 0.50),
('KP0209', '元组数', 0.50),
('KP0209', 'n+m 列', 0.50),
('KP0210', '选择', 1.00),
('KP0210', '投影', 0.90),
('KP0210', '连接', 0.80),
('KP0210', '等值连接', 0.70),
('KP0210', '自然连接', 0.60),
('KP0210', '除', 0.50),
('KP0210', '象集', 0.50),
('KP0210', '悬浮元组', 0.50),
('KP0210', '外连接', 0.50),
('KP0301', 'SQL', 1.00),
('KP0301', '非过程化', 0.90),
('KP0301', '三级模式', 0.80),
('KP0301', '外模式', 0.70),
('KP0301', '视图', 0.60),
('KP0301', '基本表', 0.50),
('KP0301', '存储文件', 0.50),
('KP0301', 'SEQUEL', 0.50),
('KP0301', '9 个动词', 0.50),
('KP0302', 'CREATE SCHEMA', 1.00),
('KP0302', 'CASCADE', 0.90),
('KP0302', 'RESTRICT', 0.80),
('KP0302', 'CREATE TABLE', 0.70),
('KP0302', '列级完整性约束', 0.60),
('KP0302', '表级完整性约束', 0.50),
('KP0302', 'ALTER TABLE', 0.50),
('KP0302', '索引', 0.50),
('KP0302', '数据字典', 0.50),
('KP0303', 'SELECT', 1.00),
('KP0303', 'DISTINCT', 0.90),
('KP0303', 'WHERE', 0.80),
('KP0303', 'LIKE', 0.70),
('KP0303', 'ESCAPE', 0.60),
('KP0303', 'ORDER BY', 0.50),
('KP0303', '聚集函数', 0.50),
('KP0303', 'GROUP BY', 0.50),
('KP0303', 'HAVING', 0.50),
('KP0303', 'LIMIT', 0.50),
('KP0304', '连接谓词', 1.00),
('KP0304', '等值连接', 0.90),
('KP0304', '自然连接', 0.80),
('KP0304', '自身连接', 0.70),
('KP0304', '外连接', 0.60),
('KP0304', 'LEFT OUTER JOIN', 0.50),
('KP0304', '悬浮元组', 0.50),
('KP0304', '多表连接', 0.50),
('KP0305', '查询块', 1.00),
('KP0305', '不相关子查询', 0.90),
('KP0305', '相关子查询', 0.80),
('KP0305', '元组变量', 0.70),
('KP0305', 'IN', 0.60),
('KP0305', 'ANY', 0.50),
('KP0305', 'ALL', 0.50),
('KP0305', 'EXISTS', 0.50),
('KP0305', 'NOT EXISTS', 0.50),
('KP0305', '全称量词', 0.50),
('KP0306', 'UNION', 1.00),
('KP0306', 'UNION ALL', 0.90),
('KP0306', 'INTERSECT', 0.80),
('KP0306', 'EXCEPT', 0.70),
('KP0306', '派生表', 0.60),
('KP0306', 'FROM 子查询', 0.50),
('KP0306', '别名', 0.50),
('KP0306', '中间结果表', 0.50),
('KP0307', 'INSERT', 1.00),
('KP0307', 'VALUES', 0.90),
('KP0307', '插入子查询', 0.80),
('KP0307', 'UPDATE', 0.70),
('KP0307', 'SET', 0.60),
('KP0307', 'DELETE', 0.50),
('KP0307', 'WHERE', 0.50),
('KP0307', '参照完整性', 0.50),
('KP0308', '空值', 1.00),
('KP0308', 'NULL', 0.90),
('KP0308', 'IS NULL', 0.80),
('KP0308', 'NOT NULL', 0.70),
('KP0308', 'UNIQUE', 0.60),
('KP0308', 'UNKNOWN', 0.50),
('KP0308', '三值逻辑', 0.50),
('KP0308', '真值表', 0.50),
('KP0309', 'CREATE VIEW', 1.00),
('KP0309', 'WITH CHECK OPTION', 0.90),
('KP0309', '行列子集视图', 0.80),
('KP0309', '分组视图', 0.70),
('KP0309', '视图消解', 0.60),
('KP0309', 'DROP VIEW', 0.50),
('KP0309', '可更新视图', 0.50),
('KP0310', '视图作用', 1.00),
('KP0310', '安全保护', 0.90),
('KP0310', '逻辑独立性', 0.80),
('KP0310', '重构数据库', 0.70),
('KP0310', '简化操作', 0.60),
('KP0310', '多种角度', 0.50),
('KP0310', '外模式', 0.50)
ON DUPLICATE KEY UPDATE weight = VALUES(weight);

UPDATE course SET dimension_json = '[{"key":"concept","name":"概念理解"},{"key":"sql","name":"SQL 编写"},{"key":"schema","name":"模式设计"},{"key":"transaction","name":"事务与并发"},{"key":"boundary","name":"边界与异常"}]'
WHERE course_id = 1;

-- 自检提示（讲解末段使用；契约 V2 §7.1 要求）
UPDATE knowledge_point SET trial_hint='用一句话说出关系模型的三个组成部分' WHERE kp_id='KP0201';
UPDATE knowledge_point SET trial_hint='笛卡儿积的基数怎么算？' WHERE kp_id='KP0202';
UPDATE knowledge_point SET trial_hint='候选码为什么要求真子集不能唯一标识？' WHERE kp_id='KP0203';
UPDATE knowledge_point SET trial_hint='关系模式与关系，哪个是型、哪个是值？' WHERE kp_id='KP0204';
UPDATE knowledge_point SET trial_hint='五种基本关系运算分别是什么？' WHERE kp_id='KP0205';
UPDATE knowledge_point SET trial_hint='主码能否取空值？为什么？' WHERE kp_id='KP0206';
UPDATE knowledge_point SET trial_hint='外码的取值有哪两类合法情况？' WHERE kp_id='KP0207';
UPDATE knowledge_point SET trial_hint='举一个用户定义完整性的例子' WHERE kp_id='KP0208';
UPDATE knowledge_point SET trial_hint='并、差、交运算对两个关系有什么前提？' WHERE kp_id='KP0209';
UPDATE knowledge_point SET trial_hint='投影后为什么可能需要取消重复元组？' WHERE kp_id='KP0210';
UPDATE knowledge_point SET trial_hint='SQL 的外模式、模式、内模式分别对应什么？' WHERE kp_id='KP0301';
UPDATE knowledge_point SET trial_hint='DROP TABLE 与 DELETE 的区别是什么？' WHERE kp_id='KP0302';
UPDATE knowledge_point SET trial_hint='WHERE 与 HAVING 的分工是什么？' WHERE kp_id='KP0303';
UPDATE knowledge_point SET trial_hint='自然连接与等值连接差在哪里？' WHERE kp_id='KP0304';
UPDATE knowledge_point SET trial_hint='什么情况下必须用相关子查询？' WHERE kp_id='KP0305';
UPDATE knowledge_point SET trial_hint='UNION 默认会去重吗？' WHERE kp_id='KP0306';
UPDATE knowledge_point SET trial_hint='UPDATE 漏写 WHERE 会发生什么？' WHERE kp_id='KP0307';
UPDATE knowledge_point SET trial_hint='NULL 与任何值比较的结果是什么？' WHERE kp_id='KP0308';
UPDATE knowledge_point SET trial_hint='哪些视图一般是可更新的？' WHERE kp_id='KP0309';
UPDATE knowledge_point SET trial_hint='视图的两个主要作用是什么？' WHERE kp_id='KP0310';
