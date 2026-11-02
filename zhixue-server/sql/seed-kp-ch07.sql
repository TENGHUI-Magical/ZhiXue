-- 智学领航 · 第7章知识点种子（由 csv_to_sql.py 生成）
-- 前置：init-v2.sql；可重复执行（upsert）。
USE zhixue;
SET NAMES utf8mb4;

INSERT INTO course_chapter (course_id, chapter_no, title, sort_no) VALUES
(1, 7, '数据库设计', 7)
ON DUPLICATE KEY UPDATE title = VALUES(title), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0701', 1, chapter_id, '数据库设计概述与六个阶段', '数据库设计是对于一个给定应用环境，构造优化的数据库模式、外模式和内模式，并据此建立数据库及其应用系统，使之有效存储和管理数据，满足信息管理与数据操作需求。广义上包括应用系统设计，狭义上指设计数据库各级模式。其特点可概括为“三分技术，七分管理，十二分基础数据”，并要求数据库设计与数据处理设计相结合。基本步骤分为6个阶段：需求分析、概念结构设计、逻辑结构设计、物理结构设计、数据库实施、数据库运行和维护。需求分析与概念结构设计可独立于具体DBMS，逻辑与物理设计则依赖所选DBMS；6个阶段往往不断反复，而非一次完成。常用设计方法包括新奥尔良方法、基于E-R模型的方法、3NF方法、面向对象方法和UML方法；设计人员通常包括系统分析员、数据库设计员、应用程序员、数据库管理员和最终用户代表。', '①把数据库设计等同于编写建表语句，忽略需求分析与概念设计\\n②认为6个阶段必须严格一次通过，不能返回修改\\n③认为所有阶段都独立于DBMS\\n④忽视基础数据的收集、整理与更新', 'P201', 'concept', '1', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 7
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0702', 1, chapter_id, '数据库设计过程中的各级模式', '数据库设计的不同阶段形成数据库的各级模式。需求分析阶段综合各用户的应用需求；概念结构设计阶段形成独立于机器特点和DBMS产品的概念模式，即E-R图；逻辑结构设计阶段把E-R图转换为DBMS支持的数据模型（如关系模型），形成数据库逻辑模式，再根据用户处理要求和安全性建立视图，形成外模式；物理结构设计阶段按DBMS特点进行物理存储安排、建立索引，形成内模式。概念模式面向用户和设计人员，属于概念模型层次；逻辑模式、外模式、内模式由DBMS支持，可在DBMS中描述和存储。外模式/模式映像支持逻辑独立性，模式/内模式映像支持物理独立性。', '①把E-R图当成DBMS中的数据模式\\n②认为外模式就是基本表\\n③混淆概念模式与逻辑模式\\n④认为内模式只与磁盘容量有关', 'P207', 'schema', '2', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 7
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0703', 1, chapter_id, '需求分析的任务与方法', '需求分析是数据库设计的起点，其任务是详细调查现实世界要处理的对象，充分了解原系统（手工系统或计算机系统）的工作概况，明确用户各种需求，并在此基础上确定新系统功能；新系统还要考虑今后可能的扩充和改变。调查重点是“数据”和“处理”，需获得信息要求、处理要求以及安全性与完整性要求。调查步骤包括：调查组织机构总体情况；熟悉各部门业务活动；协助用户明确新系统要求；确定新系统的边界。常用方法有跟班作业、开调查会、请专人介绍、询问、问卷调查、查阅记录。结构化分析SA采用自顶向下、逐层分解的方式，分析结果形成数据字典和用户需求规格说明书。', '①只调查计算机系统，忽略手工系统现状\\n②只收集数据，不调查处理要求与安全完整性要求\\n③认为需求分析可以一次完成、不需用户参与\\n④把新系统边界交给用户单方面决定而不做分析', 'P207', 'concept', '3', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 7
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0704', 1, chapter_id, '数据字典', '数据字典是详细的数据收集和数据分析之后获得的主要成果，是关于数据库中数据的描述，即元数据，而不是数据本身；它在需求分析阶段建立，并在数据库设计过程中不断修改、充实和完善。数据字典通常包括5部分：数据项、数据结构、数据流、数据存储和处理过程。数据项是不可再分的数据单位，其取值范围以及与其他数据项的逻辑关系定义完整性约束；数据结构反映数据项之间的组合关系；数据流是数据结构在系统内传输的路径，需描述来源、去向、组成和流量；数据存储是数据结构停留或保存的地方；处理过程只需描述输入、输出和简要处理说明，具体逻辑常用判定表或判定树表示。数据字典是概念结构设计的基础。', '①把数据字典等同于数据库中的数据表\\n②认为数据字典只在实施阶段建立\\n③把数据项与数据结构混为一谈\\n④在处理过程中写入完整程序代码', 'P209', 'concept', '4', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 7
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0705', 1, chapter_id, '概念模型与E-R模型', '概念结构设计就是把需求分析得到的用户需求抽象为信息结构（即概念模型）的过程，是整个数据库设计的关键。概念模型应能真实、充分地反映现实世界，易于理解，易于更改，并易于向关系、网状、层次等各种数据模型转换。E-R模型是用E-R图描述现实世界概念模型的有力工具，其三要素是实体型、属性和联系：实体型用矩形表示，属性用椭圆形表示，联系用菱形表示并标注联系类型。两个实体型之间有一对一（1:1）、一对多（1:n）和多对多（m:n）三类联系；两个以上实体型之间以及单个实体型内部也可存在这三类联系；联系本身可以有属性。', '①把E-R图当成某个DBMS的表结构图\\n②认为概念模型依赖具体数据库产品\\n③只记三类联系却不会判断实际语义\\n④把联系属性画到某个实体上', 'P211', 'concept', '5', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 7
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0706', 1, chapter_id, '扩展E-R模型', '基本E-R模型可以扩展，使其表达能力更强。ISA联系表示父类-子类关系，子类继承父类的全部属性，也可有自己的属性；分类属性决定实体进入哪个子类，三角形内加叉号表示不相交约束，不加叉号表示可重叠约束；父类实体必须属于某个子类称完全特化，用双线连接表示，否则为部分特化。基数约束用min..max细化联系，min=1为强制参与，min=0为非强制参与。Part-of联系表示部分关系：非独占联系可用非强制参与表示，独占联系用弱实体型和识别联系表示；弱实体型用双矩形，识别联系用双菱形。', '①认为子类不继承父类属性\\n②把不相交约束与完全特化混为一谈\\n③把min..max当成元组个数范围\\n④把弱实体型画成普通矩形', 'P215', 'concept', '6', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 7
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0707', 1, chapter_id, '实体与属性划分及E-R图集成', '用E-R图进行概念结构设计，首先要确定实体、属性和联系。实体与属性没有形式上截然划分的界限，基本准则是：现实世界的事物能作为属性的尽量作为属性；属性必须是不可再分的数据项，不能包含其他属性；属性不能与其他实体有联系。若对象需要进一步描述或参与联系（如职称关联工资、专业关联类别和年限、课程有多门先修课），则应上升为实体。大型系统常先按子系统设计分E-R图，再集成为全局E-R图。集成分两步：合并分E-R图、消除冲突，生成初步E-R图；修改与重构、消除不必要冗余，生成基本E-R图。冲突分属性冲突、命名冲突和结构冲突三类。', '①把需要进一步描述的对象仍当作属性\\n②认为E-R图集成就是把各分图简单拼在一起\\n③只记冲突名称，不会给出解决办法\\n④把可由基本数据导出的冗余联系保留为基本联系', 'P220', 'schema', '7', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 7
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0708', 1, chapter_id, 'E-R图向关系模型的转换', 'E-R图向关系模型转换要解决实体型、属性和联系如何变为关系模式，以及如何确定关系模式的属性和码。一个实体型转换为一个关系模式，关系属性就是实体属性，关系码就是实体码。联系按类型处理：1:1联系可转换为独立关系模式，也可与任意一端对应的关系模式合并；独立转换时各实体码及联系属性为关系属性，各实体码均为候选码。1:n联系可独立转换，也可与n端对应的关系模式合并；独立时关系码为n端实体的码。m:n联系必须转换为一个关系模式，各实体码组成关系码或码的一部分。三个或以上实体的多元联系也转换为一个关系模式。具有相同码的关系模式可合并。', '①把1:n联系也强行转换为独立关系模式而不知可与n端合并\\n②把m:n联系拆成两个1:n联系\\n③认为联系属性在转换后可以丢弃\\n④把所有关系模式都独立保留，不做同码合并', 'P229', 'schema', '8', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 7
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0709', 1, chapter_id, '数据模型的优化与用户外模式', '数据库逻辑设计的结果不是唯一的，需要以规范化理论为指导优化数据模型：确定各关系模式内部及不同关系模式属性间的数据依赖；对关系模式间的数据依赖极小化，消除冗余联系；逐一分析部分函数依赖、传递函数依赖、多值依赖，确定各模式范式；结合处理要求判断模式是否合适，决定合并或分解。规范化程度并非越高越好：查询涉及多个关系时连接代价高，可合并关系，此时2NF甚至1NF也可能合适；只查询不更新的非BCNF模式未必有实际影响。分解分水平分解和垂直分解，垂直分解须保证无损连接性和保持函数依赖性。设计用户外模式可利用视图，考虑用户习惯别名、不同级别用户视图和简化复杂查询。', '①认为规范化程度越高性能一定越好\\n②把水平分解与垂直分解混为一谈\\n③分解后不检查无损连接与保持函数依赖\\n④认为外模式只是给表起别名', 'P230', 'schema', '9', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 7
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0710', 1, chapter_id, '物理结构设计、实施与运行维护', '物理结构设计是为逻辑数据模型选取适合应用环境的物理结构，分两步：确定物理结构（关系数据库中主要指存取方法和存储结构），并对时间效率、空间效率进行评价。常用存取方法有B+树索引、哈希索引和聚簇：常在查询条件、聚集函数参数、连接条件出现的属性可建索引，但索引不是越多越好；聚簇把聚簇码相同的元组集中存放，一个关系只能加入一个聚簇。存储结构设计包括数据存放位置与系统配置。数据库实施包括数据载入、应用程序编码调试和试运行，试运行应分期分批入库并做好转储恢复。运行维护包括转储恢复、安全性与完整性控制、性能监督分析改造、重组与重构；重组不改模式与内模式，重构则部分修改模式和内模式。', '①认为索引越多查询一定越快\\n②把聚簇当成普通索引\\n③试运行时一次性倒入全部数据\\n④把数据库重组与重构当成同一件事', 'P232', 'boundary', '10', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 7
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

