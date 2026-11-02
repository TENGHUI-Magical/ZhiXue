-- 智学领航 · 演示库合并种子（自动生成，勿手改）
-- 生成脚本：scripts/build-seed-all.ps1
-- 顺序：knowledge_point -> question_bank -> knowledge_prereq -> 演示账号/数据
-- 幂等：INSERT 均为 upsert；无 upsert 的由前置 DELETE 或 WHERE NOT EXISTS 保证
USE zhixue;
SET NAMES utf8mb4;

-- ============================================================
-- [kp] seed-kp-ch01.sql
-- ============================================================
-- 智学领航 · 第1章知识点种子（由 csv_to_sql.py 生成）
-- 前置：init-v2.sql；可重复执行（upsert）。


INSERT INTO course_chapter (course_id, chapter_no, title, sort_no) VALUES
(1, 1, '绪论', 0)
ON DUPLICATE KEY UPDATE title = VALUES(title), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0101', 1, chapter_id, '数据库的4个基本概念（数据、数据库、DBMS、DBS）', '数据是描述事物的符号记录，数字、文字、图形、图像、音频、视频等都可经数字化后存入计算机；数据与其语义不可分，同一数值在不同语境下含义不同。数据库是长期存储在计算机内、有组织、可共享的大量数据的集合，数据按数据模型组织、描述和存储，具有较小冗余、较高数据独立性和可扩展性。数据库管理系统是位于用户与操作系统之间的一层数据管理软件，提供数据定义、组织存储、数据操纵、事务管理与运行管理、建立与维护等功能。数据库系统是引入数据库后的计算机系统，一般由数据库、数据库管理系统（及其应用开发工具）、应用系统和数据库管理员组成。四者按“被管理对象—数据集合—管理软件—整体系统”层层递进，不能混同。', '①把数据等同于数字，忽略文本、图形、图像、音频、视频等广义数据\\n②把数据库当成普通文件夹，忽略“有组织、可共享、按数据模型组织”\\n③把数据库系统与数据库混为一谈，漏掉 DBMS、应用系统与 DBA\\n④认为 DBMS 就是数据库', 'P4', 'concept', '1', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 1
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0102', 1, chapter_id, '数据管理技术的产生与发展（人工管理、文件系统、数据库系统）', '数据管理是对数据进行分类、组织、编码、存储、检索和维护，是数据处理的中心问题；数据处理则是对数据收集、存储、加工和传播的一系列活动。数据管理技术经历三个阶段。人工管理阶段（20世纪50年代中期以前）硬件只有纸带、卡片、磁带，没有操作系统和数据管理软件，数据不保存、由应用程序管理、不共享、不具备独立性。文件系统阶段（50年代后期至60年代中期）有磁盘、磁鼓和文件系统，数据可长期保存、由专门软件管理、记录内有结构，但共享性弱、冗余度高、独立性弱、整体无结构。数据库系统阶段（60年代后期以来）由 DBMS 统一管理，实现整体数据结构化、共享性强冗余低、独立性强，并提供安全性、完整性、并发控制与恢复。三阶段的核心差异是数据是否结构化、是否共享、独立性高低以及由谁管理数据。', '①认为文件系统阶段数据已完全独立、可共享\\n②把“数据管理”与“数据处理”当成同一概念\\n③认为人工管理阶段的数据可长期保存\\n④认为三阶段只是存储介质变化，忽略管理方式与独立性差异', 'P6', 'concept', '2', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 1
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0103', 1, chapter_id, '数据库系统的组成与人员职责', '数据库系统是引入数据库后的计算机系统，是一个人机系统。按教材 1.1.1，一般由数据库、数据库管理系统（及其应用开发工具）、应用系统和数据库管理员组成；按 1.4 的支撑视角，还包括硬件平台、软件平台和人员三类。硬件平台提供存储与处理能力；软件平台包括操作系统、DBMS、高级语言及其编译系统、应用开发工具和应用系统；人员包括数据库管理员、系统分析员和数据库设计员、应用程序员与最终用户。数据库管理员负责设计与定义数据库、培训最终用户、负责系统运维、监视与调优性能、转储与恢复数据库、必要时重构数据库。不同角色对应不同的数据抽象级别与数据视图。', '①认为数据库系统只由数据库和 DBMS 组成，漏掉应用系统与 DBA\\n②把 DBA 当成编写应用程序的人\\n③认为数据库系统只是软件，忽略硬件平台与人员\\n④认为最终用户不需要了解数据库', 'P27', 'concept', '3', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 1
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0104', 1, chapter_id, '数据模型的三要素（数据结构、数据操纵、完整性约束）', '数据模型是对现实世界数据特征的抽象，用来描述数据、组织数据和对数据进行操作，是数据库系统的核心和基础。数据模型通常由数据结构、数据操纵和完整性约束三部分组成，称为三要素。数据结构描述数据库的组成对象以及对象之间的联系，是对系统静态特性的描述，也是刻画数据模型性质最重要的方面，人们常按数据结构类型命名数据模型（层次、网状、关系）。数据操纵是对数据库中各种对象的实例允许执行的操作集合，包括查询与更新（插入、删除、修改）及操作规则，是对系统动态特性的描述。完整性约束是一组完整性规则，限定数据库状态及其变化，以保证数据正确、有效和相容，如关系模型必须满足实体完整性与参照完整性。三要素完整描述一个数据模型，缺一不可。', '①只把数据模型理解为表结构，漏掉数据操纵与完整性约束\\n②认为数据结构描述动态特性\\n③把概念模型与逻辑数据模型混同\\n④认为完整性约束只与具体应用有关，不是数据模型的组成部分', 'P14', 'concept', '4', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 1
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0105', 1, chapter_id, '概念模型与 E-R 图（实体、属性、联系）', '数据建模分两步：先把现实世界抽象为信息世界，建立概念模型；再把概念模型转换为计算机上某一 DBMS 支持的数据模型。概念模型按用户观点建模，不依赖具体计算机系统，主要用于数据库设计，是数据库设计人员与用户交流的语言，应具备较强的语义表达能力且简单清晰。信息世界的基本概念包括：实体（客观存在并可相互区别的事物）、属性（实体所具有的某一特性）、码（唯一标识实体的属性集）、实体型（用实体名及属性名集合抽象同类实体）、实体集（同一类型实体的集合）、联系（实体内部属性之间以及不同实体集之间的联系）。联系有一对一、一对多、多对多。最常用的概念模型表示方法是 P.P.S.Chen 于 1976 年提出的实体-联系模型（E-R 模型），用 E-R 图描述现实世界的概念模型。', '①认为概念模型依赖具体 DBMS\\n②把实体型与实体集、实体与属性混为一谈\\n③认为 E-R 图可以直接在 DBMS 中实现\\n④忽略实体间联系的类型区分', 'P13', 'schema', '5', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 1
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0106', 1, chapter_id, '常用逻辑数据模型（层次、网状、关系等）', '逻辑数据模型是按计算机系统观点建模、由具体 DBMS 支持的数据模型。层次模型用树形结构表示实体及联系，满足“有且只有一个结点无双亲（根）”和“其余结点有且只有一个双亲”，典型代表是 IBM 的 IMS；优点是结构清晰、查询效率高、完整性约束支持好，缺点是难以自然表示多对多联系、查询子女必须经过双亲。网状模型允许一个以上结点无双亲、一个结点可有多个双亲，典型代表是 DBTG/CODASYL 系统；它更直接地描述现实世界、存取效率高，但结构复杂、语言复杂、用户须了解存取路径。关系模型用二维表表示实体与联系，1970 年由 E.F.Codd 提出，建立在严格数学概念上，概念单一、存取路径对用户隐蔽，但查询效率需靠优化器保证。此外还有面向对象、对象关系、半结构化 XML 以及键值、文档、图、时序等新型数据模型。', '①认为层次模型可以自然表示多对多联系\\n②把网状模型与层次模型的成立条件记反\\n③认为关系模型没有完整性约束\\n④认为新型数据模型都已具备严格的三要素', 'P15', 'concept', '6', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 1
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0107', 1, chapter_id, '数据库系统的三级模式结构（外模式、模式、内模式）', '从 DBMS 角度看，数据库系统通常采用模式、外模式、内模式三级模式结构。模式也称逻辑模式，是数据库中全体数据的逻辑结构和特征的描述，是所有用户的公共数据视图，处于三级结构的中间层，不涉及物理存储细节，与具体应用、开发工具和语言无关；一个数据库只有一个模式。外模式也称子模式或用户模式，是数据库用户能够看见和使用的局部数据的逻辑结构和特征的描述，是用户的数据视图；一个数据库可以有多个外模式，同一外模式可为多个应用使用，但一个应用程序只能使用一个外模式；外模式是保证数据库安全性的有力措施。内模式也称物理模式或存储模式，是对数据物理结构和存储方式的描述，如记录存储方式、索引组织方式、是否压缩加密等；一个数据库只有一个内模式。三级模式是对数据的三个抽象级别。', '①认为一个数据库可以有多个内模式\\n②把模式与内模式混同\\n③认为外模式越多越不安全\\n④认为模式涉及物理存储细节', 'P23', 'schema', '7', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 1
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0108', 1, chapter_id, '数据库的二级映像与数据独立性', '三级模式之间由 DBMS 提供两级映像：外模式/模式映像和模式/内模式映像，它们保证数据库中的数据具有较高的逻辑独立性和物理独立性。外模式/模式映像定义外模式与模式之间的对应关系，通常包含在各自外模式的描述中；当模式改变（如增加关系、属性或改变数据类型）时，DBA 修改映像可使外模式保持不变，依据外模式编写的应用程序不必修改，从而保证数据的逻辑独立性。模式/内模式映像定义数据全局逻辑结构与存储结构之间的对应关系，通常包含在模式描述中；当存储结构改变时，DBA 修改该映像可使模式保持不变，应用程序也不必改变，从而保证数据的物理独立性。模式/内模式映像是唯一的，因为一个数据库只有一个模式和一个内模式。数据独立性把数据定义从应用程序中分离出去，简化了应用编制与维护。', '①把逻辑独立性与物理独立性记反\\n②认为模式改变时应用程序必须修改\\n③认为两级映像是可选的\\n④认为外模式/模式映像也是唯一的', 'P25', 'schema', '8', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 1
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0109', 1, chapter_id, '数据库系统的体系结构（集中式、C/S、并行、分布式、云）', '从最终用户角度看，数据库系统可分为集中式、客户-服务器（浏览器/应用服务器/数据库服务器）、并行、分布式和云数据库系统等，这是数据库系统的外部体系结构。集中式系统的 DBMS、数据库和应用程序都在一台计算机上，小型机/大型机上一般为多用户，微机上的常为单用户。客户-服务器系统中 DBMS 与数据库驻留服务器，应用程序放在客户机，通过网络通信；互联网应用常发展为三层结构，界面用浏览器，应用服务器部署应用模块，数据库服务器部署 DBMS 与数据库。并行数据库系统运行在并行计算机上，利用多处理机并行处理提高性能，具有高性能、高可用、高扩展性。分布式数据库的数据逻辑上是一个整体、物理上分布在网络不同节点，各节点场地自治，也可执行全局应用。云数据库把数据库部署或虚拟化在云计算环境，以服务形式提供存储、更新、查询、事务管理等功能，但仍面临安全可信与隐私保护问题。', '①把分布式数据库与并行数据库混同\\n②认为客户-服务器结构只有两层\\n③认为集中式系统一定是单用户\\n④认为云数据库没有安全与隐私问题', 'P28', 'concept', '9', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 1
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0110', 1, chapter_id, '数据库系统的特点与数据管理新发展', '数据库系统阶段的数据具有四个主要特点。第一，整体数据的结构化：数据不再只面向某一应用，而是面向整个组织或企业的多种应用，不仅数据本身结构化，数据之间也有联系，这是数据库系统与文件系统的本质区别；DBMS 维护记录结构及记录间联系，减轻程序员负担。第二，共享性强、冗余度低且易于扩充：数据面向整个系统，可被多个用户和应用通过不同接口与语言共享，降低冗余、避免不相容与不一致，并便于增加新应用。第三，独立性强：包括物理独立性与逻辑独立性，由两级映像保证，使应用程序与数据定义分离。第四，数据由 DBMS 统一管理和控制：提供安全性保护、完整性检查、并发控制与数据库恢复。数据库系统的出现使信息系统从以程序为中心转向以共享数据库为中心。', '①把“整体数据结构化”理解为单张表有结构\\n②认为共享性强必然导致无法控制安全性\\n③把数据独立性与数据冗余混为一谈\\n④认为数据库系统不需要并发控制与恢复', 'P9', 'boundary', '10', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 1
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

-- ============================================================
-- [kp] seed-kp-ch02-ch03.sql
-- ============================================================
-- 智学领航 · 第2—3章知识点种子（由 strict 通过的 CSV 重生成）
-- 保留原有先修边与关键词；前置 init-v2.sql；可重复执行（upsert）。


INSERT INTO course_chapter (course_id, chapter_no, title, sort_no) VALUES
(1, 2, '关系模型', 1)
ON DUPLICATE KEY UPDATE title = VALUES(title), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0201', 1, chapter_id, '关系模型的基本概念与三要素', '关系模型是用单一结构——关系——描述现实世界实体及其联系的模型。用户看到的关系数据结构是一张二维表：每一行是一个元组，每一列是一个属性，列的取值范围是域，元组中的每一个值是分量。关系模型由三部分组成：关系数据结构、关系操作集合、关系完整性约束。数据结构回答“数据如何表示”，关系操作回答“能施加哪些查询与更新”，完整性约束回答“数据必须满足哪些语义条件”。关系模型建立在集合代数之上，本章用集合论给出关系、关系模式等形式化定义。还要区分型与值：关系模式是对关系的描述，属于型；关系是某一时刻的元组集合，属于值。关系模型的数据结构虽然简单，却能表达丰富的语义，描述现实世界的实体以及实体间的各种联系。与层次模型、网状模型相比，它把实体和联系统一用关系表示，用户只需面对二维表。', '把关系模型只当成二维表，漏掉关系操作与完整性约束\\n认为关系模型的结构与层次、网状模型一样复杂\\n把“关系”与“关系模式”当作同一概念', 'P34', 'concept', '1', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 2
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0202', 1, chapter_id, '关系的形式化定义（域、笛卡儿积、关系）', '域是一组具有相同数据类型的值的集合。给定一组域 D1,D2,…,Dn，允许其中某些域相同，其笛卡儿积定义为 D1×D2×…×Dn={(d1,d2,…,dn) | di∈Di, i=1,2,…,n}。每个元素 (d1,d2,…,dn) 是一个 n 元组，元组中的每个 di 是分量。若 Di 为有限集、基数为 mi，则笛卡儿积的基数为 M=∏(i=1…n) mi。笛卡儿积可列成二维表：一行对应一个元组，一列的值来自一个域。关系是笛卡儿积的有限子集：D1×D2×…×Dn 的子集称为这组域上的关系，记作 R(D1,D2,…,Dn)，R 为关系名，n 为目或度；n=1 为一元关系，n=2 为二元关系。因域可相同，必须给每一列起属性名，n 目关系必有 n 个属性。笛卡儿积中的元组并不都具有实际语义，只有它的某个真子集才可能有实际含义，关系正是取这样的子集。', '把关系的“目”误当成元组个数\\n认为笛卡儿积的每个元组都有实际语义\\n把域与属性混为一谈，认为属性值可跨域取值', 'P34', 'concept', '2', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 2
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0203', 1, chapter_id, '候选码、主码、主属性与非主属性', '在关系模式中，若某一个属性或一组属性的值能唯一标识一个元组，而它的真子集不能唯一标识一个元组，则称该属性或属性组为候选码。一个关系可以有多个候选码，选定其中一个作为主码（主键），并在其名称下加下划线标识。候选码所包含的属性称为主属性；不包含在任何候选码中的属性称为非主属性（非码属性）。最简单的情形是候选码只含一个属性；最极端的情形是关系模式的所有属性共同构成候选码，称为全码。候选码强调“最小性”：属性组整体能唯一标识元组，去掉任一属性后便不能唯一标识。主码则是在多个候选码中选定的一个，用于实体完整性约束。码是能唯一标识元组的属性或属性组，理解时应把“唯一标识”与“最小性”同时抓住。候选码的定义以“能唯一标识元组”和“真子集不能唯一标识”为两个条件。', '认为候选码只能由一个属性组成\\n认为主属性可以取空值\\n把主码与候选码等同，认为一个关系只有一个码', 'P38', 'schema', '3', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 2
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0204', 1, chapter_id, '关系模式与关系数据库', '关系模式是对关系的描述，关系模式是型，关系是值；关系是元组集合，是关系模式在某一时刻的状态或内容。关系模式可形式化表示为 R(U,D,DOM,F)：R 为关系名，U 为组成该关系的属性名集合，D 为 U 中属性所来自的域，DOM 为属性向域的映像集合，F 为属性间数据依赖关系的集合。本章只涉及 R(U,D,DOM)，数据依赖留到第 6 章。关系模式常简记为 R(U) 或 R(A1,A2,…,An)，域名与映像常直接说明为属性的类型、长度。关系模式是静态、稳定的；关系随时间不断变化。某一时刻所有关系模式对应的关系的集合构成一个关系数据库；所有关系模式的集合是关系数据库模式，是型；其对应的关系集合是关系数据库的值。实际工作中，人们常把关系模式和关系都笼统地称为“关系”，需要根据上下文加以区别。', '认为关系模式会随元组增删而变化\\n把关系数据库模式与某一时刻的关系数据库值混同\\n认为关系模式只描述属性名，不含域名与映像', 'P37', 'schema', '4', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 2
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0205', 1, chapter_id, '关系操作与关系数据语言分类', '关系模型中常用的关系操作包括查询和更新两大部分，更新又分为插入、删除、修改。查询操作可进一步分为选择、投影、连接、除、并、差、交、笛卡儿积等；其中选择、投影、并、差、笛卡儿积是 5 种基本操作，其他操作可由基本操作定义和导出，就像乘法可由加法定义一样。关系操作的特点是集合操作方式，即操作对象和结果都是集合，也称为一次一个集合的处理方式；所有输入、输出以及中间结果都是关系。关系数据语言分为关系代数、关系演算和结构化查询语言 SQL。关系演算按谓词变元的基本对象是元组变量还是域变量，分为元组关系演算和域关系演算。关系代数、元组关系演算、域关系演算表达能力等价，都具有关系完备性。SQL 集数据查询、数据定义、数据操纵和数据控制于一体。', '认为查询不属于关系操作，只把插入删除修改当操作\\n认为交、连接、除是基本运算\\n认为关系代数与关系演算表达能力不等价', 'P40', 'concept', '5', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 2
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0206', 1, chapter_id, '实体完整性', '关系模型有三类完整性约束：实体完整性、参照完整性和用户定义的完整性。其中实体完整性和参照完整性是关系模型必须满足的完整性约束，被称为关系的两个不变性，应由关系系统自动支持。实体完整性规则：若属性 A（一个或一组属性）是基本关系 R 的主属性，则 A 不能取空值。空值是“不知道”“不存在”或“无意义”的值。按照该规则，如果主码由若干属性组成，则所有这些属性都不能取空值。例如学生关系中“学号”为主码，学号取值唯一且不能为空；学生选课关系中主码为（学号,课程号），两个属性都不能为空。实体完整性针对基本关系而言，基本表通常对应现实世界的一个实体集；现实世界实体可区分，关系模型以主码作为唯一性标识。主码若取空值，就会出现不可标识的实体，与实体可区分性矛盾。', '认为主码只要唯一即可，允许取空值\\n认为实体完整性约束所有属性都不能为空\\n认为主码由多属性组成时只需其中一个非空', 'P42', 'schema', '6', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 2
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0207', 1, chapter_id, '参照完整性与外码（违约处理见第5章）', '参照完整性约束定义外码与主码之间的引用规则。设 F 是基本关系 R 的一个或一组属性，但不是 R 的码；K_s 是基本关系 S 的主码。若 F 与 K_s 相对应，则称 F 是 R 的外码，R 为参照关系，S 为被参照关系（目标关系）。R 与 S 不一定是不同的关系，F 与 K_s 必须定义在同一（或同一组）域上。参照完整性规则：对于 R 中每个元组在 F 上的值，或者取空值（F 的每个属性值均为空值），或者等于 S 中某个元组的主码值。外码不一定要与主码同名，例如“主修专业”引用“专业名”、“先修课”引用“课程号”。同一关系的内部属性间也可存在引用，如课程关系中的“先修课”引用本关系的“课程号”。参照完整性刻画的是关系与关系之间、或同一关系内部属性之间的引用约束。教材 P43—45 只给出上述规则与示例，未展开违约处理。', '认为外码必须与对应主码同名\\n认为数据库只有一个关系就不存在参照完整性\\n认为外码在任何情况下都不能取空值', 'P43', 'schema', '7', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 2
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0208', 1, chapter_id, '用户定义的完整性', '任何关系数据库系统都应支持实体完整性和参照完整性，这是关系模型本身的要求。除此之外，不同关系数据库根据应用场景不同，往往还需要一些特殊的约束条件。用户定义的完整性就是针对某一具体关系数据库的约束条件，它反映某一具体应用所涉及的数据必须满足的语义要求。例如，某个属性必须取唯一值，某个非主属性不能取空值；在学生关系中若要求学生必须有姓名，可定义“姓名”属性不能取空值；在学生选课关系中可定义“成绩”的取值范围为 0～100。用户定义的完整性是应用领域需要遵循的约束条件，体现了具体领域中的语义约束。关系模型应提供定义和检验这类完整性约束的机制，以便用统一的、系统的方法处理，而不需要由应用程序承担这一功能。教材本节未给出编号规则。', '把用户定义完整性当成关系模型的两个不变性之一\\n认为“成绩 0～100”属于实体完整性\\n认为完整性约束只能由应用程序检查', 'P45', 'schema', '8', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 2
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0209', 1, chapter_id, '传统集合运算（并、差、交、笛卡儿积）', '传统的集合运算是二目运算，包括并、差、交、笛卡儿积 4 种。设关系 R 和关系 S 具有相同的目 n，即两个关系都有 n 个属性，且相应属性取自同一个域；t 是元组变量。并：R∪S={t | t∈R∨t∈S}，结果仍为 n 目关系，由属于 R 或属于 S 的元组组成。差：R−S={t | t∈R∧t∉S}，由属于 R 而不属于 S 的元组组成。交：R∩S={t | t∈R∧t∈S}，由既属于 R 又属于 S 的元组组成；交可用差表示，R∩S=R−(R−S)。笛卡儿积是广义笛卡儿积：若 R 为 n 目、S 为 m 目，R×S 是 n+m 列的元组集合，前 n 列来自 R 的一个元组，后 m 列来自 S 的一个元组；若 R 有 k1 个元组、S 有 k2 个元组，则 R×S 有 k1×k2 个元组，记作 R×S={t_r t_s | t_r∈R∧t_s∈S}。', '认为交的结果元组数最多为两关系元组数之和\\n认为差运算会减少结果关系的列数\\n把笛卡儿积的列数误算为 n+m−公共属性数', 'P46', 'concept', '9', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 2
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0210', 1, chapter_id, '专门关系运算（选择、投影、连接、除）', '专门的关系运算包括选择、投影、连接、除。选择又称限制，σ_F(R)={t | t∈R∧F(t)=''真''}，F 是逻辑表达式，基本形式为 X1θY1，θ 为 >、≥、<、≤、=、<> 等比较运算符，并可用 ¬、∧、∨ 组合；选择从行的角度进行。投影 π_A(R)={t[A] | t∈R}，从列的角度进行；取消某些属性列后可能出现重复行，应取消重复元组。连接从两个关系的笛卡儿积中选取属性间满足条件的元组：R⋈S={t_r t_s | t_r∈R∧t_s∈S∧t_r[A]θt_s[B]}；θ 为“=”时是等值连接；自然连接要求比较分量是同名属性列，并在结果中去掉重复属性列。自然连接舍弃的元组称悬浮元组，保留悬浮元组则构成外连接（左外、右外）。除运算 R(X,Y)÷S(Y,Z) 的结果 P(X) 由满足“x 的象集 Y_x 包含 S 在 Y 上投影”的 x 组成，即 R÷S={t_r[X] | t_r∈R∧Π_Y(S)⊆Y_x}。8 种运算中并、差、笛卡儿积、选择、投影为基本运算，交、连接、除可由其导出。', '认为选择会减少属性列，投影会减少元组数\\n认为投影不必取消重复元组\\n把等值连接与自然连接等同，忽略去掉重复属性列', 'P46', 'concept', '10', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 2
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);


INSERT INTO course_chapter (course_id, chapter_no, title, sort_no) VALUES
(1, 3, '关系数据库标准语言SQL', 2)
ON DUPLICATE KEY UPDATE title = VALUES(title), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0301', 1, chapter_id, 'SQL 概述、特点与三级模式对应', 'SQL（结构化查询语言）是关系数据库的标准语言，包括数据查询、数据库模式创建、数据的增删改、安全性和完整性定义与控制等功能。1974 年由 Boyce 和 Chamberlin 提出，原名 SEQUEL，在 IBM 的 System R 上实现后改名为 SQL；1986 年 ANSI 批准 SQL 86 为美国标准，1987 年 ISO 通过。SQL 是非过程化语言，用户只需说明"要什么"，无须指明"怎么干"，存取路径由 DBMS 自动选择。特点有五：功能综合且风格统一，集数据定义、数据操纵、数据控制于一体，核心功能只用 9 个动词（CREATE、DROP、ALTER、SELECT、INSERT、UPDATE、DELETE、GRANT、REVOKE）；数据操纵高度非过程化；面向集合的操作方式；统一语法结构支持独立与嵌入式两种使用方式；语言简洁易学。SQL 支持三级模式：外模式对应视图和部分基本表，模式对应基本表，内模式对应存储文件。', '误认为 SQL 是过程化语言，用户必须指定存取路径\\n误认为 SQL 只含查询功能，不含数据控制\\n误认为嵌入式 SQL 与独立 SQL 语法差别很大', 'P67', 'concept', '1', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 3
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0302', 1, chapter_id, '数据定义：模式、基本表、索引、数据字典', '数据定义包括数据库模式、表、视图和索引的定义。模式：CREATE SCHEMA [<模式名>] AUTHORIZATION <用户名>；DROP SCHEMA <模式名><CASCADE|RESTRICT>，CASCADE 级联删除模式中全部对象，RESTRICT 在模式已有对象时拒绝删除。基本表：CREATE TABLE <表名>(<列名><数据类型>[列级完整性约束][，…][，<表级完整性约束>])，涉及多个属性列的约束必须定义在表级；主码加下划线，SC 的主码 (Sno,Cno) 必须用表级完整性约束定义。所属模式可由表名前缀或搜索路径（SHOW SEARCH_PATH）确定。修改用 ALTER TABLE 的 ADD、DROP、ALTER、RENAME 子句；删除用 DROP TABLE <表名>[RESTRICT|CASCADE]，默认 RESTRICT。索引：CREATE [UNIQUE][CLUSTER] INDEX <索引名> ON <表名>(<列名>[<次序>][,…])，默认 ASC；删除用 DROP INDEX。数据字典记录模式、视图、索引、约束与权限等定义。', '误以为 DROP 用于删除表中数据（删数据应用 DELETE）\\n误认为 SQL 标准有修改模式语句（只能先删再重建）\\n误把涉及多列的完整性约束定义在列级', 'P71', 'sql', '2', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 3
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0303', 1, chapter_id, '单表查询（WHERE、ORDER BY、聚合、分组）', '单表查询指仅涉及一个表的查询。SELECT 语句一般格式：SELECT [ALL|DISTINCT] <目标列表达式>[别名][,…] FROM <表名或视图名>[别名][,…] [WHERE <条件表达式>] [GROUP BY <列名1>[HAVING <条件表达式>]] [ORDER BY <列名2>[ASC|DESC]]。未指定 DISTINCT 默认 ALL。WHERE 常用条件：比较运算符 =、>、<、>=、<=、!=、<>；BETWEEN…AND…；IN；LIKE，其中 % 代表任意长度的字符串、_ 代表任意单个字符，匹配串含 % 或 _ 时用 ESCAPE 转义；IS NULL；AND、OR、NOT，AND 优先级高于 OR。ORDER BY 按列升序（默认 ASC）或降序（DESC）排列，不改变基本表存储。聚集函数 COUNT、SUM、AVG、MAX、MIN 可加 DISTINCT，除 COUNT(*) 外跳过空值。GROUP BY 按列值分组，分组后可用 HAVING 筛选组；WHERE 作用于元组，HAVING 作用于组，WHERE 中不能直接用聚集函数。', '在 WHERE 中直接用聚集函数作条件，如 AVG(Grade)>=90\\n用 = NULL 判断空值，正确写法是 IS NULL\\n误认为 AND 与 OR 优先级相同，漏加括号', 'P81', 'sql', '3', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 3
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0304', 1, chapter_id, '连接查询（等值、自然、外连接）', '若一个查询同时涉及两个以上的表，称为连接查询，包括等值、非等值、自然、自身、外连接、复合条件连接和多表连接查询。连接条件（连接谓词）格式为 [<表名1>.]<列名1><比较运算符>[<表名2>.]<列名2>，运算符主要有 =、>、<、>=、<=、!=(或<>)；连接字段类型必须可比，名字不必相同。比较运算符为"="的是等值连接；去掉结果表中重复属性列的等值连接为自然连接，如例 3.52 去掉 SC 的 Sno，两表同名的 Sno 引用时必须加表名前缀。一个表与其自身连接为自身连接，须取不同别名，如例 3.54 求间接先修课：SELECT FIRST.Cno,SECOND.Cpno FROM Course FIRST,Course SECOND WHERE FIRST.Cpno=SECOND.Cno AND SECOND.Cpno IS NOT NULL。外连接以空值填补未匹配元组：SELECT … FROM Student LEFT OUTER JOIN SC ON (Student.Sno=SC.Sno)；左外连接列出左边关系全部元组，右外连接列出右边关系全部元组。', '自然连接忘记去掉重复的连接属性列\\n自身连接未取两个别名，无法区分两个副本\\n误认为外连接只输出匹配成功的元组', 'P91', 'sql', '4', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 3
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0305', 1, chapter_id, '嵌套查询与相关子查询', '一个 SELECT-FROM-WHERE 语句称为一个查询块，将一个查询块嵌套在另一个查询块的 WHERE 或 HAVING 条件中称为嵌套查询；上层为父查询，下层为子查询。子查询的 SELECT 中不能使用 ORDER BY，只能对最终结果排序。子查询条件不依赖父查询的称为不相关子查询，由里向外分步求解；依赖父查询的称为相关子查询，整个语句称相关嵌套查询，必须反复求值，如例 3.59 求每个学生超过本人平均成绩的课程号，SC 的别名 x、y 称元组变量。带 IN 谓词的子查询最常用；内层返回单值时可用 >、<、=、>=、<=、!= 或 <>；返回多值须用 ANY（或 SOME）或 ALL，且必须同时使用比较运算符：=ANY 等价于 IN，<>ALL 等价于 NOT IN，<ANY 等价于 <MAX，<ALL 等价于 <MIN。EXISTS 代表存在量词 ∃，只产生 true/false，目标列通常用 *；NOT EXISTS 在内层为空时返回真。SQL 无全称量词，用 (∀x)P≡¬(∃x(¬P)) 转换，例 3.64 据此求选修全部课程的学生；蕴涵 p→q 等价于 ¬p∨q。', '在子查询的 SELECT 语句中写 ORDER BY\\nANY/ALL 未与比较运算符同时使用\\n求“全部课程”时不会用 NOT EXISTS 双重否定转换', 'P96', 'sql', '5', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 3
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0306', 1, chapter_id, '集合查询与基于派生表的查询', 'SELECT 的查询结果是元组的集合，可对多个 SELECT 结果做集合操作：并 UNION、交 INTERSECT、差 EXCEPT。参加操作的各结果列数必须相同，对应项数据类型也必须相同。UNION 合并时系统自动去掉重复元组，要保留重复元组则用 UNION ALL。例 3.70 用 EXCEPT 求计算机科学与技术专业学生与年龄不大于 19 岁学生的差集。子查询还可出现在 FROM 子句中，生成的临时派生表成为主查询的查询对象，例如：SELECT Sno,Cno FROM SC,(SELECT Sno,AVG(Grade) FROM SC GROUP BY Sno) AS Avg_SC(Avg_sno,Avg_grade) WHERE SC.Sno=Avg_SC.Avg_sno AND SC.Grade>=Avg_SC.Avg_grade。含聚集函数时必须指定派生表属性列名，否则以子查询 SELECT 的列名为默认属性。生成派生表时 AS 可省略，但必须指定别名；基本表别名可选。派生表是中间结果表，查询完成后由系统自动清除。', '集合操作两侧列数或对应列类型不一致\\n以为 UNION 会保留重复元组（应写 UNION ALL）\\n派生表未指定别名，或含聚集函数却省略列名', 'P105', 'sql', '6', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 3
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0307', 1, chapter_id, '数据更新（INSERT、UPDATE、DELETE）', 'SQL 数据更新包括插入、修改、删除三类语句。插入一个元组：INSERT INTO <表名>[(<属性列1>[，<属性列2>]…)] VALUES(<常量1>[，<常量2>]…)；INTO 中未出现的属性列取空值（有默认值则取默认值），但 NOT NULL 列不能取空值；INTO 未指明属性列时，VALUES 必须在每个属性列上均有值，次序同 CREATE TABLE。插入子查询结果可一次插入多个元组：INSERT INTO <表名>[(<属性列1>[，…])] 子查询，例 3.74 按专业分组求平均年龄存入新表。修改：UPDATE <表名> SET <列名>=<表达式>[，<列名>=<表达式>]… [WHERE <条件>]，省略 WHERE 表示修改所有元组；例 3.76 将某学期某课程成绩减 5 分。删除：DELETE FROM <表名> [WHERE <条件>]，省略 WHERE 删除全部元组，但表定义仍在字典中，即删除的是数据而不是表的定义。增删改可能破坏参照完整性。', 'INTO 未列出属性列，却漏给某些列指定值\\nUPDATE/DELETE 漏写 WHERE，误改误删全部元组\\n误以为 DELETE 会连同表定义一起删除', 'P108', 'sql', '7', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 3
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0308', 1, chapter_id, '空值处理与三值逻辑', '空值是"不知道""不存在"或"无意义"的值，产生于插入未赋值、更新置 NULL、外连接和空值参与运算。判断空值用 IS NULL 或 IS NOT NULL，不能用等号（=）代替。约束方面：NOT NULL 属性不能取空值，主码不能取空值；UNIQUE 属性可以取空值，能否有多条记录取空值 SQL 标准未规定，Oracle、KingbaseES、PostgreSQL 允许，SQL Server 不允许。运算方面：空值与另一个值（包括空值）的算术运算结果为空值，比较运算结果为 UNKNOWN。二值逻辑因此扩展为三值逻辑（表 3.7）：NULL AND FALSE 为 FALSE，NULL AND TRUE 为 UNKNOWN，NULL OR TRUE 为 TRUE，NULL OR FALSE 为 UNKNOWN，NOT UNKNOWN 为 UNKNOWN。查询中只有 WHERE、HAVING 条件为 TRUE 的元组才被选出，所以 Grade<60 不会选出缺考（Grade 为空值）的学生。', '用 WHERE X = NULL 判断空值（应为 IS NULL）\\n误认为 NULL 与 NULL 比较的结果为 TRUE\\n忽略 UNKNOWN，把三值逻辑当作二值逻辑', 'P111', 'boundary', '8', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 3
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0309', 1, chapter_id, '视图的定义、查询与更新', '视图是从一个或几个基本表（或视图）导出的表，是一个虚表；数据库中只存放视图定义，不存放对应数据。建立视图：CREATE VIEW <视图名>[(<列名>[，<列名>]…)] AS <子查询> [WITH CHECK OPTION]；子查询是否含 ORDER BY、DISTINCT 取决于系统实现；WITH CHECK OPTION 保证更新、插入、删除的行满足视图定义的谓词条件。组成视图的列名或全部省略或全部指定，必须指定列名的三种情况：目标列为聚集函数或表达式、多表连接出现同名列、需要启用新列名。例 3.86 的 IS_Student 是保留主码的行列子集视图；例 3.91 的 S_GradeAVG 是分组视图，必须指定列名。删除视图：DROP VIEW <视图名>[CASCADE]；基本表删除后视图定义仍在字典中，需显式删除。查询视图时系统从数据字典取出定义，与用户查询结合转换成等价的基本表查询，称视图消解；行列子集视图一般能正确转换，非行列子集视图（例 3.96）不一定。更新视图最终转换为对基本表的更新，一般地行列子集视图可更新；不可更新与不允许更新是两个概念。', '认为视图中存放了数据（实际只存视图定义）\\n认为所有视图都可更新（只有行列子集视图一般可更新）\\n对非行列子集视图直接消解，未改用 HAVING', 'P114', 'schema', '9', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 3
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0310', 1, chapter_id, '视图的作用', '视图定义在基本表之上，对视图的操作最终转换为对基本表的操作，非行列子集视图查询或更新时还可能出问题，但合理使用视图好处很多。作用有四：① 对机密数据提供安全保护。对不同用户定义不同视图，使机密数据不出现在不应看到这些数据的用户视图上；如 Student 表涉及全校 30 个院系，可定义 30 个视图，每个只含一个院系数据，只允许该院系主任查询和修改。② 对重构数据库提供一定程度的逻辑独立性。逻辑独立性指数据库重构时（如增加新关系或新字段）应用程序不受影响；把 Student 垂直分成 SX(Sno,Sname,Sbirthdate) 和 SY(Sno,Ssex,Smajor)，再定义视图 Student 为二者自然连接结果，逻辑结构改变而不必修改程序。③ 简化用户操作，把多表连接对用户隐蔽起来。④ 使用户能以多种角度看待同一数据。', '误认为视图能提供完全的逻辑独立性\\n把视图当作独立存储的表，忽视其虚表性质\\n只答“方便查询”，漏掉安全保护与多角度两点', 'P121', 'concept', '10', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 3
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);




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

-- ============================================================
-- [kp] seed-kp-ch04.sql
-- ============================================================
-- 智学领航 · 第4章知识点种子（由 csv_to_sql.py 生成）
-- 前置：init-v2.sql；可重复执行（upsert）。


INSERT INTO course_chapter (course_id, chapter_no, title, sort_no) VALUES
(1, 4, '数据库安全性', 3)
ON DUPLICATE KEY UPDATE title = VALUES(title), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0401', 1, chapter_id, '数据库安全性概述、不安全因素与安全标准', '数据库安全性是指保护数据库，防止不合法使用造成数据泄露、篡改或破坏。威胁主要来自三类：非授权用户恶意存取和破坏，如窃取口令、假冒合法用户、锁定数据勒索；重要或敏感数据被泄露，如 SQL 注入在查询语句末尾追加语句，骗取数据库执行非授权查询；安全环境脆弱，硬件、操作系统、网络协议的安全缺陷都会波及数据库。计算机系统安全问题分技术安全、管理安全、政策法律三类，本章只讨论技术安全。安全标准方面，TCSEC 与 TCSEC/TDI 把系统分为 D、C1、C2、B1、B2、B3、A1 四组七级，可信程度递增；C2 支持自主存取控制与审计，B1 增加标记与强制存取控制，是真正意义上的安全产品。CC 2.1 于 1999 年成为 ISO 国际标准、2001 年成为我国国家标准，用 EAL1—EAL7 评估保证级衡量保证程度。', '①把数据库安全性等同于完整性，忽视“防非法存取”与“防不合语义数据”的区别\\n②只把威胁理解为黑客攻击，漏掉 SQL 注入、环境脆弱和内部人员\\n③认为安全级别越高越好，不考虑成本与可用性\\n④把 TCSEC 的 D 级当作最高级，记反等级顺序', 'P127', 'concept', '1', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 4
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0402', 1, chapter_id, '用户身份鉴别', '用户身份鉴别是 DBMS 提供的最外层安全保护措施。每个用户在系统中都有用户标识，由用户名和用户标识号（UID）组成，UID 在系统整个生命周期内唯一；系统内部记录所有合法用户标识，用户进入系统时由系统核对，通过后才提供使用权限。常用方法有五类：静态口令鉴别简单但易被攻击，通常配合双因子（口令+数字证书）、口令复杂度/过期/锁定/历史策略，并以密文存储传输；动态口令一次一密，验证码、动态令牌安全性更高；生物特征鉴别用指纹、虹膜、掌纹、人脸等；智能卡内置芯片、不可复制，但读取数据是静态的，可能被内存扫描或网络监听截取，实际常与 PIN 结合；入侵检测按预设规则实时分析并处理，如邮件报警、断开会话、锁定用户。', '①把身份鉴别当成存取控制，认为登录成功就拥有所有权限\\n②认为静态口令足够安全，忽略双因子与口令策略\\n③认为智能卡不可复制就绝对安全，忽略静态数据可被截取\\n④把入侵检测当成事前预防，忽略它是实时检测与处置', 'P132', 'concept', '2', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 4
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0403', 1, chapter_id, '存取控制与自主存取控制', '存取控制是数据库安全控制的核心，确保只授权有资格的用户访问数据，未被授权者无法接近数据。机制由两部分组成：一是定义用户权限并把权限登记到数据字典，形成安全规则或授权规则；二是合法权限检查，用户每次请求（操作类型、对象、用户）时查数据字典，超出权限则拒绝。自主存取控制（DAC）中，不同用户对不同对象有不同权限，同一对象对不同用户也有不同权限，且用户可把自己拥有的权限转授他人，因而灵活；强制存取控制（MAC）中，每个对象标密级、每个用户发许可证，只有合法许可证才能存取，因而严格。C2 级 DBMS 支持 DAC，B1 级 DBMS 支持 MAC。关系数据库中授权对象不仅包括数据（基本表、视图、属性列），还包括数据库、模式、基本表、视图、索引的创建等。', '①把存取控制等同于身份鉴别\\n②认为 DAC 中任何用户都能转授权，忽略 WITH GRANT OPTION\\n③认为 C2 级已支持强制存取控制\\n④认为权限只作用于表中的数据，忽略模式、表、视图、索引等对象', 'P133', 'concept', '3', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 4
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0404', 1, chapter_id, 'GRANT 授权与 WITH GRANT OPTION', 'SQL 用 GRANT 授予权限，一般格式为 GRANT <权限>[,<权限>] ON <对象类型><对象名>[,<对象类型><对象名>] TO <用户>[,<用户>] [WITH GRANT OPTION]。语义是把指定对象的指定操作权限授予指定用户。发出者可以是 DBA、数据库属主，或已拥有该权限的用户；接受者可以是一个或多个具体用户，也可以是 PUBLIC（全体用户）。指定 WITH GRANT OPTION 后，被授权者还能把权限再授予他人；未指定则只能使用、不能传播。SQL 标准允许传递权限或其子集，但不允许循环授权，被授权者不能把权限授回授权者或其祖先。对属性列授权必须写明列名，如 GRANT UPDATE(Sno), SELECT ON TABLE Student TO U4；给列 INSERT 权限时必须包含主码列的 INSERT 权限，否则插入会因主码为空被拒绝。', '①认为 GRANT 默认带 WITH GRANT OPTION\\n②认为被授权者可以把权限授回授权者，形成循环\\n③对属性列授权不写列名\\n④认为给列 INSERT 权限可以不包含主码列', 'P135', 'sql', '4', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 4
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0405', 1, chapter_id, 'REVOKE 收回与级联收回', 'REVOKE 用于收回已授予的权限，一般格式为 REVOKE <权限>[,<权限>] ON <对象类型><对象名>[,<对象类型><对象名>] FROM <用户>[,<用户>] [CASCADE|RESTRICT]。指定 CASCADE 时级联收回被授权者转授出去的权限；指定 RESTRICT 时，若权限已被转授则不能收回；默认值为 RESTRICT。例：U5 把 SC 表 INSERT 权限授予 U6，U6 又授予 U7；REVOKE INSERT ON TABLE SC FROM U5 CASCADE 会同时收回 U6、U7 的 INSERT 权限。若 U6 或 U7 还从其他用户处获得同一权限，则他们仍保留该权限，系统只收回直接或间接从 U5 处获得的部分。DBA 可以收回其授予的权限，用户也可以收回自己授出的权限。', '①认为 REVOKE 默认级联收回\\n②认为级联收回会把被授权者从其他来源获得的同一权限也收回\\n③认为收回角色等于删除角色\\n④认为 RESTRICT 与 CASCADE 只是语法差异，结果相同', 'P137', 'sql', '5', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 4
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0406', 1, chapter_id, '数据库角色', '数据库角色是被命名的一组与数据库操作相关的权限，本质是权限的集合。为一组具有相同权限的用户创建一个角色，可以简化授权过程。SQL 中先用 CREATE ROLE <角色名> 创建空角色；再用 GRANT <权限> ON <对象类型><对象名> TO <角色> 给角色授权；用 GRANT <角色> TO <用户或角色> [WITH ADMIN OPTION] 把角色授予用户或其他角色，角色包含直接授予它的权限加上其他角色授予它的权限；指定 WITH ADMIN OPTION 后，获得者还能把角色再授予他人；用 REVOKE <权限> FROM <角色> 收回角色权限，或用 REVOKE <角色> FROM <用户> 收回用户的角色。例 4.14 用 R1 一次性给王平、张明、赵玲授予 Student 表三个权限，再 REVOKE R1 FROM 王平 一次收回。', '①把角色当成一个数据库用户\\n②认为授予角色默认带 WITH ADMIN OPTION\\n③认为修改角色权限不会影响已授予该角色的用户\\n④认为收回用户的角色会删除角色本身', 'P139', 'sql', '6', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 4
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0407', 1, chapter_id, '强制存取控制（MAC）', '强制存取控制是系统按 TCSEC/TDI 安全策略要求实施的强制存取检查，用户不能直接感知或控制，适用于对数据严格且固定密级分类的部门，如军事、政府部门。DBMS 管理的实体分为主体（活动实体：实际用户及代表用户的进程）和客体（被动实体：文件、基本表、索引、视图等），每个实例被指派敏感度标记。主体标记称许可证级别，客体标记称密级，级别如绝密 TS、机密 S、秘密 C、公开 P，次序 TS>=S>=C>=P。存取规则有两条：仅当主体许可证级别大于或等于客体密级时才能读；仅当主体许可证级别小于或等于客体密级时才能写。写规则可防止高密级数据降密后写回造成泄露。实现 MAC 时要先实现 DAC，DAC 与 MAC 共同构成 DBMS 安全机制，系统先做 DAC 检查，通过后再做 MAC 检查。', '①认为 MAC 可以替代 DAC\\n②把读写规则记成同一方向，认为高许可证可写低密级\\n③认为用户可以自主决定自己的许可证级别\\n④忽略“先 DAC 后 MAC”的检查顺序', 'P141', 'concept', '7', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 4
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0408', 1, chapter_id, '视图机制', '视图具有安全保护功能。为不同用户定义不同视图，可以把数据对象限制在一定范围内，把保密数据对无权存取的用户隐藏起来，从而自动提供一定程度的安全保护。视图还能与授权机制配合，间接实现支持存取谓词的用户权限定义。例如王平老师只能查询计算机科学与技术专业学生信息，张明老师具有该专业学生信息的查询和增删改权限；在不直接支持存取谓词的系统中，可先建立视图 CS_Student（SELECT * FROM Student WHERE Smajor=''计算机科学与技术'' WITH CHECK OPTION），再在视图上分别授予王平 SELECT、授予张明 ALL PRIVILEGES。WITH CHECK OPTION 保证对该视图进行增删改时必须满足视图定义中的条件。', '①把视图当成独立存储的物理表\\n②认为授予视图权限等于授予基本表的全部权限\\n③把 WITH CHECK OPTION 当成授权选项\\n④认为视图能提供完全的逻辑独立性', 'P142', 'schema', '8', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 4
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0409', 1, chapter_id, '审计', '审计是 DBMS 提供的事后检查安全机制，是达到 C2 以上安全级别必不可少的指标。它把用户对数据库的所有操作自动记录到审计日志，审计员可据此监控行为、重现导致数据库现状的事件、找出非法存取的人/时间/内容，并分析潜在威胁。审计费时费空间，通常作为可选特征，由 DBA 按安全需求开关，主要用于安全要求较高的部门；既能审计成功操作，也能审计失败操作。审计事件分服务器事件、系统权限、语句事件、模式对象事件四类。审计功能包括多种查阅方式、多套审计规则、审计分析与报表、审计日志管理：先转储后删除，只允许审计员查阅和转储，禁止普通用户新增修改。AUDIT 设置审计，NOAUDIT 取消；BY ACCESS 记录每次操作，BY SESSION 每次会话只记录最早一次。用户级审计任何用户可设，系统级审计只能由 DBA 设置。', '①认为审计能事前阻止非法操作，忽略它是事后检查\\n②认为审计日志任何用户都能修改\\n③认为审计默认常开，忽略其时间空间成本\\n④把 BY ACCESS 与 BY SESSION 记反', 'P143', 'concept', '9', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 4
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0410', 1, chapter_id, '数据加密（存储加密与传输加密）', '数据加密是防止数据在存储和传输中失密的有效手段，把明文按算法变换为不可直接识别的密文。存储加密分透明与非透明：透明存储加密是内核级、对用户隐蔽，建表时说明加密字段即可，应用无需修改；非透明存储加密通过多个加密函数实现。传输加密常用链路加密与端到端加密：链路加密对报头和报文都加密，需要中间节点密码设备；端到端加密只在发送端加密、接收端解密，只加密报文不加密报头，所需密码设备较少，但报头可能被监听。SSL 是端到端方案，包含创建可信连接、确认端点可靠性（CA 证书与 CRL）、协商算法与密钥（PKI）、可信传输、关闭连接，会话密钥仅限本次通信。加密会增加查询处理复杂性并影响效率，密钥管理也是需要考虑的问题。', '①认为加密可以完全消除数据泄露风险\\n②认为端到端加密也加密报头\\n③认为链路加密不需要中间节点密码设备\\n④忽略加密对查询效率与密钥管理的影响', 'P145', 'boundary', '10', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 4
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0411', 1, chapter_id, '其他安全性保护（推理控制、隐蔽信道、数据隐私、三权分立）', '其他安全性保护针对自主存取控制与强制存取控制之外或未解决的问题。推理控制防止用户利用属性间的函数依赖、敏感关联等，从可访问的低密级信息推导出无权访问的高密级信息；例如职务→工资的函数依赖，同级职务用户可由自己的工资推出他人工资。隐蔽信道防止高安全等级用户利用系统机制向低安全等级用户传递信息，例如利用 UNIQUE 约束列的插入成功/失败约定信息位，使信息从高密级流向低密级。数据隐私控制个人数据在收集、存储、处理、发布各阶段不被非法获取；发布时先去掉候选码并考虑准标识符，常用 k 匿名（相同准标识符组至少 k 条记录）、l 多样性、t 临近等标准。三权分立按 GB/T 20273—2019 把数据库管理员过于集中的权限拆分，避免超级用户不受检查。', '①把推理控制当成强制存取控制的一部分\\n②认为隐蔽信道只存在于网络层\\n③把 k 匿名理解成“每组共 k 条记录”\\n④认为三权分立只是把 DBA 改个名字', 'P147', 'boundary', '11', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 4
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

-- ============================================================
-- [kp] seed-kp-ch05.sql
-- ============================================================
-- 智学领航 · 第5章知识点种子（由 csv_to_sql.py 生成）
-- 前置：init-v2.sql；可重复执行（upsert）。


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

-- ============================================================
-- [kp] seed-kp-ch06.sql
-- ============================================================
-- 智学领航 · 第6章知识点种子（由 csv_to_sql.py 生成）
-- 前置：init-v2.sql；可重复执行（upsert）。


INSERT INTO course_chapter (course_id, chapter_no, title, sort_no) VALUES
(1, 6, '关系数据理论', 6)
ON DUPLICATE KEY UPDATE title = VALUES(title), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0601', 1, chapter_id, '问题的提出与数据依赖', '关系数据库逻辑设计要解决两个问题：什么样的关系模式是“好”的，以及如何把“不好”的模式改造为“好”的模式。关系模式可形式化表示为 R(U,F)，U 为属性集，F 为 U 上的一组数据依赖。数据依赖是属性间通过值相等与否体现的约束，最重要的是函数依赖和多值依赖。以例6.1 Student(Sno,School,Mname,Cno,Grade) 为例，F={Sno→School,School→Mname,(Sno,Cno)→Grade}，该模式存在数据冗余、更新异常、插入异常、删除异常；原因是函数依赖存在“不好”的性质。好模式应尽量避免这些异常、减少冗余；改造方法是模式分解，如分解为 S(Sno,School)、SC(Sno,Cno,Grade)、SCHOOL(School,Mname)。', '①把数据冗余当成唯一问题，忽略插入/删除/更新异常\\n②认为范式越高越好，不考虑应用语义与查询需求\\n③把函数依赖当成某个具体关系实例的约束\\n④认为模式分解一定无损，忽略分解准则', 'P171', 'concept', '1', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 6
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0602', 1, chapter_id, '函数依赖', '函数依赖定义：对 R(U,F) 的任意可能关系 r，不可能存在两个元组在 X 上值相等而在 Y 上值不等，称 X 函数决定 Y，记 X→Y。函数依赖是语义范畴，只能由语义确定，且要求 R 的所有关系实例都满足，不是某一个实例满足。术语：X→Y 且 Y⊄X 为非平凡函数依赖；Y⊆X 为平凡函数依赖；X 为决定因素。完全函数依赖：X→Y 且 X 的任何真子集 X'' 都有 X''↛Y；否则为部分函数依赖。传递函数依赖：X→Y、Y→Z，且 Y⊄X、Y≠X、Z⊄Y，记 X→Z（传递）。', '①认为函数依赖可由表中数据统计得出，忽略语义\\n②把部分函数依赖当成完全函数依赖\\n③把平凡函数依赖当成有信息量的依赖\\n④认为 X→Y、Y→Z 一定有 X→Z，忽略传递依赖的附加条件', 'P174', 'concept', '2', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 6
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0603', 1, chapter_id, '码', '码用函数依赖定义：设 K 为 R(U,F) 的属性或属性组，若 K→U，则 K 为候选码；若 U 部分函数依赖于 K（K→U 但非完全），K 为超码。候选码的超集一定是超码，候选码的任何真子集一定不是超码。候选码多于一个时选一个为主码；包含在任一候选码中的属性为主属性，否则为非主属性；整个属性组是码时称为全码。若 X 不是 R 的码但是另一个关系模式的码，则 X 是 R 的外码。例：Student 的 Sno 是码；SC 的 (Sno,Cno) 是码，Sno 是外码；R(P,W,A) 全码 (P,W,A)。', '①把候选码与超码混为一谈\\n②认为主属性只属于主码\\n③认为全码不是码\\n④求码时只看单个属性，忽略属性组', 'P176', 'concept', '3', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 6
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0604', 1, chapter_id, '范式与 2NF', '1NF：关系每个分量都是不可分的数据项。2NF：R∈1NF，且每个非主属性都完全函数依赖于任一候选码。例6.4 S-L-C(Sno,School,Sloc,Cno,Grade)，码 (Sno,Cno)，但 Sno→School、Sno→Sloc，School、Sloc 是部分函数依赖，故 S-L-C 不是 2NF；会产生插入异常（新生未选课无法插入）、删除异常（删除课程连带学生信息）、修改复杂（转学院要改多条）。分解为 SC(Sno,Cno,Grade) 和 S-L(Sno,School,Sloc) 后，非主属性对码完全函数依赖。', '①认为 1NF 已足够，忽略部分函数依赖\\n②把 2NF 说成“每个属性都完全依赖码”\\n③认为 2NF 一定消除所有异常\\n④分解时只按属性个数拆，不按依赖关系拆', 'P177', 'schema', '4', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 6
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0605', 1, chapter_id, '3NF', '3NF：R∈1NF，若 R 中不存在码 X、属性组 Y 及非主属性 Z（Z⊄Y），使 X→Y、Y→Z 成立且 Y⊄X，则 R∈3NF（等价强化：若 Y 是码，则不属于此禁止项）。等价地，3NF 要求每个非主属性既不传递依赖于码，也不部分依赖于码；因此 3NF 一定 2NF。例6.4 S-L(Sno,School,Sloc) 中 Sno→School、School→Sloc，Sloc 传递依赖于 Sno，故 S-L 不是 3NF；分解为 S-SS(Sno,School) 与 S-SL(School,Sloc) 后消除传递依赖。', '①认为 3NF 只要求没有部分依赖\\n②认为 3NF 一定 BCNF\\n③把主属性之间的传递依赖也当成 3NF 禁止项\\n④分解后不检查新模式的范式', 'P178', 'schema', '5', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 6
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0606', 1, chapter_id, 'BCNF', 'BCNF：R∈1NF，若 X→Y 且 Y⊄X 时 X 必含有码，则 R∈BCNF；即每个决定因素都包含码。BCNF 满足：所有非主属性对每个码完全函数依赖；所有主属性对不包含它的码完全函数依赖；没有任何属性完全函数依赖于非码属性组。BCNF 一定 3NF，但 3NF 不一定 BCNF。例 STJ(S,T,J)，F={(S,J)→T,(S,T)→J,T→J}，(S,J)、(S,T) 是候选码，STJ 是 3NF；但 T 是决定因素且不含码，故不是 BCNF。STJ 可分解为 ST(S,T) 与 TJ(T,J)，均为 BCNF。', '①认为 3NF 与 BCNF 等价\\n②只检查非主属性，漏掉主属性对码的依赖\\n③认为 BCNF 分解一定保持函数依赖\\n④把“决定因素含码”误写成“右部含码”', 'P179', 'schema', '6', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 6
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0607', 1, chapter_id, '多值依赖与 4NF', '多值依赖：设 X、Y、Z 是 U 的子集，Z=U-X-Y。若对任一关系 r，给定一对 (x,z) 值，Y 的值仅由 x 决定而与 z 无关，则 X→→Y。若 Z=∅，称为平凡多值依赖。函数依赖是多值依赖的特例：X→Y 则 X→→Y。例 Teaching(C,T,B) 中课程 C 决定一组教师 T，也决定一组参考书 B，C→→T、C→→B；该模式全码 (C,T,B)，属于 BCNF，但增删教师/参考书要动多个元组。4NF：R∈1NF，若对每个非平凡且非函数依赖的多值依赖 X→→Y，X 都含有码，则 R∈4NF。Teaching 可分解为 CT(C,T) 与 CB(C,B) 消去非平凡 MVD。多值依赖性质：对称性（X→→Y 则 X→→Z，Z=U-X-Y）、传递性（X→→Y、Y→→Z 则 X→→Z-Y）、合并/交/差；函数依赖 X→Y 可推出 X→→Y。', '①把多值依赖当成函数依赖\\n②认为 BCNF 一定没有异常\\n③把平凡多值依赖当成需要消除的依赖\\n④认为 4NF 条件只要求 X 含属性即可', 'P180', 'schema', '7', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 6
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0608', 1, chapter_id, '函数依赖公理系统与闭包', 'Armstrong 公理系统：A1 自反律（若 Y⊆X⊆U，则 X→Y）、A2 增广律（若 X→Y 且 Z⊆U，则 XZ→YZ）、A3 传递律（若 X→Y、Y→Z，则 X→Z）。由 A1—A3 可推出合并规则（X→Y、X→Z 则 X→YZ）、伪传递规则（X→Y、WY→Z 则 XW→Z）、分解规则（X→Y、Z⊆Y 则 X→Z）。F 的逻辑蕴涵全体称 F 的闭包 F+；X 关于 F 的闭包 X+ = {A | X→A 能由 F 导出}。判定 X→Y 是否可由 F 导出，只需求 X+ 并判断 Y⊆X+。算法6.1 迭代求 X+，最多 |U|-|X| 次循环。Armstrong 公理有效且完备。', '①把自反律当成依赖 F 才成立\\n②认为 F+ 可以手工全部列出\\n③求闭包时只扫一遍函数依赖\\n④把“导出”与“蕴涵”当成两套不同语义', 'P185', 'concept', '8', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 6
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0609', 1, chapter_id, '最小依赖集与保持函数依赖的取舍', '若 F+ = G+，称 F 与 G 等价（F 覆盖 G）。F 是最小依赖集（最小覆盖）当且仅当：①每个函数依赖右部只含一个属性；②F 中不存在 X→A 使 F 与 F-{X→A} 等价；③F 中不存在 X→A 及 X 的真子集 Z 使 F-{X→A}∪{Z→A} 与 F 等价。每个 F 都等价于一个最小依赖集，但最小依赖集不唯一。保持函数依赖的分解：分解后的各子模式投影之并 F+ 等于原 F+。算法6.2（合成法）先把 F 极小化，去掉不出现在 F 中的属性，按相同左部原则分组，得到保持函数依赖的 3NF 分解。选择合成法时还要与无损连接目标对照：保持函数依赖不必然无损，不能把任一准则当成唯一标准。', '①认为最小依赖集唯一\\n②只检查右部单属性，忽略左部冗余\\n③认为任意分解都保持函数依赖\\n④把保持函数依赖与无损连接当成同一准则', 'P188', 'boundary', '9', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 6
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0610', 1, chapter_id, '无损连接定义、表算法与定理6.5', '无损连接分解 ρ={R1,...,Rk} 要求对原关系模式的任一关系 r，各子模式投影的自然连接都能恢复 r。算法6.3用表算法判别：建立k行n列表，属性属于子模式填aj，否则填bij；逐个FD寻找左部列符号相同的行，若右部列有aj则统一为aj，否则统一为最小行号对应的bmj，整列相同b符号同步替换，重复扫描到不动点，出现全a行即无损。两关系分解可用定理6.5：若U1∩U2→U1-U2或U1∩U2→U2-U1属于F+，则分解无损。无损连接与保持函数依赖是不同准则。', '①把无损连接理解为“不丢属性”\\n②认为保持函数依赖一定无损\\n③直接按定义逐个关系实例验证\\n④表算法只扫描一遍就下结论\\n⑤把定理6.5的交集—差集方向写反', 'P191', 'schema', '10', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 6
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0611', 1, chapter_id, '3NF/BCNF/4NF无损分解算法（算法6.4—6.6）', '算法6.4在算法6.2的3NF保持FD分解上加入原模式候选码关系R*(X)，再按包含关系删去冗余子模式，得到既无损又保持FD的3NF分解。算法6.5从原模式出发，对违反BCNF的X→A且X非码，把R分解为XA与U-A并递归，直至全部BCNF；它保持无损但不保证保持FD。定理6.6指出X→→Y成立的充要条件是R1(X,Y)与R2(X,Z)无损，其中Z=U-X-Y。算法6.6先用算法6.5达到BCNF，再按定理6.6处理非4NF子模式，直至全部4NF。三种算法的目标、输入与保证不同，不能混用。', '①算法6.4忘记加入候选码关系或删错子模式\\n②认为算法6.5既无损又必然保持FD\\n③把算法6.6当成单纯BCNF分解\\n④把定理6.6中的Z误写成U-X或U-Y', 'P193', 'schema', '11', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 6
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

-- ============================================================
-- [kp] seed-kp-ch07.sql
-- ============================================================
-- 智学领航 · 第7章知识点种子（由 csv_to_sql.py 生成）
-- 前置：init-v2.sql；可重复执行（upsert）。


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

-- ============================================================
-- [kp] seed-kp-ch08.sql
-- ============================================================
-- 智学领航 · 第8章知识点种子（由 csv_to_sql.py 生成）
-- 前置：init-v2.sql；可重复执行（upsert）。


INSERT INTO course_chapter (course_id, chapter_no, title, sort_no) VALUES
(1, 8, '数据库编程', 8)
ON DUPLICATE KEY UPDATE title = VALUES(title), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0801', 1, chapter_id, 'SQL表达能力的限制与数据库编程任务', '数据库编程旨在弥补SQL高度非过程化、缺少流程控制的弱点。教材用4个任务说明SQL表达能力的限制：任务1打印“数据库系统概论”的全部直接与间接先修课，需要递归查询；任务2打印一周内将过生日的学生，需要日期函数；任务3按成绩与绩点对照表计算平均学分绩点GPA，需要自定义业务逻辑；任务4由学生提交课程评价、教师回复，需要交互界面。扩展SQL表达能力有两类途径：一是扩展SQL自身功能，包括引入新的SQL子句（如WITH RECURSIVE）、新的内置函数，以及引入PL/SQL与存储过程/存储函数；二是通过高级语言实现复杂应用，包括动态链接库调用、嵌入式SQL和ODBC/JDBC中间件。', '①把数据库编程等同于书写SELECT查询，忽视流程控制与交互需求\\n②认为基本SQL本身就能完成递归、日期计算和复杂业务逻辑\\n③只知道存储过程一种扩展途径，遗漏WITH子句、内置函数与宿主语言\\n④把ODBC、JDBC当成某种数据库产品，而不是访问数据库的接口标准', 'P241', 'concept', '1', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 8
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0802', 1, chapter_id, 'WITH子句', 'WITH子句用于创建一个命名的临时结果集，该结果集仅在当前SELECT、INSERT或DELETE等SQL语句执行期间有效，不长期存储，可提高SQL语句的性能和可读性。语法为WITH RS1[(目标列,...)]AS(SELECT语句1)[,RS2[(目标列,...)]AS(SELECT语句2),...] SQL语句；RS1、RS2是临时结果集名，其目标列必须与对应SELECT语句的目标列保持一致，后续SQL语句可以引用这些结果集。例8.1用两个CTE分别求教学班81001-01与81001-02的平均成绩，再在外层查询中相减，得到两个教学班平均成绩的差异。WITH子句把复杂查询拆成命名步骤，避免重复书写子查询。', '①认为WITH建立的临时结果集会长期保存在数据库中\\n②把CTE声明的目标列与内部SELECT的目标列写成不一致\\n③认为WITH只能用于SELECT，不能用于INSERT或DELETE\\n④把WITH子句本身当成递归查询', 'P245', 'sql', '2', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 8
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0803', 1, chapter_id, 'WITH RECURSIVE子句', 'WITH RECURSIVE子句是WITH子句的特殊形式，用于查询具有层次结构的数据。它由种子查询和递归查询两部分组成，中间用UNION或UNION ALL连接：种子查询初始化临时结果集，得到第一层数据L[1]；递归查询以L[1]为基础查找第二层L[2]，再以L[2]查找L[3]，直到某一层为空。递归查询通过当前层某属性与基表另一属性匹配来确定下一层数据，UNION ALL保留重复记录。例8.2以“数据库系统概论”的直接先修课为种子，逐层查找先修课，输出81002数据结构和81001程序设计基础与C语言；同一课程存在多门直接先修课时同样适用。', '①把种子查询与递归查询的顺序颠倒\\n②认为递归查询一次就能得到全部层次\\n③使用UNION ALL却不做去重或深度控制，遇到重复路径可能无法终止\\n④认为WITH RECURSIVE只能处理单一路径的树形结构', 'P245', 'sql', '3', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 8
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0804', 1, chapter_id, 'SQL内置函数扩展', '关系数据库管理系统提供丰富的内置函数，是扩展SQL功能的途径之一。常用内置函数可分为数学函数（如绝对值）、聚合函数（如求和、求平均）、字符串函数（如求长度、求子串）、日期和时间函数（如返回当前日期）、格式化函数、控制流函数、加密函数和系统信息函数等。例8.3打印一周内将过生日的学生：用current_date取当前日期，用to_char取当前年份和学生出生月日，用to_date把拼接后的字符串转成日期，再用current_date + interval ''7'' day得到七天后日期，最后用BETWEEN判断转换后的生日是否落在[当前日期,当前日期+7天]区间。不同DBMS的函数名与语法存在差异。', '①认为内置函数不属于SQL扩展\\n②把interval ''7'' day理解成固定七天后的同一时刻而不是日期偏移\\n③认为to_char与to_date在所有DBMS中同名同义\\n④忽略字符串拼接后必须转回日期类型才能参与日期比较', 'P247', 'sql', '4', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 8
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0805', 1, chapter_id, '嵌入式SQL与SQLCA、主变量、游标', '通过高级语言实现复杂应用是扩展SQL能力的第二类途径，主要有动态链接库调用、嵌入式SQL和ODBC/JDBC中间件等方式。嵌入式SQL把SQL语句嵌入宿主语言（主语言），DBMS一般采用预编译方法，把SQL语句转换成主语言的库函数调用，再由主语言编译器编译。数据库工作单元与源程序工作单元通过SQL通信区、主变量和游标通信：SQLCA向主语言传递SQL执行状态，其中SQLCODE存放每次执行SQL后的返回码；主变量分输入主变量和输出主变量，可附带指示变量表示空值或被截断，必须在BEGIN DECLARE SECTION与END DECLARE SECTION之间说明，SQL语句中主变量名前加冒号；游标用于协调SQL面向集合与高级语言面向记录的处理方式。', '①认为嵌入式SQL由主语言编译器直接编译，不需要预编译\\n②把SQLCODE当成异常对象而不是SQL执行返回码\\n③认为主变量可以直接使用无需说明，或在SQL语句中引用时不加冒号\\n④把游标当成一条SQL查询语句而不是存放结果集的缓冲区', 'P248', 'concept', '5', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 8
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0806', 1, chapter_id, '过程化SQL块结构与变量常量', '过程化SQL是对基本SQL的扩展，增加了过程控制语句，其程序基本结构是块，块与块之间可以嵌套。块由定义部分DECLARE、执行部分BEGIN…END和异常处理部分EXCEPTION组成：DECLARE中定义变量、常量、游标、异常等，作用域仅限该基本块；BEGIN后写SQL语句与流程控制语句；EXCEPTION在遇到不能继续执行的情况时采取措施纠正错误或报告。变量定义格式为“变量名 数据类型[[NOT NULL]:=初值表达式]”，常量定义格式为“常量名 数据类型 CONSTANT:=常量表达式”，常量必须赋初值且作用域内不能修改，否则产生异常；赋值语句格式为“变量名:=表达式”。', '①认为DECLARE中定义的变量在整个程序中都有效\\n②常量定义后仍随意赋值\\n③混淆赋值运算符:=与比较运算符=\\n④认为异常处理部分可以省略，异常会自动被忽略', 'P250', 'concept', '6', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 8
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0807', 1, chapter_id, '过程化SQL流程控制', '过程化SQL提供条件控制与循环控制语句，语法语义类似C语言，但使用时需参考具体产品手册。条件控制有三种IF形式：IF-THEN（条件为真时执行，假或NULL时什么也不做）、IF-THEN-ELSE（二选一执行）以及可嵌套的IF。循环控制有三种：LOOP（循环体反复执行，依靠EXIT、BREAK或LEAVE等语句在适当条件下提前结束）、WHILE-LOOP（先判断条件，为真才执行循环体）、FOR-LOOP（计数循环，格式FOR count IN [REVERSE] bound1..bound2 LOOP…END LOOP，指定REVERSE时count从上界递减到下界）。执行出现异常时应让程序在异常语句处停下，按异常类型执行异常处理。', '①认为LOOP会自行按条件终止\\n②把WHILE-LOOP当成先执行循环体再判断条件\\n③混淆FOR-LOOP指定REVERSE时的上下界与步长方向\\n④把EXIT WHEN与普通IF条件混为一谈，或忽略异常处理', 'P251', 'concept', '7', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 8
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0808', 1, chapter_id, '游标的定义与使用', '过程化SQL中，SELECT只返回一条记录时可将结果直接存入变量；返回多条记录时必须使用游标逐行处理。一个游标与一个SQL语句关联，使用分四步：①DECLARE声明游标，格式为“DECLARE 游标名[(参数...)] CURSOR FOR SELECT语句”，此步只作说明，不执行SELECT；②OPEN打开游标，实际执行SELECT并把查询结果取入缓冲区，指针指向结果集第一条记录；③FETCH推进游标指针并取出当前记录到变量，变量必须与SELECT目标列一一对应，通常循环执行FETCH逐条处理；④CLOSE关闭游标，释放结果集占用的缓冲区及其他资源。关闭后游标不再与原结果集相联系，但可再次OPEN与新的查询结果相联系。例8.4按给定学号用游标输出该生全部选课记录。', '①认为执行DECLARE时就执行了SELECT\\n②OPEN后指针指向第一条记录，却说成指向记录之前\\n③FETCH变量个数或类型与SELECT目标列不匹配\\n④认为CLOSE后游标永久失效、不能再次打开', 'P253', 'sql', '8', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 8
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0809', 1, chapter_id, '存储过程与存储函数', '过程化SQL程序可以被命名、编译并保存在数据库中，形成存储过程或存储函数，二者都是持久性存储模块。创建存储过程用CREATE OR REPLACE PROCEDURE 过程名(参数...) AS 过程化SQL块，参数有IN、OUT、INOUT三种模式，缺省为IN；输入参数调用时须指定值，输出参数调用时不传入值而作为返回值，输入输出参数传入初值并返回最终值。过程体是过程化SQL块，含声明部分和可执行部分。用CALL或PERFORM激活，可用ALTER PROCEDURE重命名或重新编译，用DROP PROCEDURE删除。优点包括：创建时即完成语法分析和优化，运行效率高；减少客户机与服务器通信量；便于集中实施企业规则。存储函数用CREATE OR REPLACE FUNCTION … RETURNS 类型 AS 块，必须指定返回类型。', '①认为OUT参数在调用时也要传入初始值\\n②把存储函数与存储过程当成完全相同，忽略返回类型\\n③认为存储过程每次执行都要重新做语法分析和优化\\n④认为存储过程只能由应用程序调用，过程体内不能调用其他存储过程', 'P254', 'sql', '9', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 8
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0810', 1, chapter_id, 'JDBC编程', 'JDBC是Java访问数据库的标准接口。体系结构由用户应用程序、JDBC驱动程序管理器和数据源三部分组成，驱动程序管理器把应用程序请求交给厂商JDBC驱动，再由驱动访问数据源。常用类包括DriverManager、Connection、Statement、PreparedStatement、CallableStatement和ResultSet。使用流程为：加载驱动（JDBC 4.0后可省略）、用getConnection(URL,用户名,密码)建立连接、创建语句对象执行SQL、处理结果集、依次关闭结果集/语句对象/连接。executeQuery返回ResultSet，executeUpdate返回影响行数，execute用于存储过程或动态SQL；PreparedStatement预编译可复用并降低SQL注入风险。', '①认为JDBC驱动程序由应用程序自己实现\\n②混淆executeQuery与executeUpdate的返回值\\n③认为PreparedStatement只是写法方便，不能防止SQL注入\\n④认为关闭连接时只需关闭Connection，不必关闭ResultSet和Statement', 'P257', 'sql', '10', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 8
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

-- ============================================================
-- [question] seed-question-ch01.sql
-- ============================================================
-- 智学领航 · 第1章题库种子（由 csv_to_sql.py 生成）
-- 前置：init-v2.sql、seed-kp-ch01.sql；可重复执行（upsert）。


INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0101', 1, 'KP0101', '单选', '长期存储在计算机内、有组织的、可共享的大量数据的集合是（ ）。', '[{"key": "A", "text": "数据"}, {"key": "B", "text": "数据库"}, {"key": "C", "text": "数据库管理系统"}, {"key": "D", "text": "数据库系统"}]', 'B', '数据库是长期存储在计算机内、有组织、可共享的大量数据集合，数据按数据模型组织、描述和存储，具有较小冗余、较高独立性和可扩展性。A“数据”是描述事物的符号记录，范围更广；C 是管理数据的软件；D 是引入数据库后的整个计算机系统，包含数据库、DBMS、应用系统与 DBA。把“数据库”与“数据库系统”混同是常见错误。', '1', '习题册 P11 补充选择⑨', '把题干改为“数据库管理系统”，检验能否区分 DB 与 DBMS。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0102', 1, 'KP0101', '多选', '下列属于数据库技术四个基本概念的有（ ）。', '[{"key": "A", "text": "数据"}, {"key": "B", "text": "数据库"}, {"key": "C", "text": "数据库管理系统"}, {"key": "D", "text": "数据库系统"}]', 'ABCD', '教材 1.1.1 明确数据、数据库、数据库管理系统、数据库系统是与数据库技术密切相关的四个基本概念。四者层层递进：数据是被管理的对象，数据库是数据的集合，DBMS 是管理软件，DBS 是引入数据库后的计算机系统。漏选任何一个都说明没有建立概念层次；尤其不能把 DBMS 与 DBS 混为一谈。', '2', '教材 P4 四个基本概念', '改成“下列不属于四个基本概念的是”，加入“应用系统”作为干扰项。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0103', 1, 'KP0101', '填空', '数据与其______是不可分的；同一数值在不同语境下可能表示不同含义。', NULL, '语义', '教材强调数据与关于数据的解释不可分，数据的含义称为数据的语义。例如 93 既可能是成绩、体重，也可能是人数。若只存数值而不存语义（属性名、单位、上下文），数据无法被正确解释。填空应写“语义”，不要写成“意义”“解释”等非教材术语。', '1', '教材 P4 数据的语义', '改为“数据与其语义不可分”的判断并说明理由。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0104', 1, 'KP0102', '单选', '与人工管理阶段相比，文件系统阶段管理数据的一个显著优势是（ ）。', '[{"key": "A", "text": "数据可以长期保存"}, {"key": "B", "text": "数据共享性很强"}, {"key": "C", "text": "数据独立性很好"}, {"key": "D", "text": "数据整体结构化"}]', 'A', '文件系统阶段利用磁盘等直接存取设备，可把数据长期保留在外存上反复查询、修改、插入和删除，这是相对人工管理阶段的显著进步。B、C、D 都是数据库系统阶段的特点：文件系统共享性弱、冗余度高，独立性弱，且记录内虽有结构但整体无结构。不要把数据库系统阶段的优点错记到文件系统阶段。', '1', '习题册 P10 补充选择③', '反向问“人工管理阶段的特点”，防止阶段特征错位。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0105', 1, 'KP0102', '简答', '简述使用文件系统管理数据的主要缺点。', NULL, '①数据共享性差、冗余度高：文件基本面向某一应用，相同数据需重复建立文件；②数据独立性差：文件逻辑结构针对具体应用设计，结构改变时应用程序必须修改；③文件之间孤立、整体无结构，不能反映现实世界事物之间的内在联系；④数据由应用程序自行控制，缺乏统一的并发控制、完整性与恢复机制。', '答题要抓住“共享、冗余、独立性、整体结构、控制能力”五个维度，并与数据库系统阶段的优点一一对照。只答“共享性差”不完整；把“数据可长期保存”当缺点是把文件系统的进步说反了。例如同一学生信息出现在多个文件时，一处更新而另一处未更新就会不一致，这不是简单的多备份，而是缺乏统一约束与事务控制。', '2', '习题册 P13 补充问答②；教材 P30 习题2', '改问“文件系统与数据库系统的区别和联系”。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0106', 1, 'KP0103', '单选', '在数据库系统中，负责数据库的建立、使用和维护，控制用户访问权限、监视系统性能并完成转储与恢复的专门人员是（ ）。', '[{"key": "A", "text": "应用程序员"}, {"key": "B", "text": "系统分析员"}, {"key": "C", "text": "最终用户"}, {"key": "D", "text": "数据库管理员"}]', 'D', '数据库管理员（DBA）负责设计与定义数据库、培训最终用户、负责系统运维与权限控制、监视分析性能并重组数据库、制定并执行转储与恢复策略、必要时重构数据库。应用程序员以外模式为基础开发应用；系统分析员负责需求分析与总体设计；最终用户通过应用界面使用数据库。把 DBA 理解为“写程序的人”是常见误区。', '2', '教材 P27 DBA职责', '把职责改为“以外模式为基础编写应用程序”，应选应用程序员。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0107', 1, 'KP0103', '填空', '按教材定义，数据库系统一般由数据库、______（及其应用开发工具）、应用系统和______组成。', NULL, '数据库管理系统；数据库管理员', '教材 1.1.1 给出数据库系统的四个组成部分：数据库、数据库管理系统（及其应用开发工具）、应用系统、数据库管理员（DBA）。第一空不能只写“管理系统”，应写全“数据库管理系统”；第二空写“数据库管理员”或“DBA”。若第二空写成“用户”则不准确，最终用户属于人员角色但不等于 DBA。', '2', '教材 P6 数据库系统组成', '改问“数据库系统由哪四部分组成”。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0108', 1, 'KP0104', '单选', '数据库系统的核心和基础是（ ）。', '[{"key": "A", "text": "物理模型"}, {"key": "B", "text": "概念模型"}, {"key": "C", "text": "数据模型"}, {"key": "D", "text": "逻辑模型"}]', 'C', '现有数据库系统均基于某种数据模型，数据模型是对现实世界数据特征的抽象，用来描述数据、组织数据和对数据进行操作，因此是数据库系统的核心和基础。概念模型是现实世界到信息世界的中间层次，主要用于数据库设计；物理模型描述 DBMS 内部的组织与存取实现；逻辑模型是数据模型在逻辑层的具体形态。把“概念模型”当成核心是混淆了建模阶段与系统基础。', '1', '习题册 P10 补充选择①', '改为“按用户观点建模、不依赖具体 DBMS 的是”，应选概念模型。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0109', 1, 'KP0104', '填空', '数据模型通常由数据结构、______和______三部分组成，称为数据模型的三要素。', NULL, '数据操纵；完整性约束', '三要素中，数据结构描述数据库组成对象及对象间联系，是对静态特性的描述；数据操纵描述允许执行的操作及规则，是对动态特性的描述；完整性约束是一组完整性规则，保证数据正确、有效和相容。顺序可写成“数据结构、数据操纵、完整性约束”。漏写“完整性约束”或写成“数据完整性”都不严谨。', '2', '教材 P14 数据模型三要素', '改问“三要素中哪一个描述系统动态特性”。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0110', 1, 'KP0105', '单选', '实现将现实世界抽象为信息世界的是（ ）。', '[{"key": "A", "text": "物理模型"}, {"key": "B", "text": "概念模型"}, {"key": "C", "text": "关系模型"}, {"key": "D", "text": "逻辑模型"}]', 'B', '数据建模分两步，第一步把现实世界抽象为信息世界，建立概念模型；第二步把概念模型转换为计算机上 DBMS 支持的逻辑数据模型。概念模型按用户观点建模，不依赖具体计算机系统，是数据库设计人员与用户交流的语言。物理模型描述数据在 DBMS 内部的存储与存取；关系模型属于逻辑数据模型。把概念模型与逻辑模型混同会答错。', '1', '习题册 P10 补充选择②', '改问“把概念模型转换为机器世界模型的是哪一步”。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0111', 1, 'KP0105', '多选', '在概念模型中，两个实体集之间的联系类型可以包括（ ）。', '[{"key": "A", "text": "一对一联系"}, {"key": "B", "text": "一对多联系"}, {"key": "C", "text": "多对多联系"}, {"key": "D", "text": "无联系"}]', 'ABC', '教材 1.2.2 指出实体之间的联系有一对一、一对多和多对多等多种类型，E-R 图用 1:1、1:n、m:n 表示。D“无联系”不是联系类型；若两个实体集之间确实没有语义联系，就不画联系，而不是把它列为一种联系类型。多选要按教材原文选全三种，漏选多对多最常见。', '2', '教材 P14 联系类型', '改为“学生与课程之间的选课联系属于哪种类型”，答案多对多。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0112', 1, 'KP0105', '简答', '说明概念模型中实体、实体型、实体集三个术语的区别，并各举一例。', NULL, '①实体是客观存在并可相互区别的具体事物，如学生“刘晨”；②实体型是用实体名及其属性名集合对同类实体的抽象刻画，如“学生（学号，姓名，性别，出生日期，主修专业）”；③实体集是同一类型实体的集合，如“全体学生”。三者是“个体—型—集合”的层次：实体型描述结构，实体是型的一个具体值，实体集是同一型的所有值的集合。', '三个术语常被混用。判断关键是问“说的是一个具体对象、一类对象的结构，还是同类对象的全体”。举例时必须让三者处于同一语义域（都用“学生”），否则无法体现层次关系。只背定义不举例，说明没有真正理解型与值的区别。可类比面向对象中的对象、类与类的实例集合，但答题必须以教材术语为主，不能用类比代替定义。', '3', '教材 P14；习题册 P6 习题7', '加入“码”与“属性”，要求四者一起辨析。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0113', 1, 'KP0106', '单选', 'IBM 公司的 IMS 数据库管理系统采用的数据模型是（ ）。', '[{"key": "A", "text": "层次模型"}, {"key": "B", "text": "网状模型"}, {"key": "C", "text": "关系模型"}, {"key": "D", "text": "面向对象模型"}]', 'A', '层次模型的典型代表是 IBM 于 1968 年推出的 IMS，这是第一个大型商用数据库管理系统。网状模型的典型代表是 DBTG/CODASYL 系统；关系模型由 E.F.Codd 于 1970 年提出；面向对象模型是 20 世纪 80 年代以后发展的模型。记代表产品时要把 IMS 与层次模型绑定，把 DBTG 与网状模型绑定。', '1', '习题册 P10 补充选择⑤', '改问“DBTG 系统采用的数据模型”，答案网状模型。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0114', 1, 'KP0106', '单选', '关于网状数据库，下列说法正确的是（ ）。', '[{"key": "A", "text": "只有一个结点可以无双亲结点"}, {"key": "B", "text": "一个结点可以有多于一个的双亲结点"}, {"key": "C", "text": "两个结点之间只能有一种联系"}, {"key": "D", "text": "每个结点有且只有一个双亲结点"}]', 'B', '网状模型的两个条件：允许一个以上结点无双亲；一个结点可以有多于一个双亲。因此 A 错在“只有一个”，D 是层次模型的条件。网状模型还允许两个结点之间有多种联系（复合联系），C 也错。B 正好是网状模型区别于层次模型的关键特征，且层次模型可视为网状模型的特例。', '3', '习题册 P11 补充选择⑦', '改为判断“层次模型是网状模型的特例”，答案为正确。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0115', 1, 'KP0106', '简答', '简述层次模型与网状模型的主要优缺点。', NULL, '层次模型：优点是结构简单清晰、查询效率高、完整性约束支持良好；缺点是难以自然表示非层次的多对多联系、查询子女必须经过双亲、结构严密导致命令趋于程序化。网状模型：优点是能更直接地描述现实世界（一个结点可有多个双亲、结点间可有多种联系）、存取效率较高；缺点是结构随应用扩大而复杂、DDL/DML 复杂且需嵌入高级语言、用户必须了解存取路径。', '比较要按“结构表达能力、存取效率、完整性约束、使用复杂度”四个维度展开，并说明层次模型是网状模型的特例。只写“层次简单、网状复杂”过于笼统；把两者优缺点记反是高频错误。例如一个职工有多个直接上级时，层次树需要重复结点或另设联系，网状结构表达更直接，但用户必须理解导航路径，维护成本也随之上升。', '3', '教材 P18—P20；习题册 P8 习题11', '加入关系模型做三方对比，突出存取路径是否对用户隐蔽。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0116', 1, 'KP0107', '单选', '数据库三级模式结构中，描述局部数据的逻辑结构和特征的是（ ）。', '[{"key": "A", "text": "外模式"}, {"key": "B", "text": "模式"}, {"key": "C", "text": "内模式"}, {"key": "D", "text": "存储模式"}]', 'A', '外模式也称子模式或用户模式，是数据库用户能够看见和使用的局部数据的逻辑结构和特征的描述，是用户的数据视图。模式描述全体数据的全局逻辑结构；内模式也称存储模式，描述物理存储结构。D 与 C 是同义项，属于干扰项；看到“局部”应立刻对应外模式。', '2', '习题册 P12 填空⑦（改为单选）', '改问“描述全体数据逻辑结构的是”，答案模式。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0117', 1, 'KP0107', '单选', '下列说法中，正确的是（ ）。', '[{"key": "A", "text": "数据库的概念模型与具体的 DBMS 有关"}, {"key": "B", "text": "三级模式中描述全体数据的逻辑结构和特征的是外模式"}, {"key": "C", "text": "数据库管理员负责设计和编写应用系统的程序模块"}, {"key": "D", "text": "从逻辑模型到物理模型的转换一般是由 DBMS 完成的"}]', 'D', '概念模型按用户观点建模、不依赖具体 DBMS，A 错；描述全体数据逻辑结构的是模式而非外模式，B 错；编写应用系统程序模块是应用程序员的职责，C 错；DBMS 负责把逻辑结构映射到内部物理组织与存取实现，D 正确。本题四项分别考察概念模型、三级模式、人员分工与 DBMS 职责。', '2', '习题册 P11 补充选择⑧', '把 D 改为“由数据库设计人员手工完成”，则四项全错，训练逐项排除。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0118', 1, 'KP0107', '多选', '关于数据库三级模式结构，下列说法正确的有（ ）。', '[{"key": "A", "text": "一个数据库只有一个模式"}, {"key": "B", "text": "一个数据库只有一个内模式"}, {"key": "C", "text": "一个数据库可以有多个外模式"}, {"key": "D", "text": "外模式通常是模式的子集"}]', 'ABCD', '模式是全体数据的公共逻辑视图，一个数据库只有一个模式；内模式描述物理存储，一个数据库也只有一个；外模式面向不同用户与应用，可以有多个，且通常是模式的子集。四项均符合教材 1.3.2 的表述。注意“一个应用程序只能使用一个外模式”与“一个外模式可被多个应用使用”并不矛盾。', '2', '教材 P24—P25；三级模式结构', '改成“一个数据库可以有多个模式/内模式”的判断，检验数量约束。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0119', 1, 'KP0108', '单选', '能够保证数据库系统中的数据具有较高逻辑独立性的是（ ）。', '[{"key": "A", "text": "外模式/模式映像"}, {"key": "B", "text": "模式"}, {"key": "C", "text": "模式/内模式映像"}, {"key": "D", "text": "外模式"}]', 'A', '当模式改变时，DBA 修改外模式/模式映像，使外模式保持不变，从而依据外模式编写的应用程序不必修改，这就是逻辑独立性。模式/内模式映像保证的是物理独立性：存储结构改变时修改映像使模式不变。外模式与模式本身是结构描述，不是保证独立性的机制。把两个映像的作用记反是本题主要陷阱。', '2', '习题册 P10 补充选择④', '改问“保证物理独立性的是”，答案模式/内模式映像。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0120', 1, 'KP0108', '填空', '数据独立性是数据库领域的重要概念，包括数据的______独立性和数据的______独立性。', NULL, '物理；逻辑', '物理独立性指用户的应用程序与数据库中数据的物理存储相互独立；逻辑独立性指应用程序与数据库的逻辑结构相互独立。两者分别由模式/内模式映像和外模式/模式映像保证。填空顺序写成“物理、逻辑”最稳妥；若写成“逻辑、物理”需与前后文顺序匹配。', '1', '习题册 P12 填空③', '改为“哪一种独立性由外模式/模式映像保证”，答案逻辑独立性。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0121', 1, 'KP0108', '简答', '为什么数据库系统具有较强的数据与程序独立性？', NULL, '因为 DBMS 在三级模式之间提供了两级映像。①外模式/模式映像：模式改变时修改映像可保持外模式不变，应用程序不必修改，保证逻辑独立性；②模式/内模式映像：存储结构改变时修改映像可保持模式不变，应用程序不必改变，保证物理独立性。两级映像把数据定义从应用程序中分离出去，因此应用程序稳定、维护成本低。', '答题必须点出“三级模式 + 两级映像”这一机制，并分别说明两个映像对应哪种独立性。只写“因为有 DBMS”不得分；把逻辑/物理独立性对应错映像也不得分。可补一句“模式/内模式映像唯一，因为一个库只有一个模式和一个内模式”。注意独立性不是数据与程序完全无关，而是结构变化可通过映像隔离，避免应用随之修改。', '3', '教材 P26；习题册 P10 习题15', '改问“模式改变时为什么应用程序可以不改”。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0122', 1, 'KP0109', '单选', '数据库中的数据在逻辑上是一个整体，但物理地分布在计算机网络的不同节点上，每个节点可独立处理本地数据（场地自治），这种数据库系统属于（ ）。', '[{"key": "A", "text": "集中式数据库系统"}, {"key": "B", "text": "客户-服务器数据库系统"}, {"key": "C", "text": "分布式数据库系统"}, {"key": "D", "text": "并行数据库系统"}]', 'C', '分布式数据库系统的本质特征是“逻辑整体、物理分布、场地自治”，并可执行全局应用。集中式系统把 DBMS、数据库与应用放在一台机器上；客户-服务器系统按功能划分客户机与服务器；并行数据库系统运行在并行计算机上，强调多处理机并行处理而非地理分布。抓住“分布 + 自治”即可区分。', '2', '教材 P29 分布式数据库', '改问“利用多处理机并行处理提高性能的是”，答案并行数据库系统。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0123', 1, 'KP0109', '填空', '客户-服务器结构随互联网应用发展为三层结构，一般是指______、______和______。', NULL, '浏览器；应用服务器；数据库服务器', '教材 1.5 指出三层结构一般为浏览器/应用服务器/数据库服务器：用户界面统一用浏览器，应用服务器安装应用系统或应用模块，数据库服务器安装 DBMS 与数据库。三层结构合理分配 DBMS 功能，减轻数据库服务器负担。填空顺序按“浏览器—应用服务器—数据库服务器”书写，不要写成“客户机—服务器”两层结构。', '2', '教材 P29 三层结构', '改为选择题，把“浏览器/应用服务器/数据库服务器”作为正确项。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0124', 1, 'KP0110', '单选', '数据库系统阶段与文件系统阶段的本质区别是（ ）。', '[{"key": "A", "text": "数据可以长期保存"}, {"key": "B", "text": "数据量更大"}, {"key": "C", "text": "整体数据的结构化"}, {"key": "D", "text": "使用磁盘存储"}]', 'C', '教材明确指出，数据库系统实现了整体数据的结构化，这是数据库的主要特征之一，也是与文件系统阶段的本质区别。文件系统阶段已经能长期保存数据、使用磁盘，A、D 不是本质区别；数据量大只是规模变化。整体结构化意味着数据面向整个组织而非某一应用，且数据之间具有联系。', '2', '习题册 P5 习题4解析（改为单选）', '改问“数据库系统与文件系统在数据独立性上的差别”。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0125', 1, 'KP0110', '简答', '试述数据库系统的主要特点。', NULL, '①整体数据的结构化：数据面向整个组织而非单一应用，数据本身及数据之间的联系均结构化，这是与文件系统的本质区别；②共享性强、冗余度低且易于扩充：数据可被多用户多应用共享，减少冗余与不一致，便于增加新应用；③数据的独立性强：包括物理独立性与逻辑独立性，由两级映像保证；④数据由 DBMS 统一管理和控制：提供安全性保护、完整性检查、并发控制与数据库恢复。', '四个特点要成对出现“结论 + 解释”，并点明整体结构化是本质区别、独立性由两级映像保证、统一控制包含四项功能。只列四个标题不解释，通常只能拿一半分；把“数据共享”写成“数据公开”会偏离教材术语。例如多班共享同一学生主数据可避免重复保存，成绩更新由 DBMS 的并发与完整性机制控制，应用不必各自维护副本。', '3', '教材 P9—P11；习题册 P5 习题4', '要求用“与文件系统对比”的方式重述四个特点。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0126', 1, 'KP0105', '综合', '某校要描述学生与课程的选课情况：学生有学号、姓名，课程有课程号、课程名；每名学生可选多门课程，每门课程可被多名学生选修，每次选课还要记录成绩。请写出 E-R 图的文字结构，标出两个实体的码、选课联系的类型与属性，并说明为什么不能只在学生实体中增加一个“课程号”属性。', NULL, '学生实体以学号为码并含姓名；课程实体以课程号为码并含课程名；学生与课程之间建立“选课”联系，类型为多对多（m:n），成绩作为选课联系的属性。只在学生实体中放一个课程号，一名学生至多能记录一门课，若改成重复组又会破坏属性的原子性并造成学生信息重复，无法同时自然表示多名学生选同一门课及每次选课的成绩。', '本题要把实体、码、联系和联系属性放在各自正确的位置。学生与课程的基数两边都是“多”，所以必须是 m:n；成绩属于一次选课事实而非学生或课程自身。误判为一对多会导致模型无法表达实际选课语义。若把课程号作为多值属性塞进学生实体，也无法区分同一学生不同课程各自的成绩，进一步说明选课应独立成联系。', '3', '教材 P13—P14；1.2.2 概念模型', '把成绩改为选修学期并要求说明该属性应挂在哪个对象上。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0127', 1, 'KP0108', '综合', '某应用只通过外模式访问学生数据。现需做两项调整：①模式中的学生关系增加“主修专业”属性，但原应用仍只看到原有列；②不改变任何逻辑结构，只调整学生数据的物理索引组织。分别说明 DBA 应调整哪一级映像、保证哪一种数据独立性，以及为什么原应用可以不改。', NULL, '①调整外模式/模式映像，使原外模式的属性集合和对应关系保持不变，从而保证逻辑独立性；应用依据外模式编写，不必感知模式新增属性。②调整模式/内模式映像，使模式保持不变，从而保证物理独立性；索引组织属于内模式细节，应用不直接依赖。两种调整都由 DBMS 按映像完成数据结构与访问请求的转换。', '第①项变的是全局逻辑结构，所以用外模式/模式映像屏蔽；第②项只变物理存储，所以用模式/内模式映像屏蔽。常见错误是把“新增属性”当物理变化或把“调整索引”当逻辑变化。两级映像不是自动消除所有维护工作，而是让 DBA 通过修改映像保持应用视图稳定。', '3', '教材 P25—P26；1.3.3 二级映像', '增加“拆分学生关系但保持旧应用视图不变”的情形并判断映像与独立性。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0128', 1, 'KP0110', '综合', '某学院的多个教务应用各自保存学生文件和选课文件，出现四类问题：学生信息重复且更新不一致；文件格式一变就要修改多个程序；文件之间难以表达学生与选课的联系；并发写入时缺少统一的权限、完整性与恢复控制。请分别用数据库系统的主要特点提出改造办法，并说明哪一项体现数据库系统与文件系统的本质区别。', NULL, '①用统一的数据模型描述学生、课程及选课联系，实现整体数据结构化，这是与文件系统的本质区别；②由共享数据库保存统一主数据，减少重复副本和不一致，并便于新应用复用；③通过三级模式与两级映像隔离文件或逻辑结构变化，提高物理与逻辑独立性；④由 DBMS 统一提供安全性、完整性、并发控制与恢复，不再让每个应用各自实现。', '本题不能把四个特点简单贴标签，必须把问题与机制一一对应：重复副本对应共享性差与冗余高，格式变化牵连程序对应独立性差，联系难表达对应整体无结构，缺少权限与故障控制对应 DBMS 统一控制不足。整体结构化不仅指单张表有行列，还包括数据之间的联系也由统一模型描述。', '3', '教材 P9—P11；数据库系统的特点', '增加“某字段存储格式改变”的情形并要求判断对应哪种独立性。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

-- ============================================================
-- [question] seed-question-ch02-ch03.sql
-- ============================================================
-- 智学领航 · 第2—3章题库种子（由 strict 通过的 CSV 重生成）
-- 保留原有 question_kp 关联；前置 init-v2.sql、seed-kp-ch02-ch03.sql；可重复执行（upsert）。


INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0201', 1, 'KP0201', '单选', '关于关系模型，下列叙述不正确的是（ ）。', '[{"key": "A", "text": "关系模型由关系数据结构、关系操作集合和关系完整性约束三部分组成"}, {"key": "B", "text": "关系模型只包含单一的数据结构——关系，用户看到的是二维表"}, {"key": "C", "text": "关系模型只规定数据的逻辑结构，关系操作与完整性约束由应用程序自行实现"}, {"key": "D", "text": "关系中实体以及实体之间的联系都用同一种结构类型表示"}]', 'C', '关系模型由关系数据结构、关系操作集合、关系完整性约束三部分组成，缺一不可。C 把后两部分推给应用程序，与“完整性约束应由关系系统自动支持”相矛盾；A 是三要素的完整表述，B、D 是关系模型“单一结构、可表达实体间联系”的特点。', '1', '习题册 P23—P27', '把本题改成填空，要求写出关系模型三个组成部分并各举一例。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0202', 1, 'KP0202', '单选', '设 D1=导师={张清玫,刘逸}，D2=专业={计算机科学与技术,信息管理与信息系统}，D3=研究生={李勇,刘晨,王敏}，则笛卡儿积 D1×D2×D3 的基数为（ ）。', '[{"key": "A", "text": "12"}, {"key": "B", "text": "7"}, {"key": "C", "text": "6"}, {"key": "D", "text": "9"}]', 'A', '笛卡儿积的基数为各域基数之积，即 2×2×3=12。7 是三个域基数之和，属于把乘法误当加法的典型错误；6、9 是少乘或多乘一个域基数的结果。', '1', '教材 P34—P35', '把三个域改为 {男,女}、0～100 的整数、3 个学号，再求基数。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0203', 1, 'KP0203', '单选', '下列关于候选码的叙述，正确的是（ ）。', '[{"key": "A", "text": "候选码只能由一个属性组成"}, {"key": "B", "text": "候选码的值能唯一标识元组，且它的真子集不能唯一标识元组"}, {"key": "C", "text": "一个关系最多只能有一个候选码"}, {"key": "D", "text": "候选码所包含的属性可以取空值"}]', 'B', '候选码的定义包含两个条件：能唯一标识元组、真子集不能唯一标识，B 完整表述了这两点。A 忽略了多属性候选码，C 与“一个关系可以有多个候选码”矛盾，D 违反实体完整性。', '1', '习题册 P23—P27', '给出一个关系模式，让学生列出全部候选码并指出主属性。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0204', 1, 'KP0204', '单选', '下列关于关系模式与关系的叙述，正确的是（ ）。', '[{"key": "A", "text": "关系模式随元组的插入、删除而变化"}, {"key": "B", "text": "关系模式是值，关系是型"}, {"key": "C", "text": "关系模式只描述属性名，不包含域名与映像"}, {"key": "D", "text": "关系模式是型，关系是关系模式在某一时刻的状态或内容"}]', 'D', '关系模式是对关系的描述，属于型，一经定义就稳定；关系是元组集合，属于值，随时间变化，故 D 正确。A 把型的稳定性说成动态，B 恰好颠倒，C 漏掉了 R(U,D,DOM) 中的 D 与 DOM。', '2', '教材 P37—P39', '用 Student 表举例说明“型不变、值可变”。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0205', 1, 'KP0205', '单选', '关系操作的特点是集合操作方式，其操作的对象和结果都是（ ）。', '[{"key": "A", "text": "记录"}, {"key": "B", "text": "集合"}, {"key": "C", "text": "元组"}, {"key": "D", "text": "列"}]', 'B', '关系操作一次处理一个集合，输入、输出以及中间结果都是关系（集合），故对象和结果都是集合。元组和列只是关系的组成成分，不能概括操作对象与结果；记录是文件系统的术语。', '1', '习题册 P23—P27', '追问“一次一个集合”与“一次一个记录”的处理方式差别。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0206', 1, 'KP0206', '单选', '学生选课关系 SC(学号,课程号,成绩)，主码为(学号,课程号)。根据实体完整性规则，下列叙述正确的是（ ）。', '[{"key": "A", "text": "学号、课程号中只要有一个非空即可"}, {"key": "B", "text": "只有学号不能取空值"}, {"key": "C", "text": "学号和课程号都不能取空值"}, {"key": "D", "text": "成绩作为非主属性也不能取空值"}]', 'C', '实体完整性要求主属性不能取空值；主码由多个属性组成时，每个组成属性都不能为空，故 C 正确，A、B 都只覆盖了主码的一部分。D 把实体完整性扩大到非主属性，成绩是否可空属于用户定义的完整性范畴。', '2', '习题册 P23—P27', '把主码改为只含学号，再判断哪些属性不能为空。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0207', 1, 'KP0207', '单选', '职工(职工号,姓名,年龄,部门号)与部门(部门号,部门名称)，职工.部门号是外码。下列叙述正确的是（ ）。', '[{"key": "A", "text": "职工是参照关系，部门是被参照关系"}, {"key": "B", "text": "部门是参照关系，职工是被参照关系"}, {"key": "C", "text": "外码必须与被参照关系的主码同名"}, {"key": "D", "text": "职工.部门号在任何情况下都不能取空值"}]', 'A', '外码所在的关系 R 是参照关系，被引用主码所在的关系 S 是被参照关系（目标关系），故 A 正确。C 错，外码不必与主码同名，如“主修专业”引用“专业名”；D 错，参照完整性允许外码取空值。', '2', '习题册 P23—P27', '让学生指出本例中 F 与 K_s 分别对应哪个属性。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0208', 1, 'KP0208', '单选', '职工表中“性别”属性只能取“男”或“女”，这一约束属于（ ）。', '[{"key": "A", "text": "实体完整性"}, {"key": "B", "text": "参照完整性"}, {"key": "C", "text": "关系不变性"}, {"key": "D", "text": "用户定义的完整性"}]', 'D', '该约束是具体应用对某一属性取值范围的语义要求，属于用户定义的完整性。实体完整性只管主属性非空，参照完整性管外码引用，“关系不变性”指实体完整性与参照完整性两者，都不针对取值范围。', '1', '习题册 P23—P27', '换成“成绩 0～100”或“姓名不能为空”，再判断类别。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0209', 1, 'KP0209', '单选', '设 R、S 是同目的关系，下列叙述正确的是（ ）。', '[{"key": "A", "text": "R−S 的结果列数少于 R 的列数"}, {"key": "B", "text": "R∩S 的结果元组数可能大于 R 与 S 的元组数之和"}, {"key": "C", "text": "R∩S 可用差运算表示，即 R∩S=R−(R−S)"}, {"key": "D", "text": "R×S 的结果列数为 R 与 S 的列数之和减去公共属性数"}]', 'C', '交可由差导出，R−(R−S) 恰为既属于 R 又属于 S 的元组，C 正确。并、差、交都是同目运算，不改变列数，A 错；交的结果必是 R 的子集，元组数不会超过 R，B 错；笛卡儿积列数为 n+m 且不去掉公共属性，D 描述的是自然连接。', '2', '教材 P46', '给出具体元组集合，分别计算 R∪S、R−S、R∩S 并核对元组数。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0210', 1, 'KP0210', '单选', '关系 R(A,B,C) 与 S(B,C,D) 进行自然连接，结果包含的列数为（ ）。', '[{"key": "A", "text": "6"}, {"key": "B", "text": "4"}, {"key": "C", "text": "5"}, {"key": "D", "text": "2"}]', 'B', '自然连接在公共属性 B、C 上做等值比较，并在结果中去掉重复的公共属性列，故结果为 A、B、C、D 共 4 列。6 是直接做笛卡儿积的列数，5 是只去掉一列公共属性的错误算法。', '2', '习题册 P23—P27', '改为 R(A,B) 与 S(B,C)，或公共属性有三个的情形。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0211', 1, 'KP0210', '单选', '对关系 R(A,B,C) 做投影 π_A,B(R)，下列叙述正确的是（ ）。', '[{"key": "A", "text": "结果的元组数可能少于 R，且需取消重复元组"}, {"key": "B", "text": "结果的列数与 R 相同"}, {"key": "C", "text": "投影是从行的角度选取满足条件的元组"}, {"key": "D", "text": "结果的元组数一定等于 R 的元组数"}]', 'A', '投影从列的角度选取属性，取消某些列后可能出现重复行，必须取消重复元组，故结果元组数可能减少，A 对、D 错。投影后列数减少，B 错；从行角度选取元组的是选择，C 错。', '2', '教材 P46—P53', '给 5 行 R，写出 π_A(R) 并手工去重。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0212', 1, 'KP0207', '单选', '课程(课程号,课程名,学分,先修课)，课程号为主码，先修课为外码，参照本关系的主码。下列叙述正确的是（ ）。', '[{"key": "A", "text": "参照关系与被参照关系是同一个关系，因此不存在参照完整性"}, {"key": "B", "text": "先修课只能取空值，不能取本关系中已存在的课程号"}, {"key": "C", "text": "先修课必须非空，否则违反参照完整性"}, {"key": "D", "text": "先修课可以取空值，也可以取本关系中某个元组的课程号值"}]', 'D', '参照完整性不要求参照关系与被参照关系不同，同一关系内部属性之间也可引用，A 错。规则允许外码取空值（表示该课程无先修课），也允许取被参照关系中已存在的主码值，故 D 对、B 错；C 把“可为空”误当成“必须非空”。', '3', '习题册 P23—P27', '把“先修课”换成“系主任”，再判断该外码能否取空值。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0213', 1, 'KP0201', '填空', '在关系模型中，二维表的每一行称为一个____，每一列称为一个____，列中值的取值范围称为____，元组中的每一个值称为____。', NULL, '元组；属性；域；分量', '四者依次对应行、列、列的取值集合、行中的单个值，是最基础的一组术语。域是“一组具有相同数据类型的值的集合”，不能与属性混为一谈。', '1', '教材 P34', '再补填“目（度）”与“基数”，并说明二者区别。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0214', 1, 'KP0205', '填空', '关系模型中有 8 种查询操作，其中____、____、____、____和____是 5 种基本操作，其他操作可由它们定义和导出。', NULL, '选择、投影、并、差、笛卡儿积（顺序可互换）', '教材 2.2 节明确这 5 种为基本操作，交、连接、除都可由它们导出，就像乘法可由加法定义。把交、连接、除填入即错。', '1', '习题册 P23—P27', '追问交、连接、除分别如何用基本操作表示。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0215', 1, 'KP0206', '填空', '实体完整性规则：若属性 A（一个或一组属性）是基本关系 R 的____，则 A 不能取____。', NULL, '主属性；空值', '该规则只针对主属性，空值指“不知道”“不存在”或“无意义”的值。主码由若干属性组成时，所有组成属性都不能取空值。', '1', '教材 P42—P43', '把“主属性”改成“非主属性”，再判断命题真假。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0216', 1, 'KP0207', '填空', '设 F 是基本关系 R 的一个或一组属性，但不是 R 的码；K_s 是基本关系 S 的主码。若 F 与 K_s 相对应，则称 F 是 R 的____，R 称为____，S 称为____。', NULL, '外码；参照关系；被参照关系（目标关系）', '三者成对出现，且 F 与 K_s 必须定义在同一（或同一组）域上；外码不必与主码同名。填“外码—参照关系—被参照关系”时还要看 F 在哪张表：F 所在 R 永远是参照方，主码 K_s 所在 S 才是被参照方，方向不能写反。', '2', '教材 P43—P45', '给出“主修专业”引用“专业名”的例子，填出 F 与 K_s。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0217', 1, 'KP0208', '填空', '学生选课关系中规定“成绩”的取值范围为 0～100，这类约束属于关系模型的____完整性。', NULL, '用户定义的完整性（用户定义完整性）', '它不属于关系模型固有的两个不变性，而是具体应用提出的语义要求。实体完整性管主属性非空，参照完整性管外码引用，都不涉及取值范围。', '1', '习题册 P23—P27', '再举“姓名不能为空”，判断它属于哪类完整性。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0218', 1, 'KP0202', '填空', '若域 D_i(i=1,2,…,n) 为有限集、其基数为 m_i，则笛卡儿积 D1×D2×…×Dn 的基数为____。', NULL, '∏(i=1…n) m_i，即 m1×m2×…×mn', '每个元组由各域各取一个分量组成，故总数为各域基数之积。基数指一个域允许的不同取值个数，不是元组个数。', '1', '教材 P34—P36', '给 m1=2、m2=101、m3=3 代入求值。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0219', 1, 'KP0202', '简答', '说明域、笛卡儿积、关系、元组、属性之间的联系与区别。', NULL, '域是一组具有相同数据类型的值的集合；笛卡儿积是给定一组域后所有有序组合的集合，其基数为各域基数之积；关系是笛卡儿积的有限子集，只有这样的子集才可能有实际语义；元组是笛卡儿积的一个元素，也是关系中的一行；属性是给关系每一列起的名字，n 目关系必有 n 个属性。区别在于：域是值的集合，属性是列名并对应一个域，元组是分量的有序组合，关系是元组的有限集合。', '答题要按“值集合—组合全体—有语义的子集—行—列名”的顺序串起来，并点明“关系的目”是属性个数而非元组个数。例如“性别、年龄、学号”各是一个属性并分别对应域，某个学生的一行是元组，全体有语义的学生行才组成关系；不能把所有性别年龄学号组合都当成真实学生。这也解释了为何笛卡儿积通常远大于实际关系。', '2', '教材 P34—P36', '用导师、专业、研究生三个域举例，指出哪些元组才能构成关系。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0220', 1, 'KP0203', '简答', '定义并说明候选码、主码、主属性、非主属性、全码之间的联系与区别。', NULL, '能唯一标识元组且其真子集不能唯一标识的属性（组）称为候选码；从多个候选码中选定一个作为主码；候选码中包含的属性称为主属性，不包含在任何候选码中的属性称为非主属性（非码属性）；若关系的所有属性共同构成候选码，则称为全码。联系：主码必是候选码之一，主属性由全部候选码决定。区别：候选码强调最小性，主码强调在候选码中“选定一个”，并用于实体完整性约束。', '候选码的两个条件必须同时写出，只答“能唯一标识”会漏掉最小性；主属性看的是全部候选码，而不是只看主码。例如（学号，课程号）共同唯一且缺一不可时是候选码；若学号单独也能唯一，则（学号，课程号）只是超码而非候选码。全码并非常见情形，而是所有属性共同构成唯一候选码的极端情况。', '2', '教材 P38', '给出一个具体关系模式，要求写出全部候选码、主码、主属性、非主属性。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0221', 1, 'KP0204', '简答', '举例说明关系模式和关系的区别。', NULL, '关系模式是型，例如 Student(Sno,Sname,Ssex,Sbirthdate,Smajor)，它描述关系的结构，一经定义就稳定不变；关系是值，例如某一时刻 Student 中的 3 个学生元组，它随插入、删除、修改而变化。同一关系模式在不同时刻可以对应不同的关系，因此日常所说的“关系”要结合上下文区分型与值。', '判断关键是“是否随元组变化”。关系模式是静态的，关系是动态的；关系数据库模式是型的集合，关系数据库是某一时刻值的集合。例如今天插入一名学生只改变关系的值，并不会把 Student 的属性集合改掉；只有执行 ALTER TABLE 等定义操作才改变关系模式。判断时不要看表中有多少行。', '2', '教材 P37—P39', '追问关系数据库模式与某一时刻关系数据库的区别。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0222', 1, 'KP0206', '简答', '为什么主码不能取空值？若主码由多个属性组成，是否允许其中某个属性为空？', NULL, '空值是“不知道”“不存在”或“无意义”的值。主码的作用是唯一标识元组，若取空值就会出现无法标识的实体，与“现实世界中的实体是可区分的”这一前提矛盾，因此实体完整性规定主属性不能取空值。主码由多个属性组成时，所有组成属性都不能为空，只要求其中一个非空是错误的。实体完整性只约束主属性，不约束非主属性。', '要从“实体可区分性”推出“主码非空”，而不是只背规则；还要说明规则的作用范围仅限主属性。例如（Sno，Cno）共同标识选课记录时，Sno 为空就无法确认是谁选课，Cno 为空就无法确认选了什么课，因此不能只保证另一列非空。唯一索引也不能替代这条非空语义。', '2', '教材 P42—P43', '讨论“允许主码为空、另加唯一约束”是否会破坏实体可区分性。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0223', 1, 'KP0207', '简答', '如果数据库只有一个关系，是否就不存在参照完整性？请举例说明。', NULL, '仍然可能存在。课程(课程号,课程名,学分,先修课)中，课程号是主码，先修课是外码，它参照本关系的主码课程号。该关系既是参照关系又是被参照关系，刻画的是关系内部属性之间的引用约束。有些课程不要求先修课，“先修课”可取空值；若取非空值，则必须是本关系中确实存在的课程号。', '参照完整性刻画的是“引用”，不要求两个关系不同；同一关系内部的自引用同样受参照完整性约束。答“只有一个关系就没有参照完整性”是把参照完整性误解为必须跨表。若把先修课写成不存在的课程号，即使只有一个关系也同样违约；空值表示无先修课，不等于引用了一个空课程号。', '2', '习题册 P23—P27', '把“先修课”换成“系主任”，讨论自引用外码的空值与取值。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0224', 1, 'KP0206', '简答', '试述关系模型的完整性约束，并说明在参照完整性中，什么情况下外码属性的值可以为空值。', NULL, '关系模型有三类完整性约束：实体完整性、参照完整性和用户定义的完整性。实体完整性与参照完整性是关系模型必须满足的约束，称为关系的两个不变性，应由关系系统自动支持；用户定义的完整性针对具体应用。参照完整性允许外码取空值，条件是 F 的每个属性值均为空值（不能部分为空），此时表示该元组在外码上的取值“不知道”或“不存在”，例如某门课程没有先修课、新教师尚未确定所属系；若外码取非空值，则必须等于被参照关系中某个元组的主码值。', '答案须同时给出三类约束、两个不变性、外码空值的“全空”条件和非空时的取值要求，只答“可以为空”会漏掉“每个属性值均为空”。复合外码尤其不能只空一部分，否则无法整体对应被参照主码；是否允许空值仍由应用语义和列上的非空约束共同决定。实体完整性还可能另行禁止主属性为空。', '2', '教材 P42—P45', '把“外码可为空”改成“外码能否部分为空”，要求举例判断。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0225', 1, 'KP0202', '综合', '设 D1={男,女}，D2={0,1,2,…,100}（共 101 个整数），D3={20180001,20180002,20180003}。求 D1×D2×D3 的基数；并说明能否把该笛卡儿积直接当作一个关系使用。', NULL, '基数 M=2×101×3=606。不能直接当作关系使用：笛卡儿积中的元组大多没有实际语义（如“男,0,20180002”），关系模型中的关系是笛卡儿积的有限子集，只取有实际含义的元组；同时还要为每一列命名属性（如性别、年龄、学号），因为域可以相同，不加属性名无法区分列。', '基数按各域基数相乘；易错点是把 0～100 当成 100 个数而算得 600。第二问要落到“关系是子集”和“n 目关系必有 n 个属性”两点。计算时 0 到 100 含两端所以 D2 基数为 101；选出的实际关系必须进一步限定每行表达的实体或联系，不能只做列改名后就把 606 行全部当成有效数据。', '3', '教材 P34—P36', '把 D2 改为 {0,1,…,99}，再求基数并说明关系应如何取名。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0226', 1, 'KP0209', '综合', '设关系 S(A,B,C,D) 有 N1 个元组，关系 T(C,D,E,F) 有 N2 个元组，且 N2>N1>0。分别写出 S∪T、S∩T、S−T、S×T 在一般情况下可得到的最大、最小元组数及结果列数。', NULL, 'S∪T：最大 N1+N2，最小 N2，4 列；S∩T：最大 N1，最小 0，4 列；S−T：最大 N1，最小 0，4 列；S×T：最大 N1×N2，最小 N1×N2，8 列。', '并、差、交要求同目且相应属性同域，列数保持 4 不变。并的最小值出现在 S⊆T 时，结果为 T 的 N2 个元组；差的最小值出现在 S⊆T 时为空；笛卡儿积元组数恒为乘积，列数为 4+4=8。交的最大值在 S⊆T 时为 N1，并的最大值在两关系无共同元组时达到；这些边界都假设集合同目同域，若前提不成立就不能直接做并、差、交。', '3', '习题册 P23—P27', '改为 N1>N2，再问 R∪S 的最小元组数与 R∩S 的最大元组数。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0227', 1, 'KP0210', '综合', '设 Teacher(Tno,Tname,Tage,Tsex)、Department(Dno,Dname,DTno)、Work(Tno,Dno,Year,Salary)。用关系代数表示：(a) 工资超过 5000 元的教师的不同年龄；(b) 不在计算机系工作的教师代码。', NULL, '(a) π_Tage(Teacher ⋈ σ_Salary>5000(Work))；(b) π_Tno(Teacher) − π_Tno(Work ⋈ π_Dno(σ_Dname=''计算机系''(Department)))。', '(a) 先选出工资大于 5000 的 Work 元组，与 Teacher 自然连接后只在 Tage 上投影，投影自动取消重复年龄，故得到“不同年龄”。(b) 先求出计算机系的系代码集合，再求在该系工作的教师代码，最后用全体教师代码做差；漏掉最后的差运算就得不到“不在”的语义。', '2', '习题册 P23—P27', '把 (b) 改成“不在计算机系且尚未参加任何工作的教师代码”。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0228', 1, 'KP0210', '综合', '已知 Teacher：T1 张丽 42 女、T2 李波 45 男、T3 王艳 33 女、T4 赵明 29 男；Department：D1 计算机系（系主任 T1）、D2 数学系（T2）、D3 电子系（NULL）；Work：T1 在 D1、1995 年、6000 元，T2 在 D2、1992 年、6500 元，T3 在 D1、2005 年、4500 元。用左外连接写出“所有教师的姓名及其所在系名”的关系代数表达式，并给出结果。', NULL, 'π_Tname,Dname(Teacher ⟕ (Work ⋈ Department))；结果为：张丽—计算机系、李波—数学系、王艳—计算机系、赵明—NULL。', '先把 Work 与 Department 自然连接得到教师所在系，再与 Teacher 做左外连接。左外连接保留左边关系 Teacher 的全部元组，T4 赵明没有工作记录，其 Dname 补空值，因此仍出现在结果中且系名为 NULL；若改用内连接，赵明会被丢掉。', '3', '习题册 P23—P27', '改为右外连接，说明结果中为什么会出现“NULL—电子系”。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0229', 1, 'KP0210', '综合', 'SPJ 数据库：S(SNO,SNAME,STATUS,CITY)、P(PNO,PNAME,COLOR,WEIGHT)、J(JNO,JNAME,CITY)、SPJ(SNO,PNO,JNO,QTY)。用关系代数表示：① 供应工程 J1 零件的供应商代码 SNO；② 供应工程 J1 零件 P1 的供应商代码 SNO；③ 供应工程 J1 红色零件的供应商代码 SNO。', NULL, '① π_SNO(σ_JNO=''J1''(SPJ))；② π_SNO(σ_JNO=''J1'' ∧ PNO=''P1''(SPJ))；③ π_SNO(σ_JNO=''J1''(SPJ) ⋈ σ_COLOR=''红''(P))。', '①② 的条件都只涉及 SPJ 一张表，可直接在选择中并列；③ 需先分别选出 J1 的供应记录和红色零件，再按公共属性 PNO 自然连接，最后投影 SNO。漏掉与 P 的连接就无法判断零件颜色，漏掉与 SPJ 的连接则无法判断供应工程。自然连接会按 PNO 匹配并去掉重复的 PNO 列，最后只保留供应商代码。', '3', '教材 P64—P65', '再加一小题“没有使用天津供应商生产的红色零件的工程号 JNO”。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0230', 1, 'KP0210', '综合', '关系 R(A,B,C) 的数据为 (a1,b1,c2)、(a2,b3,c7)、(a3,b4,c6)、(a1,b2,c3)、(a4,b6,c6)、(a2,b2,c3)、(a1,b2,c1)；关系 S(B,C,D) 的数据为 (b1,c2,d1)、(b2,c1,d1)、(b2,c3,d2)。求 R÷S，并说明判断过程。', NULL, 'R÷S={a1}。S 在 (B,C) 上的投影为 {(b1,c2),(b2,c1),(b2,c3)}；a1 的象集 {(b1,c2),(b2,c1),(b2,c3)} 包含该投影，a2 的象集 {(b3,c7),(b2,c3)}、a3 的象集 {(b4,c6)}、a4 的象集 {(b6,c6)} 都不包含该投影，故只有 a1 满足条件，结果为 {a1}。', '除运算按“象集是否包含 S 在公共属性上的投影”逐值判断，结果属性为 R 中去掉 S 属性后的 X（此处为 A）。易错点是只检查公共属性是否出现过，而不检查是否覆盖 S 的全部 (B,C) 组合。还要先按 A 值分组收集（B，C）象集，再逐组与 S 的三个组合比较；a1 虽然还有其他（B，C）组合也不影响包含判断，关键是 S 要求的三个组合一个都不能少。', '3', '教材 P52', '把 S 改为只含 (b2,c3) 一个元组，再求 R÷S 并说明结果变化。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);


INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0301', 1, 'KP0301', '单选', '下列关于 SQL 语言特点的叙述中，正确的是（ ）。', '[{"key": "A", "text": "SQL 只有数据查询和数据操纵两类功能，不包含数据定义与数据控制"}, {"key": "B", "text": "SQL 是过程化语言，用户必须指出数据的存取路径"}, {"key": "C", "text": "SQL 是非过程化语言，用户只说明\\"要什么\\"，存取路径由 DBMS 自动选择"}, {"key": "D", "text": "嵌入式 SQL 与独立使用的 SQL 语法差别很大，需另学一套语法"}]', 'C', 'SQL 集数据定义、数据查询、数据操纵、数据控制于一体，故 A 错；它只描述目标而不描述过程，存取路径由 DBMS 决定，故 B 错、C 对；独立使用与嵌入式使用采用统一的语法结构，只是嵌入式需加主语言接口，故 D 错。', '2', '习题册 P37 补充习题 选择①（改写）', '把选项改为"核心功能只用 9 个动词"判断正误。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0302', 1, 'KP0302', '单选', '在 SQL 中，删除表中已有数据但保留该表结构，应使用的语句是（ ）。', '[{"key": "A", "text": "DELETE"}, {"key": "B", "text": "DROP TABLE"}, {"key": "C", "text": "ALTER TABLE"}, {"key": "D", "text": "CREATE TABLE"}]', 'A', 'DELETE 只删除元组，表定义仍保存在数据字典中；DROP TABLE 会把表定义连同数据一起删除；ALTER TABLE 用于修改表结构，CREATE TABLE 用于建表，三者都不符合"只删数据"的要求。', '1', '习题册 P38 补充习题 选择②', '改问"删除表及其定义用哪条语句"。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0303', 1, 'KP0302', '单选', '在数据库中建立索引的主要目的是（ ）。', '[{"key": "A", "text": "加快建表速度"}, {"key": "B", "text": "加快查询（存取）速度"}, {"key": "C", "text": "提高数据安全性"}, {"key": "D", "text": "节省存储空间"}]', 'B', '索引按指定列建立额外的存取路径，以增加存储空间和维护代价换取查询速度的提升。索引既不负责建表，也不承担安全性控制，反而占用更多空间，故 A、C、D 均错。', '1', '习题册 P38 补充习题 选择③', '改问 UNIQUE 索引与 CLUSTER 索引的区别。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0304', 1, 'KP0301', '单选', '在 SQL 支持的三级模式结构中，视图对应于（ ）。', '[{"key": "A", "text": "外模式"}, {"key": "B", "text": "模式"}, {"key": "C", "text": "内模式"}, {"key": "D", "text": "模式映像"}]', 'A', 'SQL 支持三级模式：外模式对应视图和部分基本表，模式对应基本表，内模式对应存储文件。模式映像只是层次之间的转换规则，不是某一级模式本身，故 D 错。', '1', '习题册 P38 补充习题 选择④', '改问"基本表对应哪一级模式"。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0305', 1, 'KP0309', '单选', '下列关于基本表和视图的叙述中，不正确的是（ ）。', '[{"key": "A", "text": "基本表和视图都是关系"}, {"key": "B", "text": "可以用 SQL 对基本表和视图进行查询等操作"}, {"key": "C", "text": "可以在基本表或已定义的视图之上再定义视图"}, {"key": "D", "text": "基本表和视图在数据库中都存储了各自对应的数据"}]', 'D', '视图是从基本表（或其他视图）导出的虚表，数据库中只保存视图定义，不保存视图数据，数据仍在导出它的基本表中，故 D 不正确。A、B、C 均符合教材对视图的说明。', '2', '习题册 P38 补充习题 选择⑤', '改问"视图的数据存放在哪里"。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0306', 1, 'KP0308', '单选', '按 SQL 的三值逻辑，当 Grade 取值为空值时，条件表达式 `Grade > 60 OR FALSE` 的值为（ ）。', '[{"key": "A", "text": "TRUE"}, {"key": "B", "text": "UNKNOWN"}, {"key": "C", "text": "FALSE"}, {"key": "D", "text": "NULL"}]', 'B', '空值参与比较运算得到 UNKNOWN；按表 3.7，UNKNOWN OR FALSE 的结果仍为 UNKNOWN，只有 UNKNOWN OR TRUE 才为 TRUE，故 A 错、B 对。C 只在两侧均为 FALSE 时成立；NULL 是数据值而不是真值，故 D 错。', '2', '教材 P113 表 3.7 三值逻辑真值表（改写）', '把 OR FALSE 换成 AND FALSE 再判断。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0307', 1, 'KP0305', '单选', '关于嵌套查询，下列说法正确的是（ ）。', '[{"key": "A", "text": "子查询的 SELECT 语句中可以写 ORDER BY，先对子查询结果排序"}, {"key": "B", "text": "不相关子查询和相关子查询都只需由内向外求值一次"}, {"key": "C", "text": "子查询返回多个值时，必须使用 ANY（或 SOME）或 ALL 并同时配合比较运算符"}, {"key": "D", "text": "EXISTS 谓词返回子查询的结果集，可以再与 =、> 等比较运算符连用"}]', 'C', '子查询中不能使用 ORDER BY，排序只能对最终结果进行，A 错；相关子查询依赖父查询的元组变量，必须反复求值，B 错；EXISTS 只产生 true/false，不返回结果集，D 错；返回多值必须用 ANY/ALL 且前面必须带比较运算符，C 对。', '3', '教材 P99—P105；嵌套查询与 ANY/ALL、EXISTS（改写）', '改问 `<> ALL` 与 `NOT IN` 的等价关系。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0308', 1, 'KP0304', '单选', '下列连接操作中，结果里可能出现悬浮元组（未匹配的元组用空值补齐）的是（ ）。', '[{"key": "A", "text": "等值连接"}, {"key": "B", "text": "自然连接"}, {"key": "C", "text": "自身连接"}, {"key": "D", "text": "左外连接"}]', 'D', '等值连接、自然连接、自身连接都只保留满足连接条件的匹配元组，不匹配的元组被丢弃；外连接才用空值填补未匹配元组，左外连接列出左关系的全部元组，故只有 D 符合。', '2', '教材 P94—P96；外连接（改写）', '改问"右外连接列出哪一侧的全部元组"。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0309', 1, 'KP0306', '单选', '对两个 SELECT 语句的结果做并操作，若要求保留重复元组，应使用（ ）。', '[{"key": "A", "text": "UNION ALL"}, {"key": "B", "text": "UNION"}, {"key": "C", "text": "INTERSECT"}, {"key": "D", "text": "EXCEPT"}]', 'A', 'UNION 合并时由系统自动去掉重复元组，UNION ALL 才保留重复元组。INTERSECT 求交、EXCEPT 求差，都不是并操作，故 B、C、D 错。', '1', '教材 P105—P106；集合查询（改写）', '改问"参加集合操作的各结果必须满足什么条件"。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0310', 1, 'KP0307', '单选', '执行语句 `DELETE FROM Student;` 后，结果是（ ）。', '[{"key": "A", "text": "Student 表连同其定义一起被删除"}, {"key": "B", "text": "只删除 Sno 取空值的那些元组"}, {"key": "C", "text": "Student 表的全部元组被删除，表定义仍保存在数据字典中"}, {"key": "D", "text": "语句语法错误，DELETE 必须带 WHERE 子句"}]', 'C', 'DELETE 省略 WHERE 表示删除表中所有元组，删除的是数据而不是表的定义，表定义仍在数据字典中，故 C 对、A 错；该语句与空值无关，B 错；WHERE 子句可省略，D 错。', '2', '教材 P110—P111；删除数据（改写）', '改问"删除表定义应使用哪条语句"。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0311', 1, 'KP0303', '填空', '在 SELECT 语句中若省略 DISTINCT 关键字，则默认使用关键字____，即查询结果中允许出现重复元组。', NULL, 'ALL', 'SELECT 的一般格式为 `SELECT [ALL|DISTINCT] <目标列表达式>`，不写 DISTINCT 时默认为 ALL，保留重复行；只有显式写 DISTINCT 才消除重复元组。', '1', '教材 P81 SELECT 语句一般格式（改写）', '改问"消除重复元组用哪个关键字，它作用于哪些列"。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0312', 1, 'KP0306', '填空', '在 FROM 子句中使用子查询生成派生表时，必须为派生表指定____；若子查询的目标列中含有聚集函数，还必须为派生表指定____。', NULL, '别名（表别名）；属性列名', '派生表是查询过程中生成的中间结果表，必须用别名才能在主查询中引用；含聚集函数时目标列没有可继承的列名，必须显式给出属性列名，否则无法引用该列。', '2', '教材 P107 基于派生表的查询（改写）', '改问"派生表在查询结束后是否保留"。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0313', 1, 'KP0308', '填空', '空值与另一个值（包括另一个空值）的比较运算结果为____；判断某属性是否取空值应使用____谓词，而不能用等号。', NULL, 'UNKNOWN；IS NULL', '空值代表"不知道""不存在"或"无意义"，参与比较时无法判定真假，结果为 UNKNOWN；SQL 用 IS NULL / IS NOT NULL 专门判断空值，`= NULL` 的比较结果恒为 UNKNOWN，起不到筛选作用。', '2', '教材 P111—P113；空值处理（改写）', '改问"NOT UNKNOWN 的结果是什么"。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0314', 1, 'KP0309', '填空', '若一个视图是从单个基本表导出的，并且只去掉了基本表的某些行和某些列，但保留了主码，这类视图称为____视图，它一般是可更新的。', NULL, '行列子集视图', '行列子集视图在行、列两个方向上都只是基本表的子集且保留主码，因而更新可唯一确定基本表中的元组，一般能正确转换为对基本表的更新；分组视图、带表达式的视图则不然。', '1', '习题册 P38 补充习题 填空③', '改问"分组视图为什么必须显式指定列名"。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0315', 1, 'KP0310', '简答', '简述视图的四点作用，并各用一句话说明其含义。', NULL, '① 对机密数据提供安全保护：为不同用户定义不同视图，使机密数据不出现在无权查看者的视图上；② 对重构数据库提供一定程度的逻辑独立性：基本表增加字段或垂直拆分后，可通过重新定义视图保持原有逻辑结构，应用程序不必修改；③ 简化用户操作：把多表连接等复杂操作对用户隐蔽起来，用户只面对一个视图；④ 使用户能以多种角度看待同一数据：同一基本表可按不同需求导出不同视图。', '四点分别对应安全性、逻辑独立性、操作简化和多视角，答题时不能只答"方便查询"而漏掉安全保护与逻辑独立性。视图只是提供了"一定程度"的逻辑独立性，并非完全独立。例如按院系建立视图同时体现安全保护和简化操作；把拆分后的基本表重新连接成原视图则体现逻辑独立性。答题应让例子与作用一一对应，而不是把同一个“查询方便”重复四次。', '1', '教材习题 3 第 7 题（P123）+ 教材 P121—P122', '改问"用 Student 按院系建 30 个视图说明哪一点作用"。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0316', 1, 'KP0307', '简答', 'INSERT INTO 语句有"在 INTO 中指定属性列"和"不指定属性列"两种写法，分别说明两种写法下 VALUES 子句必须满足的要求。', NULL, '指定属性列时，INTO 中未出现的列取空值（有默认值则取默认值），但这些列不能是 NOT NULL 列，因此 NOT NULL 列必须在 INTO 中列出并给值，且 VALUES 的常量个数、次序要与所列属性列一一对应。不指定属性列时，VALUES 必须按 CREATE TABLE 中属性列的定义次序，为每一个属性列给出值，一个也不能省略。', '两种写法的差别在于"属性列清单"是否与 VALUES 对齐；漏列 NOT NULL 列或漏给某列赋值都会因违反完整性约束而插入失败。例如表有 Sno、Sname 两列且均非空时，只写 INSERT INTO Student VALUES(''01'') 会因少值失败；若写 INSERT INTO Student(Sno) VALUES(''01'') 又会因 Sname 非空且没有默认值而失败。', '2', '教材习题 3 第 5 题⑪及习题册 P36 解答（改写）', '改问"用 INSERT ... SELECT 一次插入多个元组时的注意点"。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0317', 1, 'KP0304', '简答', '说明等值连接与自然连接的区别；并解释自身连接为什么必须为同一张表取两个不同的别名。', NULL, '等值连接以"="为连接运算符，结果表中保留两个表的全部属性列，包括重复的连接属性列；自然连接是在等值连接的基础上去掉结果表中重复的属性列，如 Student 与 SC 按 Sno 自然连接后只保留一个 Sno。自身连接是把一张表与其自身连接，若不取两个别名，DBMS 无法区分两个副本，也无法在连接条件中分别引用它们的属性，如 `Course FIRST, Course SECOND`。', '自然连接 = 等值连接 + 去掉重复列，二者结果列数不同；自身连接的关键是别名，连接条件中必须用别名前缀区分两个副本。例如 Course 与自身连接求间接先修课时，FIRST 表示当前课程副本，SECOND 表示先修课副本，FIRST.Cpno=SECOND.Cno 才能沿联系再走一步；不写别名就无法说明引用方向。', '2', '教材 P92—P94；等值连接、自然连接与自身连接（改写）', '改问"两表同名列在连接条件中为什么必须加表名前缀"。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0318', 1, 'KP0308', '简答', 'SC 表中某学生某门课缺考，Grade 取空值。请说明为什么 `SELECT * FROM SC WHERE Grade < 60;` 查不出该缺考记录，并给出能查出"缺考"记录的写法。', NULL, '空值参与比较运算的结果是 UNKNOWN，而不是 TRUE 或 FALSE；WHERE 子句只选出条件取值为 TRUE 的元组，UNKNOWN 与 FALSE 一样不被选出，因此 `Grade < 60` 查不到缺考记录。应改用 `SELECT * FROM SC WHERE Grade IS NULL;` 专门判断空值，若要连同不及格一起查，可写 `WHERE Grade < 60 OR Grade IS NULL`。', '本题的关键是把二值逻辑扩展为三值逻辑：TRUE / FALSE / UNKNOWN，只有 TRUE 能通过 WHERE。用 `Grade = NULL` 或 `Grade <> NULL` 同样得到 UNKNOWN，仍然查不出记录。同理 NOT (Grade>=60) 对空值仍为 UNKNOWN，也不会被选出。', '3', '教材 P111—P114；空值与三值逻辑；习题册 P37 补充习题 10（改写）', '改问"`NOT (Grade >= 60)` 能否查出缺考记录"。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0319', 1, 'KP0305', '简答', '解释相关子查询与不相关子查询的区别，并说明二者在求解方式上的不同。', NULL, '若子查询的查询条件不依赖于父查询，称为不相关子查询；若子查询的查询条件依赖于父查询的某个属性值，则称为相关子查询。不相关子查询由里向外分步求解，先一次求出子查询结果，再用它求解父查询；相关子查询的查询条件中含有父查询的元组变量，父查询每取一个元组就要重新求值一次子查询，必须反复求值。', '判断依据是"子查询是否引用父查询的属性"；由此决定执行方式是一次求值还是反复求值。例：`WHERE Sno IN (SELECT Sno FROM SC WHERE Cno=''81001'')` 为不相关子查询，`WHERE Grade > (SELECT AVG(Grade) FROM SC y WHERE y.Sno=x.Sno)` 为相关子查询。', '2', '习题册 P38 补充习题 问答①（答案见 P39）', '改问"相关子查询能否用连接查询改写"。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0320', 1, 'KP0309', '简答', '哪类视图一般是可以更新的？哪类视图是不可更新的？各举一例说明。', NULL, '基本表的行列子集视图一般是可更新的，例如由 Student 按专业导出的 IS_Student 视图，其行、列均为基本表的子集且保留了主码，对它的更新可转换为对 Student 的更新。属性来自聚集函数或表达式的视图不可更新，例如 `S_GradeAVG(Sno,Gavg)` 由 `SELECT Sno,AVG(Grade) FROM SC GROUP BY Sno` 定义，对 Gavg 的修改无法唯一对应到基本表的元组；由 `Sno,Sname,(...extract...)` 定义的 S_AGE 视图同理。', '判断依据是视图能否唯一映射回基本表的元组。还需区分"不可更新视图"与"不允许更新视图"：前者理论上已证明不可更新，后者由各 DBMS 的产品规定决定。行列子集视图保留主码，所以一行更新能定位基本表一行；聚集视图的一行对应多条基本表记录，平均值变化无法唯一分摊回每条成绩，因此不可更新。', '1', '教材习题 3 第 8 题（P123）+ 习题册 P36 解答', '改问"含 GROUP BY 的视图为什么不可更新"。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0321', 1, 'KP0303', '综合', '设供应商表 S(SNO, SNAME, STATUS, CITY)、零件表 P(PNO, PNAME, COLOR, WEIGHT)、工程项目表 J(JNO, JNAME, CITY)、供应情况表 SPJ(SNO, PNO, JNO, QTY)。写出找出所有供应商的姓名和所在城市的 SQL 语句。', NULL, 'SELECT SNAME, CITY
  FROM S;', '查询只涉及 S 一张表，目标列为 SNAME 与 CITY，无须 WHERE 条件；这是最基本的投影操作。若误加 SPJ 等表会产生不必要的连接甚至重复行，因此先判断查询所需列都在 S 表，再决定 FROM 只保留 S。这也说明 SQL 查询应按语义确定所需关系，而不是表越多越好。', '1', '教材习题 3 第 5 题①（P123），答案见习题册 P34', '改问"只查 CITY 并去掉重复值"。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0322', 1, 'KP0303', '综合', '设 Student(Sno, Sname, Ssex, Sbirthdate, Smajor)、Course(Cno, Cname, Ccredit, Cpno)、SC(Sno, Cno, Grade, Semester, Teachingclass)。写出查询课程号为 ''81001'' 且成绩在 80 至 90 分之间（含 80 和 90）的选课记录，输出学号、课程号和成绩，并按成绩从高到低排列的 SQL 语句。', NULL, 'SELECT Sno, Cno, Grade
  FROM SC
  WHERE Cno = ''81001''
    AND Grade BETWEEN 80 AND 90
  ORDER BY Grade DESC;', 'BETWEEN 80 AND 90 等价于 `Grade >= 80 AND Grade <= 90`，两端均包含；ORDER BY 默认 ASC，降序须写 DESC。ORDER BY 只改变结果输出顺序，不改变基本表的存储顺序。Grade 若为空则不会满足 BETWEEN 条件，因此不会被误纳入 80—90 分结果。', '2', '教材 P83—P87；单表查询（BETWEEN、ORDER BY），按教材表结构改写', '改问"成绩不在 80～90 之间"（NOT BETWEEN）。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0323', 1, 'KP0303', '综合', '设 Student(Sno, Sname, Ssex, Sbirthdate, Smajor)、Course(Cno, Cname, Ccredit, Cpno)、SC(Sno, Cno, Grade, Semester, Teachingclass)。写出按课程分组统计每门课程的选课人数和平均成绩，只输出选课人数不少于 3 人且平均成绩不低于 75 分的课程号、人数和平均成绩的 SQL 语句。', NULL, 'SELECT Cno, COUNT(*) AS Cnt, AVG(Grade) AS AvgGrade
  FROM SC
  GROUP BY Cno
  HAVING COUNT(*) >= 3 AND AVG(Grade) >= 75;', '分组统计用 GROUP BY Cno；对"组"的筛选条件必须写在 HAVING 中，不能写在 WHERE 中，因为 WHERE 作用于元组且不允许直接使用聚集函数。选课人数用 COUNT(*) 统计全部选课记录，不受 Grade 为空值影响。', '2', '教材 P89—P91；聚集函数与 GROUP BY / HAVING（改写）', '改问"统计每门课有成绩的人数"（应改用 COUNT(Grade)）。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0324', 1, 'KP0303', '综合', '设 Student(Sno, Sname, Ssex, Sbirthdate, Smajor)、Course(Cno, Cname, Ccredit, Cpno)、SC(Sno, Cno, Grade, Semester, Teachingclass)。写出查询 2000 年 1 月 1 日至 2001 年 12 月 31 日出生、且专业为"计算机科学与技术"或"信息安全"的学生的学号、姓名、出生日期和主修专业，并要求出生日期早的排在前的 SQL 语句。', NULL, 'SELECT Sno, Sname, Sbirthdate, Smajor
  FROM Student
  WHERE Sbirthdate BETWEEN ''2000-01-01'' AND ''2001-12-31''
    AND Smajor IN (''计算机科学与技术'', ''信息安全'')
  ORDER BY Sbirthdate ASC;', '日期范围用 BETWEEN 表达，两端包含；集合成员判断用 IN，比两个 OR 更简洁。AND 的优先级高于 OR，本题用 IN 后不存在优先级歧义；若改写成 OR 形式必须加括号。ORDER BY Sbirthdate 默认升序，即年龄大的在前。', '2', '教材 P85—P86；字符匹配、确定范围与 ORDER BY（改写）', '改问"专业不是这两个之一"（NOT IN）。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0325', 1, 'KP0305', '综合', '设 Student(Sno, Sname, Ssex, Sbirthdate, Smajor)、Course(Cno, Cname, Ccredit, Cpno)、SC(Sno, Cno, Grade, Semester, Teachingclass)。写出查询选修了全部课程的学生姓名的 SQL 语句，要求用 NOT EXISTS 实现，不得用 COUNT 比较课程门数。', NULL, 'SELECT Sname
  FROM Student
  WHERE NOT EXISTS
        (SELECT *
         FROM Course
         WHERE NOT EXISTS
               (SELECT *
                FROM SC
                WHERE Sno = Student.Sno
                  AND Cno = Course.Cno));', 'SQL 没有全称量词，按 (∀x)P ≡ ¬(∃x(¬P)) 转换：查询"没有一门课程是他没选的"学生。外层 NOT EXISTS 对每个学生判断，内层 NOT EXISTS 判断该学生是否缺少某门课，两重否定后即为选修全部课程的学生。用 COUNT 比较门数依赖数据一致性，且题目限定不得使用。', '3', '教材 P104 例 3.64（求选修全部课程的学生姓名）', '改问"至少选修了 20180002 所选全部课程的学生学号"（例 3.65，注意 DISTINCT 与自连接别名）。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0326', 1, 'KP0305', '综合', '设 Student(Sno, Sname, Ssex, Sbirthdate, Smajor)、Course(Cno, Cname, Ccredit, Cpno)、SC(Sno, Cno, Grade, Semester, Teachingclass)。写出查询每个学生成绩高于其本人平均成绩的学号和课程号的 SQL 语句，要求用相关子查询实现。', NULL, 'SELECT Sno, Cno
  FROM SC x
  WHERE Grade > (SELECT AVG(Grade)
                 FROM SC y
                 WHERE y.Sno = x.Sno);', '外层 SC 取别名 x，内层 SC 取别名 y，内层通过 `y.Sno = x.Sno` 引用外层当前元组的学号，因此是相关子查询，父查询每取一个元组，子查询就要重新求值一次。别名 x、y 称为元组变量，缺一不可，否则无法区分内外两层的 SC。', '3', '教材 P99—P100；例 3.59（相关子查询）', '改问"成绩不低于本人平均成绩"（把 > 改为 >=）。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0327', 1, 'KP0305', '综合', '设供应商表 S(SNO, SNAME, STATUS, CITY)、零件表 P(PNO, PNAME, COLOR, WEIGHT)、工程项目表 J(JNO, JNAME, CITY)、供应情况表 SPJ(SNO, PNO, JNO, QTY)。写出找出没有使用天津产的零件的工程项目代码的 SQL 语句。', NULL, 'SELECT JNO
  FROM J
  WHERE NOT EXISTS
        (SELECT *
         FROM SPJ, S
         WHERE SPJ.JNO = J.JNO
           AND SPJ.SNO = S.SNO
           AND S.CITY = ''天津'');', '思路是"不存在这样的供应记录：该工程使用了天津供应商供应的零件"。外层遍历 J，内层用 SPJ 与 S 连接查出天津供应商给该工程供货的记录；NOT EXISTS 在内层为空时返回真，即该工程没用过天津零件。也可先求天津供应商的 SNO 集合再用 NOT IN。', '3', '教材习题 3 第 5 题⑦（P123），答案见习题册 P35', '改问"找出使用了天津产零件的工程名称"（去掉 NOT，并按 JNO 连接 J 取 JNAME）。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0328', 1, 'KP0308', '综合', '设 Student(Sno, Sname, Ssex, Sbirthdate, Smajor)、Course(Cno, Cname, Ccredit, Cpno)、SC(Sno, Cno, Grade, Semester, Teachingclass)，其中 Grade 允许取空值表示缺考未录入。写出查询所有成绩为空值的选课记录（输出学号、课程号、学期）的 SQL 语句，并说明为什么 `Grade < 60` 查不到这些记录。', NULL, 'SELECT Sno, Cno, Semester
  FROM SC
  WHERE Grade IS NULL;', '判断空值只能用 IS NULL，写成 `Grade = NULL` 的比较结果为 UNKNOWN，起不到筛选作用。`Grade < 60` 对空值同样求值为 UNKNOWN，而 WHERE 只保留条件为 TRUE 的元组，所以缺考记录不会被选出；若要一并查出不及格，须写 `WHERE Grade < 60 OR Grade IS NULL`。', '2', '教材 P111—P114；空值处理；习题册 P37 补充习题 10（改写）', '改问"成绩非空且低于 60 分"（`Grade IS NOT NULL AND Grade < 60`）。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0329', 1, 'KP0309', '综合', '设 Student(Sno, Sname, Ssex, Sbirthdate, Smajor)、Course(Cno, Cname, Ccredit, Cpno)、SC(Sno, Cno, Grade, Semester, Teachingclass)。先建立视图 S_GradeAVG(Sno, Gavg)，存放每个学生的学号及平均成绩；再查询平均成绩不低于 90 分的学生学号和平均成绩。', NULL, 'CREATE VIEW S_GradeAVG(Sno, Gavg)
  AS
  SELECT Sno, AVG(Grade)
  FROM SC
  GROUP BY Sno;

  SELECT Sno, Gavg
  FROM S_GradeAVG
  WHERE Gavg >= 90;', '该视图的目标列含聚集函数，属于分组视图，必须在视图名后显式指定列名，否则第二列没有可继承的名字。这种视图不可更新，但可以查询：查询时系统取出视图定义做视图消解，把条件转换为对 SC 的分组查询。若改用 HAVING AVG(Grade)>=90 可直接得到同一结果；视图的价值在于复用平均成绩定义，而不是让聚合结果可更新。', '3', '教材 P120 例 3.91（分组视图）', '改问"不建视图直接用 GROUP BY + HAVING 写出等价查询"。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0330', 1, 'KP0310', '综合', '设 Student(Sno, Sname, Ssex, Sbirthdate, Smajor)。为使"计算机科学与技术"专业的系主任只能看到本专业学生，请建立视图 CS_Student（仅含学号、姓名、性别、出生日期四列），写出建立该视图并查询其中全部记录的 SQL 语句，并说明它体现的视图作用。', NULL, 'CREATE VIEW CS_Student
  AS
  SELECT Sno, Sname, Ssex, Sbirthdate
  FROM Student
  WHERE Smajor = ''计算机科学与技术'';

  SELECT *
  FROM CS_Student;', '视图只暴露本专业的行和部分列，其他专业的数据不出现在该视图上，因此对机密数据起到安全保护作用。该视图是从单个基本表导出、去掉部分行列但保留主码 Sno 的行列子集视图，一般是可更新的；若再加 WITH CHECK OPTION，可防止通过视图插入非本专业的记录。', '1', '教材 P115 例 3.86（行列子集视图），按"安全保护"作用改写', '改问"加上 WITH CHECK OPTION 后，插入其他专业学生会怎样"。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);


INSERT INTO question_kp (q_id, kp_id, is_primary) VALUES
('Q_0201', 'KP0201', 1),
('Q_0201', 'KP0205', 0),
('Q_0202', 'KP0202', 1),
('Q_0203', 'KP0203', 1),
('Q_0204', 'KP0204', 1),
('Q_0205', 'KP0205', 1),
('Q_0206', 'KP0206', 1),
('Q_0206', 'KP0207', 0),
('Q_0207', 'KP0207', 1),
('Q_0207', 'KP0206', 0),
('Q_0208', 'KP0208', 1),
('Q_0208', 'KP0206', 0),
('Q_0209', 'KP0209', 1),
('Q_0210', 'KP0210', 1),
('Q_0211', 'KP0210', 1),
('Q_0212', 'KP0207', 1),
('Q_0212', 'KP0206', 0),
('Q_0213', 'KP0201', 1),
('Q_0214', 'KP0205', 1),
('Q_0214', 'KP0210', 0),
('Q_0215', 'KP0206', 1),
('Q_0216', 'KP0207', 1),
('Q_0217', 'KP0208', 1),
('Q_0218', 'KP0202', 1),
('Q_0219', 'KP0202', 1),
('Q_0219', 'KP0201', 0),
('Q_0220', 'KP0203', 1),
('Q_0221', 'KP0204', 1),
('Q_0222', 'KP0206', 1),
('Q_0223', 'KP0207', 1),
('Q_0224', 'KP0206', 1),
('Q_0224', 'KP0207', 0),
('Q_0225', 'KP0202', 1),
('Q_0226', 'KP0209', 1),
('Q_0227', 'KP0210', 1),
('Q_0228', 'KP0210', 1),
('Q_0229', 'KP0210', 1),
('Q_0230', 'KP0210', 1),
('Q_0301', 'KP0301', 1),
('Q_0302', 'KP0302', 1),
('Q_0302', 'KP0307', 0),
('Q_0303', 'KP0302', 1),
('Q_0304', 'KP0301', 1),
('Q_0304', 'KP0309', 0),
('Q_0305', 'KP0309', 1),
('Q_0306', 'KP0308', 1),
('Q_0307', 'KP0305', 1),
('Q_0307', 'KP0303', 0),
('Q_0308', 'KP0304', 1),
('Q_0309', 'KP0306', 1),
('Q_0310', 'KP0307', 1),
('Q_0310', 'KP0302', 0),
('Q_0311', 'KP0303', 1),
('Q_0312', 'KP0306', 1),
('Q_0313', 'KP0308', 1),
('Q_0314', 'KP0309', 1),
('Q_0315', 'KP0310', 1),
('Q_0315', 'KP0309', 0),
('Q_0316', 'KP0307', 1),
('Q_0317', 'KP0304', 1),
('Q_0318', 'KP0308', 1),
('Q_0318', 'KP0303', 0),
('Q_0319', 'KP0305', 1),
('Q_0320', 'KP0309', 1),
('Q_0320', 'KP0310', 0),
('Q_0321', 'KP0303', 1),
('Q_0322', 'KP0303', 1),
('Q_0323', 'KP0303', 1),
('Q_0323', 'KP0308', 0),
('Q_0324', 'KP0303', 1),
('Q_0325', 'KP0305', 1),
('Q_0325', 'KP0306', 0),
('Q_0326', 'KP0305', 1),
('Q_0327', 'KP0305', 1),
('Q_0327', 'KP0304', 0),
('Q_0328', 'KP0308', 1),
('Q_0328', 'KP0303', 0),
('Q_0329', 'KP0309', 1),
('Q_0329', 'KP0303', 0),
('Q_0330', 'KP0310', 1),
('Q_0330', 'KP0309', 0)
ON DUPLICATE KEY UPDATE is_primary = VALUES(is_primary);

-- ============================================================
-- [question] seed-question-ch04.sql
-- ============================================================
-- 智学领航 · 第4章题库种子（由 csv_to_sql.py 生成）
-- 前置：init-v2.sql、seed-kp-ch04.sql；可重复执行（upsert）。


INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0401', 1, 'KP0401', '单选', '数据库的安全性是指（ ）。', '[{"key":"A","text":"保证数据库中数据的正确性和相容性"},{"key":"B","text":"保护数据库，防止不合法使用造成数据泄露、篡改或破坏"},{"key":"C","text":"提高数据库查询速度"},{"key":"D","text":"保证数据库在故障后能恢复"}]', 'B', '数据库安全性的防范对象是非法用户和非法操作，目标是防止数据泄露、篡改、破坏；完整性防范对象是不合语义、不正确的数据，目标是正确性和相容性。查询速度属于性能，故障恢复属于恢复技术，均不能与安全性混淆。', '1', '教材 P127', '可对比“完整性”的定义出反向题。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0402', 1, 'KP0401', '多选', '下列属于对数据库安全性产生威胁的因素有（ ）。', '[{"key":"A","text":"非授权用户对数据库的恶意存取和破坏"},{"key":"B","text":"数据库中重要或敏感数据被泄露"},{"key":"C","text":"计算机系统安全环境脆弱"},{"key":"D","text":"数据库磁盘容量不足"}]', 'ABC', '教材把威胁归为三类：非授权用户恶意存取和破坏、重要或敏感数据被泄露（含 SQL 注入）、安全环境脆弱（硬件、操作系统、网络协议）。磁盘容量不足属于资源与性能问题，不是安全性威胁。', '2', '教材 P128', '可把 SQL 注入单列为一个选项。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0403', 1, 'KP0401', '简答', '简述 TCSEC/TDI 的安全级别划分，并说明 C2 级与 B1 级 DBMS 的主要特征；CC 的评估保证级如何划分？', NULL, 'TCSEC/TDI 分 D、C1、C2、B1、B2、B3、A1 四组七级，可信程度递增。C2 为受控的存取保护，在 C1 自主存取控制基础上细化，以个人身份注册并实施审计和资源隔离；B1 为标记安全保护，对数据加标记并对主体、客体实施强制存取控制和审计，是真正意义上的安全产品。CC 用 EAL1—EAL7 七个评估保证级，保证程度递增，大致对应 C1—A1。', '记忆主线是“D 最低、C 组自主存取控制、B 组强制存取控制、A1 形式化验证”。C2 的关键词是审计与资源隔离，B1 的关键词是标记与 MAC；CC 的 EAL1—EAL7 与 TCSEC 级别只是大致对应，不是一一等同。答题时也不能把 EAL 当成产品功能清单，它表示产品经过评估后获得的保证程度。', '3', '教材 P128—130；习题 4 第 3 题', '可单独问“B1 为什么被称为真正意义的安全产品”。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0404', 1, 'KP0402', '单选', 'DBMS 提供的最外层安全保护措施是（ ）。', '[{"key":"A","text":"存取控制"},{"key":"B","text":"用户身份鉴别"},{"key":"C","text":"视图机制"},{"key":"D","text":"审计"}]', 'B', '用户进入系统前先做身份鉴别，只有合法用户才被允许进入，因此它是最外层保护。A 存取控制是在用户进入后检查其请求是否有权限，C 视图通过限定可见数据继续约束访问，D 审计则在操作发生后追查，三者都不是进入系统前的第一道关卡。“最外层”不等于可以替代后续控制。', '1', '教材 P132', '可把“最外层”改为“事后检查”再问一次。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0405', 1, 'KP0402', '填空', '用户标识由______和______两部分组成，其中______在系统整个生命周期内唯一；智能卡鉴别常与______结合使用，以弥补智能卡数据静态、可能被截取的缺陷。', NULL, '用户名；用户标识号（UID）；UID；个人识别号（PIN）', '教材 4.2.1 明确用户标识由 username 与 UID 组成，UID 生命周期唯一；智能卡读取的数据是静态的，可能被内存扫描或网络监听截取，实际采用 PIN 与智能卡结合，二者其一被窃取仍不致身份被冒充。', '2', '教材 P132—133', '可把“UID”与“用户名”顺序打乱出选择题。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0406', 1, 'KP0403', '单选', '存取控制机制主要由哪两部分组成（ ）。', '[{"key":"A","text":"身份鉴别与入侵检测"},{"key":"B","text":"定义用户权限与合法权限检查"},{"key":"C","text":"视图定义与审计"},{"key":"D","text":"加密与解密"}]', 'B', '存取控制先把用户权限登记到数据字典形成安全规则，再在每次存取请求时查字典做合法权限检查，二者共同构成存取控制子系统；身份鉴别、入侵检测、视图、审计、加密属于其他安全措施。', '1', '教材 P133', '可问“权限登记在哪里”，答案为数据字典。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0407', 1, 'KP0403', '简答', '比较自主存取控制（DAC）与强制存取控制（MAC）的主要区别，并说明 C2、B1 级 DBMS 各支持哪一种。', NULL, 'DAC 中不同用户对不同对象有不同权限，同一对象对不同用户也有不同权限，用户还可把权限转授他人，因而灵活；MAC 中每个对象标密级、每个用户发许可证，只有合法许可证才能存取，因而严格。C2 级支持 DAC，B1 级支持 MAC，并在 B1 增加标记与强制存取控制，同时保留审计。', '区分要点是“权限由谁决定”：DAC 由数据属主自主决定并可转授，MAC 由系统按密级与许可证强制检查、用户不可自行绕过。C2 是 DAC 加审计，B1 才引入标记与 MAC，并保留审计。DAC 的转授链可能把权限扩散到非预期用户，MAC 则用系统统一的信息流规则减少这类绕行，二者不能只按“严格程度”死记。', '2', '教材 P133', '可追问“为什么 DAC 仍可能无意泄露”。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0408', 1, 'KP0404', '单选', '在 GRANT 语句中，使被授权者能够把获得的权限再授予其他用户的子句是（ ）。', '[{"key":"A","text":"WITH CHECK OPTION"},{"key":"B","text":"WITH GRANT OPTION"},{"key":"C","text":"CASCADE"},{"key":"D","text":"RESTRICT"}]', 'B', 'WITH GRANT OPTION 赋予被授权者传播权；WITH CHECK OPTION 用于视图，保证增删改满足视图定义条件；CASCADE 与 RESTRICT 是 REVOKE 的收回方式，不能混用。', '1', '教材 P135', '可把四个子句放进同一道多选题。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0409', 1, 'KP0404', '综合', '已知关系模式：学生（学号，姓名，年龄，性别，家庭住址，班级号）、班级（班级号，班级名，班主任，班长）。请用 SQL 的 GRANT 语句完成：①授予用户 U1 对两个表的所有权限，并可给其他用户授权；②授予 U2 对学生表查看权限、对家庭住址更新权限；③将班级表查看权限授予所有用户；④将学生表查询、更新权限授予角色 R1；⑤将角色 R1 授予 U1，且 U1 可继续授权给其他角色。', NULL, '①GRANT ALL PRIVILEGES ON TABLE 学生,班级 TO U1 WITH GRANT OPTION; ②GRANT SELECT, UPDATE(家庭住址) ON TABLE 学生 TO U2; ③GRANT SELECT ON TABLE 班级 TO PUBLIC; ④CREATE ROLE R1; GRANT SELECT, UPDATE ON TABLE 学生 TO R1; ⑤GRANT R1 TO U1 WITH ADMIN OPTION;', '①全部权限用 ALL PRIVILEGES，可转授加 WITH GRANT OPTION；②列级 UPDATE 必须写列名；③PUBLIC 表示全体用户；④先建角色再给角色授权；⑤WITH ADMIN OPTION 使 U1 能把角色再授予其他角色。', '3', '习题 4 第 5 题；习题册 P42—P43；PDF 57—58', '可把“全体用户”改为“角色”再练一次。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0410', 1, 'KP0404', '简答', '写出 GRANT 语句的一般格式，并说明哪些用户可以发出 GRANT、TO PUBLIC 的含义，以及对属性列授权时需要注意什么。', NULL, '格式：GRANT <权限>[,<权限>] ON <对象类型><对象名>[,<对象类型><对象名>] TO <用户>[,<用户>] [WITH GRANT OPTION]。发出者可以是 DBA、数据库属主，或已拥有该权限的用户；PUBLIC 表示全体用户；对属性列授权必须写明列名，给列 INSERT 权限时必须包含主码列的 INSERT 权限，否则插入会因主码为空被拒绝。', 'GRANT 的四个要素是权限、对象、用户、是否可转授。易错点是把 PUBLIC 当成某个具体用户，以及列级 INSERT 只授普通列而漏掉主码列，导致插入被主码约束拒绝。WITH GRANT OPTION 只控制能否继续授权，并不扩大权限本身；对象类型、角色与列级写法还应与目标 DBMS 方言核对，不能把一个产品的语法当成所有产品通用。', '2', '教材 P135—136', '可让学生找出格式中哪一项可省略。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0411', 1, 'KP0405', '单选', 'REVOKE 语句中未指定 CASCADE 或 RESTRICT 时，默认行为是（ ）。', '[{"key":"A","text":"级联收回"},{"key":"B","text":"若权限已被转授则不能收回"},{"key":"C","text":"删除被授权用户"},{"key":"D","text":"同时收回该用户的所有权限"}]', 'B', 'REVOKE 默认 RESTRICT，若权限已被转授则不能收回；只有显式 CASCADE 才级联收回。REVOKE 不会删除用户，也不会波及与该权限无关的其他权限。', '2', '教材 P137', '可改成“已转授时如何收回”再问一次。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0412', 1, 'KP0405', '综合', 'U5 将对 SC 表的 INSERT 权限授予 U6（带 WITH GRANT OPTION），U6 又授予 U7（未带 WITH GRANT OPTION）。现执行 REVOKE INSERT ON TABLE SC FROM U5 CASCADE。请说明 U5、U6、U7 的权限变化；若 U6 还从其他用户处获得过 SC 表的 INSERT 权限，结果有何不同？', NULL, 'U5 的 INSERT 被收回；U6、U7 因权限直接或间接来自 U5，被级联收回。若 U6 还从其他用户处获得同一权限，则 U6 仍保留该权限，系统只收回从 U5 处获得的部分；U7 的权限来自 U6 从 U5 获得的授权链，通常随该链级联收回，但若 U7 另有其他合法授权来源，则仍保留其他来源的权限。', '级联收回先撤销 U5 自身的 INSERT，再沿 U5→U6→U7 的授权链传播，只收回直接或间接来自 U5 的权限；来自其他授权者的同一权限不受影响，所以判断时必须逐条追踪授权来源，不能简单把三人的权限全部清空。若改用默认 RESTRICT，由于 U5 已把权限转授给 U6，系统会拒绝这次收回，而不是只收 U5 并保留下游。', '3', '教材 P137—138；习题 4 第 7 题', '可把 CASCADE 换成 RESTRICT 对比结果。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0413', 1, 'KP0406', '单选', '数据库角色的本质是（ ）。', '[{"key":"A","text":"一个数据库用户"},{"key":"B","text":"一组与数据库操作相关的权限集合"},{"key":"C","text":"一张系统表"},{"key":"D","text":"一种加密算法"}]', 'B', '角色是被命名的一组权限，用于给一组具有相同权限要求的用户统一授权。A 错在用户是登录和持有会话的身份，角色本身不代表一个登录人；C 错在系统表只是保存角色定义与授权关系的载体，不是角色的本质；D 与权限管理无关。角色可授予用户或其他角色，从而复用权限集并集中收回。', '1', '教材 P139', '可问“角色与用户的区别”。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0414', 1, 'KP0406', '填空', '在 SQL 中创建角色使用______语句；给角色授权使用______语句；把角色授予用户或其他角色使用______语句，若允许获得者继续转授，需加______子句。', NULL, 'CREATE ROLE；GRANT；GRANT；WITH ADMIN OPTION', '角色管理分四步：CREATE ROLE 创建空角色；GRANT 权限 TO 角色；GRANT 角色 TO 用户或角色；需要继续转授时加 WITH ADMIN OPTION。注意角色转授用 ADMIN OPTION，权限转授用 GRANT OPTION，二者不要混。', '2', '教材 P139—140', '可把 ADMIN OPTION 与 GRANT OPTION 做成辨析题。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0415', 1, 'KP0406', '简答', '使用数据库角色管理权限有什么好处？以例 4.14 说明通过角色授予和收回权限的步骤。', NULL, '角色是权限集合，可为具有相同权限的一组用户统一授权，简化授权过程、便于集中管理。步骤：CREATE ROLE R1；GRANT SELECT,UPDATE,INSERT ON TABLE Student TO R1；GRANT R1 TO 王平,张明,赵玲；REVOKE R1 FROM 王平，一次性收回王平的三个权限。', '角色的价值在“一次定义、多次复用、一次收回”。例 4.14 先把三个权限打包进 R1，再把 R1 授予三人；收回时只撤角色成员关系，不必逐条撤三个权限，因此适合班级、岗位等批量授权场景。若目标是收回所有人的某一权限，应从角色定义撤该权限；若只撤某人的角色，则只影响该成员，两种操作的作用范围不能混淆。', '2', '教材 P140—141；例 4.14', '可问“给角色新增权限后已授予用户是否立即生效”。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0416', 1, 'KP0407', '单选', '强制存取控制策略是 TCSEC/TDI 哪一级安全级别的特色（ ）。', '[{"key":"A","text":"C1"},{"key":"B","text":"C2"},{"key":"C","text":"B1"},{"key":"D","text":"B2"}]', 'C', 'C1、C2 以自主存取控制为主，C2 增加审计与资源隔离；B1 起对主体、客体加标记并实施强制存取控制，是真正意义上的安全产品；B2 进一步要求形式化安全策略模型与结构化保护。习题册 P61 答案页 OCR 标 D，疑似错答，以教材 P129 为准选 C。', '1', '教材 P129', '可把 B1 与 B2 的特征做成对比题。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0417', 1, 'KP0407', '多选', '在强制存取控制中，下列存取规则正确的有（ ）。', '[{"key":"A","text":"仅当主体许可证级别大于或等于客体密级时，主体才能读该客体"},{"key":"B","text":"仅当主体许可证级别小于或等于客体密级时，主体才能写该客体"},{"key":"C","text":"主体许可证级别越高，越可以写低密级客体"},{"key":"D","text":"主体许可证级别越低，越可以读高密级客体"}]', 'AB', '读规则要求主体许可证级别大于或等于客体密级，写规则要求主体许可证级别小于或等于客体密级，所以 A、B 正确且等号边界允许同级读写。C 把写规则方向说反，高许可证写低密级会造成高密级信息降密外流；D 把读规则方向说反，低许可证不能读取高密级客体。记忆时可写成“读时许可不低于密级，写时许可不高于密级”。', '2', '教材 P142', '可把“等于”边界单独拿出来问。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0418', 1, 'KP0407', '简答', '强制存取控制为什么规定“仅当主体许可证级别小于或等于客体密级时才能写客体”？请举例说明违反该规则可能造成的后果。', NULL, '该规则禁止高许可证主体写低密级客体，防止主体把高密级数据降低密级后写回，使原本绝密或机密的数据被低权限用户读到。例如绝密级用户把绝密数据恶意降为公开级再写回，所有用户都能读到，造成绝密数据泄露；因此写规则与读规则共同保证信息只能由低密级向高密级流动，不能反向流动。', '写规则的核心是防止“高密级数据降密外流”。读规则只挡住低权限用户直接读取高密级客体，却挡不住高许可证主体把高密级内容写入低密级客体；一旦允许这种写入，低权限用户就能合法读取泄密副本。题中“绝密改为公开再写回”正是绕过读规则的旁路，因此必须用写规则封住高到低的信息流，而不是把高权限等同于可以随意改写密级。', '3', '教材 P142', '可让学生画一条“低→高允许、高→低禁止”的信息流图。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0419', 1, 'KP0408', '综合', '已知关系模式：职工（职工号，姓名，年龄，职务，工资，部门号）、部门（部门号，名称，经理名，地址，电话号）。请用 SQL 的 GRANT 语句（加上视图机制）完成：①王明对两个表有 SELECT 权限；②李勇对两个表有 INSERT 和 DELETE 权限；③每个职工只对自己的记录有 SELECT 权限；④刘星对职工表有 SELECT 权限、对工资字段有更新权限；⑤张新具有修改两个表结构的权限；⑥周平对两个表有所有权限并可给其他用户授权；⑦杨兰能查询每个部门职工的最高、最低、平均工资，但不能查看个人工资。', NULL, '①GRANT SELECT ON TABLE 职工 TO 王明; GRANT SELECT ON TABLE 部门 TO 王明; ②GRANT INSERT,DELETE ON TABLE 职工 TO 李勇; GRANT INSERT,DELETE ON TABLE 部门 TO 李勇; ③为每个职工建立只含本人记录的视图，如 CREATE VIEW V_EMP_E01 AS SELECT * FROM 职工 WHERE 职工号=''E01''; GRANT SELECT ON V_EMP_E01 TO 该职工;（教材的 GRANT ... WHEN USER=NAME 仅作备注，MySQL 8 不支持） ④GRANT SELECT,UPDATE(工资) ON TABLE 职工 TO 刘星; ⑤GRANT ALTER ON TABLE 职工 TO 张新; GRANT ALTER ON TABLE 部门 TO 张新;（教材写 ALTER TABLE 权限，MySQL 8 用 ALTER） ⑥GRANT ALL PRIVILEGES ON TABLE 职工 TO 周平 WITH GRANT OPTION; GRANT ALL PRIVILEGES ON TABLE 部门 TO 周平 WITH GRANT OPTION; ⑦CREATE VIEW 部门工资 AS SELECT 部门.部门号,部门.名称,MAX(工资),MIN(工资),AVG(工资) FROM 职工,部门 WHERE 职工.部门号=部门.部门号 GROUP BY 部门.部门号; GRANT SELECT ON 部门工资 TO 杨兰;', '①③④⑤⑥练对象级、列级、结构修改与转授权限；教材允许 ON TABLE 表1,表2 与 ALTER TABLE 权限，MySQL 8 一条 GRANT 只能一个对象、结构修改权限名为 ALTER，故答案给出拆写；③的 WHEN USER=NAME 是部分 DBMS 扩展，MySQL 不支持，标准做法是本人记录视图；⑦用聚合视图把部门汇总与个人工资分离。', '3', '习题 4 第 6 题；习题册 P43—P44；PDF 58—59', '可把⑦改为按部门分组的安全视图，再让学生写授权。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0420', 1, 'KP0408', '简答', '如何利用视图机制与授权机制实现“王平老师只能查询计算机科学与技术专业学生，张明老师可查询并增删改该专业学生”的权限控制？说明 WITH CHECK OPTION 的作用。', NULL, '先建视图 CS_Student：CREATE VIEW CS_Student AS SELECT * FROM Student WHERE Smajor=''计算机科学与技术'' WITH CHECK OPTION；再 GRANT SELECT ON CS_Student TO 王平；GRANT ALL PRIVILEGES ON CS_Student TO 张明。WITH CHECK OPTION 保证对该视图执行增删改时必须满足 Smajor=''计算机科学与技术''，防止通过视图写入不符合视图条件的数据。', '视图把“只能看本专业”的存取谓词固化到数据对象上，再在视图上授权，避免直接授予基本表权限。WITH CHECK OPTION 是视图定义的一部分，管的是写入数据是否满足视图条件，不是授权子句。若张明另获基本表增删改权限，就能绕过视图的专业限制，所以授权对象必须限定为 CS_Student；没有该选项时还可能通过视图把其他专业学生写入本专业视图。', '2', '教材 P142—143；例 4.17', '可把专业条件换成班级条件再练一次。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0421', 1, 'KP0409', '单选', '数据库审计属于（ ）安全机制。', '[{"key":"A","text":"事前预防"},{"key":"B","text":"事中控制"},{"key":"C","text":"事后检查"},{"key":"D","text":"数据加密"}]', 'C', '审计把用户操作记录到审计日志，供事后监控、重现事件和追查非法存取，因此选 C。A 事前预防依靠身份鉴别等措施阻挡非法进入，B 事中控制依靠存取检查决定是否允许当前操作，二者发生时间都不同；D 数据加密是另一项保护技术，不是按防护阶段划分的审计类别。审计能形成追责证据，但不等于已经实时阻止了操作。', '1', '教材 P143', '可把“事前/事中/事后”三组措施做成匹配题。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0422', 1, 'KP0409', '简答', '简述审计事件的主要类别、审计功能的主要内容，以及 AUDIT/NOAUDIT 与 BY ACCESS/BY SESSION 的含义。', NULL, '审计事件分服务器事件、系统权限、语句事件、模式对象事件四类。审计功能包括多种审计查阅方式、多套审计规则、审计分析与报表、审计日志管理：先转储后删除，只允许审计员查阅和转储，禁止普通用户新增修改。AUDIT 设置审计，NOAUDIT 取消审计；BY ACCESS 对每次审计操作都记录，BY SESSION 对每次会话中同类操作只记录最早一次。用户级审计任何用户可设，系统级审计只能由 DBA 设置。', '审计的四类事件按“服务器—权限—语句—对象”从粗到细排列；功能重点是日志管理与审计员专属权限。BY ACCESS 与 BY SESSION 的区别是“每次操作”与“每会话中同类操作的最早一次”，常被记反。BY SESSION 不是只审计登录，而是压缩同类记录；若需要完整还原每一次操作，应选择 BY ACCESS，并同步考虑审计日志的存储与转储。', '3', '教材 P143—145', '可只问 BY ACCESS 与 BY SESSION 的区别。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0423', 1, 'KP0410', '单选', '关于链路加密与端到端加密，下列说法正确的是（ ）。', '[{"key":"A","text":"链路加密只加密报文，不加密报头"},{"key":"B","text":"端到端加密只加密报文，不加密报头"},{"key":"C","text":"端到端加密对报文和报头都加密"},{"key":"D","text":"链路加密不需要中间节点参与"}]', 'B', '链路加密对报文和报头都加密，并由各中间节点的密码设备逐跳解密再加密，所以 A 的“不加密报头”和 D 的“不需要中间节点”都错。端到端加密只在发送端加密、接收端解密，中间节点无需密码设备；为让网络能够转发，报头保持明文，只加密报文，因此 B 对、C 错，其代价是报头仍可能被监听。', '2', '教材 P145—146', '可把 SSL 归入端到端加密再问一次。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0424', 1, 'KP0410', '填空', '存储加密分为______和______两种方式；传输加密常用______和______；SSL 属于______加密方式；SSL 会话密钥的生命周期仅限于______。', NULL, '透明存储加密；非透明存储加密；链路加密；端到端加密；端到端；本次通信', '透明存储加密是内核级、对用户隐蔽；非透明通过多个加密函数实现。传输加密分链路与端到端：链路加密对报头和报文都加密，端到端只加密报文不加密报头。SSL 是端到端方案，会话密钥仅限本次通信，避免固定密钥的存储问题。', '2', '教材 P145—146', '可把链路加密与端到端加密的报头差异做成选择题。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0425', 1, 'KP0411', '简答', '分别说明推理控制、隐蔽信道、数据隐私与“三权分立”要解决的问题，并给出一个推理控制或隐蔽信道的例子。', NULL, '推理控制防止用户利用函数依赖、敏感关联等从可访问的低密级信息推导出无权访问的高密级信息；隐蔽信道防止高安全等级用户利用系统机制向低安全等级用户传递信息；数据隐私控制个人数据在收集、存储、处理、发布各阶段不被非法获取，发布时用准标识符与 k 匿名、l 多样性、t 临近等标准；三权分立把数据库管理员过于集中的权限拆分，避免超级用户不受检查。例子：职务→工资函数依赖，同级职务用户可由自己工资推出他人工资；或利用 UNIQUE 列插入成功/失败约定信息位。', '四项都针对 MAC 之外或 DAC/MAC 未覆盖的泄露路径：推理控制防“推导”，隐蔽信道防“旁路传递”，数据隐私防“发布与关联识别”，三权分立防“管理员权限过大”。答题要抓住各自的防范对象，不能只说名词。例如平均成绩本身可公开，但结合个别已知成绩仍可能反推出特定学生，说明可直接访问的数据不敏感，并不等于推导结果也安全。', '3', '教材 P147—148', '可只让学生设计一个隐蔽信道场景。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0426', 1, 'KP0411', '单选', '数据库推理控制主要防范的是（ ）。', '[{"key":"A","text":"用户利用函数依赖等从可访问数据推出更高密级数据"},{"key":"B","text":"高密级用户利用 UNIQUE 约束向低密级用户传递信息"},{"key":"C","text":"审计日志被普通用户修改"},{"key":"D","text":"传输报文被窃听或篡改"}]', 'A', '推理控制防的是“推导”：用户利用函数依赖、敏感关联和背景知识，从自己有权访问的低密级数据推出无权访问的高密级数据。B 属于隐蔽信道，C 属于审计日志管理，D 属于传输加密要解决的问题。', '2', '教材 P147', '可把推理控制与隐蔽信道的例子互换出题。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

-- ============================================================
-- [question] seed-question-ch05.sql
-- ============================================================
-- 智学领航 · 第5章题库种子（由 csv_to_sql.py 生成）
-- 前置：init-v2.sql、seed-kp-ch05.sql；可重复执行（upsert）。


INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0501', 1, 'KP0501', '单选', '数据库的完整性是指（ ）。', '[{"key":"A","text":"数据的正确性和相容性"},{"key":"B","text":"数据不被非法用户访问"},{"key":"C","text":"数据库的高可用性"},{"key":"D","text":"数据库的并发控制能力"}]', 'A', '完整性指数据的正确性和相容性：正确性要求数据符合现实世界语义、反映当前实际状况；相容性要求同一对象在不同关系表中一致。防非法用户属于安全性，高可用属于可靠性，并发控制属于事务管理。', '1', '教材 P151', '可对比“安全性”的定义出反向题。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0502', 1, 'KP0501', '多选', '关系数据库管理系统的完整性控制机制应具备的功能有（ ）。', '[{"key":"A","text":"提供定义完整性约束的机制"},{"key":"B","text":"提供检查完整性约束的方法"},{"key":"C","text":"提供违约处理的方法"},{"key":"D","text":"提供数据加密与解密"}]', 'ABC', 'RDBMS 的完整性控制机制包括定义、检查、违约处理三方面：先把约束定义并存入数据字典，再在 INSERT/UPDATE/DELETE 后检查，发现违约时拒绝或级联处理。加密解密属于安全性措施，不是完整性控制机制。', '2', '教材 P152', '可问三方面功能的执行顺序。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0503', 1, 'KP0501', '简答', '数据库的完整性与安全性有什么区别和联系？', NULL, '完整性是防止数据库中存在不符合语义、不正确的数据，防范对象是不合语义的数据；安全性是保护数据库防止恶意破坏和非法存取，防范对象是非法用户和非法操作。二者既有区别又有联系：完整性约束和安全性控制都由 DBMS 统一实施，触发器等技术既可用于完整性检查，也可用于安全控制（如审计、入侵检测）。', '区分要点是“防什么”：完整性防错误数据，安全性防非法访问。二者不是对立概念，DBMS 往往共用约束、触发器和数据字典机制；答题时既要说区别，也要说联系。例如 CHECK 拒绝负成绩属于完整性，GRANT 后仍拒绝无权限读取属于安全性；二者可共同约束同一次操作，但失败原因不同。', '2', '习题 5 第 2 题；教材 P152', '可让学生各举一个完整性/安全性措施例子。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0504', 1, 'KP0502', '单选', '在 CREATE TABLE 中定义实体完整性使用（ ）短语。', '[{"key":"A","text":"FOREIGN KEY"},{"key":"B","text":"PRIMARY KEY"},{"key":"C","text":"CHECK"},{"key":"D","text":"UNIQUE"}]', 'B', '实体完整性由 PRIMARY KEY 定义，保证主码唯一且非空。FOREIGN KEY 定义参照完整性，CHECK 定义用户定义完整性中的条件约束，UNIQUE 只保证列值唯一但不等于主码。', '1', '教材 P153', '可把四种约束短语做成匹配题。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0505', 1, 'KP0502', '填空', '实体完整性检查包括主码值是否______、主码各属性是否______；多属性主码必须在______定义；为避免全表扫描，DBMS 通常在主码上建立______。', NULL, '唯一；为空（非空）；表级；索引（B+ 树索引）', '定义主码后，插入或更新主码列时 DBMS 自动检查唯一性和非空性，任一不满足即拒绝。单属性主码可列级或表级定义，多属性主码只能表级定义；主码上通常自动建索引，用 B+ 树查找替代全表扫描。', '2', '教材 P153—154', '可把“全表扫描 vs 索引”做成对比题。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0506', 1, 'KP0502', '综合', '已知 Student(Sno,Sname,Ssex,Sbirthdate,Smajor)，SC(Sno,Cno,Grade,Semester,Teachingclass)。请分别用列级和表级方式为 Student 定义主码 Sno，并为 SC 定义主码 (Sno,Cno)，说明多属性主码为什么只能表级定义。', NULL, 'Student 列级：Sno CHAR(8) PRIMARY KEY；Student 表级：PRIMARY KEY(Sno)。SC：PRIMARY KEY(Sno,Cno)。多属性主码由多个属性共同构成，列级约束一次只能约束一个列，无法表达属性组，因此必须在表级定义。', '单属性主码两种写法等价；多属性主码必须用表级 PRIMARY KEY(列组)。答题时要写出完整列定义或至少写出主码子句，并解释“属性组”这一关键原因，不能只背语法。若把 PRIMARY KEY(Sno、Cno) 错写在 Sno 的列级定义后，语义就会变成 Sno 单列主码，导致同一学生不能再选多门课。检查时也只有两列共同唯一且两列均非空，才符合选课实体的标识要求。', '3', '教材 P153；例 5.1/5.2', '可让学生再写一个三属性主码的例子。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0507', 1, 'KP0503', '单选', '定义参照完整性时，用（ ）短语指明外码参照哪个表的主码。', '[{"key":"A","text":"PRIMARY KEY"},{"key":"B","text":"FOREIGN KEY"},{"key":"C","text":"REFERENCES"},{"key":"D","text":"CHECK"}]', 'C', 'FOREIGN KEY 用来定义哪些列是外码，REFERENCES 用来指明外码参照的被参照表和主码；两者配合完成参照完整性定义。PRIMARY KEY 定义主码，CHECK 定义条件约束。', '1', '教材 P155', '可把 FOREIGN KEY 与 REFERENCES 的作用做成填空。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0508', 1, 'KP0503', '填空', 'SC 表的 Sno 参照 Student 表的 Sno，则 SC.Sno 是______，Student 是______；外码列是否允许空值应由______决定。', NULL, '外码；被参照表；应用语义', 'FOREIGN KEY(Sno) REFERENCES Student(Sno) 中，Sno 是外码，Student 是被参照表。外码能否为空不是语法问题，而要看应用语义：学生主修专业未定可为空，SC 的 Sno 是主属性则不能为空。', '2', '教材 P155—156', '可换一个外码场景再练一次。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0509', 1, 'KP0504', '多选', '下列可能破坏参照完整性的操作有（ ）。', '[{"key":"A","text":"向参照表 SC 插入元组，其 Sno 在被参照表 Student 中不存在"},{"key":"B","text":"修改参照表 SC 的外码值，修改后在被参照表中找不到对应主码"},{"key":"C","text":"删除被参照表 Student 的元组，使 SC 中某些元组失去参照"},{"key":"D","text":"修改被参照表 Student 的主码值，使 SC 中某些元组失去参照"}]', 'ABCD', '参照完整性有四种破坏情况：参照表插入、参照表修改外码值、被参照表删除、被参照表修改主码值。A/B 属于参照表侧，C/D 属于被参照表侧；查询不会破坏参照完整性。', '2', '教材 P155；表 5.1', '可去掉一个正确项改成三选二。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0510', 1, 'KP0504', '单选', '参照完整性违约处理中，系统一般采用的默认策略是（ ）。', '[{"key":"A","text":"级联删除"},{"key":"B","text":"设置为空值"},{"key":"C","text":"拒绝执行（NO ACTION）"},{"key":"D","text":"忽略违约并继续执行"}]', 'C', '教材明确默认策略是拒绝执行（NO ACTION）；若希望级联或置空，必须在创建参照表时用 ON DELETE/ON UPDATE 显式说明。忽略违约会破坏数据一致性。', '1', '教材 P156', '可问“想级联要在哪里说明”。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0511', 1, 'KP0504', '简答', '参照完整性违约处理有哪三种策略？例 5.4 中 SC 对 Student、Course 分别采用了什么策略？说明删除与更新能否使用不同策略。', NULL, '三种策略：拒绝执行（NO ACTION，默认）、级联操作（CASCADE）、设置为空值（SET NULL，要求外码允许空值）。例 5.4 中 SC 对 Student 的删除和更新都用 CASCADE；对 Course 的删除用 NO ACTION，更新用 CASCADE。可以对 DELETE 和 UPDATE 采用不同策略，具体由应用语义决定。', '三种策略要按“拒绝/级联/置空”顺序记，并写明 SET NULL 的前提。例 5.4 的关键是同一外码在删除和更新上可采用不同策略，说明策略是分事件配置的，不是一张表只能选一种。例如删除仍有选课记录的课程时，NO ACTION 可阻止悬空外码；更新课程号时用 CASCADE 可同步外码，但执行前仍应确认会影响多少行。', '3', '教材 P156—157；例 5.4', '可改成“删除拒绝、更新级联”的反向题。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0512', 1, 'KP0505', '单选', '下列不属于用户定义完整性中属性上约束的是（ ）。', '[{"key":"A","text":"NOT NULL"},{"key":"B","text":"UNIQUE"},{"key":"C","text":"CHECK"},{"key":"D","text":"FOREIGN KEY"}]', 'D', '属性上的用户定义完整性包括 NOT NULL、UNIQUE、CHECK；FOREIGN KEY 定义的是参照完整性，不属于用户定义完整性的属性约束。', '1', '教材 P158', '可把四类约束归到三类完整性中。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0513', 1, 'KP0505', '多选', '关于用户定义的完整性，下列说法正确的有（ ）。', '[{"key":"A","text":"属性约束可用 NOT NULL、UNIQUE、CHECK 定义"},{"key":"B","text":"元组 CHECK 可以约束不同属性之间的取值关系"},{"key":"C","text":"违约时 DBMS 一般拒绝执行该操作"},{"key":"D","text":"用户定义完整性只能由应用程序实现"}]', 'ABC', '用户定义完整性由 RDBMS 提供定义和检查机制：属性约束用 NOT NULL/UNIQUE/CHECK，元组 CHECK 可跨属性，违约时拒绝执行。D 错在“只能由应用实现”，教材明确不必由应用程序完成。', '2', '教材 P158—159', '可问“元组 CHECK 与属性 CHECK 的区别”。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0514', 1, 'KP0505', '简答', '写出 SQL 片段：Student 表的 Ssex 只允许取“男”或“女”；性别为男时 Sname 不能以 Ms. 开头；SC 表的 Grade 必须在 0—100 之间。', NULL, 'Ssex CHAR(6) CHECK(Ssex IN (''男'',''女''))；表级 CHECK(Ssex=''女'' OR Sname NOT LIKE ''Ms.%'')；Grade SMALLINT CHECK(Grade>=0 AND Grade<=100)。', '前两个约束分别演示属性级 CHECK 与元组级 CHECK：Ssex 只约束单列，性别与姓名的组合约束必须放在表级 CHECK。Grade 用 AND 连接上下界，注意闭区间与 NULL 的处理由 DBMS 语义决定。Sname NOT LIKE ''Ms.%'' 中的百分号匹配任意后缀，OR 则把“若为男性就不能以 Ms. 开头”的蕴涵改写成可执行的布尔条件。', '3', '教材 P159；例 5.7/5.9', '可把“男/女”换成“1/0”再练一次。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0515', 1, 'KP0506', '单选', '在 CREATE TABLE 中使用 CONSTRAINT 子句的主要作用是（ ）。', '[{"key":"A","text":"提高查询速度"},{"key":"B","text":"给完整性约束命名，便于增删和维护"},{"key":"C","text":"自动创建索引"},{"key":"D","text":"把约束转为触发器"}]', 'B', 'CONSTRAINT 给 NOT NULL、UNIQUE、PRIMARY KEY、FOREIGN KEY、CHECK 等约束命名，命名后可用 ALTER TABLE DROP/ADD CONSTRAINT 灵活增删。它不负责查询优化或自动建索引，也不等于触发器。', '1', '教材 P160', '可问“删除命名约束用什么语句”。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0516', 1, 'KP0506', '综合', '已知 Student 表已用 C3 命名了出生日期约束。请写出 SQL：删除 C3；增加 C4，要求出生日期在 1985-1-1 之后。', NULL, 'ALTER TABLE Student DROP CONSTRAINT C3; ALTER TABLE Student ADD CONSTRAINT C4 CHECK(Sbirthdate > ''1985-1-1'');', '修改命名约束的标准做法是先 DROP 再 ADD；不能直接修改已有 CHECK 的条件。DROP 要写约束名 C3，ADD 要写新约束名 C4 与完整 CHECK 表达式，两步顺序不能颠倒。执行前还应核对现有数据：若已有出生日期不满足新条件，ADD CHECK 可能失败；只改约束名而不写完整条件也无法表达新规则。', '3', '教材 P161；例 5.13', '可把日期条件换成学号区间再练一次。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0517', 1, 'KP0507', '单选', 'CREATE DOMAIN 的主要作用是（ ）。', '[{"key":"A","text":"创建一个基本表"},{"key":"B","text":"建立一个带完整性约束、可被多个属性复用的域"},{"key":"C","text":"创建一个视图"},{"key":"D","text":"创建一个触发器"}]', 'B', '域是一组相同数据类型的值的集合，CREATE DOMAIN 可同时声明域上的 CHECK 约束；多个属性可来自同一域，域约束改变时只改域定义。它不创建表、视图或触发器。MySQL 8 不支持 CREATE DOMAIN，本题按标准 SQL/纸面作答。', '1', '教材 P161', '可问“修改域约束用什么语句”。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0518', 1, 'KP0507', '填空', 'CREATE DOMAIN 用______子句声明域取值必须满足的条件；给域约束命名用______子句；删除或增加域约束使用______语句。', NULL, 'CHECK；CONSTRAINT；ALTER DOMAIN', '例 5.14 用 CHECK(VALUE IN (''男'',''女'')) 约束域；例 5.15 用 CONSTRAINT GD 命名域约束；例 5.16/5.17 用 ALTER DOMAIN ... DROP/ADD CONSTRAINT 修改域约束。注意 VALUE 代表域中的值。MySQL 8 不支持 CREATE/ALTER DOMAIN，本题按标准 SQL/纸面作答。', '2', '教材 P161—162', '可把 CREATE DOMAIN 与 CREATE TABLE 的约束写法对比。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0519', 1, 'KP0508', '单选', 'SQL 断言（ASSERTION）最适合用来表达（ ）。', '[{"key":"A","text":"单列的非空约束"},{"key":"B","text":"单表的列值范围约束"},{"key":"C","text":"涉及多个表或聚集操作的一般完整性约束"},{"key":"D","text":"外码的级联删除策略"}]', 'C', '断言是声明性的一般完整性约束，条件可涉及多个表或 COUNT/SUM 等聚集操作，比单表 CHECK 更通用。单列非空/范围用 NOT NULL、CHECK 即可；级联策略属于参照完整性违约处理。MySQL 8 不支持 CREATE ASSERTION，本题按标准 SQL/纸面作答。', '2', '教材 P162；习题 5 第 8 题', '可把断言与表级 CHECK 做对比。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0520', 1, 'KP0508', '填空', '创建断言的语句是 CREATE ASSERTION <断言名> ______ (<条件>)；断言的条件可以涉及多个表和______操作。', NULL, 'CHECK；聚集（COUNT、SUM 等）', '断言用 CHECK 子句给出条件，条件可跨表并可使用 COUNT、SUM 等聚集函数，因此能表达“两会总人数不超过 50”这类表级 CHECK 难以表达的约束。断言属于 SQL 标准，部分 DBMS 未必实现；MySQL 8 不支持，本题按标准 SQL/纸面作答。', '3', '教材 P162；习题 5 第 8 题', '可让学生把断言改写成触发器对比。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0521', 1, 'KP0508', '综合', '（纸面 SQL）已知 Male、Female 两个关系分别记录男宾、女宾信息。请用标准 SQL 断言把来宾总人数限制在 50 人以内，并说明断言的检查时机。', NULL, 'CREATE ASSERTION Party CHECK((SELECT COUNT(*) FROM Male)+(SELECT COUNT(*) FROM Female)<=50); 断言由 DBMS 在相关数据发生变化时检查，条件不满足则拒绝该操作。', '断言条件是两个 COUNT 子查询之和 <=50，涉及两个表，无法用单表 CHECK 表达。检查由 DBMS 统一执行，不需要应用在每次插入前手写校验；这也正是断言相对普通约束的优势。MySQL 8 不支持 CREATE ASSERTION，本题只要求写出标准 SQL，不要求上机执行。', '3', '习题 5 第 8 题；习题册 P49；PDF 64', '可把 50 改成 100 或改为“男女各不超过 30”。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0522', 1, 'KP0509', '单选', '触发器又被称为（ ）。', '[{"key":"A","text":"实体-联系规则"},{"key":"B","text":"事件-条件-动作规则"},{"key":"C","text":"主码-外码规则"},{"key":"D","text":"定义-检查-违约规则"}]', 'B', '触发器是事件驱动的特殊过程，教材称其为事件-条件-动作规则（ECA）：事件发生时检查条件，条件为真则执行动作体。实体-联系属于概念模型，主码-外码属于完整性约束，定义-检查-违约是完整性控制机制的三方面。', '2', '教材 P162—163', '可让学生画 ECA 三要素。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0523', 1, 'KP0509', '多选', '关于触发器，下列说法正确的有（ ）。', '[{"key":"A","text":"触发器只能定义在基本表上，不能定义在视图上"},{"key":"B","text":"BEFORE 触发器在触发事件执行之前激活"},{"key":"C","text":"行级触发器可以在动作体中使用 OLD/NEW"},{"key":"D","text":"语句级触发器可以在动作体中使用 OLD/NEW"}]', 'ABC', '触发器只能建在基本表；BEFORE 在事件前、AFTER 在事件后；行级触发器可用 OLD/NEW，语句级不能用 OLD/NEW（可用 OLD TABLE/NEW TABLE）。D 错在把行级特性套到语句级。', '2', '教材 P163—164', '可把 OLD/NEW 与 OLD TABLE/NEW TABLE 做成匹配题。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0524', 1, 'KP0509', '填空', '触发器按触发动作的间隔分为______和______；行级触发器用______引用修改前/后的值；同表触发器的执行顺序为______→SQL 语句→______。', NULL, '行级触发器（FOR EACH ROW）；语句级触发器（FOR EACH STATEMENT）；OLD/NEW；BEFORE；AFTER', '行级触发器每影响一行执行一次，语句级每条语句执行一次；只有行级能用 OLD/NEW。执行顺序固定为 BEFORE 触发器、触发 SQL、AFTER 触发器；同表多个同类触发器按创建顺序执行。', '2', '教材 P164—165', '可问“语句级为什么不能用 OLD/NEW”。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0525', 1, 'KP0509', '综合', '以教材例5.20（教授工资不得低于4000元）为例，写出 MySQL 8 可执行的 BEFORE UPDATE 触发器；给出教材标准 SQL 写法的对照，并说明触发器的执行顺序与性能风险。', NULL, 'MySQL 8：DELIMITER $$ CREATE TRIGGER Update_Sal BEFORE UPDATE ON Teacher FOR EACH ROW BEGIN IF NEW.job=''教授'' AND NEW.sal<4000 THEN SET NEW.sal=4000; END IF; END$$ DELIMITER ; 教材标准 SQL：CREATE TRIGGER Update_Sal BEFORE UPDATE ON Teacher REFERENCING NEW AS newTuple FOR EACH ROW BEGIN IF (newTuple.job=''教授'') AND (newTuple.sal<4000) THEN newTuple.sal := 4000; END IF; END; 执行顺序：BEFORE 触发器→SQL 语句→AFTER 触发器；同表多个同类触发器按创建顺序（部分 DBMS 按名称序）执行。触发器每次访问表都可能激活，增加开销；触发链可能导致难以预见的错误。', 'MySQL 8 用 NEW 直接引用新行、SET 赋值，并用 DELIMITER 包裹过程体；教材标准 SQL 用 REFERENCING NEW AS newTuple 与 := 赋值，二者都是 BEFORE 行级触发器。执行顺序与性能风险是触发器通用知识：BEFORE→SQL→AFTER，慎用触发链。', '3', '教材 P165；例 5.20', '可把“教授”改成“副教授”或工资阈值再练一次。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

-- ============================================================
-- [question] seed-question-ch06.sql
-- ============================================================
-- 智学领航 · 第6章题库种子（由 csv_to_sql.py 生成）
-- 前置：init-v2.sql、seed-kp-ch06.sql；可重复执行（upsert）。


INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0601', 1, 'KP0601', '单选', '下列哪项不是不良关系模式带来的问题（ ）。', '[{"key":"A","text":"数据冗余"},{"key":"B","text":"插入异常"},{"key":"C","text":"删除异常"},{"key":"D","text":"查询结果必为空"}]', 'D', '不良模式通常带来数据冗余（A）、插入异常（B）、删除异常（C），还可能带来更新异常；这些都会浪费空间或破坏一致性。D“查询结果必为空”取决于数据与查询条件，与模式设计好坏无关，也不是不良模式的必然问题。题干问“不是问题”，故选 D。', '1', '教材 P171—173', '可让学生各举一个异常例子。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0602', 1, 'KP0601', '简答', '以例6.1 Student(Sno,School,Mname,Cno,Grade) 为例，说明不良模式会产生哪些异常，并写出一种分解思路。', NULL, '该模式存在数据冗余（院长姓名重复）与三类异常：更新异常（换院长要改多条）、插入异常（新学院无学生无法存入）、删除异常（学生毕业删记录会丢失学院与院长信息）。原因是函数依赖存在不良性质；可分解为 S(Sno,School)、SC(Sno,Cno,Grade)、SCHOOL(School,Mname)，分别保留 Sno→School、(Sno,Cno)→Grade、School→Mname。', '三类异常（更新/插入/删除）要逐一对应例6.1的语义；数据冗余是问题的根源而不是“异常”。分解的依据是把传递依赖 School→Mname 与部分依赖 Sno→School 从原模式中分离，使每个新模式只保留必要的函数依赖。注意分解不是简单把属性平均分组，而要保留Sno→School、School→Mname及选课码—成绩依赖，并检查自然连接能否恢复原关系。', '2', '教材 P172—P174；例6.1', '可换成“学院/学生/课程”的其他语义再练。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0603', 1, 'KP0602', '单选', '函数依赖 X→Y 成立的含义是（ ）。', '[{"key":"A","text":"对 R 的任一关系，任意两元组在 X 上值相等则在 Y 上值也相等"},{"key":"B","text":"对 R 的某一关系，存在两元组在 X 上值相等而 Y 上不等"},{"key":"C","text":"X 的值一定小于 Y 的值"},{"key":"D","text":"Y 的值可以决定 X 的值"}]', 'A', 'A 正确：函数依赖要求 R 的所有可能关系都满足“X 值相同则 Y 值相同”，不是某一个实例。B 把“存在违反”当成定义，正好相反；C 把依赖误解为数值大小；D 把决定方向反过来。只要存在一个关系实例违反 X→Y，该依赖就不成立，故选 A。', '1', '教材 P174', '可给一个违反 FD 的具体表让学生判断。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0604', 1, 'KP0602', '多选', '关于函数依赖，下列说法正确的有（ ）。', '[{"key":"A","text":"X→Y 且 Y⊄X 为非平凡函数依赖"},{"key":"B","text":"X→Y 且 X 的任何真子集都不能决定 Y，则 Y 完全函数依赖于 X"},{"key":"C","text":"X→Y、Y→Z 且满足传递依赖条件时，Z 传递函数依赖于 X"},{"key":"D","text":"X→Y 且 Y⊆X 表示 X 部分函数依赖于 Y"}]', 'ABC', 'A 是非平凡定义；B 是完全函数依赖定义；C 是传递函数依赖定义。D 错：Y⊆X 时 X→Y 是平凡函数依赖，不是部分依赖；部分依赖讨论的是 X 的真子集能否决定 Y。', '2', '教材 P174—175', '可把完全/部分/传递做成三道判断。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0605', 1, 'KP0602', '填空', 'X→Y 且 Y⊄X 称为______；X 称为该函数依赖的______；X→Y 且 X 的任何真子集都不能决定 Y，称为______；X→Y、Y→Z 且满足附加条件时，称为______。', NULL, '非平凡函数依赖；决定因素；完全函数依赖；传递函数依赖', '四个空对应函数依赖的四个基本术语。注意平凡/非平凡看 Y 是否被 X 包含；决定因素是左部；完全依赖看真子集；传递依赖还要满足 Y⊄X、Y≠X、Z⊄Y 等条件。', '2', '教材 P174—175', '可把“平凡”与“非平凡”互换出题。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0606', 1, 'KP0603', '单选', '设 K 为 R(U,F) 的属性或属性组，K 成为候选码的条件是（ ）。', '[{"key":"A","text":"K→U 且 U 完全函数依赖于 K（K 的任何真子集都不能决定 U）"},{"key":"B","text":"U→K"},{"key":"C","text":"K→Y 且 Y⊂U"},{"key":"D","text":"K 是单个属性"}]', 'A', '候选码要求 K→U，并且 U 完全函数依赖于 K，即 K 的任何真子集都不能决定 U；只满足 K→U 而 U 部分依赖 K 时，K 只是超码。B 方向反了；C 只决定部分属性；D 单属性只是候选码的一种形态。故选 A。', '1', '教材 P176', '可问“候选码与超码的区别”。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0607', 1, 'KP0603', '综合', '给定 R(U,F)，U={A,B,C,D,E}，F={B→D, DE→C, EC→B}。求 R 的所有候选码，并指出主属性与非主属性。', NULL, 'N={A}，L={E}，LR={B,C,D}，OnlyRight=∅。候选码必须包含 A、E，迭代枚举 LR 中属性，得到 (AEB)+=(AEC)+=(AED)+=ABCDE，所以候选码为 AEB、AEC、AED；A、B、C、D、E 都是主属性，无非主属性。', '求码用“N/L/OnlyRight/LR”分类法：只出现在左部的属性和未出现的属性必在码中，只出现在右部的属性（OnlyRight）必不在码中；再枚举 LR 属性求闭包。本题 A 未出现、E 只在左部，故码必含 AE，逐个加 B/C/D 即得三个候选码。', '3', '习题6第4题（教材 P197）；习题册 PDF73（印刷P58）答案', '可改一个 FD 让候选码数量变化。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0608', 1, 'KP0604', '单选', '关系模式 R 属于 2NF 的条件是（ ）。', '[{"key":"A","text":"R 的每个分量不可分"},{"key":"B","text":"R 的每个非主属性都完全函数依赖于任一候选码"},{"key":"C","text":"R 的每个属性都完全函数依赖于主码"},{"key":"D","text":"R 中不存在传递函数依赖"}]', 'B', '2NF 在 1NF 基础上要求每个非主属性完全函数依赖于任一候选码；A 只是 1NF，C 把主属性也纳入要求（2NF 不要求主属性），D 是 3NF 的典型特征之一。', '1', '教材 P177', '可把 2NF/3NF 定义做成对比题。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0609', 1, 'KP0604', '简答', '关系模式 S-L-C(Sno,School,Sloc,Cno,Grade)，码为 (Sno,Cno)。说明它为什么不是 2NF，会产生哪些问题，如何分解为 2NF。', NULL, 'Sno→School、Sno→Sloc，而 Sno 是码 (Sno,Cno) 的真子集，所以 School、Sloc 部分函数依赖于码，S-L-C 不是 2NF。会产生插入异常（新生未选课无法插入）、删除异常（删除课程连带学生信息）、修改复杂（转学院要改多条）。分解为 SC(Sno,Cno,Grade) 与 S-L(Sno,School,Sloc)，两个新模式非主属性都对码完全函数依赖。', '判断 2NF 的关键是找非主属性是否部分依赖码；Sno 是复合码的真子集，能单独决定 School/Sloc，因此违约。分解要按依赖关系拆，而不是随意按属性个数拆。例如只保留SC而把S-L丢弃，会同时失去学生—学院与学院—地址信息；只写两个新表名还不够，还应说明新模式的码、依赖及异常为何消失。', '2', '教材 P177—178；例6.4', '可把码改成单属性再判断 2NF。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0610', 1, 'KP0605', '综合', '关系模式 R(员工编号,日期,零件数,部门名称,部门经理)，语义：每个员工每天只有一个零件数，每个员工只在一个部门，每个部门只有一个经理。写出基本函数依赖与码，判断是否 2NF，并分解到 2NF、3NF。', NULL, 'F={(员工编号,日期)→零件数, 员工编号→部门名称, 部门名称→部门经理}；码=(员工编号,日期)。R 不是 2NF，因为员工编号→部门名称，部门名称部分依赖于码。2NF 分解：R1(员工编号,日期,零件数)、R2(员工编号,部门名称,部门经理)。3NF 再分解：R1(员工编号,日期,零件数)、R2(员工编号,部门名称)、R3(部门名称,部门经理)。', '先由语义写 FD，再由“必含属性+闭包”确定码。2NF 检查复合码的真子集是否决定非主属性；3NF 再把 R2 中的传递依赖 员工编号→部门名称→部门经理 拆开。分解顺序体现 2NF→3NF 的递进。R2不是3NF，因为员工编号→部门名称→部门经理形成传递依赖；拆成R2/R3后，员工直接决定部门、部门直接决定经理，分别落在两个模式中，避免更新部门经理时重复修改。', '3', '习题册第6章补充第5题；题目 PDF77（印刷P62）/答案 PDF80（印刷P65）', '可把“部门经理”改为“部门电话”再练一次。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0611', 1, 'KP0605', '单选', '3NF 要求关系模式中（ ）。', '[{"key":"A","text":"不存在非主属性对码的部分依赖和传递依赖"},{"key":"B","text":"只要求消除部分依赖，不要求消除传递依赖"},{"key":"C","text":"要求主属性也完全函数依赖于每一个码"},{"key":"D","text":"允许非主属性传递依赖于码"}]', 'A', 'A 正确：3NF 要求非主属性既不部分依赖也不传递依赖于码；B 把 3NF 降成 2NF；C 把主属性也纳入 3NF 的非主属性条件，教材无此要求；D 与 3NF 定义相反。3NF 也不禁止主属性对码的传递依赖，故 A 对。', '1', '教材 P178', '可问“3NF 是否允许主属性传递依赖”。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0612', 1, 'KP0605', '简答', '说明 3NF 与 2NF 的关系，并以 S-L(Sno,School,Sloc)、Sno→School、School→Sloc 为例说明如何消除传递依赖。', NULL, '3NF 一定 2NF，但 2NF 不一定 3NF；3NF 在 2NF 基础上排除非主属性对码的传递依赖。S-L 中 Sno→School、School→Sloc，Sloc 传递依赖于码 Sno，故 S-L 不是 3NF。分解为 S-SS(Sno,School) 与 S-SL(School,Sloc) 后，两个新模式都不再含传递依赖，均为 3NF。', '关系是“3NF⊂2NF”：3NF 条件更强。消除传递依赖的办法是把中间决定因素 School 单独成表，使 School→Sloc 在新表中成为直接依赖，而不是跨表的传递链。例如School不是码却决定Sloc，而Sno又决定School，所以Sloc经由School传递依赖Sno。分解后S-SS的码是Sno，S-SL的码是School，检查每个非主属性对各自码均完全且直接依赖。', '2', '教材 P178—179', '可让学生判断分解后是否仍保持函数依赖。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0613', 1, 'KP0606', '单选', '关系模式 R 属于 BCNF 的条件是（ ）。', '[{"key":"A","text":"每个非主属性都完全依赖码"},{"key":"B","text":"每个决定因素都包含码"},{"key":"C","text":"每个属性都是主属性"},{"key":"D","text":"不存在非平凡多值依赖"}]', 'B', 'B 正确：BCNF 要求对 X→Y 且 Y⊄X，X 必含码，即每个决定因素含码。A 只是 2NF/3NF 的非主属性条件；C 把“全主属性”误当 BCNF，STJ 就是反例；D 是 4NF 关注的多值依赖，不是 BCNF 定义。', '1', '教材 P179', '可把“决定因素”与“右部”互换出题。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0614', 1, 'KP0606', '多选', '关于 3NF 与 BCNF，下列说法正确的有（ ）。', '[{"key":"A","text":"BCNF 一定属于 3NF"},{"key":"B","text":"3NF 不一定属于 BCNF"},{"key":"C","text":"STJ(S,T,J)，F={(S,J)→T,(S,T)→J,T→J} 是 3NF 但不是 BCNF"},{"key":"D","text":"3NF 与 BCNF 等价"}]', 'ABC', 'BCNF 条件更强，BCNF⊂3NF，A 对；反向不成立，B 对；STJ 中 T→J 的决定因素 T 不含码，C 对；D 与 A/B 矛盾，3NF 不一定 BCNF，故 D 错。本题选 ABC。', '2', '教材 P179—180；例6.8', '可让学生自己构造一个 3NF 非 BCNF 的例子。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0615', 1, 'KP0606', '简答', '关系模式 STJ(S,T,J)，F={(S,J)→T,(S,T)→J,T→J}。说明它为什么是 3NF 但不是 BCNF，并给出一个 BCNF 分解。', NULL, 'STJ 的候选码是 (S,J)、(S,T)，所有属性都是主属性，没有非主属性对码的部分/传递依赖，所以是 3NF。但 T→J 的决定因素 T 不含码，违反 BCNF 的“每个决定因素含码”，所以不是 BCNF。可分解为 ST(S,T) 与 TJ(T,J)，二者均为 BCNF。', 'STJ 的候选码是 (S,J)、(S,T)，所有属性都是主属性，无部分/传递依赖，故为 3NF；T→J 的决定因素 T 不含码，故非 BCNF。分解 ST(S,T)、TJ(T,J) 虽为 BCNF，但不保持 (S,J)→T 与 (S,T)→J，说明 BCNF 分解不保证保持函数依赖，答题时应注明这一代价。', '3', '教材 P180；例6.8', '可问分解后是否保持函数依赖。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0616', 1, 'KP0607', '单选', '关系模式 R 属于 4NF 的条件是（ ）。', '[{"key":"A","text":"所有函数依赖左部都含码"},{"key":"B","text":"对每个非平凡且非函数依赖的多值依赖 X→→Y，X 都含有码"},{"key":"C","text":"只要求非平凡多值依赖左部含属性"},{"key":"D","text":"4NF 与 BCNF 是等价定义"}]', 'B', 'B 正确：4NF 要求每个非平凡且非函数依赖的 MVD 左部含码；平凡 MVD 和 FD 派生的 MVD 不受此限。A 是 BCNF 的 FD 版本；C 把“含码”降成“含属性”；D 错，4NF 比 BCNF 强。', '1', '教材 P180—184', '可把 4NF 与 BCNF 条件做成对比。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0617', 1, 'KP0607', '多选', '关于多值依赖，下列说法正确的有（ ）。', '[{"key":"A","text":"函数依赖可以看作多值依赖的特例"},{"key":"B","text":"若 X→→Y、X→→Z，则 X→→YZ"},{"key":"C","text":"Z=∅ 时的多值依赖称为平凡多值依赖"},{"key":"D","text":"多值依赖一定是函数依赖"}]', 'ABC', 'X→Y 可推出 X→→Y，故 FD 是 MVD 特例；MVD 有合并性质 X→→Y、X→→Z ⇒ X→→YZ；Z 为空是平凡 MVD。D 反了：MVD 不一定 FD，Teaching 中 C→→T 就不是 FD。', '2', '教材 P180—183', '可让学生举一个非 FD 的 MVD 例子。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0618', 1, 'KP0607', '综合', '关系模式 Teaching(C,T,B)，C 为课程、T 为教师、B 为参考书；每门课有多个教师、多本参考书，教师与参考书相互独立。说明它的码、最高范式与存在的问题，并分解为 4NF。', NULL, '码为全码 (C,T,B)，Teaching 属于 BCNF；但存在 C→→T、C→→B 两个非平凡且非函数依赖的多值依赖，增删教师或参考书要动多个元组，冗余明显。分解为 CT(C,T) 与 CB(C,B)，两个模式中 MVD 变为平凡 MVD，均属于 4NF。', '全码模式常被误认为“最规范”，但 BCNF 只处理函数依赖，管不住多值依赖。C→→T 表示课程决定一组教师，与参考书无关；把两组独立信息拆成 CT、CB 即可消除非平凡 MVD。分解前若给同一课程增加一名教师，需要为该课程每本参考书都插入一行；分解后只需在CT插一行，CB完全不受影响。这个变化直接说明4NF分解减少了多值依赖造成的冗余。', '3', '教材 P180—184；例6.9', '可换成 WSC(仓库,保管员,商品) 再练一次。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0619', 1, 'KP0608', '简答', '试用Armstrong公理系统说明合并规则、伪传递规则和分解规则的推导过程，并列出公理系统有效性与完备性的关键理由。', NULL, '①合并：X→Y 增广 X 得 X→XY；X→Z 增广 Y 得 XY→YZ；传递得 X→YZ。②伪传递：X→Y 增广 W 得 XW→WY；WY→Z，传递得 XW→Z。③分解：Z⊆Y，自反律 Y→Z；X→Y，传递得 X→Z。Armstrong 公理 A1—A3 有效且完备：由 F 导出的 FD 都蕴涵于 F，F 蕴涵的 FD 都可由 F 导出，F+ 即全体蕴涵的 FD。', '证明要严格使用 A1 自反、A2 增广、A3 传递，不能跳步；合并规则先增广再传递，伪传递先增广左部，分解先用自反律。有效性与完备性说明“导出”与“蕴涵”等价，因此可用闭包判定 X→Y，这是算法6.1 的理论依据。完备性证明的关键理由可用反证与构造两元组关系：若F不能导出X→A，则构造一个满足F却不满足X→A的关系，说明F不蕴涵X→A；这正是教材定理6.2的思路。本题按“说明”要求列出该理由，不展开逐行证明。', '2', '教材 P185—P187；定理6.2；习题6第3题', '可只给其中一条规则让学生补证明。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0620', 1, 'KP0608', '填空', 'Armstrong 公理包括 A1______、A2______、A3______；由它们可推出合并规则、______规则和分解规则；X 关于 F 的闭包 X+ 是______的集合。', NULL, '自反律；增广律；传递律；伪传递；所有能由 F 导出 X→A 的 A', 'A1—A3 是公理，合并/伪传递/分解是派生规则；X+ 是属性集合，不是函数依赖集合，定义为 {A | X→A 能由 F 导出}。求 X+ 用算法6.1 迭代扫描。', '2', '教材 P185—186', '可让学生写出合并规则的推导过程。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0621', 1, 'KP0608', '简答', '已知 U={A,B,C,D,E}，F={AB→C, B→D, C→E, EC→B, AC→B}，求 (AB)+，并判断 AB 是否为候选码。', NULL, '(AB)+：初始 AB；由 AB→C 得 ABC；由 B→D 得 ABCD；由 C→E 得 ABCDE，故 (AB)+=U。再验证最小性：A+=A，B+=BD，二者都不等于 U，因此 AB 的任何真子集都不能决定 U，AB 是候选码（而不只是超码）。', '只证 (AB)+=U 只能说明 AB 是超码；候选码还要求最小性，必须分别求 A+、B+，确认都不是 U。本题 A+=A、B+=BD，故 AB 是候选码。这个最小性步骤是超码与候选码的分界，不能省略。闭包顺序可以不同，但最终结果必须达到不动点；例如先使用C→E也可，只要反复扫描到不再增加。去掉AB→C后AB不再直接得到C，候选码结论可能改变，因此变式必须重新求闭包。', '3', '教材 P187；例6.11', '可去掉 AB→C 再判断 AB 是否仍为码。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0622', 1, 'KP0609', '单选', '关于最小依赖集（最小覆盖），下列说法正确的是（ ）。', '[{"key":"A","text":"最小依赖集一定唯一"},{"key":"B","text":"最小依赖集要求每个函数依赖右部只含一个属性，且左部无冗余、整体无冗余依赖"},{"key":"C","text":"最小依赖集就是原依赖集去掉重复项"},{"key":"D","text":"最小依赖集一定保持无损连接"}]', 'B', '最小依赖集三条件：右部单属性；删除任一依赖后不再等价；左部无冗余属性。它不唯一，因为极小化顺序不同可能得到不同但等价的覆盖；也不是简单去重，更不直接保证无损连接。A/C/D 都把最小覆盖的性质说错，故选 B。', '2', '教材 P188', '可给一个小 F 让学生求两个不同的最小覆盖。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0623', 1, 'KP0609', '简答', '给定 R(U,F)，U={A,B,C,D}，F={A→B, B→C, A→C, AB→C}。求 F 的最小依赖集，并用合成法分解为保持函数依赖的 3NF，说明每一步依据。', NULL, '①右部已单属性；②A→C 冗余：A→B、B→C 已推出 A→C，删除；③AB→C 冗余：A→B、B→C 已推出 AB→C，删除；最小覆盖 Fm={A→B, B→C}。合成法按左部分组：R1(A,B) 保留 A→B，R2(B,C) 保留 B→C；D 不出现在 F 中，单独成 R3(D)。分解 ρ={R1,R2,R3} 保持 Fm，故保持 F，且各模式均为 3NF。', '先拆右部、再删整体冗余依赖、最后检查左部冗余；本题两个冗余依赖分别由传递和增广/传递推出。合成法按相同左部分组，未出现的属性单独成表，因此不丢依赖；3NF 由算法6.2 保证，答案要写出被删依赖及理由。R1与R2的自然连接通过B关联，能恢复A→B→C的语义；R3(D)虽然没有FD，也不能漏掉，因为U中的每个属性都必须出现在分解的某个子模式中。', '3', '教材 P188—P189；习题册 P61—P62；PDF 76—77', '可换一组 F 让学生重跑极小化。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0624', 1, 'KP0610', '简答', '判断：保持函数依赖的分解一定具有无损连接性吗？给出反例；并写出两关系分解的无损判定定理6.5。', NULL, '不一定。反例：R(学号,姓名,课程号,课程名)，F={学号→姓名, 课程号→课程名}，分解 R1(学号,姓名)、R2(课程号,课程名) 保持 F，但 R1、R2 无公共属性，自然连接退化为笛卡儿积，不能恢复原关系，故不无损。定理6.5：对 ρ={R1(U1),R2(U2)}，若 U1∩U2→U1-U2 或 U1∩U2→U2-U1 属于 F+，则 ρ 无损。', '保持 FD 与无损连接是两个不同准则：前者保证依赖不丢，后者保证投影连接能恢复原关系。反例说明“保持 FD”推不出“无损”；定理6.5 给出两关系分解的充分判定，注意方向是交集决定差集，不能把两个差集方向混用。若要用定理6.5判定，必须找两个子模式的公共属性并检查它是否决定某个差集；本例交集为空，无法形成这样的依赖，所以也不能由定理6.5推出无损。', '3', '教材 P193；例6.14/6.5.2', '可让学生再举一个“无损但不保持 FD”的例子。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0625', 1, 'KP0610', '综合', '已知 R(A,B,C,D,E)，F={AB→C, C→D, D→E}，分解 ρ={R1(A,B,C), R2(C,D), R3(D,E)}。请用算法6.3 表算法判断 ρ 是否无损，写出关键表格变化并说明 m 为最小行号的含义。', NULL, '初始表：R1[a1,a2,a3,b14,b15]，R2[b21,b22,a3,a4,b25]，R3[b31,b32,b33,a4,a5]。AB→C：A、B 列无相同符号行，不变。C→D：C 列 a3 的行 R1、R2，R2 的 D 列为 a4，故把 R1 的 b14 改为 a4（若有 b 需统一改为这些行中最小行号对应的 b_mj）。D→E：D 列 a4 的行 R1、R2、R3，R3 的 E 列为 a5，故把 R1、R2 的 E 列改为 a5。此时 R1 行为 a1,a2,a3,a4,a5，出现全 a 行，ρ 无损。', '表算法按 FD 逐列改符号，直到不动点；m 是参与合并的这些行中的最小行号，所有相同 b 符号都要统一改成 b_mj，否则后续扫描无法正确合并。本题经 C→D、D→E 两轮即出现全 a 行，故无损；若始终无全 a 行则不是无损分解。每次修改b符号时，整列相同的b_ij都要同步替换，不能只改当前两行；否则后续FD扫描会使用不一致的符号，得到错误结论。', '3', '教材 P192—193；例6.15', '可换一个非无损分解让学生跑表。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0626', 1, 'KP0611', '综合', '已知R(A,B,C,D)，F={A→B,B→C}。算法6.2已得到ρ={R1(A,B),R2(B,C),R3(D)}，且原模式候选码X=(A,D)。请按算法6.4加入码关系并删除应删子模式，给出最终的3NF无损且保持FD分解，说明每一步依据。', NULL, '先加入R*(A,D)。X=(A,D)不包含于R1、R2或R3，因此码关系保留；但R3(D)的属性集D⊆X，所以按算法6.4删去R3(D)。最终τ={R1(A,B),R2(B,C),R*(A,D)}。R1保留A→B，R2保留B→C，故仍保持F；R*包含原候选码(A,D)，用于保证无损连接，三个子模式均达到3NF。', '算法6.4不是只把候选码关系机械追加，还要做包含关系清理：若X已包含于某个Ui才删除R*，若某个Ui被X包含则删除该Ui。本题D未被任何FD决定，所以原码必须是(A,D)而不是A；R3(D)被码关系包含，应删除。漏掉码关系无法保证无损，漏删R3则没有执行完整算法。', '3', '教材 P188—P189；教材 P193—P194；算法6.2/6.4', '把F改为{A→B,B→C,D→E}并比较码关系与最终子模式。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0627', 1, 'KP0611', '综合', '对STJ(S,T,J)，F={(S,J)→T,(S,T)→J,T→J}，用算法6.5给出一步BCNF无损分解。要求写出选择的违反BCNF依赖、两个子模式、无损判定依据，并说明是否保持原FD集。', NULL, '选T→J：T不是候选码却决定J，违反BCNF。按算法6.5分解为TJ(T,J)与ST(S,T)。两模式均属BCNF。交集为T，TJ-ST=J，且T→J成立，所以由定理6.5可知无损。该分解不保持(S,J)→T与(S,T)→J，说明算法6.5保证无损和BCNF，但不保证保持FD。', '算法6.5的分解格式是XA与U-A，这里X=T、A=J，所以得到TJ和ST，而不是随意按三个属性拆表。无损验证必须写交集T决定差集J；保持FD验证则要单独做，不能因为达到BCNF就默认原依赖都保留。选择不同违反依赖可能得到不同分解树，但每一步都要同时检查BCNF与无损。', '3', '教材 P180；教材 P194；例6.8与算法6.5', '改选另一个违反BCNF的依赖并比较分解树与保持FD结果。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0628', 1, 'KP0611', '综合', 'Teaching(C,T,B)满足C→→T、C→→B，教师与参考书相互独立。请按算法6.6给出4NF无损分解，并用定理6.6说明为什么该分解无损。', NULL, '先用算法6.5检查BCNF，Teaching全码(C,T,B)，已是BCNF。再取X=C、Y=T，则Z=U-X-Y=B，按定理6.6分解为CT(C,T)与CB(C,B)。C→→T成立当且仅当{CT,CB}无损，因此该分解无损；两子模式不再含原来的非平凡MVD，均达到4NF。', '定理6.6给出的是X→→Y与R1(X,Y)、R2(X,Z)无损之间的充要关系，Z必须取U-X-Y，本题即B。算法6.6不是直接跳到4NF，而是先确保BCNF，再逐个子模式处理非4NF依赖。分解前增加一名教师要与每本参考书组合，分解后只写CT一行，说明无损与消除冗余是两个同时核对的目标。', '3', '教材 P180—P184；教材 P195；定理6.6与算法6.6', '改为先分解C与B，再说明另一种等价4NF分解及其无损依据。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

-- ============================================================
-- [question] seed-question-ch07.sql
-- ============================================================
-- 智学领航 · 第7章题库种子（由 csv_to_sql.py 生成）
-- 前置：init-v2.sql、seed-kp-ch07.sql；可重复执行（upsert）。


INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0701', 1, 'KP0701', '单选', '按照教材，数据库设计的基本步骤不包括（ ）。', '[{"key":"A","text":"需求分析"},{"key":"B","text":"概念结构设计"},{"key":"C","text":"逻辑结构设计"},{"key":"D","text":"代码重构"}]', 'D', '教材把数据库设计分为需求分析、概念结构设计、逻辑结构设计、物理结构设计、数据库实施、数据库运行和维护6个阶段；代码重构属于一般软件维护活动，不是数据库设计的基本阶段。', '1', '教材 P204—206', '可把6个阶段打乱，让学生重新排序。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0702', 1, 'KP0701', '多选', '教材介绍的数据库设计方法包括（ ）。', '[{"key":"A","text":"新奥尔良法"},{"key":"B","text":"基于E-R模型法"},{"key":"C","text":"3NF法"},{"key":"D","text":"快速原型法"}]', 'ABC', '教材 P203—204 列出新奥尔良法、基于E-R模型法、3NF法、面向对象法和 UML 法；快速原型法不是本书列出的五种方法。新奥尔良法分需求分析、概念设计、逻辑设计、物理设计四阶段；基于E-R法以E-R图为中心；3NF法以规范化理论为指导。', '2', '教材 P203—204；习题册 P77 填空2①', '可改成“哪些阶段依赖DBMS”的单选。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0703', 1, 'KP0701', '简答', '简述数据库设计的6个阶段，并说明每个阶段的主要任务。', NULL, '①需求分析：调查用户的信息、处理、安全性与完整性要求，形成数据字典；②概念结构设计：把需求抽象为独立于DBMS的概念模型（E-R图）；③逻辑结构设计：把E-R图转换为DBMS支持的数据模型并优化；④物理结构设计：选取存取方法与存储结构并评价；⑤数据库实施：创建数据库、编写调试应用程序、组织数据入库并试运行；⑥数据库运行和维护：转储恢复、安全完整性控制、性能监督、重组重构。', '答题要按“阶段+任务”成对写，不能只列阶段名。需求分析与概念设计独立于DBMS，逻辑与物理设计依赖DBMS；6个阶段是反复迭代过程，实施后还可能返回逻辑或物理设计修改。习题册解析还强调完善应用系统常需六阶段不断反复，因此实施后发现模式问题应返回相应阶段修正，而非把六阶段当作一次性瀑布流程。', '2', '教材 P204—206；习题7 第1题（习题册 P70）', '可让学生针对一个具体系统说明每阶段的产出物。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0704', 1, 'KP0702', '单选', '数据库外模式通常在数据库设计的哪个阶段形成（ ）。', '[{"key":"A","text":"概念结构设计"},{"key":"B","text":"逻辑结构设计"},{"key":"C","text":"物理结构设计"},{"key":"D","text":"数据库实施"}]', 'B', '逻辑结构设计先把E-R图转换为关系模式形成逻辑模式，再根据用户处理要求和安全性建立视图，形成外模式。概念结构设计形成E-R图，物理结构设计形成内模式，实施阶段负责建立数据库并载入数据。', '1', '教材 P207、P229—232；习题册 7.3补充习题 1（习题册 P77—78）', '可把外模式、内模式、概念模式做成匹配题。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0706', 1, 'KP0702', '简答', '说明概念模式、逻辑模式、外模式、内模式分别在哪个阶段形成，以及它们之间靠什么机制保持数据独立性。', NULL, '概念模式在概念结构设计阶段形成，即E-R图；逻辑模式在逻辑结构设计阶段由E-R图转换得到；外模式在同一阶段根据用户要求和安全性用视图建立；内模式在物理结构设计阶段通过存储安排和索引建立。外模式与逻辑模式之间靠外模式/模式映像，逻辑模式与内模式之间靠模式/内模式映像，分别保证逻辑独立性与物理独立性。', '四级模式与两级映像要对应清楚：外模式/模式映像支持逻辑独立性，模式/内模式映像支持物理独立性。不要把概念模式误认为DBMS中的模式，它只是E-R图层次的概念模型。教材7.1.4明确逻辑结构设计形成逻辑模式并建立必要视图形成外模式，物理结构设计建立索引形成内模式；这两句可分别作为阶段判定的原文锚点。', '2', '教材 P207；习题7 第2题（习题册 P70—71）', '可结合具体DBMS说明视图对应外模式。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0707', 1, 'KP0703', '单选', '需求分析阶段调查的重点是（ ）。', '[{"key":"A","text":"数据和处理"},{"key":"B","text":"程序代码和界面"},{"key":"C","text":"网络带宽和服务器"},{"key":"D","text":"数据库品牌和版本"}]', 'A', '教材明确需求调查的重点是“数据”和“处理”，由此获得信息要求、处理要求以及安全性与完整性要求。代码、界面、网络、DBMS品牌都不是需求调查的核心对象。A 正确；B/C/D 分别把程序代码、网络带宽、数据库品牌当成需求调查重点，均偏离“数据和处理”。', '1', '教材 P208', '可让学生列出三类需求并各举一例。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0708', 1, 'KP0703', '多选', '关于需求分析，下列说法正确的有（ ）。', '[{"key":"A","text":"需求分析是数据库设计的起点，也是基础和最困难、最耗时的阶段"},{"key":"B","text":"调查内容包括信息要求、处理要求和安全性与完整性要求"},{"key":"C","text":"结构化分析SA采用自顶向下、逐层分解的方法"},{"key":"D","text":"需求分析只调查现有计算机系统，不需要了解手工系统"}]', 'ABC', '需求分析要了解原系统（手工或计算机）的工作概况，因此D错误；A、B、C均符合教材7.2节。需求分析结果直接影响后续设计，做得不好可能导致整个设计返工。A 正确；B 正确；C 正确；D 错在只调查计算机系统，手工系统也要了解。', '2', '教材 P207—209', '可把调查步骤做成排序题。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0709', 1, 'KP0703', '简答', '需求分析阶段的设计目标是什么？调查用户需求通常按哪些步骤进行？', NULL, '目标是通过详细调查充分了解原系统情况，明确用户各种需求，并在此基础上确定新系统功能，同时考虑今后可能的扩充和改变。步骤：①调查组织机构总体情况，了解部门组成与职责；②熟悉各部门业务活动，弄清输入、处理、输出数据及格式；③协助用户明确信息、处理、安全性与完整性要求；④初步分析并确定新系统边界，划分计算机完成与人工完成的功能。', '设计目标要落到“确定新系统功能”，不能只写“了解需求”。四个步骤的顺序体现从组织到业务、再到需求与边界的逐层细化；调查时可配合跟班作业、调查会、问卷等方法。四步依次是组织机构、业务活动、用户需求、系统边界；漏掉“确定系统边界”或把用户职责当成设计员职责是常见错误。', '2', '教材 P207—209；习题7 第3题（习题册 P71）', '可让学生模拟调查一个校园场景并写出系统边界。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0710', 1, 'KP0704', '单选', '数据字典通常不包括下列哪一部分（ ）。', '[{"key":"A","text":"数据项"},{"key":"B","text":"数据结构"},{"key":"C","text":"数据流"},{"key":"D","text":"程序源代码"}]', 'D', '数据字典通常包括数据项、数据结构、数据流、数据存储和处理过程5部分；程序源代码属于应用程序实现，不是需求分析阶段数据字典的内容。A/B/C 分别是数据项、数据结构、数据流；D 程序源代码不是数据字典内容；数据存储和处理过程才是第4/5部分。', '1', '教材 P209—210', '可把5部分与各自定义做成连线题。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0711', 1, 'KP0704', '多选', '关于数据字典，下列说法正确的有（ ）。', '[{"key":"A","text":"数据字典是关于数据库中数据的描述，即元数据，不是数据本身"},{"key":"B","text":"数据字典在需求分析阶段建立，并在数据库设计过程中不断修改、充实和完善"},{"key":"C","text":"数据项是不可再分的数据单位，其取值范围和与其他数据项的逻辑关系定义完整性约束"},{"key":"D","text":"数据字典中的处理过程必须写出全部程序代码"}]', 'ABC', '数据字典只描述处理过程的说明性信息，具体处理逻辑一般用判定表或判定树描述，不需要写程序代码，故D错。A、B、C分别对应数据字典的性质、建立时机和数据项的作用。A/B/C 分别对应元数据性质、建立时机、数据项完整性；D 错：处理过程只写输入/输出/简要说明，具体逻辑用判定表/判定树。', '2', '教材 P209—211', '可让学生为一个小系统写一条数据项条目。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0712', 1, 'KP0704', '简答', '数据字典包括哪5部分？简述各部分描述的主要内容及数据字典的作用。', NULL, '5部分为数据项、数据结构、数据流、数据存储和处理过程。数据项是不可再分单位，描述名称、含义、别名、类型、长度、取值范围及逻辑关系；数据结构描述数据项的组合关系；数据流描述传输路径、来源、去向、组成和流量；数据存储描述保存的数据结构、数据量、存取频度和方式；处理过程描述输入、输出和简要处理说明。作用是描述数据库中数据（元数据），是概念结构设计的基础，并在设计过程中不断完善。', '五部分要按“是什么+描述什么”作答。数据字典不是数据本身，也不保存处理过程的全部细节；它把需求阶段收集的数据项与结构固定下来，为概念设计和后续逻辑设计提供依据。教材原文逐项列出数据项、数据结构、数据流、数据存储和处理过程五部分；其中处理过程只写输入输出与简要说明，不能拿程序源代码替代元数据。', '3', '教材 P209—211；习题7 第4题（习题册 P71）', '可给出一个“学生”数据项让学生补全描述。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0713', 1, 'KP0705', '单选', '在E-R图中，实体型、属性、联系通常分别用（ ）表示。', '[{"key":"A","text":"矩形、椭圆形、菱形"},{"key":"B","text":"椭圆形、矩形、菱形"},{"key":"C","text":"菱形、矩形、椭圆形"},{"key":"D","text":"矩形、菱形、椭圆形"}]', 'A', '教材规定实体型用矩形，属性用椭圆并用无向边连接实体型，联系用菱形并用无向边连接有关实体型，同时在边旁标注1:1、1:n或m:n。若联系有属性，也用无向边连接到该菱形。', '1', '教材 P213', '可让学生补画一个带联系属性的E-R图。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0714', 1, 'KP0705', '多选', '关于两个实体型之间的联系，下列说法正确的有（ ）。', '[{"key":"A","text":"一对一联系记为1:1"},{"key":"B","text":"一对多联系记为1:n"},{"key":"C","text":"多对多联系记为m:n"},{"key":"D","text":"两个以上实体型之间不可能存在多对多联系"}]', 'ABC', '教材指出两个以上实体型之间同样存在一对一、一对多和多对多联系，例如供应商、项目、零件之间的供应联系就是多对多，因此D错误。A、B、C是三类基本联系的记法。A/B/C 是三类基本联系；D 错：两个以上实体型之间也可以有多对多联系，例如供应商—项目—零件。', '2', '教材 P212—213', '可给出三个业务场景让学生判断联系类型。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0715', 1, 'KP0705', '填空', 'E-R图的三要素是______、______和______；两个实体型之间的三类联系分别记为______、______和______；若联系具有属性，应把属性用______连接到该联系。', NULL, '实体型；属性；联系；1:1；1:n；m:n；无向边', '前三个空对应E-R模型三要素，中间三个空对应联系类型，最后一个空是联系属性的画法。注意联系属性属于联系而不是某个实体，画图时应连到菱形。实体型用矩形、属性用椭圆、联系用菱形；联系属性连到菱形；1:1、1:n、m:n 标在无向边旁，不要连到实体。', '3', '教材 P211—214', '可把联系类型与联系属性合并成一道画图题。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0716', 1, 'KP0707', '单选', '在E-R图设计中，把事物作为属性对待的两条基本准则是（ ）。', '[{"key":"A","text":"属性必须不可再分，且不能与其他实体有联系"},{"key":"B","text":"属性必须越多越好，且必须单独建表"},{"key":"C","text":"每个属性都必须有码，且必须唯一"},{"key":"D","text":"属性必须能分解为子属性，且可与其他实体联系"}]', 'A', '教材给出两条准则：属性不能再具有需要描述的性质，必须是不可分的数据项；属性不能与其他实体有联系，E-R图表示的联系是实体之间的联系。满足这两条的事物一般可作为属性。', '2', '教材 P220；习题册 7.3补充习题 3①（习题册 P77—78）', '可给出“职称”“专业”等例子让学生判断。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0717', 1, 'KP0707', '简答', '举例说明在E-R图设计中，什么情况下把“职称”“专业”“先修课”作为属性，什么情况下应上升为实体。', NULL, '若“职称”不需要再描述工资、岗位津贴、福利等，可作为教师属性；若需描述不同职称的工资福利，则应作为实体。若学生只能选一个专业且专业无类别、年限等属性，可作为学生属性；若学生可选多个专业或专业有类别、年限，则应作为实体并建立多对多“专业选择”联系。若一门课程只有一门直接先修课，可把先修课程号作为课程属性；若有多门直接先修课，则“先修课”应作为实体，形成课程实体内部的多对多“先修”联系。', '判断标准不是对象大小，而是是否满足两条准则：是否还需要进一步描述、是否与其他实体有联系。一旦需要描述自身属性或参与联系，就应从属性上升为实体。判断标准是两条准则：属性不可再分、不能与其他实体有联系；满足则可作属性，否则上升为实体，不要按对象大小判断。', '2', '教材 P220—221；习题册 7.3补充习题 3①（习题册 P77—78）', '可换成“教室”“教学班”再判断一次。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0718', 1, 'KP0707', '综合', '参照例7.1“教师教学管理”子系统，列出该子系统的主要实体、属性及实体间联系，并说明各联系的类型。', NULL, '实体及主要属性：教师（职工号、姓名、出生日期、职称）、教学班（教学班号、人数上限、开课学期）、学生（学号、姓名、性别、出生日期）、教室（教室号、教学楼号、联系人、联系方式）、时间片（时间片编码、星期几、开始时间、截止时间）。联系：教师与教学班之间为多对多“讲授”，联系属性为是否为主讲教师；教师、学生、教学班三者之间为多对多“课程评价”，带评价类型、评价内容、教师反馈；教室与教学班、时间片之间为“排课”联系，属于一对多联系，即给定教学班和时间片可唯一确定教室，而一个教室在不同时间段可安排多个教学班。', '该题考“从业务描述抽实体、属性、联系”的完整过程。注意“课程评价”是三个实体型之间的多元联系，不是教师与学生的简单二元联系；“排课”联系要依据“一个教室在同一时间段只能安排一门课程”的语义判断。例7.1中时间片应单列为实体，排课唯一性要按“教室+时间片”判断：同一教室同一时间片只能安排一门课程，但不同时间片可排不同课程。课程评价则必须保留教师、学生、教学班三元语义。', '3', '教材 P221—224；例7.1', '可换成“学生选课管理”子系统再设计一次。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0720', 1, 'KP0707', '多选', '合并分E-R图时需要消除的冲突主要有（ ）。', '[{"key":"A","text":"属性冲突"},{"key":"B","text":"命名冲突"},{"key":"C","text":"结构冲突"},{"key":"D","text":"用户权限冲突"}]', 'ABC', '教材把分E-R图冲突归为属性冲突（属性域、取值单位）、命名冲突（同名异义、异名同义）和结构冲突（抽象不同、属性个数次序不同、联系类型不同）三类；用户权限冲突属于访问控制问题，不是E-R图合并冲突。', '2', '教材 P224—225；习题册 7.3补充习题 1（习题册 P77—78）', '可给出具体冲突案例让学生归类。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0721', 1, 'KP0707', '简答', '简述结构冲突的三种表现及解决方法，并说明消除E-R图冗余的两种方法。', NULL, '结构冲突表现为：①同一对象在不同应用中抽象不同，如“职工”在一处是实体、另一处是属性，解决方法是按两条准则把属性变换为实体或把实体变换为属性；②同一实体在不同分E-R图中属性个数和次序不同，解决方法是取各分E-R图属性的并集并调整次序；③同一对实体在不同图中的联系类型不同，或二元联系与多元联系不一致，解决方法是按应用语义综合或调整联系类型。消除冗余可用分析方法，依据数据字典和数据流图判断可由基本数据导出的冗余数据与冗余联系；也可用规范化理论，以实体码间函数依赖求最小覆盖，对差集中的联系逐一判断是否冗余。', '三种结构冲突要分别写出“表现+解决办法”，不能只列名称。冗余消除的两种方法各有依据：分析方法靠语义与数据流，规范化方法靠函数依赖与最小覆盖；保留必要冗余时要把它写成完整性约束。教材在P225把“同一实体属性个数和排列次序不同”列为结构冲突之一，因此取属性并集后还需统一排列；冗余消除不能只凭图形看起来重复，要由数据字典、数据流或函数依赖证明。', '3', '教材 P225—227', '可给出两个子系统的E-R图片段让学生找冲突。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0723', 1, 'KP0708', '多选', '关于E-R图向关系模型转换，下列说法正确的有（ ）。', '[{"key":"A","text":"一个实体型转换为一个关系模式，关系属性为实体属性，关系码为实体码"},{"key":"B","text":"一个1:1联系可转换为独立关系模式，也可与任意一端对应关系模式合并"},{"key":"C","text":"一个1:n联系可转换为独立关系模式，也可与n端对应关系模式合并"},{"key":"D","text":"具有相同码的关系模式可以合并"}]', 'ABCD', '四项均符合教材7.4.1的转换原则。1:1独立转换时各实体码都是候选码；1:n独立转换时关系码为n端实体码；同码合并可减少关系模式数量。A/B/C/D 均正确；1:1 独立转换时各实体码都是候选码，1:n 独立转换时码为 n 端实体码，m:n 必须独立转换，同码可合并。', '2', '教材 P229', '可让学生比较1:1与1:n合并端的差异。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0724', 1, 'KP0708', '综合', '将习题7中“学院—系—教研室/班级—教师/学生—课程”的E-R图转换为关系模型，写出关系模式并标明主码与外码。', NULL, '系（系编号，系名，学校名）；教研室（教研室编号，教研室名，系编号）；班级（班级编号，班级名，系编号）；教员（职工号，姓名，职称，教研室编号）；学生（学号，姓名，学历，班级编号，导师职工号）；课程（课程编号，课程名）；选课（学号，课程编号，成绩）。主码分别为系编号、教研室编号、班级编号、职工号、学号、课程编号、(学号,课程编号)；外码为教研室.系编号、班级.系编号、教员.教研室编号、学生.班级编号、学生.导师职工号，以及选课.学号、选课.课程编号。', '转换时先把每个实体型变成关系模式，再处理联系：系与教研室、系与班级、教研室与教员、班级与学生都是一对多，可与n端合并；学生与课程是多对多，必须单独转换为“选课”关系，码为两个实体码的组合，并带联系属性“成绩”。转换得分点有两层：1:n联系合并到n端并把1端码写成外码；m:n联系独立成表，以两端码组成组合码并保留联系属性。只写表名不标主外码仍未完成题目。', '3', '教材 P229—230；习题7 第11题（习题册 P75）', '可改用习题8的工厂场景再转换一次。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0725', 1, 'KP0709', '单选', '关于数据模型优化与规范化程度，下列说法正确的是（ ）。', '[{"key":"A","text":"规范化程度越高，数据库性能一定越好"},{"key":"B","text":"查询经常涉及多个关系时，可考虑合并关系，此时2NF甚至1NF也可能合适"},{"key":"C","text":"非BCNF关系模式在任何应用中都必须分解"},{"key":"D","text":"水平分解一定比垂直分解好"}]', 'B', '教材强调“并不是规范化程度越高的关系就越优”：多关系查询的连接代价高，可合并关系；非BCNF模式若只查询不更新，未必产生实际影响；水平分解与垂直分解各有适用场景，不能一概而论。', '2', '教材 P230—231', '可给一个查询密集场景让学生权衡是否合并。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0726', 1, 'KP0709', '填空', '常用的两种关系模式分解方法是______和______；垂直分解把关系的属性分解为若干子集合，分解后必须保证______和______。', NULL, '水平分解；垂直分解；无损连接性；保持函数依赖性', '水平分解按元组划分为若干子关系，常用于把经常访问的约20%数据分离出来；垂直分解按属性划分，可提高某些事务效率，但可能使其他事务增加连接操作，因此必须检查无损连接性和保持函数依赖性。', '2', '教材 P231', '可让学生判断一个给定分解属于水平还是垂直分解。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0727', 1, 'KP0709', '简答', '简述以规范化理论为指导优化关系数据模型的步骤，并说明设计用户外模式时通常考虑哪些方面。', NULL, '优化步骤：①按需求语义确定各关系模式内部及不同关系模式属性间的数据依赖；②对关系模式间的数据依赖极小化，消除冗余联系；③逐一分析部分函数依赖、传递函数依赖、多值依赖，确定各模式范式；④结合处理要求判断模式是否合适，决定合并或分解。设计用户外模式通常考虑：使用符合用户习惯的别名；对不同级别用户定义不同视图以保证安全性；把复杂查询定义为视图以简化用户使用。', '四步优化从“确定依赖”到“极小化”再到“判范式”“定取舍”，体现规范化理论是工具而非教条。外模式设计的三个方面分别对应易用性、安全性和简化操作，均通过视图实现。教材P230的“极小化处理”针对关系模式之间的数据依赖，目的是消除冗余联系；之后仍要逐一判定范式并结合查询、更新需求决定合并或分解，不能把规范化当成必须一路升到最高范式。', '3', '教材 P230—232；习题7 第13题（习题册 P75）', '可结合一个具体系统说明应保留哪些视图。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0728', 1, 'KP0710', '单选', '关系数据库管理系统常用的存取方法主要是（ ）。', '[{"key":"A","text":"索引方法和聚簇方法"},{"key":"B","text":"编译方法和解释方法"},{"key":"C","text":"加密方法和解密方法"},{"key":"D","text":"分区方法和分片方法"}]', 'A', '教材指出关系DBMS一般提供多种存取方法，常用的是索引方法和聚簇方法，其中B+树索引和哈希索引是两种经典索引方法。编译/解释、加密/解密、分区/分片都不是这里所说的存取方法。', '1', '教材 P233—234', '可让学生说明B+树索引与哈希索引的适用差异。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0729', 1, 'KP0710', '多选', '数据库运行和维护阶段的主要工作包括（ ）。', '[{"key":"A","text":"数据库的转储和恢复"},{"key":"B","text":"数据库安全性与完整性控制"},{"key":"C","text":"数据库性能的监督、分析和改造"},{"key":"D","text":"数据库的重组与重构"}]', 'ABCD', '教材把运行维护工作归纳为四方面：转储和恢复、安全性与完整性控制、性能监督分析改造、重组与重构。重组按原设计重新安排存储位置、回收垃圾，不修改模式；重构则部分修改模式和内模式。', '2', '教材 P237—238；习题7 第16题（习题册 P76）', '可把“重组”和“重构”做成辨析题。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0730', 1, 'KP0710', '综合', '某教务系统经常按“所在系”查询学生名单，并频繁连接“学生”和“选课”表统计成绩；学生表更新较少，选课表更新频繁。请说明可选择哪些存取方法及理由，并指出数据库运行一段时间后可能需要进行重组还是重构。', NULL, '对“学生”表的所在系属性可建索引或聚簇：按系查询会把同系学生元组集中，减少I/O；若学生表更新少、按系访问是主要应用，聚簇较合适。对学生与选课的连接，可按连接属性建立索引，或对经常连接的关系建立聚簇；但选课表更新频繁，索引和聚簇会增加维护代价，应控制数量。运行一段时间后，若只是存储碎片、指针链变长导致性能下降，应进行重组；若业务变化需要增删数据项、表或索引，修改模式和内模式，则应进行重构。', '选择存取方法要结合事务频率与更新代价：索引适合查询条件、连接条件和聚集函数参数，聚簇适合按聚簇码访问且聚簇码稳定的场景。重组不改变逻辑和物理结构，重构才修改模式与内模式，二者不能混用。习题册第16题把重组解释为重新安排存储位置、回收垃圾、减少指针链，把重构解释为修改模式和内模式；因此碎片整理选重组，业务结构变化才选重构。选课更新频繁时还要评估索引维护成本。', '3', '教材 P233—238；习题7 第14、16题（习题册 P75—76）', '可换一个“订单—商品”场景重新选择存取方法。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0731', 1, 'KP0706', '多选', '在扩展E-R模型中，关于 ISA 联系与特化约束，下列说法正确的有（ ）。', '[{"key":"A","text":"子类继承父类的全部属性，也可有自己的属性"},{"key":"B","text":"三角形内加叉号表示不相交约束，子类之间不能重叠"},{"key":"C","text":"父类实体必须属于某个子类称完全特化，用双线连接表示"},{"key":"D","text":"不相交约束与完全特化是同一概念"}]', 'ABC', 'A 是 ISA 的继承性；B 的叉号表示不相交约束；C 的双线表示完全特化；D 错，不相交约束管子类能否重叠，完全特化管父类实体是否必须属于某个子类，二者维度不同。', '2', '教材 P215—P217；7.3.3 扩展E-R模型', '可把“完全特化”改成“部分特化”作为干扰项。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0732', 1, 'KP0706', '多选', '关于 min..max 基数约束与 Part-of 联系，下列说法正确的有（ ）。', '[{"key":"A","text":"min=1 表示强制参与，min=0 表示非强制参与"},{"key":"B","text":"Part-of 的独占联系可用弱实体型和识别联系表示"},{"key":"C","text":"弱实体型用双矩形，识别联系用双菱形"},{"key":"D","text":"所有 Part-of 联系都必须用弱实体表示"}]', 'ABC', 'A 正确解释 min..max；B 是独占 Part-of 的表示法；C 是弱实体/识别联系的图元；D 过强，非独占 Part-of 可用非强制参与表示，不必弱实体化。', '2', '教材 P217—P219；7.3.3 扩展E-R模型', '可把“非强制参与”改成“强制参与”作干扰项。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0733', 1, 'KP0701', '填空', '数据库设计开始前首先要选定参加设计的人员，通常包括系统分析员、______、应用程序员、数据库管理员和最终用户代表。', NULL, '数据库设计员', '教材 7.1.3 指出数据库设计人员包括系统分析员、数据库设计员、应用程序员、数据库管理员和最终用户代表；系统分析员和数据库设计员是核心人员，自始至终参与设计。题干空格处对应“数据库设计员”，不要与“数据库管理员”混淆。', '1', '教材 P230；7.1.3 数据库设计的基本步骤', '可改成多选，问“下列哪些属于数据库设计人员”。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0734', 1, 'KP0708', '综合', '参照例7.3“学生学籍管理”子系统，将学院、系、专业、教师、学生以及学生与专业之间的“选修专业”联系转换为关系模式。写出各关系模式的主要属性、主码与外码，并说明1:n联系合并和m:n联系独立转换的依据。', NULL, '学院（学院编号，学院名，建院时间，院长），主码学院编号，院长外码参照教师；系（系编号，系名，联系人，联系方式，系主任，所在学院），主码系编号，系主任与所在学院为外码；专业（专业编码，专业名，类别，年限，开设系），主码专业编码，开设系为外码；教师（职工号，姓名，职称，出生日期，所在系），主码职工号，所在系为外码；学生（学号，姓名，性别，出生日期，所在学院），主码学号，所在学院为外码；选修专业（学号，专业编码，是否主修），主码（学号，专业编码），学号与专业编码分别为外码。1:n联系合并到n端并保留1端码作外码，m:n联系独立成表并以两端码组成组合码。', '例7.3的转换先把每个实体型转为关系模式，再按联系类型合并或独立建表：学院—系、系—专业、系—教师以及学院—学生等1:n联系都把1端码放入n端作外码；学生与专业的m:n“选修专业”联系必须独立成表，保留联系属性“是否主修”，主码由学号和专业编码共同组成。只写六个实体表而漏掉选修专业，或只写属性不标主外码，都不完整。', '3', '教材 P229—P230；例7.3', '把“选修专业”改为1:n或1:1联系，比较关系模式与主码的变化。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

-- ============================================================
-- [question] seed-question-ch08.sql
-- ============================================================
-- 智学领航 · 第8章题库种子（由 csv_to_sql.py 生成）
-- 前置：init-v2.sql、seed-kp-ch08.sql；可重复执行（upsert）。


INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0801', 1, 'KP0801', '单选', '教材任务1要求打印“数据库系统概论”课程的全部先修课信息，基本SQL难以直接完成的主要原因是（ ）。', '[{"key": "A", "text": "缺少递归查询能力，无法沿先修课关系逐层回溯"}, {"key": "B", "text": "缺少CREATE TABLE语句"}, {"key": "C", "text": "缺少主码与外码约束"}, {"key": "D", "text": "缺少COMMIT与ROLLBACK语句"}]', 'A', '任务1既要输出直接先修课，又要输出间接先修课，必须沿Course表的Cpno逐层回溯，属于递归查询。基本SQL没有递归子句，故教材引入WITH RECURSIVE。CREATE TABLE、主码外码、COMMIT/ROLLBACK都是基本SQL具备的能力，不是本任务的难点。', '1', '教材 P241—242', '可改为给出3门课的Cpno链，让学生判断需要几层递归。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0802', 1, 'KP0801', '多选', '教材指出扩展SQL表达能力主要有两类途径。下列属于这两类途径的有（ ）。', '[{"key": "A", "text": "引入新的SQL子句，如WITH RECURSIVE"}, {"key": "B", "text": "引入新的内置函数，如日期和时间函数"}, {"key": "C", "text": "引入PL/SQL以及存储过程/存储函数"}, {"key": "D", "text": "通过高级语言访问数据库，如嵌入式SQL、ODBC/JDBC"}]', 'ABCD', '教材8.1.2把扩展SQL自身功能分为引入新SQL子句、引入新内置函数、引入PL/SQL与存储过程/存储函数；8.1.3把通过高级语言实现复杂应用分为动态链接库调用、嵌入式SQL和ODBC/JDBC中间件。因此A、B、C属于第一类，D属于第二类，四项都正确。', '2', '教材 P243—250', '可把某一项换成“新增数据库产品”作为干扰项。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0803', 1, 'KP0801', '简答', '教材8.1.1给出了4个应用任务。请分别指出每个任务的难点，以及教材用哪类技术解决。', NULL, '任务1打印“数据库系统概论”的全部先修课，难点是直接先修课之外还有间接先修课，需要递归查询，用WITH RECURSIVE子句解决。任务2打印一周内将过生日的学生，难点是日期运算与格式转换，用日期和时间内置函数解决。任务3给定学号计算平均学分绩点，难点是按成绩—绩点对照表逐门判断并累加学分绩点，需要自定义业务逻辑，用过程化SQL存储过程（例8.5）解决。任务4学生提交课程评价、教师回复，难点是用户与数据库之间的交互，用高级语言加JDBC实现。归纳起来是两类途径：扩展SQL自身功能，以及通过高级语言实现复杂应用。', '四个任务分别对应递归查询、内置函数、存储过程、宿主语言交互，是本章的总纲。答题时应把“任务—难点—技术”三者对应起来，不能只列技术名称；任务4的关键是交互界面，而不是单纯写一条INSERT。任务1难点是间接先修课层数未知，不能固定写多次自连接；任务3也不是简单AVG(Grade)，必须把每门课成绩映射为绩点后按学分加权。这四项正好说明为何既要扩展SQL，也要借助过程化和宿主语言。', '2', '教材 P241—250', '可让学生为每个任务各补一条自己的实现思路。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0804', 1, 'KP0802', '单选', '关于WITH子句创建的临时结果集，下列说法正确的是（ ）。', '[{"key": "A", "text": "长期保存在数据库中，可被所有会话访问"}, {"key": "B", "text": "仅在当前SQL语句执行时有效，不长期存储"}, {"key": "C", "text": "只能用于SELECT，不能用于INSERT或DELETE"}, {"key": "D", "text": "必须为每个临时结果集定义主码"}]', 'B', '教材指出WITH子句创建的是命名的临时结果集，该结果集仅在当前SQL语句（例如SELECT、INSERT或DELETE）执行时有效，不长期存储。它不需要主码，也不限于SELECT，因此A、C、D错误。WITH的价值在于提高语句性能和可读性。', '1', '教材 P244—245', '可把“临时结果集”改成“视图”让学生辨析生命周期。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0805', 1, 'KP0802', '填空', '例8.1先定义WITH RS1(grade) AS (SELECT AVG(grade) FROM SC WHERE Teachingclass=''81001-01'')和RS2(grade) AS (SELECT AVG(grade) FROM SC WHERE Teachingclass=''81001-02'')，则外层查询应写成______，其含义是______。', NULL, 'SELECT RS1.grade-RS2.grade FROM RS1,RS2；求教学班81001-01与81001-02学生选课平均成绩的差异', '两个CTE各产生一个单行单列的结果集，列名都是grade。外层查询把RS1与RS2连接后相减，即得到两个教学班平均成绩的差值。CTE的目标列必须与内部SELECT的输出列一致，引用时用“结果集名.列名”。', '1', '教材 P245；例8.1', '可把两个教学班号换成其他班号，或改成求平均成绩之和。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0806', 1, 'KP0803', '单选', 'WITH RECURSIVE子句的正确组成是（ ）。', '[{"key": "A", "text": "只有种子查询"}, {"key": "B", "text": "种子查询、UNION[ALL]和递归查询，最后再接引用临时结果集的SQL语句"}, {"key": "C", "text": "两个互不相关的SELECT语句"}, {"key": "D", "text": "一个CREATE PROCEDURE加一个CALL语句"}]', 'B', '教材给出WITH RECURSIVE RS AS 种子查询 UNION[ALL] 递归查询 SQL语句的结构：种子查询初始化临时结果集L[1]，递归查询以当前层为基础生成下一层，最后的SQL语句引用RS。只有种子查询无法递归，两个不相关SELECT不构成递归，存储过程也不是WITH RECURSIVE的组成部分。', '2', '教材 P245—246', '可把UNION与UNION ALL的区别做成另一道辨析题。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0807', 1, 'KP0803', '综合', '习题8第1题的 Course(Cno,Cname,Ccredit,Cpno) 用单列 Cpno 表示先修课，无法表示“一门课程有多门直接先修课”。请先说明如何把 Course 优化为 BCNF，再基于优化后的 Course(Cno,Cname,Ccredit) 与 Prereq(Cno,PreCno) 用 MySQL 8 的 WITH RECURSIVE 写出查找“数据库系统概论”全部先修课（含直接与间接）的课程号和课程名，并说明如何避免重复路径与死循环。', NULL, '-- 先优化：Course(Cno,Cname,Ccredit) 的码为 Cno；Prereq(Cno,PreCno) 的码为 (Cno,PreCno)，两者均为 BCNF，原单表 Cpno 的多值问题被消除。
-- 查询一：集合去重
WITH RECURSIVE ancestor(Cno, Cname) AS (
  SELECT P.Cno, P.Cname
    FROM Course C
    JOIN Prereq X ON C.Cno = X.Cno
    JOIN Course P ON X.PreCno = P.Cno
   WHERE C.Cname = ''数据库系统概论''
  UNION
  SELECT PP.Cno, PP.Cname
    FROM ancestor R
    JOIN Prereq X ON R.Cno = X.Cno
    JOIN Course PP ON X.PreCno = PP.Cno
)
SELECT DISTINCT Cno, Cname FROM ancestor ORDER BY Cno;

-- 查询二：若先修关系可能有环，增加 path 防死循环（MySQL 8 不支持 CYCLE 子句）
WITH RECURSIVE ancestor(Cno, Cname, path) AS (
  SELECT P.Cno, P.Cname, CAST(CONCAT('','', P.Cno, '','') AS CHAR(1000))
    FROM Course C
    JOIN Prereq X ON C.Cno = X.Cno
    JOIN Course P ON X.PreCno = P.Cno
   WHERE C.Cname = ''数据库系统概论''
  UNION ALL
  SELECT PP.Cno, PP.Cname, CONCAT(R.path, PP.Cno, '','')
    FROM ancestor R
    JOIN Prereq X ON R.Cno = X.Cno
    JOIN Course PP ON X.PreCno = PP.Cno
   WHERE LOCATE(CONCAT('','', PP.Cno, '',''), R.path) = 0
)
SELECT DISTINCT Cno, Cname FROM ancestor ORDER BY Cno;', '原 Course(Cno,Cname,Ccredit,Cpno) 若同一 Cno 出现多行（多门先修课），Cno 就不是码，违反 BCNF；习题8第1题正是先问“Course 属第几范式、如何优化”。优化为 Course(Cno,Cname,Ccredit) 与 Prereq(Cno,PreCno) 后，Course 的码是 Cno，Prereq 的码是 (Cno,PreCno)，两个模式都满足 BCNF，且能表示多门直接先修课。递归查询的种子从 Prereq 找“数据库系统概论”的直接先修课，递归步用当前课程号再查 Prereq 的 PreCno；UNION 去重；UNION ALL + path 防环，外层再 DISTINCT 按课程去重（同一先修课经多条路径到达时只保留一行）。 注意 CTE 名不要与基表 Prereq 同名：MySQL 在 Windows 上表名不区分大小写，递归 CTE 若叫 prereq 会把基表误认成递归表，报 3577（递归表只能引用一次）。', '3', '教材 P246；习题8第1题（含 BCNF 优化，习题册 P84）；第6章范式理论', '可改为查找某课程的全部后继课程，或输出 depth 层级。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0808', 1, 'KP0803', '简答', '简述WITH RECURSIVE中种子查询与递归查询的执行过程，并说明例8.2为什么输出81002和81001两门先修课。', NULL, '种子查询先执行，初始化临时结果集为第一层L[1]。例8.2的种子查询从Course中取出课程名为“数据库系统概论”的Cpno，得到81002，作为L[1]。递归查询以L[1]为基础，把RS中的Cpno与Course的Cno做连接，取出这些课程的Cpno作为第二层L[2]；例中81002的先修课是81001，故L[2]={81001}。再以L[2]为基础查第三层，81001没有先修课，L[3]为空，递归结束。最后外层查询用Cno IN (SELECT Cpno FROM RS)在Course中取出课程号与课程名，因此输出81002数据结构和81001程序设计基础与C语言。', '执行过程要按“种子→逐层递归→某层为空终止→外层查询”的顺序说明。关键是递归查询把上一层结果与基表连接，而不是一次扫描全表；例8.2的终止条件是81001的Cpno为空。若一门课程有多门直接先修课，同一层会得到多个课程号，机制不变。教材例8.2的分层结果是L[1]={81002}、L[2]={81001}、L[3]=∅；写执行过程时应逐层列出，不能把最终结果当成种子查询一次返回。', '2', '教材 P245—246；例8.2', '可给出三层先修链，让学生写出每层L[i]。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0809', 1, 'KP0804', '单选', '例8.3中表达式current_date + interval ''7'' day的作用是（ ）。', '[{"key": "A", "text": "当前日期之前第7天"}, {"key": "B", "text": "当前日期之后第7天"}, {"key": "C", "text": "当前时间加7小时"}, {"key": "D", "text": "当前月份加7个月"}]', 'B', 'interval是时间单位参数，''7'' day表示7天，current_date + interval ''7'' day即当前日期之后第七天。例8.3用它作为生日判断区间的上界，下界是current_date。加7小时应写interval ''7'' hour，加7个月应写interval ''7'' month。', '1', '教材 P247；例8.3', '可改为interval ''7'' month或current_date - interval ''7'' day。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0810', 1, 'KP0804', '简答', '说明例8.3中current_date、to_char、to_date、interval各自的作用，并给出该查询在MySQL 8中的等价写法。', NULL, 'current_date返回当前系统日期；to_char(current_date,''yyyy'')取当前年份，to_char(Sbirthdate,''mm-dd'')取学生出生月日；to_date把“当前年份+出生月日”拼成的字符串按格式转回日期；interval表示时间粒度，current_date + interval ''7'' day得到当前日期后第七天，BETWEEN判断转换后的生日是否在[当前日期,当前日期+7天]。MySQL 8等价写法：SELECT Sno,Sname,Ssex,Sbirthdate,Smajor FROM Student WHERE STR_TO_DATE(CONCAT(YEAR(CURDATE()),''-'',DATE_FORMAT(Sbirthdate,''%m-%d'')),''%Y-%m-%d'') BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 7 DAY);', '例8.3的核心是“把出生月日套到当前年份，再与当前日期及七天后日期比较”。Oracle/Kingbase风格用to_char/to_date，MySQL用DATE_FORMAT/STR_TO_DATE和DATE_ADD，函数名不同但语义一致。答题既要解释原函数，又要给出可执行的MySQL等价语句，不能只写“用日期函数”。', '3', '教材 P247；例8.3', '可改为判断一周内过生日且性别为女的学生。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0811', 1, 'KP0805', '多选', '嵌入式SQL中，数据库工作单元与源程序工作单元之间的通信通常借助（ ）。', '[{"key": "A", "text": "SQL通信区SQLCA"}, {"key": "B", "text": "主变量"}, {"key": "C", "text": "游标"}, {"key": "D", "text": "触发器"}]', 'ABC', '教材把嵌入式SQL的通信方式归纳为SQL通信区、主变量和游标：SQLCA传递SQL执行状态，主变量在SQL与主语言之间传递数据，游标协调SQL面向集合与主语言面向记录的处理方式。触发器是数据库完整性机制，不是嵌入式SQL的通信方式，故D错误。', '2', '教材 P248—249', '可把D换成“指示变量”，变成四个正确项的多选。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0812', 1, 'KP0805', '简答', '为什么嵌入式SQL需要游标？SQLCODE和指示变量分别起什么作用？', NULL, 'SQL是面向集合的，一条语句可产生或处理多条记录；高级语言是面向记录的，一次只能处理一条记录。游标是DBMS为应用程序开设的数据缓冲区，存放SQL语句的执行结果，并带一个指针，应用程序可通过指针逐条获取记录，从而协调两种处理方式。SQLCODE是SQLCA中的一个变量，用来存放每次执行SQL语句后返回的代码；执行不成功时存放错误代码，程序据此控制流程。指示变量是附在主变量后的整型变量，可指示输入主变量是否为空值，也可检测输出主变量是否为空值或被截断。', '回答要抓住“集合 vs 记录”这一矛盾，说明游标是缓冲区而不是查询语句。SQLCODE解决“执行成功与否”的状态传递，指示变量解决“空值与截断”的数据语义，两者都属于主语言与数据库之间的通信机制，不能混为一谈。教材P248以“SQL操纵数据库、高级语言控制流程”说明两种计算模型如何通信：游标解决逐行读取，SQLCODE报告状态，指示变量补充NULL与截断语义，三者职责不同。', '2', '教材 P248—249', '可让学生写出带指示变量的FETCH语句。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0813', 1, 'KP0806', '单选', '过程化SQL块中，定义部分、执行部分和异常处理部分的关键字依次是（ ）。', '[{"key": "A", "text": "BEGIN、DECLARE、EXCEPTION"}, {"key": "B", "text": "DECLARE、BEGIN、EXCEPTION"}, {"key": "C", "text": "DECLARE、EXCEPTION、BEGIN"}, {"key": "D", "text": "EXCEPTION、BEGIN、DECLARE"}]', 'B', '教材图8.4给出过程化SQL块的基本结构：DECLARE为定义部分，声明变量、常量、游标、异常等；BEGIN开始执行部分，书写SQL语句和流程控制语句；EXCEPTION为异常处理部分；最后以END结束。定义必须在执行之前，异常处理在执行部分之后。', '1', '教材 P250—251', '可把EXCEPTION换成END，考查块结束关键字。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0814', 1, 'KP0806', '填空', '过程化SQL中，变量定义可写为“变量名 数据类型[[NOT NULL]:=初值表达式]”，常量定义写为“______”，赋值语句写为“______”；常量在作用域内不能修改，否则会______。', NULL, '常量名 数据类型 CONSTANT:=常量表达式；变量名:=表达式；返回一个异常', '常量定义必须在CONSTANT后给出常量表达式作为初值，且该值在常量存在期间或作用域内不能改变；若试图修改，过程化SQL将返回异常。赋值使用:=，不是=，后者在过程化SQL中通常用于比较。变量定义与常量定义都只在该基本块内有效。', '2', '教材 P251', '可给出一个具体常量定义让学生改错。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0815', 1, 'KP0807', '单选', '关于FOR-LOOP语句FOR count IN [REVERSE] bound1..bound2 LOOP…END LOOP，下列说法正确的是（ ）。', '[{"key": "A", "text": "未指定REVERSE时count从bound2递减到bound1"}, {"key": "B", "text": "指定REVERSE时count从bound2递减到bound1"}, {"key": "C", "text": "bound1必须大于bound2"}, {"key": "D", "text": "FOR-LOOP不能提前退出"}]', 'B', '教材说明：未指定REVERSE时count先置为下界bound1，检查是否小于上界bound2，按+1步长递增；指定REVERSE时count置为上界bound2，检查是否大于下界bound1，按-1步长递减。因此A方向说反，C无此要求，D错误，循环仍可用EXIT/LEAVE等提前结束。', '2', '教材 P252', '可改为FOR count IN REVERSE 10..1，问执行次数与终值。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0816', 1, 'KP0807', '简答', '过程化SQL有哪三种循环语句？各如何结束？IF语句有哪三种形式？并指出MySQL存储过程中与之对应的写法。', NULL, '三种循环是LOOP、WHILE-LOOP和FOR-LOOP。LOOP是无限循环，依靠EXIT、BREAK或LEAVE等语句在适当条件下提前结束；WHILE-LOOP先判断条件，为真才执行循环体；FOR-LOOP按计数变量从下界到上界（或REVERSE时从上界到下界）循环。IF有三种形式：IF-THEN、IF-THEN-ELSE和可嵌套的IF。MySQL存储过程中对应写法为：LOOP…END LOOP配合LEAVE label退出；WHILE condition DO…END WHILE；REPEAT…UNTIL condition END REPEAT；条件语句用IF…THEN…ELSEIF…ELSE…END IF。', '教材以PL/SQL风格给出LOOP、WHILE-LOOP、FOR-LOOP，MySQL没有FOR-LOOP和EXIT WHEN，需用LOOP加LEAVE、WHILE或REPEAT实现。答题要同时写出教材三种循环和MySQL落地写法，并说明IF三种形式，体现标准SQL与产品方言的差异。', '3', '教材 P251—253', '可把FOR-LOOP改写为MySQL WHILE循环。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0817', 1, 'KP0808', '综合', '对“学生选课”数据库，用MySQL 8编写存储过程stat_discrete_math，使用游标统计“离散数学”课程各分数段人数：100分、90—99、80—89、70—79、60—69、60分以下，并输出统计结果。', NULL, 'DELIMITER $$
CREATE PROCEDURE stat_discrete_math()
BEGIN
  DECLARE v_grade INT;
  DECLARE p_100, p_90, p_80, p_70, p_60, p_other INT DEFAULT 0;
  DECLARE done INT DEFAULT 0;
  DECLARE cur CURSOR FOR
    SELECT SC.Grade
      FROM SC JOIN Course C ON SC.Cno = C.Cno
     WHERE C.Cname = ''离散数学'';
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

  OPEN cur;
  read_loop: LOOP
    FETCH cur INTO v_grade;
    IF done = 1 THEN LEAVE read_loop; END IF;
    IF v_grade = 100 THEN SET p_100 = p_100 + 1;
    ELSEIF v_grade >= 90 THEN SET p_90 = p_90 + 1;
    ELSEIF v_grade >= 80 THEN SET p_80 = p_80 + 1;
    ELSEIF v_grade >= 70 THEN SET p_70 = p_70 + 1;
    ELSEIF v_grade >= 60 THEN SET p_60 = p_60 + 1;
    ELSE SET p_other = p_other + 1;
    END IF;
  END LOOP;
  CLOSE cur;

  SELECT p_100 AS `100分`, p_90 AS `90-99`, p_80 AS `80-89`,
         p_70 AS `70-79`, p_60 AS `60-69`, p_other AS `60以下`;
END$$
DELIMITER ;

CALL stat_discrete_math();', 'MySQL要求变量、游标、处理器按顺序声明，处理器必须在游标之后。CONTINUE HANDLER FOR NOT FOUND在FETCH取空时把done置1，循环用LEAVE read_loop退出，避免取空后继续比较。分数段用降序IF-ELSEIF判断，保证90—99不会落入80—89。教材例8.2使用PL/SQL的游标与EXIT WHEN %NOTFOUND，MySQL需改写为处理器加LEAVE；输出用SELECT一行展示六个计数。', '3', '教材 P253—254；习题8 第2题①（习题册 P84—85）', '可改为统计任意课程，课程名作为IN参数。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0818', 1, 'KP0808', '单选', '使用游标处理多行查询结果的正确步骤是（ ）。', '[{"key": "A", "text": "OPEN→DECLARE→FETCH→CLOSE"}, {"key": "B", "text": "DECLARE→OPEN→FETCH→CLOSE"}, {"key": "C", "text": "DECLARE→FETCH→OPEN→CLOSE"}, {"key": "D", "text": "OPEN→FETCH→DECLARE→CLOSE"}]', 'B', '教材给出的游标使用步骤是：先DECLARE声明游标（只说明，不执行SELECT），再OPEN打开游标（执行SELECT并把结果取入缓冲区），然后循环FETCH取当前记录到变量，最后CLOSE关闭游标释放资源。因此顺序必须是DECLARE→OPEN→FETCH→CLOSE。', '1', '教材 P253—254；例8.4', '可打乱选项顺序，或加入“先FETCH后OPEN”干扰项。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0819', 1, 'KP0808', '填空', '声明游标使用______语句，打开游标使用______语句，取当前记录使用______语句，关闭游标使用______语句；循环中常用______判断游标是否已无记录。', NULL, 'DECLARE…CURSOR FOR；OPEN；FETCH…INTO；CLOSE；EXIT WHEN 游标名%NOTFOUND（MySQL用CONTINUE HANDLER FOR NOT FOUND）', '声明游标要把SELECT语句关联到游标名；OPEN才真正执行SELECT并填充缓冲区；FETCH把当前记录送入与目标列一一对应的变量；CLOSE释放缓冲区。教材例8.4用EXIT WHEN mycursor%NOTFOUND在无记录时退出循环，MySQL没有%NOTFOUND属性，改用NOT FOUND处理器配合LEAVE。', '2', '教材 P253—254；例8.4', '可给出例8.4代码，让学生补全缺失的四个关键字。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0820', 1, 'KP0809', '综合', '教材例8.5 的 70 AND 72 为教材笔误，以表8.2 的 70—79 为准。依据例8.5，用MySQL 8编写存储过程compGPA(inSno,outGPA)：给定学生学号，用游标遍历该生全部选课，按表8.2的成绩—绩点对照表计算平均学分绩点，并给出调用示例。', NULL, 'DELIMITER $$
CREATE PROCEDURE compGPA(IN inSno CHAR(10), OUT outGPA DECIMAL(4,2))
BEGIN
  DECLARE v_credit INT;
  DECLARE v_grade INT;
  DECLARE v_courseGPA DECIMAL(3,1);
  DECLARE totalGPA DECIMAL(10,2) DEFAULT 0;
  DECLARE totalCredit INT DEFAULT 0;
  DECLARE done INT DEFAULT 0;
  DECLARE cur CURSOR FOR
    SELECT C.Ccredit, SC.Grade
      FROM SC JOIN Course C ON SC.Cno = C.Cno
     WHERE SC.Sno = inSno;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

  OPEN cur;
  read_loop: LOOP
    FETCH cur INTO v_credit, v_grade;
    IF done = 1 THEN LEAVE read_loop; END IF;
    IF v_grade BETWEEN 90 AND 100 THEN SET v_courseGPA = 4.0;
    ELSEIF v_grade BETWEEN 80 AND 89 THEN SET v_courseGPA = 3.0;
    ELSEIF v_grade BETWEEN 70 AND 79 THEN SET v_courseGPA = 2.0;
    ELSEIF v_grade BETWEEN 60 AND 69 THEN SET v_courseGPA = 1.0;
    ELSE SET v_courseGPA = 0;
    END IF;
    SET totalGPA = totalGPA + v_courseGPA * v_credit;
    SET totalCredit = totalCredit + v_credit;
  END LOOP;
  CLOSE cur;

  IF totalCredit = 0 THEN
    SET outGPA = 0;
  ELSE
    SET outGPA = ROUND(totalGPA / totalCredit, 2);
  END IF;
END$$
DELIMITER ;

SET @gpa = 0;
CALL compGPA(''20180001'', @gpa);
SELECT @gpa AS GPA;', '教材例8.5 的 70 AND 72 为教材笔误，以表8.2 的 70—79 为准；本题按表8.2 判分，答案只接受 70—79。过程体先声明变量、游标和NOT FOUND处理器；游标连接SC与Course，取出学分和成绩；循环内按表8.2把成绩映射为4、3、2、1、0，并累加“绩点×学分”与总学分；循环结束后用总学分绩点除以总学分，四舍五入两位。教材示例的70分档写作70 AND 72，与表8.2的70—79不一致，实现时以表8.2为准写70—79。还需处理总学分为0的情况，避免除零。调用时OUT参数用事先定义的@变量接收。', '3', '教材 P255；表8.2 P243；例8.5', '可改为用存储函数返回GPA，或把成绩—绩点表建成数据库表。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0821', 1, 'KP0809', '多选', '关于存储过程，下列说法正确的有（ ）。', '[{"key": "A", "text": "参数有IN、OUT、INOUT三种模式，不声明时默认为IN"}, {"key": "B", "text": "OUT参数调用时不传入参数值，需要事先定义接收变量"}, {"key": "C", "text": "存储过程在创建时完成语法分析和优化，运行效率较高"}, {"key": "D", "text": "存储过程只能返回一个值，不能有输出参数"}]', 'ABC', '教材指出存储过程参数有IN、OUT、INOUT三种模式，缺省为IN；OUT参数调用时不传入值，而作为返回值返回，调用前需定义输出变量；存储过程创建时即完成语法分析和优化，因而运行效率更高。D与OUT参数的存在直接矛盾，存储过程可以有多个输出参数。', '2', '教材 P254—256', '可把D改成“存储过程可减少客户机与服务器通信量”，变为四项正确。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0822', 1, 'KP0809', '简答', '比较存储过程与存储函数，并写出创建、调用、重命名和删除的语句要点。', NULL, '二者都是经编译后保存在数据库中的持久性存储模块，创建语法类似。区别是存储函数必须用RETURNS指定返回类型，并可在表达式中调用；存储过程通过OUT/INOUT参数返回结果。创建存储过程用CREATE OR REPLACE PROCEDURE 过程名(参数...) AS 过程化SQL块；创建存储函数用CREATE OR REPLACE FUNCTION 函数名(参数...) RETURNS 类型 AS 过程化SQL块。调用存储过程用CALL或PERFORM，调用存储函数用CALL或SELECT 函数名(...)。重命名用ALTER PROCEDURE/FUNCTION 原名 RENAME TO 新名；重新编译用ALTER PROCEDURE/FUNCTION 名 COMPILE；删除用DROP PROCEDURE/FUNCTION 名。', '比较要落到“是否必须指定返回类型、如何返回结果、如何调用”三点，而不是只说二者类似。ALTER用于重命名或重新编译，DROP用于删除；MySQL不支持教材中的ALTER … RENAME TO/COMPILE写法，需先删后建或以新名重建，答题时应指出方言差异。', '2', '教材 P254—257', '可给出一段函数代码，让学生改成过程并用OUT参数返回。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0823', 1, 'KP0810', '单选', '使用JDBC执行一条查询语句并取得结果集，应调用（ ）。', '[{"key": "A", "text": "Statement.executeUpdate"}, {"key": "B", "text": "Statement.executeQuery"}, {"key": "C", "text": "Connection.commit"}, {"key": "D", "text": "DriverManager.getConnection"}]', 'B', '教材说明executeQuery用于执行数据库查询语句，成功执行后返回ResultSet对象；executeUpdate用于增删改及数据库定义语句，返回int影响行数；execute用于存储过程或动态SQL，返回boolean。Connection.commit用于提交事务，getConnection用于建立连接，都不返回结果集。', '2', '教材 P259—262', '可改为执行INSERT，问应调用哪个方法及返回值。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0824', 1, 'KP0810', '多选', '关于JDBC常用类与PreparedStatement，下列说法正确的有（ ）。', '[{"key": "A", "text": "Connection用于建立与指定数据库的连接"}, {"key": "B", "text": "Statement用于执行静态SQL语句并返回结果"}, {"key": "C", "text": "PreparedStatement用于执行含参SQL，预编译可降低SQL注入风险并可复用"}, {"key": "D", "text": "CallableStatement用于执行数据库存储过程"}]', 'ABCD', '教材8.3.2列出常用类：Connection建立连接，Statement执行静态SQL，PreparedStatement执行含参SQL，CallableStatement执行存储过程，ResultSet检索结果集。8.3.3进一步指出PreparedStatement经过预编译，可有效防止SQL注入，预编译结果可保存在对象中供多次执行，提高效率。因此四项均正确。', '2', '教材 P259—262', '可把C改成“PreparedStatement只能执行一次”，作为错误项。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0825', 1, 'KP0810', '综合', '使用JDBC编写Java程序，把MySQL中Product表的数据迁移追加到PostgreSQL的Product表。请给出建表语句，以及加载驱动、建立两个连接、读取源表、批量写入目标表、异常处理与释放资源的完整代码链。', NULL, '-- MySQL 源表
CREATE TABLE Product(
  PID INT NOT NULL AUTO_INCREMENT,
  PName VARCHAR(30) NOT NULL,
  Price DECIMAL(5,2) NOT NULL,
  Amount INT NOT NULL,
  DateOfManufacture DATE NOT NULL,
  PRIMARY KEY(PID, DateOfManufacture)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- PostgreSQL 目标表
CREATE TABLE Product(
  PID serial NOT NULL,
  PName varchar(30) NOT NULL,
  Price decimal(5,2) NOT NULL,
  Amount integer NOT NULL,
  DateOfManufacture date NOT NULL,
  PRIMARY KEY(PID, DateOfManufacture)
);

// MigrateProduct.java
import java.sql.*;

public class MigrateProduct {
  public static void main(String[] args) throws Exception {
    String mysqlUrl = "jdbc:mysql://127.0.0.1:3306/mysql_db?useSSL=true&serverTimezone=Asia/Shanghai";
    String pgUrl = "jdbc:postgresql://127.0.0.1:5432/pg_db";
    Class.forName("com.mysql.cj.jdbc.Driver");
    Class.forName("org.postgresql.Driver");

    try (Connection src = DriverManager.getConnection(mysqlUrl, System.getenv("MYSQL_USER"), System.getenv("MYSQL_PASSWORD"));
         Connection dst = DriverManager.getConnection(pgUrl, System.getenv("PG_USER"), System.getenv("PG_PASSWORD"));
         Statement st = src.createStatement();
         ResultSet rs = st.executeQuery("SELECT PName,Price,Amount,DateOfManufacture FROM Product");
         PreparedStatement ps = dst.prepareStatement(
             "INSERT INTO Product(PName,Price,Amount,DateOfManufacture) VALUES(?,?,?,?)")) {
      int i = 0;
      while (rs.next()) {
        ps.setString(1, rs.getString("PName"));
        ps.setBigDecimal(2, rs.getBigDecimal("Price"));
        ps.setInt(3, rs.getInt("Amount"));
        ps.setDate(4, rs.getDate("DateOfManufacture"));
        ps.addBatch();
        if (++i % 100 == 0) {
          ps.executeBatch();
          ps.clearBatch();
        }
      }
      ps.executeBatch();
      ps.clearBatch();
    } catch (SQLException e) {
      e.printStackTrace();
      throw e;
    }
  }
}', '代码链对应教材JDBC五步：加载MySQL与PostgreSQL驱动、用DriverManager分别建立源/目标连接、用Statement.executeQuery读取MySQL的Product、用PreparedStatement按列绑定并addBatch、每100条executeBatch并clearBatch、最后补一次批量提交。try-with-resources按ps、rs、st、dst、src的逆序自动关闭，异常统一捕获后重新抛出。目标表PID为serial，迁移时不复制PID，避免与目标库已有主码冲突；源表与目标表主码都是(PID,DateOfManufacture)，追加时需保证生产日期组合不冲突。PreparedStatement预编译同时降低SQL注入风险。 凭据从环境变量 MYSQL_USER/MYSQL_PASSWORD、PG_USER/PG_PASSWORD 读取，生产环境必须使用 TLS；本示例仅本机开发，不能把口令写进代码。', '3', '教材 P259—266；习题8 第4题（习题册 P88—91）', '可改为按生产日期增量迁移，或加入失败回滚与日志。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0826', 1, 'KP0802', '多选', '关于WITH子句创建的临时结果集，下列说法正确的有（ ）。', '[{"key":"A","text":"仅在当前SQL语句执行期间有效，不长期存储"},{"key":"B","text":"目标列必须与内部SELECT语句的输出列保持一致"},{"key":"C","text":"WITH可用于SELECT、INSERT或DELETE等语句"},{"key":"D","text":"必须为临时结果集定义主码"}]', 'ABC', 'A/B 是 WITH 临时结果集的生命周期与目标列一致性；C 符合教材“SELECT、INSERT或DELETE执行时有效”；D 错，临时结果集不需要主码。 WITH 的临时结果集只在当前语句内可见，不能跨会话/跨语句长期引用；它也不需要主码，不能与视图混淆。', '2', '教材 P244—245；例8.1', '可把“临时结果集”改成“视图”让学生辨析生命周期。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0827', 1, 'KP0804', '单选', '例8.3 中 to_date(to_char(current_date,''yyyy'')||''-''||to_char(Sbirthdate,''mm-dd'')) 的主要作用是（ ）。', '[{"key":"A","text":"把当前年份与学生出生月日拼成日期"},{"key":"B","text":"把出生日期转成字符串"},{"key":"C","text":"计算学生年龄"},{"key":"D","text":"返回当前时间"}]', 'A', 'to_char(current_date,''yyyy'') 取当前年份，to_char(Sbirthdate,''mm-dd'') 取出生月日，拼接后 to_date 转回日期类型，用于与当前日期/七天后日期比较；不是算年龄，也不是返回时间。', '2', '教材 P247；例8.3', '可改为 interval ''7'' day 或 MySQL DATE_ADD 的等价题。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0828', 1, 'KP0806', '单选', '过程化SQL块中 EXCEPTION 部分的作用是（ ）。', '[{"key":"A","text":"声明变量、常量、游标"},{"key":"B","text":"执行SQL语句与流程控制"},{"key":"C","text":"捕获异常并按异常类型处理"},{"key":"D","text":"提交事务"}]', 'C', '过程化SQL块由 DECLARE、BEGIN…END、EXCEPTION 三部分组成：DECLARE 声明变量/常量/游标/异常，BEGIN…END 执行SQL与流程控制，EXCEPTION 在异常发生时按类型处理。提交事务由 COMMIT 完成。', '2', '教材 P250—P253；8.2.1/8.2.3', '可给出一个除零异常让学生补 EXCEPTION 分支。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

-- ============================================================
-- [prereq] seed-kp-ch02-ch03.sql (knowledge_prereq)
-- ============================================================
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

-- ============================================================
-- [prereq] seed-prereq-ch01.sql
-- ============================================================
-- 智学领航 · 第1章先修边（知识图谱边）
-- 日期：2026-11-02　产出：Trae AI（按教材第1章知识结构编排，待内容复核人抽检）
-- 语义：kp_id 的先修是 prereq_kp_id，边方向为“先修 → 后继”。
-- 幂等：uk_edge(kp_id, prereq_kp_id)，可重复执行。
-- 结构：章内 12 条按教材第1章知识结构；跨章 2 条衔接第2章关系模型与第3章 SQL 三级模式。


INSERT INTO knowledge_prereq (kp_id, prereq_kp_id) VALUES
-- 1.1 数据库系统概述：四个基本概念是全章根节点
('KP0102', 'KP0101'),  -- 数据管理发展：需先懂数据/数据库/DBMS/DBS
('KP0103', 'KP0101'),  -- 系统组成与人员职责：DBMS、DBA 本身是四概念之一
('KP0104', 'KP0101'),  -- 数据模型三要素：建立在数据与数据库概念之上
-- 1.2 数据模型：三要素 → 概念模型 → 逻辑模型
('KP0105', 'KP0104'),  -- E-R 是概念模型，实体/属性/联系对应三要素
('KP0106', 'KP0104'),  -- 层次/网状/关系模型都按三要素描述
('KP0106', 'KP0105'),  -- 概念模型是逻辑模型的前序抽象
-- 1.3 数据库系统结构：三级模式 → 二级映像 → 数据独立性
('KP0107', 'KP0106'),  -- 三级模式由逻辑数据模型定义
('KP0108', 'KP0107'),  -- 二级映像是外模式/模式、模式/内模式之间的映射
('KP0109', 'KP0103'),  -- 体系结构以系统组成（DBMS/应用/DBA）为基础
('KP0109', 'KP0107'),  -- 集中式/C-S/分布式/云都围绕三级模式部署
('KP0110', 'KP0102'),  -- 特点需与人工管理、文件系统阶段对比
('KP0110', 'KP0108'),  -- 数据独立性是数据库系统的核心特点
-- 跨章衔接
('KP0201', 'KP0106'),  -- 关系模型是第1章“常用逻辑数据模型”的展开
('KP0301', 'KP0107')   -- SQL 概述含“三级模式对应”，需先懂三级模式
ON DUPLICATE KEY UPDATE kp_id = VALUES(kp_id);

-- ============================================================
-- [prereq] seed-prereq-ch04.sql
-- ============================================================
-- 智学领航 · 第4章先修边（知识图谱边，草稿）
-- 日期：2026-11-02　产出：Trae AI（按教材第4章知识结构编排，待内容复核人签核）
-- 语义：kp_id 的先修是 prereq_kp_id，边方向为“先修 → 后继”。
-- 幂等：uk_edge(kp_id, prereq_kp_id)，可重复执行。
-- 前置：先导入 seed-kp-ch04.sql；未签核前不要执行本文件，避免悬空边。


INSERT INTO knowledge_prereq (kp_id, prereq_kp_id) VALUES
-- 4.1 概述是本章根；4.2 各项控制措施建立在安全目标与威胁之上
('KP0402', 'KP0401'),  -- 身份鉴别是最外层控制，需先懂安全威胁与目标
('KP0403', 'KP0402'),  -- 存取控制针对已通过身份鉴别的用户，先鉴别后授权
-- 4.2 控制链：DAC → GRANT → REVOKE / 角色；MAC 与 DAC 并列
('KP0404', 'KP0403'),  -- GRANT 是自主存取控制的实现手段
('KP0404', 'KP0301'),  -- GRANT/REVOKE 属 SQL 的数据控制，需先懂 SQL 概述
('KP0405', 'KP0404'),  -- 先有授权才谈收回
('KP0406', 'KP0404'),  -- 角色是权限集合，建立在 GRANT 之上
('KP0407', 'KP0403'),  -- MAC 与 DAC 是并列的存取控制方法
-- 4.3 视图机制：授权 + 第3章视图定义
('KP0408', 'KP0403'),  -- 视图机制需与授权机制配合
('KP0408', 'KP0309'),  -- 需先会定义视图（第3章视图定义与查询）
-- 4.4 审计是事后检查，排在存取控制之后
('KP0409', 'KP0403'),
-- 4.5 加密：安全性体系的组成部分
('KP0410', 'KP0401'),
-- 4.6 其他保护：针对 MAC 未解决的问题
('KP0411', 'KP0407')
ON DUPLICATE KEY UPDATE kp_id = VALUES(kp_id);

-- ============================================================
-- [prereq] seed-prereq-ch05.sql
-- ============================================================
-- 智学领航 · 第5章先修边（知识图谱边，草稿）
-- 日期：2026-11-02　产出：Trae AI（按教材第5章知识结构编排，待内容复核人签核）
-- 语义：kp_id 的先修是 prereq_kp_id，边方向为“先修 → 后继”。
-- 幂等：uk_edge(kp_id, prereq_kp_id)，可重复执行。
-- 前置：先导入 seed-kp-ch05.sql；未签核前不要执行本文件，避免悬空边。


INSERT INTO knowledge_prereq (kp_id, prereq_kp_id) VALUES
-- 5.1 概述是本章根
('KP0502', 'KP0501'),  -- 实体完整性属于完整性约束体系
('KP0505', 'KP0501'),  -- 用户定义完整性属于完整性约束体系
-- 5.2 → 5.3：先有主码，才谈外码参照
('KP0503', 'KP0502'),  -- 参照完整性参照的是被参照表的主码
('KP0503', 'KP0201'),  -- 外码/主码建立在关系模型基本概念之上（跨章）
-- 5.3 定义 → 5.3 违约处理
('KP0504', 'KP0503'),
-- 5.5 约束命名建立在用户定义完整性之上
('KP0506', 'KP0505'),
-- 5.6 域约束是属性约束的类型级复用
('KP0507', 'KP0505'),
-- 断言是多表/聚集的一般完整性约束
('KP0508', 'KP0505'),
-- 5.7 触发器用于更复杂的完整性检查与违约处理
('KP0509', 'KP0504')
ON DUPLICATE KEY UPDATE kp_id = VALUES(kp_id);

-- ============================================================
-- [prereq] seed-prereq-ch06.sql
-- ============================================================
-- 智学领航 · 第6章先修边（知识图谱边，草稿）
-- 日期：2026-11-03　产出：Trae AI（按审核意见修订，AI 修订完成，待独立复审）
-- 语义：kp_id 的先修是 prereq_kp_id，边方向为“先修 → 后继”。
-- 幂等：uk_edge(kp_id, prereq_kp_id)，可重复执行。
-- 前置：先导入 seed-kp-ch06.sql；未签核前不要执行本文件，避免悬空边。


INSERT INTO knowledge_prereq (kp_id, prereq_kp_id) VALUES
-- 6.1 问题提出是本章根
('KP0602', 'KP0601'),  -- 函数依赖在不良模式的背景下引入
-- 6.2 规范化链：FD → 码 → 2NF → 3NF → BCNF → 4NF
('KP0603', 'KP0602'),  -- 码用函数依赖定义
('KP0603', 'KP0203'),  -- 码建立在第2章候选码/主属性概念之上（跨章）
('KP0604', 'KP0603'),  -- 2NF 用候选码与非主属性定义
('KP0605', 'KP0604'),  -- 3NF 在 2NF 基础上排除传递依赖
('KP0606', 'KP0605'),  -- BCNF 在 3NF 基础上强化决定因素条件
('KP0607', 'KP0606'),  -- 多值依赖/4NF 在 BCNF 之后讨论
-- 6.3 公理系统 → 6.4 最小覆盖/保持 FD 分解
('KP0608', 'KP0602'),  -- Armstrong 公理以函数依赖为对象
('KP0609', 'KP0608'),  -- 最小覆盖依赖闭包与等价判定
-- 6.5 无损连接分解
('KP0610', 'KP0609'),  -- 无损连接紧随保持FD分解，6.5.2 比较两种准则
('KP0611', 'KP0610'),  -- 算法6.4—6.6以无损连接定义与表算法为基础
('KP0611', 'KP0607')   -- 算法6.5/6.6分别需要BCNF与4NF（经KP0606链覆盖）
ON DUPLICATE KEY UPDATE kp_id = VALUES(kp_id);

-- ============================================================
-- [prereq] seed-prereq-ch07.sql
-- ============================================================
-- 智学领航 · 第7章先修边（知识图谱边，草稿）
-- 日期：2026-11-03　产出：Trae AI（按教材第7章知识结构编排，AI 修订版；待内容复核人签核）
-- 语义：kp_id 的先修是 prereq_kp_id，边方向为“先修 → 后继”。
-- 幂等：uk_edge(kp_id, prereq_kp_id)，可重复执行。
-- 前置：先导入 seed-kp-ch07.sql；未签核前不要执行本文件，避免悬空边。


INSERT INTO knowledge_prereq (kp_id, prereq_kp_id) VALUES
-- 7.1 概述 → 7.2 需求分析
('KP0702', 'KP0701'),  -- 各级模式建立在六阶段设计过程之上
('KP0703', 'KP0701'),  -- 需求分析是六阶段中的第一阶段
('KP0704', 'KP0703'),  -- 数据字典在需求分析阶段建立
-- 7.3 概念结构设计
('KP0705', 'KP0704'),  -- 概念模型以数据字典中的数据结构为基础
('KP0706', 'KP0705'),  -- 扩展 E-R 模型以基本 E-R 模型为基础
('KP0707', 'KP0705'),  -- E-R 图集成以基本 E-R 为基础；扩展 E-R（7.3.3）是选读，不作硬前置
-- 7.4 逻辑结构设计
('KP0708', 'KP0707'),  -- E-R 图转关系模型需先有基本 E-R 图
('KP0708', 'KP0603'),  -- 转换时确定关系模式的码，依赖第6章“码”的概念（跨章）
('KP0709', 'KP0708'),  -- 数据模型优化在关系模式转换之后
('KP0709', 'KP0607'),  -- 数据模型优化以第6章范式/多值依赖理论为指导（跨章，覆盖 1NF—4NF；避免与 KP0606 冗余）
-- 7.5 物理结构设计 → 7.6 实施与维护
('KP0710', 'KP0709')   -- 物理设计基于优化后的逻辑模型
ON DUPLICATE KEY UPDATE kp_id = VALUES(kp_id);

-- ============================================================
-- [prereq] seed-prereq-ch08.sql
-- ============================================================
-- 智学领航 · 第8章先修边（知识图谱边，草稿）
-- 日期：2026-11-03　产出：Trae AI（按教材第8章知识结构编排，待内容复核人签核）
-- 语义：kp_id 的先修是 prereq_kp_id，边方向为“先修 → 后继”。
-- 幂等：uk_edge(kp_id, prereq_kp_id)，可重复执行。
-- 前置：先导入 seed-kp-ch08.sql；未签核前不要执行本文件，避免悬空边。


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

-- ============================================================
-- [demo] seed-demo-users.sql
-- ============================================================
-- 智学领航 · 演示库账号（仅演示库使用）
-- 用途：全新环境重建答辩演示库，保证账号可登录、教师端多行数据可见。
-- 口令：Demo@2026（仅演示库；生产/真实试用环境严禁使用本文件）
-- 幂等：按 user_id 插入；已存在的账号只更新角色/姓名/班级，不重置已有口令。
-- 前置：先执行 init-v2.sql（建表）。


SET @demo_password = '$2a$10$B5XbzrDy2S2IrgpbVead.eJ1G7RRpnY4CHJrHYg8Z5QaVVo3N1UjO';

INSERT INTO sys_user (user_id, username, password, real_name, role, class_name) VALUES
(1,    'teacher01', @demo_password, '演示教师',   'TEACHER', NULL),
(2,    'student01', @demo_password, '演示学生01', 'STUDENT', '演示数据-23软件工程1班'),
(9001, 'demo_stu01', @demo_password, '演示学生02', 'STUDENT', '演示数据-23软件工程1班'),
(9002, 'demo_stu02', @demo_password, '演示学生03', 'STUDENT', '演示数据-23软件工程1班'),
(9003, 'demo_stu03', @demo_password, '演示学生04', 'STUDENT', '演示数据-23软件工程1班'),
(9004, 'demo_stu04', @demo_password, '演示学生05', 'STUDENT', '演示数据-23软件工程2班'),
(9005, 'demo_stu05', @demo_password, '演示学生06', 'STUDENT', '演示数据-23软件工程2班'),
(9006, 'demo_stu06', @demo_password, '演示学生07', 'STUDENT', '演示数据-23软件工程2班'),
(9007, 'demo_stu07', @demo_password, '演示学生08', 'STUDENT', '演示数据-23软件工程2班'),
(9008, 'demo_stu08', @demo_password, '演示学生09', 'STUDENT', '演示数据-23数据科学1班'),
(9009, 'demo_stu09', @demo_password, '演示学生10', 'STUDENT', '演示数据-23数据科学1班'),
(9010, 'demo_stu10', @demo_password, '演示学生11', 'STUDENT', '演示数据-23数据科学1班'),
(9011, 'demo_stu11', @demo_password, '演示学生12', 'STUDENT', '演示数据-23数据科学1班')
ON DUPLICATE KEY UPDATE
  real_name = VALUES(real_name),
  role = VALUES(role),
  class_name = VALUES(class_name),
  is_active = 1;

-- ============================================================
-- [demo] seed-demo-data.sql
-- ============================================================
-- 智学领航 · 演示数据（全部为构造数据，不是真实试用结果）
-- 覆盖：12 名学生、180 条练习、错题本、三层推荐、今日/昨日计划明细、12 条讲解留痕、画像快照。
-- 标注：姓名前缀“演示学生”，班级“演示数据-*”，作答/错题/讲解/推荐正文均带“[演示数据]”。
-- 幂等：练习/错题/计划/推荐/快照按固定键 upsert；讲解留痕按 explain_id 判重。


-- 12 名学生 × 5 个知识点 × 3 次练习 = 180 条；低/中/高三种画像。
INSERT INTO practice_record (user_id, client_token, q_id, kp_id, source, is_correct, my_answer, error_type, cost_seconds, created_at) VALUES
(2, 'demo-prac-2-1-1', 'Q_0201', 'KP0201', 'SELF', 0, '[演示数据] 错误选项占位', 'CONCEPT_CONFUSION', 47, DATE_SUB(NOW(), INTERVAL 12 HOUR)),
(2, 'demo-prac-2-1-2', 'Q_0213', 'KP0201', 'SELF', 0, '[演示数据] 错误选项占位', 'CONCEPT_CONFUSION', 58, DATE_SUB(NOW(), INTERVAL 13 HOUR)),
(2, 'demo-prac-2-1-3', 'Q_0201', 'KP0201', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 69, DATE_SUB(NOW(), INTERVAL 14 HOUR)),
(2, 'demo-prac-2-2-1', 'Q_0203', 'KP0203', 'SELF', 0, '[演示数据] 错误选项占位', 'BOUNDARY_ERROR', 54, DATE_SUB(NOW(), INTERVAL 15 HOUR)),
(2, 'demo-prac-2-2-2', 'Q_0220', 'KP0203', 'SELF', 0, '[演示数据] 错误选项占位', 'BOUNDARY_ERROR', 65, DATE_SUB(NOW(), INTERVAL 16 HOUR)),
(2, 'demo-prac-2-2-3', 'Q_0203', 'KP0203', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 76, DATE_SUB(NOW(), INTERVAL 17 HOUR)),
(2, 'demo-prac-2-3-1', 'Q_0302', 'KP0302', 'SELF', 0, '[演示数据] 错误选项占位', 'CALCULATION_ERROR', 61, DATE_SUB(NOW(), INTERVAL 18 HOUR)),
(2, 'demo-prac-2-3-2', 'Q_0303', 'KP0302', 'SELF', 0, '[演示数据] 错误选项占位', 'CALCULATION_ERROR', 72, DATE_SUB(NOW(), INTERVAL 19 HOUR)),
(2, 'demo-prac-2-3-3', 'Q_0302', 'KP0302', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 83, DATE_SUB(NOW(), INTERVAL 20 HOUR)),
(2, 'demo-prac-2-4-1', 'Q_0307', 'KP0305', 'SELF', 0, '[演示数据] 错误选项占位', 'STEP_OMISSION', 68, DATE_SUB(NOW(), INTERVAL 21 HOUR)),
(2, 'demo-prac-2-4-2', 'Q_0319', 'KP0305', 'SELF', 0, '[演示数据] 错误选项占位', 'STEP_OMISSION', 79, DATE_SUB(NOW(), INTERVAL 22 HOUR)),
(2, 'demo-prac-2-4-3', 'Q_0325', 'KP0305', 'SELF', 0, '[演示数据] 错误选项占位', 'STEP_OMISSION', 90, DATE_SUB(NOW(), INTERVAL 23 HOUR)),
(2, 'demo-prac-2-5-1', 'Q_0408', 'KP0404', 'SELF', 0, '[演示数据] 错误选项占位', 'METHOD_MISSING', 75, DATE_SUB(NOW(), INTERVAL 24 HOUR)),
(2, 'demo-prac-2-5-2', 'Q_0409', 'KP0404', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 86, DATE_SUB(NOW(), INTERVAL 25 HOUR)),
(2, 'demo-prac-2-5-3', 'Q_0410', 'KP0404', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 97, DATE_SUB(NOW(), INTERVAL 26 HOUR)),
(9001, 'demo-prac-9001-1-1', 'Q_0201', 'KP0201', 'SELF', 0, '[演示数据] 错误选项占位', 'CONCEPT_CONFUSION', 86, DATE_SUB(NOW(), INTERVAL 24 HOUR)),
(9001, 'demo-prac-9001-1-2', 'Q_0213', 'KP0201', 'SELF', 0, '[演示数据] 错误选项占位', 'CONCEPT_CONFUSION', 97, DATE_SUB(NOW(), INTERVAL 25 HOUR)),
(9001, 'demo-prac-9001-1-3', 'Q_0201', 'KP0201', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 108, DATE_SUB(NOW(), INTERVAL 26 HOUR)),
(9001, 'demo-prac-9001-2-1', 'Q_0203', 'KP0203', 'SELF', 0, '[演示数据] 错误选项占位', 'BOUNDARY_ERROR', 93, DATE_SUB(NOW(), INTERVAL 27 HOUR)),
(9001, 'demo-prac-9001-2-2', 'Q_0220', 'KP0203', 'SELF', 0, '[演示数据] 错误选项占位', 'BOUNDARY_ERROR', 104, DATE_SUB(NOW(), INTERVAL 28 HOUR)),
(9001, 'demo-prac-9001-2-3', 'Q_0203', 'KP0203', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 115, DATE_SUB(NOW(), INTERVAL 29 HOUR)),
(9001, 'demo-prac-9001-3-1', 'Q_0302', 'KP0302', 'SELF', 0, '[演示数据] 错误选项占位', 'CALCULATION_ERROR', 100, DATE_SUB(NOW(), INTERVAL 30 HOUR)),
(9001, 'demo-prac-9001-3-2', 'Q_0303', 'KP0302', 'SELF', 0, '[演示数据] 错误选项占位', 'CALCULATION_ERROR', 111, DATE_SUB(NOW(), INTERVAL 31 HOUR)),
(9001, 'demo-prac-9001-3-3', 'Q_0302', 'KP0302', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 122, DATE_SUB(NOW(), INTERVAL 32 HOUR)),
(9001, 'demo-prac-9001-4-1', 'Q_0307', 'KP0305', 'SELF', 0, '[演示数据] 错误选项占位', 'STEP_OMISSION', 107, DATE_SUB(NOW(), INTERVAL 33 HOUR)),
(9001, 'demo-prac-9001-4-2', 'Q_0319', 'KP0305', 'SELF', 0, '[演示数据] 错误选项占位', 'STEP_OMISSION', 118, DATE_SUB(NOW(), INTERVAL 34 HOUR)),
(9001, 'demo-prac-9001-4-3', 'Q_0325', 'KP0305', 'SELF', 0, '[演示数据] 错误选项占位', 'STEP_OMISSION', 129, DATE_SUB(NOW(), INTERVAL 35 HOUR)),
(9001, 'demo-prac-9001-5-1', 'Q_0408', 'KP0404', 'SELF', 0, '[演示数据] 错误选项占位', 'METHOD_MISSING', 114, DATE_SUB(NOW(), INTERVAL 36 HOUR)),
(9001, 'demo-prac-9001-5-2', 'Q_0409', 'KP0404', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 125, DATE_SUB(NOW(), INTERVAL 37 HOUR)),
(9001, 'demo-prac-9001-5-3', 'Q_0410', 'KP0404', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 136, DATE_SUB(NOW(), INTERVAL 38 HOUR)),
(9002, 'demo-prac-9002-1-1', 'Q_0201', 'KP0201', 'SELF', 0, '[演示数据] 错误选项占位', 'CONCEPT_CONFUSION', 87, DATE_SUB(NOW(), INTERVAL 36 HOUR)),
(9002, 'demo-prac-9002-1-2', 'Q_0213', 'KP0201', 'SELF', 0, '[演示数据] 错误选项占位', 'CONCEPT_CONFUSION', 98, DATE_SUB(NOW(), INTERVAL 37 HOUR)),
(9002, 'demo-prac-9002-1-3', 'Q_0201', 'KP0201', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 109, DATE_SUB(NOW(), INTERVAL 38 HOUR)),
(9002, 'demo-prac-9002-2-1', 'Q_0203', 'KP0203', 'SELF', 0, '[演示数据] 错误选项占位', 'BOUNDARY_ERROR', 94, DATE_SUB(NOW(), INTERVAL 39 HOUR)),
(9002, 'demo-prac-9002-2-2', 'Q_0220', 'KP0203', 'SELF', 0, '[演示数据] 错误选项占位', 'BOUNDARY_ERROR', 105, DATE_SUB(NOW(), INTERVAL 40 HOUR)),
(9002, 'demo-prac-9002-2-3', 'Q_0203', 'KP0203', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 116, DATE_SUB(NOW(), INTERVAL 41 HOUR)),
(9002, 'demo-prac-9002-3-1', 'Q_0302', 'KP0302', 'SELF', 0, '[演示数据] 错误选项占位', 'CALCULATION_ERROR', 101, DATE_SUB(NOW(), INTERVAL 42 HOUR)),
(9002, 'demo-prac-9002-3-2', 'Q_0303', 'KP0302', 'SELF', 0, '[演示数据] 错误选项占位', 'CALCULATION_ERROR', 112, DATE_SUB(NOW(), INTERVAL 43 HOUR)),
(9002, 'demo-prac-9002-3-3', 'Q_0302', 'KP0302', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 123, DATE_SUB(NOW(), INTERVAL 44 HOUR)),
(9002, 'demo-prac-9002-4-1', 'Q_0307', 'KP0305', 'SELF', 0, '[演示数据] 错误选项占位', 'STEP_OMISSION', 108, DATE_SUB(NOW(), INTERVAL 45 HOUR)),
(9002, 'demo-prac-9002-4-2', 'Q_0319', 'KP0305', 'SELF', 0, '[演示数据] 错误选项占位', 'STEP_OMISSION', 119, DATE_SUB(NOW(), INTERVAL 46 HOUR)),
(9002, 'demo-prac-9002-4-3', 'Q_0325', 'KP0305', 'SELF', 0, '[演示数据] 错误选项占位', 'STEP_OMISSION', 130, DATE_SUB(NOW(), INTERVAL 47 HOUR)),
(9002, 'demo-prac-9002-5-1', 'Q_0408', 'KP0404', 'SELF', 0, '[演示数据] 错误选项占位', 'METHOD_MISSING', 115, DATE_SUB(NOW(), INTERVAL 48 HOUR)),
(9002, 'demo-prac-9002-5-2', 'Q_0409', 'KP0404', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 126, DATE_SUB(NOW(), INTERVAL 49 HOUR)),
(9002, 'demo-prac-9002-5-3', 'Q_0410', 'KP0404', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 137, DATE_SUB(NOW(), INTERVAL 50 HOUR)),
(9003, 'demo-prac-9003-1-1', 'Q_0201', 'KP0201', 'SELF', 0, '[演示数据] 错误选项占位', 'CONCEPT_CONFUSION', 88, DATE_SUB(NOW(), INTERVAL 48 HOUR)),
(9003, 'demo-prac-9003-1-2', 'Q_0213', 'KP0201', 'SELF', 0, '[演示数据] 错误选项占位', 'CONCEPT_CONFUSION', 99, DATE_SUB(NOW(), INTERVAL 49 HOUR)),
(9003, 'demo-prac-9003-1-3', 'Q_0201', 'KP0201', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 110, DATE_SUB(NOW(), INTERVAL 50 HOUR)),
(9003, 'demo-prac-9003-2-1', 'Q_0203', 'KP0203', 'SELF', 0, '[演示数据] 错误选项占位', 'BOUNDARY_ERROR', 95, DATE_SUB(NOW(), INTERVAL 51 HOUR)),
(9003, 'demo-prac-9003-2-2', 'Q_0220', 'KP0203', 'SELF', 0, '[演示数据] 错误选项占位', 'BOUNDARY_ERROR', 106, DATE_SUB(NOW(), INTERVAL 52 HOUR)),
(9003, 'demo-prac-9003-2-3', 'Q_0203', 'KP0203', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 117, DATE_SUB(NOW(), INTERVAL 53 HOUR)),
(9003, 'demo-prac-9003-3-1', 'Q_0302', 'KP0302', 'SELF', 0, '[演示数据] 错误选项占位', 'CALCULATION_ERROR', 102, DATE_SUB(NOW(), INTERVAL 54 HOUR)),
(9003, 'demo-prac-9003-3-2', 'Q_0303', 'KP0302', 'SELF', 0, '[演示数据] 错误选项占位', 'CALCULATION_ERROR', 113, DATE_SUB(NOW(), INTERVAL 55 HOUR)),
(9003, 'demo-prac-9003-3-3', 'Q_0302', 'KP0302', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 124, DATE_SUB(NOW(), INTERVAL 56 HOUR)),
(9003, 'demo-prac-9003-4-1', 'Q_0307', 'KP0305', 'SELF', 0, '[演示数据] 错误选项占位', 'STEP_OMISSION', 109, DATE_SUB(NOW(), INTERVAL 57 HOUR)),
(9003, 'demo-prac-9003-4-2', 'Q_0319', 'KP0305', 'SELF', 0, '[演示数据] 错误选项占位', 'STEP_OMISSION', 120, DATE_SUB(NOW(), INTERVAL 58 HOUR)),
(9003, 'demo-prac-9003-4-3', 'Q_0325', 'KP0305', 'SELF', 0, '[演示数据] 错误选项占位', 'STEP_OMISSION', 131, DATE_SUB(NOW(), INTERVAL 59 HOUR)),
(9003, 'demo-prac-9003-5-1', 'Q_0408', 'KP0404', 'SELF', 0, '[演示数据] 错误选项占位', 'METHOD_MISSING', 116, DATE_SUB(NOW(), INTERVAL 60 HOUR)),
(9003, 'demo-prac-9003-5-2', 'Q_0409', 'KP0404', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 127, DATE_SUB(NOW(), INTERVAL 61 HOUR)),
(9003, 'demo-prac-9003-5-3', 'Q_0410', 'KP0404', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 138, DATE_SUB(NOW(), INTERVAL 62 HOUR)),
(9004, 'demo-prac-9004-1-1', 'Q_0202', 'KP0202', 'SELF', 0, '[演示数据] 错误选项占位', 'CALCULATION_ERROR', 89, DATE_SUB(NOW(), INTERVAL 60 HOUR)),
(9004, 'demo-prac-9004-1-2', 'Q_0218', 'KP0202', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 100, DATE_SUB(NOW(), INTERVAL 61 HOUR)),
(9004, 'demo-prac-9004-1-3', 'Q_0219', 'KP0202', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 111, DATE_SUB(NOW(), INTERVAL 62 HOUR)),
(9004, 'demo-prac-9004-2-1', 'Q_0206', 'KP0206', 'SELF', 0, '[演示数据] 错误选项占位', 'STEP_OMISSION', 96, DATE_SUB(NOW(), INTERVAL 63 HOUR)),
(9004, 'demo-prac-9004-2-2', 'Q_0215', 'KP0206', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 107, DATE_SUB(NOW(), INTERVAL 64 HOUR)),
(9004, 'demo-prac-9004-2-3', 'Q_0222', 'KP0206', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 118, DATE_SUB(NOW(), INTERVAL 65 HOUR)),
(9004, 'demo-prac-9004-3-1', 'Q_0311', 'KP0303', 'SELF', 0, '[演示数据] 错误选项占位', 'CALCULATION_ERROR', 103, DATE_SUB(NOW(), INTERVAL 66 HOUR)),
(9004, 'demo-prac-9004-3-2', 'Q_0321', 'KP0303', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 114, DATE_SUB(NOW(), INTERVAL 67 HOUR)),
(9004, 'demo-prac-9004-3-3', 'Q_0322', 'KP0303', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 125, DATE_SUB(NOW(), INTERVAL 68 HOUR)),
(9004, 'demo-prac-9004-4-1', 'Q_0404', 'KP0402', 'SELF', 0, '[演示数据] 错误选项占位', 'STEP_OMISSION', 110, DATE_SUB(NOW(), INTERVAL 69 HOUR)),
(9004, 'demo-prac-9004-4-2', 'Q_0405', 'KP0402', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 121, DATE_SUB(NOW(), INTERVAL 70 HOUR)),
(9004, 'demo-prac-9004-4-3', 'Q_0404', 'KP0402', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 132, DATE_SUB(NOW(), INTERVAL 71 HOUR)),
(9004, 'demo-prac-9004-5-1', 'Q_0413', 'KP0406', 'SELF', 0, '[演示数据] 错误选项占位', 'BOUNDARY_ERROR', 117, DATE_SUB(NOW(), INTERVAL 72 HOUR)),
(9004, 'demo-prac-9004-5-2', 'Q_0414', 'KP0406', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 128, DATE_SUB(NOW(), INTERVAL 73 HOUR)),
(9004, 'demo-prac-9004-5-3', 'Q_0415', 'KP0406', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 139, DATE_SUB(NOW(), INTERVAL 74 HOUR)),
(9005, 'demo-prac-9005-1-1', 'Q_0202', 'KP0202', 'SELF', 0, '[演示数据] 错误选项占位', 'CALCULATION_ERROR', 90, DATE_SUB(NOW(), INTERVAL 72 HOUR)),
(9005, 'demo-prac-9005-1-2', 'Q_0218', 'KP0202', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 101, DATE_SUB(NOW(), INTERVAL 73 HOUR)),
(9005, 'demo-prac-9005-1-3', 'Q_0219', 'KP0202', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 112, DATE_SUB(NOW(), INTERVAL 74 HOUR)),
(9005, 'demo-prac-9005-2-1', 'Q_0206', 'KP0206', 'SELF', 0, '[演示数据] 错误选项占位', 'STEP_OMISSION', 97, DATE_SUB(NOW(), INTERVAL 75 HOUR)),
(9005, 'demo-prac-9005-2-2', 'Q_0215', 'KP0206', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 108, DATE_SUB(NOW(), INTERVAL 76 HOUR)),
(9005, 'demo-prac-9005-2-3', 'Q_0222', 'KP0206', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 119, DATE_SUB(NOW(), INTERVAL 77 HOUR)),
(9005, 'demo-prac-9005-3-1', 'Q_0311', 'KP0303', 'SELF', 0, '[演示数据] 错误选项占位', 'CALCULATION_ERROR', 104, DATE_SUB(NOW(), INTERVAL 78 HOUR)),
(9005, 'demo-prac-9005-3-2', 'Q_0321', 'KP0303', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 115, DATE_SUB(NOW(), INTERVAL 79 HOUR)),
(9005, 'demo-prac-9005-3-3', 'Q_0322', 'KP0303', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 126, DATE_SUB(NOW(), INTERVAL 80 HOUR)),
(9005, 'demo-prac-9005-4-1', 'Q_0404', 'KP0402', 'SELF', 0, '[演示数据] 错误选项占位', 'STEP_OMISSION', 111, DATE_SUB(NOW(), INTERVAL 81 HOUR)),
(9005, 'demo-prac-9005-4-2', 'Q_0405', 'KP0402', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 122, DATE_SUB(NOW(), INTERVAL 82 HOUR)),
(9005, 'demo-prac-9005-4-3', 'Q_0404', 'KP0402', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 133, DATE_SUB(NOW(), INTERVAL 83 HOUR)),
(9005, 'demo-prac-9005-5-1', 'Q_0413', 'KP0406', 'SELF', 0, '[演示数据] 错误选项占位', 'BOUNDARY_ERROR', 118, DATE_SUB(NOW(), INTERVAL 84 HOUR)),
(9005, 'demo-prac-9005-5-2', 'Q_0414', 'KP0406', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 129, DATE_SUB(NOW(), INTERVAL 85 HOUR)),
(9005, 'demo-prac-9005-5-3', 'Q_0415', 'KP0406', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 140, DATE_SUB(NOW(), INTERVAL 86 HOUR)),
(9006, 'demo-prac-9006-1-1', 'Q_0202', 'KP0202', 'SELF', 0, '[演示数据] 错误选项占位', 'CALCULATION_ERROR', 91, DATE_SUB(NOW(), INTERVAL 84 HOUR)),
(9006, 'demo-prac-9006-1-2', 'Q_0218', 'KP0202', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 102, DATE_SUB(NOW(), INTERVAL 85 HOUR)),
(9006, 'demo-prac-9006-1-3', 'Q_0219', 'KP0202', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 113, DATE_SUB(NOW(), INTERVAL 86 HOUR)),
(9006, 'demo-prac-9006-2-1', 'Q_0206', 'KP0206', 'SELF', 0, '[演示数据] 错误选项占位', 'STEP_OMISSION', 98, DATE_SUB(NOW(), INTERVAL 87 HOUR)),
(9006, 'demo-prac-9006-2-2', 'Q_0215', 'KP0206', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 109, DATE_SUB(NOW(), INTERVAL 88 HOUR)),
(9006, 'demo-prac-9006-2-3', 'Q_0222', 'KP0206', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 120, DATE_SUB(NOW(), INTERVAL 89 HOUR)),
(9006, 'demo-prac-9006-3-1', 'Q_0311', 'KP0303', 'SELF', 0, '[演示数据] 错误选项占位', 'CALCULATION_ERROR', 105, DATE_SUB(NOW(), INTERVAL 90 HOUR)),
(9006, 'demo-prac-9006-3-2', 'Q_0321', 'KP0303', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 116, DATE_SUB(NOW(), INTERVAL 91 HOUR)),
(9006, 'demo-prac-9006-3-3', 'Q_0322', 'KP0303', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 127, DATE_SUB(NOW(), INTERVAL 92 HOUR)),
(9006, 'demo-prac-9006-4-1', 'Q_0404', 'KP0402', 'SELF', 0, '[演示数据] 错误选项占位', 'STEP_OMISSION', 112, DATE_SUB(NOW(), INTERVAL 93 HOUR)),
(9006, 'demo-prac-9006-4-2', 'Q_0405', 'KP0402', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 123, DATE_SUB(NOW(), INTERVAL 94 HOUR)),
(9006, 'demo-prac-9006-4-3', 'Q_0404', 'KP0402', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 134, DATE_SUB(NOW(), INTERVAL 95 HOUR)),
(9006, 'demo-prac-9006-5-1', 'Q_0413', 'KP0406', 'SELF', 0, '[演示数据] 错误选项占位', 'BOUNDARY_ERROR', 119, DATE_SUB(NOW(), INTERVAL 96 HOUR)),
(9006, 'demo-prac-9006-5-2', 'Q_0414', 'KP0406', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 130, DATE_SUB(NOW(), INTERVAL 97 HOUR)),
(9006, 'demo-prac-9006-5-3', 'Q_0415', 'KP0406', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 141, DATE_SUB(NOW(), INTERVAL 98 HOUR)),
(9007, 'demo-prac-9007-1-1', 'Q_0202', 'KP0202', 'SELF', 0, '[演示数据] 错误选项占位', 'CALCULATION_ERROR', 92, DATE_SUB(NOW(), INTERVAL 96 HOUR)),
(9007, 'demo-prac-9007-1-2', 'Q_0218', 'KP0202', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 103, DATE_SUB(NOW(), INTERVAL 97 HOUR)),
(9007, 'demo-prac-9007-1-3', 'Q_0219', 'KP0202', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 114, DATE_SUB(NOW(), INTERVAL 98 HOUR)),
(9007, 'demo-prac-9007-2-1', 'Q_0206', 'KP0206', 'SELF', 0, '[演示数据] 错误选项占位', 'STEP_OMISSION', 99, DATE_SUB(NOW(), INTERVAL 99 HOUR)),
(9007, 'demo-prac-9007-2-2', 'Q_0215', 'KP0206', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 110, DATE_SUB(NOW(), INTERVAL 100 HOUR)),
(9007, 'demo-prac-9007-2-3', 'Q_0222', 'KP0206', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 121, DATE_SUB(NOW(), INTERVAL 101 HOUR)),
(9007, 'demo-prac-9007-3-1', 'Q_0311', 'KP0303', 'SELF', 0, '[演示数据] 错误选项占位', 'CALCULATION_ERROR', 106, DATE_SUB(NOW(), INTERVAL 102 HOUR)),
(9007, 'demo-prac-9007-3-2', 'Q_0321', 'KP0303', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 117, DATE_SUB(NOW(), INTERVAL 103 HOUR)),
(9007, 'demo-prac-9007-3-3', 'Q_0322', 'KP0303', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 128, DATE_SUB(NOW(), INTERVAL 104 HOUR)),
(9007, 'demo-prac-9007-4-1', 'Q_0404', 'KP0402', 'SELF', 0, '[演示数据] 错误选项占位', 'STEP_OMISSION', 113, DATE_SUB(NOW(), INTERVAL 105 HOUR)),
(9007, 'demo-prac-9007-4-2', 'Q_0405', 'KP0402', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 124, DATE_SUB(NOW(), INTERVAL 106 HOUR)),
(9007, 'demo-prac-9007-4-3', 'Q_0404', 'KP0402', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 135, DATE_SUB(NOW(), INTERVAL 107 HOUR)),
(9007, 'demo-prac-9007-5-1', 'Q_0413', 'KP0406', 'SELF', 0, '[演示数据] 错误选项占位', 'BOUNDARY_ERROR', 120, DATE_SUB(NOW(), INTERVAL 108 HOUR)),
(9007, 'demo-prac-9007-5-2', 'Q_0414', 'KP0406', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 131, DATE_SUB(NOW(), INTERVAL 109 HOUR)),
(9007, 'demo-prac-9007-5-3', 'Q_0415', 'KP0406', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 142, DATE_SUB(NOW(), INTERVAL 110 HOUR)),
(9008, 'demo-prac-9008-1-1', 'Q_0116', 'KP0107', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 93, DATE_SUB(NOW(), INTERVAL 108 HOUR)),
(9008, 'demo-prac-9008-1-2', 'Q_0117', 'KP0107', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 104, DATE_SUB(NOW(), INTERVAL 109 HOUR)),
(9008, 'demo-prac-9008-1-3', 'Q_0118', 'KP0107', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 115, DATE_SUB(NOW(), INTERVAL 110 HOUR)),
(9008, 'demo-prac-9008-2-1', 'Q_0205', 'KP0205', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 100, DATE_SUB(NOW(), INTERVAL 111 HOUR)),
(9008, 'demo-prac-9008-2-2', 'Q_0214', 'KP0205', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 111, DATE_SUB(NOW(), INTERVAL 112 HOUR)),
(9008, 'demo-prac-9008-2-3', 'Q_0205', 'KP0205', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 122, DATE_SUB(NOW(), INTERVAL 113 HOUR)),
(9008, 'demo-prac-9008-3-1', 'Q_0309', 'KP0306', 'SELF', 0, '[演示数据] 错误选项占位', 'CALCULATION_ERROR', 107, DATE_SUB(NOW(), INTERVAL 114 HOUR)),
(9008, 'demo-prac-9008-3-2', 'Q_0312', 'KP0306', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 118, DATE_SUB(NOW(), INTERVAL 115 HOUR)),
(9008, 'demo-prac-9008-3-3', 'Q_0309', 'KP0306', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 129, DATE_SUB(NOW(), INTERVAL 116 HOUR)),
(9008, 'demo-prac-9008-4-1', 'Q_0401', 'KP0401', 'SELF', 0, '[演示数据] 错误选项占位', 'STEP_OMISSION', 114, DATE_SUB(NOW(), INTERVAL 117 HOUR)),
(9008, 'demo-prac-9008-4-2', 'Q_0402', 'KP0401', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 125, DATE_SUB(NOW(), INTERVAL 118 HOUR)),
(9008, 'demo-prac-9008-4-3', 'Q_0403', 'KP0401', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 136, DATE_SUB(NOW(), INTERVAL 119 HOUR)),
(9008, 'demo-prac-9008-5-1', 'Q_0419', 'KP0408', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 121, DATE_SUB(NOW(), INTERVAL 120 HOUR)),
(9008, 'demo-prac-9008-5-2', 'Q_0420', 'KP0408', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 132, DATE_SUB(NOW(), INTERVAL 121 HOUR)),
(9008, 'demo-prac-9008-5-3', 'Q_0419', 'KP0408', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 143, DATE_SUB(NOW(), INTERVAL 122 HOUR)),
(9009, 'demo-prac-9009-1-1', 'Q_0116', 'KP0107', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 94, DATE_SUB(NOW(), INTERVAL 120 HOUR)),
(9009, 'demo-prac-9009-1-2', 'Q_0117', 'KP0107', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 105, DATE_SUB(NOW(), INTERVAL 121 HOUR)),
(9009, 'demo-prac-9009-1-3', 'Q_0118', 'KP0107', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 116, DATE_SUB(NOW(), INTERVAL 122 HOUR)),
(9009, 'demo-prac-9009-2-1', 'Q_0205', 'KP0205', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 101, DATE_SUB(NOW(), INTERVAL 123 HOUR)),
(9009, 'demo-prac-9009-2-2', 'Q_0214', 'KP0205', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 112, DATE_SUB(NOW(), INTERVAL 124 HOUR)),
(9009, 'demo-prac-9009-2-3', 'Q_0205', 'KP0205', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 123, DATE_SUB(NOW(), INTERVAL 125 HOUR)),
(9009, 'demo-prac-9009-3-1', 'Q_0309', 'KP0306', 'SELF', 0, '[演示数据] 错误选项占位', 'CALCULATION_ERROR', 108, DATE_SUB(NOW(), INTERVAL 126 HOUR)),
(9009, 'demo-prac-9009-3-2', 'Q_0312', 'KP0306', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 119, DATE_SUB(NOW(), INTERVAL 127 HOUR)),
(9009, 'demo-prac-9009-3-3', 'Q_0309', 'KP0306', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 130, DATE_SUB(NOW(), INTERVAL 128 HOUR)),
(9009, 'demo-prac-9009-4-1', 'Q_0401', 'KP0401', 'SELF', 0, '[演示数据] 错误选项占位', 'STEP_OMISSION', 115, DATE_SUB(NOW(), INTERVAL 129 HOUR)),
(9009, 'demo-prac-9009-4-2', 'Q_0402', 'KP0401', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 126, DATE_SUB(NOW(), INTERVAL 130 HOUR)),
(9009, 'demo-prac-9009-4-3', 'Q_0403', 'KP0401', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 137, DATE_SUB(NOW(), INTERVAL 131 HOUR)),
(9009, 'demo-prac-9009-5-1', 'Q_0419', 'KP0408', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 122, DATE_SUB(NOW(), INTERVAL 132 HOUR)),
(9009, 'demo-prac-9009-5-2', 'Q_0420', 'KP0408', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 133, DATE_SUB(NOW(), INTERVAL 133 HOUR)),
(9009, 'demo-prac-9009-5-3', 'Q_0419', 'KP0408', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 144, DATE_SUB(NOW(), INTERVAL 134 HOUR)),
(9010, 'demo-prac-9010-1-1', 'Q_0116', 'KP0107', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 95, DATE_SUB(NOW(), INTERVAL 200 HOUR)),
(9010, 'demo-prac-9010-1-2', 'Q_0117', 'KP0107', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 106, DATE_SUB(NOW(), INTERVAL 201 HOUR)),
(9010, 'demo-prac-9010-1-3', 'Q_0118', 'KP0107', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 117, DATE_SUB(NOW(), INTERVAL 202 HOUR)),
(9010, 'demo-prac-9010-2-1', 'Q_0205', 'KP0205', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 102, DATE_SUB(NOW(), INTERVAL 202 HOUR)),
(9010, 'demo-prac-9010-2-2', 'Q_0214', 'KP0205', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 113, DATE_SUB(NOW(), INTERVAL 203 HOUR)),
(9010, 'demo-prac-9010-2-3', 'Q_0205', 'KP0205', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 124, DATE_SUB(NOW(), INTERVAL 204 HOUR)),
(9010, 'demo-prac-9010-3-1', 'Q_0309', 'KP0306', 'SELF', 0, '[演示数据] 错误选项占位', 'CALCULATION_ERROR', 109, DATE_SUB(NOW(), INTERVAL 204 HOUR)),
(9010, 'demo-prac-9010-3-2', 'Q_0312', 'KP0306', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 120, DATE_SUB(NOW(), INTERVAL 205 HOUR)),
(9010, 'demo-prac-9010-3-3', 'Q_0309', 'KP0306', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 131, DATE_SUB(NOW(), INTERVAL 206 HOUR)),
(9010, 'demo-prac-9010-4-1', 'Q_0401', 'KP0401', 'SELF', 0, '[演示数据] 错误选项占位', 'STEP_OMISSION', 116, DATE_SUB(NOW(), INTERVAL 206 HOUR)),
(9010, 'demo-prac-9010-4-2', 'Q_0402', 'KP0401', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 127, DATE_SUB(NOW(), INTERVAL 207 HOUR)),
(9010, 'demo-prac-9010-4-3', 'Q_0403', 'KP0401', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 138, DATE_SUB(NOW(), INTERVAL 208 HOUR)),
(9010, 'demo-prac-9010-5-1', 'Q_0419', 'KP0408', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 123, DATE_SUB(NOW(), INTERVAL 208 HOUR)),
(9010, 'demo-prac-9010-5-2', 'Q_0420', 'KP0408', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 134, DATE_SUB(NOW(), INTERVAL 209 HOUR)),
(9010, 'demo-prac-9010-5-3', 'Q_0419', 'KP0408', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 145, DATE_SUB(NOW(), INTERVAL 210 HOUR)),
(9011, 'demo-prac-9011-1-1', 'Q_0116', 'KP0107', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 96, DATE_SUB(NOW(), INTERVAL 224 HOUR)),
(9011, 'demo-prac-9011-1-2', 'Q_0117', 'KP0107', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 107, DATE_SUB(NOW(), INTERVAL 225 HOUR)),
(9011, 'demo-prac-9011-1-3', 'Q_0118', 'KP0107', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 118, DATE_SUB(NOW(), INTERVAL 226 HOUR)),
(9011, 'demo-prac-9011-2-1', 'Q_0205', 'KP0205', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 103, DATE_SUB(NOW(), INTERVAL 226 HOUR)),
(9011, 'demo-prac-9011-2-2', 'Q_0214', 'KP0205', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 114, DATE_SUB(NOW(), INTERVAL 227 HOUR)),
(9011, 'demo-prac-9011-2-3', 'Q_0205', 'KP0205', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 125, DATE_SUB(NOW(), INTERVAL 228 HOUR)),
(9011, 'demo-prac-9011-3-1', 'Q_0309', 'KP0306', 'SELF', 0, '[演示数据] 错误选项占位', 'CALCULATION_ERROR', 110, DATE_SUB(NOW(), INTERVAL 228 HOUR)),
(9011, 'demo-prac-9011-3-2', 'Q_0312', 'KP0306', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 121, DATE_SUB(NOW(), INTERVAL 229 HOUR)),
(9011, 'demo-prac-9011-3-3', 'Q_0309', 'KP0306', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 132, DATE_SUB(NOW(), INTERVAL 230 HOUR)),
(9011, 'demo-prac-9011-4-1', 'Q_0401', 'KP0401', 'SELF', 0, '[演示数据] 错误选项占位', 'STEP_OMISSION', 117, DATE_SUB(NOW(), INTERVAL 230 HOUR)),
(9011, 'demo-prac-9011-4-2', 'Q_0402', 'KP0401', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 128, DATE_SUB(NOW(), INTERVAL 231 HOUR)),
(9011, 'demo-prac-9011-4-3', 'Q_0403', 'KP0401', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 139, DATE_SUB(NOW(), INTERVAL 232 HOUR)),
(9011, 'demo-prac-9011-5-1', 'Q_0419', 'KP0408', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 124, DATE_SUB(NOW(), INTERVAL 232 HOUR)),
(9011, 'demo-prac-9011-5-2', 'Q_0420', 'KP0408', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 135, DATE_SUB(NOW(), INTERVAL 233 HOUR)),
(9011, 'demo-prac-9011-5-3', 'Q_0419', 'KP0408', 'SELF', 1, '[演示数据] 正确选项占位', NULL, 146, DATE_SUB(NOW(), INTERVAL 234 HOUR))
ON DUPLICATE KEY UPDATE q_id = VALUES(q_id), kp_id = VALUES(kp_id), source = VALUES(source), is_correct = VALUES(is_correct), my_answer = VALUES(my_answer), error_type = VALUES(error_type), cost_seconds = VALUES(cost_seconds), created_at = VALUES(created_at);

-- 每个学生的薄弱知识点都有一条已确认错题，保证画像薄弱点能点进错题本。
INSERT INTO student_error_record (log_id, user_id, client_token, q_id, kp_id, image_url, ocr_text, corrected_text, ocr_confidence, error_type, student_note, status, confirmed_at, created_at) VALUES
('EL_DEMO_2_1', 2, 'demo-err-2-1', 'Q_0201', 'KP0201', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'CONCEPT_CONFUSION', '[演示数据] 关系模型的基本概念与三要素：概念混淆，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 12 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 12 HOUR)),
('EL_DEMO_2_2', 2, 'demo-err-2-2', 'Q_0203', 'KP0203', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'BOUNDARY_ERROR', '[演示数据] 候选码、主码、主属性与非主属性：边界条件，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 15 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 15 HOUR)),
('EL_DEMO_2_3', 2, 'demo-err-2-3', 'Q_0302', 'KP0302', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'CALCULATION_ERROR', '[演示数据] 数据定义：模式、基本表、索引、数据字典：计算失误，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 18 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 18 HOUR)),
('EL_DEMO_2_4', 2, 'demo-err-2-4', 'Q_0307', 'KP0305', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'STEP_OMISSION', '[演示数据] 嵌套查询与相关子查询：步骤遗漏，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 21 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 21 HOUR)),
('EL_DEMO_2_5', 2, 'demo-err-2-5', 'Q_0408', 'KP0404', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'METHOD_MISSING', '[演示数据] GRANT 授权与 WITH GRANT OPTION：方法缺失，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 24 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 24 HOUR)),
('EL_DEMO_9001_1', 9001, 'demo-err-9001-1', 'Q_0201', 'KP0201', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'CONCEPT_CONFUSION', '[演示数据] 关系模型的基本概念与三要素：概念混淆，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 24 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 24 HOUR)),
('EL_DEMO_9001_2', 9001, 'demo-err-9001-2', 'Q_0203', 'KP0203', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'BOUNDARY_ERROR', '[演示数据] 候选码、主码、主属性与非主属性：边界条件，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 27 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 27 HOUR)),
('EL_DEMO_9001_3', 9001, 'demo-err-9001-3', 'Q_0302', 'KP0302', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'CALCULATION_ERROR', '[演示数据] 数据定义：模式、基本表、索引、数据字典：计算失误，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 30 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 30 HOUR)),
('EL_DEMO_9001_4', 9001, 'demo-err-9001-4', 'Q_0307', 'KP0305', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'STEP_OMISSION', '[演示数据] 嵌套查询与相关子查询：步骤遗漏，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 33 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 33 HOUR)),
('EL_DEMO_9001_5', 9001, 'demo-err-9001-5', 'Q_0408', 'KP0404', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'METHOD_MISSING', '[演示数据] GRANT 授权与 WITH GRANT OPTION：方法缺失，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 36 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 36 HOUR)),
('EL_DEMO_9002_1', 9002, 'demo-err-9002-1', 'Q_0201', 'KP0201', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'CONCEPT_CONFUSION', '[演示数据] 关系模型的基本概念与三要素：概念混淆，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 36 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 36 HOUR)),
('EL_DEMO_9002_2', 9002, 'demo-err-9002-2', 'Q_0203', 'KP0203', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'BOUNDARY_ERROR', '[演示数据] 候选码、主码、主属性与非主属性：边界条件，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 39 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 39 HOUR)),
('EL_DEMO_9002_3', 9002, 'demo-err-9002-3', 'Q_0302', 'KP0302', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'CALCULATION_ERROR', '[演示数据] 数据定义：模式、基本表、索引、数据字典：计算失误，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 42 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 42 HOUR)),
('EL_DEMO_9002_4', 9002, 'demo-err-9002-4', 'Q_0307', 'KP0305', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'STEP_OMISSION', '[演示数据] 嵌套查询与相关子查询：步骤遗漏，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 45 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 45 HOUR)),
('EL_DEMO_9002_5', 9002, 'demo-err-9002-5', 'Q_0408', 'KP0404', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'METHOD_MISSING', '[演示数据] GRANT 授权与 WITH GRANT OPTION：方法缺失，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 48 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 48 HOUR)),
('EL_DEMO_9003_1', 9003, 'demo-err-9003-1', 'Q_0201', 'KP0201', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'CONCEPT_CONFUSION', '[演示数据] 关系模型的基本概念与三要素：概念混淆，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 48 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 48 HOUR)),
('EL_DEMO_9003_2', 9003, 'demo-err-9003-2', 'Q_0203', 'KP0203', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'BOUNDARY_ERROR', '[演示数据] 候选码、主码、主属性与非主属性：边界条件，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 51 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 51 HOUR)),
('EL_DEMO_9003_3', 9003, 'demo-err-9003-3', 'Q_0302', 'KP0302', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'CALCULATION_ERROR', '[演示数据] 数据定义：模式、基本表、索引、数据字典：计算失误，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 54 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 54 HOUR)),
('EL_DEMO_9003_4', 9003, 'demo-err-9003-4', 'Q_0307', 'KP0305', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'STEP_OMISSION', '[演示数据] 嵌套查询与相关子查询：步骤遗漏，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 57 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 57 HOUR)),
('EL_DEMO_9003_5', 9003, 'demo-err-9003-5', 'Q_0408', 'KP0404', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'METHOD_MISSING', '[演示数据] GRANT 授权与 WITH GRANT OPTION：方法缺失，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 60 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 60 HOUR)),
('EL_DEMO_9003_6', 9003, 'demo-err-9003-6', 'Q_0220', 'KP0203', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'BOUNDARY_ERROR', '[演示数据] 候选码、主码、主属性与非主属性：边界条件，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 14 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 14 HOUR)),
('EL_DEMO_9004_1', 9004, 'demo-err-9004-1', 'Q_0202', 'KP0202', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'CALCULATION_ERROR', '[演示数据] 关系的形式化定义（域、笛卡儿积、关系）：计算失误，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 60 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 60 HOUR)),
('EL_DEMO_9004_2', 9004, 'demo-err-9004-2', 'Q_0206', 'KP0206', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'STEP_OMISSION', '[演示数据] 实体完整性：步骤遗漏，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 63 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 63 HOUR)),
('EL_DEMO_9004_3', 9004, 'demo-err-9004-3', 'Q_0311', 'KP0303', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'CALCULATION_ERROR', '[演示数据] 单表查询（WHERE、ORDER BY、聚合、分组）：计算失误，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 66 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 66 HOUR)),
('EL_DEMO_9004_4', 9004, 'demo-err-9004-4', 'Q_0404', 'KP0402', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'STEP_OMISSION', '[演示数据] 用户身份鉴别：步骤遗漏，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 69 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 69 HOUR)),
('EL_DEMO_9004_5', 9004, 'demo-err-9004-5', 'Q_0413', 'KP0406', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'BOUNDARY_ERROR', '[演示数据] 数据库角色：边界条件，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 72 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 72 HOUR)),
('EL_DEMO_9004_6', 9004, 'demo-err-9004-6', 'Q_0218', 'KP0202', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'CALCULATION_ERROR', '[演示数据] 关系的形式化定义（域、笛卡儿积、关系）：计算失误，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 14 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 14 HOUR)),
('EL_DEMO_9005_1', 9005, 'demo-err-9005-1', 'Q_0202', 'KP0202', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'CALCULATION_ERROR', '[演示数据] 关系的形式化定义（域、笛卡儿积、关系）：计算失误，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 72 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 72 HOUR)),
('EL_DEMO_9005_2', 9005, 'demo-err-9005-2', 'Q_0206', 'KP0206', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'STEP_OMISSION', '[演示数据] 实体完整性：步骤遗漏，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 75 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 75 HOUR)),
('EL_DEMO_9005_3', 9005, 'demo-err-9005-3', 'Q_0311', 'KP0303', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'CALCULATION_ERROR', '[演示数据] 单表查询（WHERE、ORDER BY、聚合、分组）：计算失误，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 78 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 78 HOUR)),
('EL_DEMO_9005_4', 9005, 'demo-err-9005-4', 'Q_0404', 'KP0402', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'STEP_OMISSION', '[演示数据] 用户身份鉴别：步骤遗漏，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 81 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 81 HOUR)),
('EL_DEMO_9005_5', 9005, 'demo-err-9005-5', 'Q_0413', 'KP0406', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'BOUNDARY_ERROR', '[演示数据] 数据库角色：边界条件，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 84 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 84 HOUR)),
('EL_DEMO_9005_6', 9005, 'demo-err-9005-6', 'Q_0219', 'KP0202', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'CALCULATION_ERROR', '[演示数据] 关系的形式化定义（域、笛卡儿积、关系）：计算失误，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 14 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 14 HOUR)),
('EL_DEMO_9006_1', 9006, 'demo-err-9006-1', 'Q_0202', 'KP0202', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'CALCULATION_ERROR', '[演示数据] 关系的形式化定义（域、笛卡儿积、关系）：计算失误，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 84 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 84 HOUR)),
('EL_DEMO_9006_2', 9006, 'demo-err-9006-2', 'Q_0206', 'KP0206', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'STEP_OMISSION', '[演示数据] 实体完整性：步骤遗漏，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 87 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 87 HOUR)),
('EL_DEMO_9006_3', 9006, 'demo-err-9006-3', 'Q_0311', 'KP0303', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'CALCULATION_ERROR', '[演示数据] 单表查询（WHERE、ORDER BY、聚合、分组）：计算失误，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 90 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 90 HOUR)),
('EL_DEMO_9006_4', 9006, 'demo-err-9006-4', 'Q_0404', 'KP0402', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'STEP_OMISSION', '[演示数据] 用户身份鉴别：步骤遗漏，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 93 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 93 HOUR)),
('EL_DEMO_9006_5', 9006, 'demo-err-9006-5', 'Q_0413', 'KP0406', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'BOUNDARY_ERROR', '[演示数据] 数据库角色：边界条件，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 96 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 96 HOUR)),
('EL_DEMO_9006_6', 9006, 'demo-err-9006-6', 'Q_0225', 'KP0202', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'CALCULATION_ERROR', '[演示数据] 关系的形式化定义（域、笛卡儿积、关系）：计算失误，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 14 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 14 HOUR)),
('EL_DEMO_9007_1', 9007, 'demo-err-9007-1', 'Q_0202', 'KP0202', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'CALCULATION_ERROR', '[演示数据] 关系的形式化定义（域、笛卡儿积、关系）：计算失误，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 96 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 96 HOUR)),
('EL_DEMO_9007_2', 9007, 'demo-err-9007-2', 'Q_0206', 'KP0206', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'STEP_OMISSION', '[演示数据] 实体完整性：步骤遗漏，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 99 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 99 HOUR)),
('EL_DEMO_9007_3', 9007, 'demo-err-9007-3', 'Q_0311', 'KP0303', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'CALCULATION_ERROR', '[演示数据] 单表查询（WHERE、ORDER BY、聚合、分组）：计算失误，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 102 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 102 HOUR)),
('EL_DEMO_9007_4', 9007, 'demo-err-9007-4', 'Q_0404', 'KP0402', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'STEP_OMISSION', '[演示数据] 用户身份鉴别：步骤遗漏，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 105 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 105 HOUR)),
('EL_DEMO_9007_5', 9007, 'demo-err-9007-5', 'Q_0413', 'KP0406', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'BOUNDARY_ERROR', '[演示数据] 数据库角色：边界条件，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 108 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 108 HOUR)),
('EL_DEMO_9008_1', 9008, 'demo-err-9008-1', 'Q_0309', 'KP0306', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'CALCULATION_ERROR', '[演示数据] 集合查询与基于派生表的查询：计算失误，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 114 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 114 HOUR)),
('EL_DEMO_9008_2', 9008, 'demo-err-9008-2', 'Q_0401', 'KP0401', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'STEP_OMISSION', '[演示数据] 数据库安全性概述、不安全因素与安全标准：步骤遗漏，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 117 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 117 HOUR)),
('EL_DEMO_9008_3', 9008, 'demo-err-9008-3', 'Q_0118', 'KP0107', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'CONCEPT_CONFUSION', '[演示数据] 数据库系统的三级模式结构（外模式、模式、内模式）：概念混淆，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 11 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 11 HOUR)),
('EL_DEMO_9009_1', 9009, 'demo-err-9009-1', 'Q_0309', 'KP0306', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'CALCULATION_ERROR', '[演示数据] 集合查询与基于派生表的查询：计算失误，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 126 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 126 HOUR)),
('EL_DEMO_9009_2', 9009, 'demo-err-9009-2', 'Q_0401', 'KP0401', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'STEP_OMISSION', '[演示数据] 数据库安全性概述、不安全因素与安全标准：步骤遗漏，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 129 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 129 HOUR)),
('EL_DEMO_9010_1', 9010, 'demo-err-9010-1', 'Q_0309', 'KP0306', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'CALCULATION_ERROR', '[演示数据] 集合查询与基于派生表的查询：计算失误，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 204 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 204 HOUR)),
('EL_DEMO_9010_2', 9010, 'demo-err-9010-2', 'Q_0401', 'KP0401', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'STEP_OMISSION', '[演示数据] 数据库安全性概述、不安全因素与安全标准：步骤遗漏，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 206 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 206 HOUR)),
('EL_DEMO_9010_3', 9010, 'demo-err-9010-3', 'Q_0402', 'KP0401', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'STEP_OMISSION', '[演示数据] 数据库安全性概述、不安全因素与安全标准：步骤遗漏，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 11 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 11 HOUR)),
('EL_DEMO_9011_1', 9011, 'demo-err-9011-1', 'Q_0309', 'KP0306', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'CALCULATION_ERROR', '[演示数据] 集合查询与基于派生表的查询：计算失误，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 228 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 228 HOUR)),
('EL_DEMO_9011_2', 9011, 'demo-err-9011-2', 'Q_0401', 'KP0401', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'STEP_OMISSION', '[演示数据] 数据库安全性概述、不安全因素与安全标准：步骤遗漏，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 230 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 230 HOUR)),
('EL_DEMO_9011_3', 9011, 'demo-err-9011-3', 'Q_0419', 'KP0408', NULL, '[演示数据] OCR 原文占位', '[演示数据] 校正后题干占位', 0.920, 'BOUNDARY_ERROR', '[演示数据] 视图机制：边界条件，该记录由种子脚本构造。', 'CONFIRMED', DATE_ADD(DATE_SUB(NOW(), INTERVAL 11 HOUR), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 11 HOUR))
ON DUPLICATE KEY UPDATE q_id = VALUES(q_id), kp_id = VALUES(kp_id), ocr_confidence = VALUES(ocr_confidence), error_type = VALUES(error_type), student_note = VALUES(student_note), status = VALUES(status), confirmed_at = VALUES(confirmed_at), created_at = VALUES(created_at);

-- 每人 3 条三层推荐，关联最新错题 log_id 与真实题号。
INSERT INTO recommendation (user_id, log_id, q_id, layer, reason, rank_no, is_adopted, created_at) VALUES
(2, 'EL_DEMO_2_1', 'Q_0213', 'CONSOLIDATE', '[演示]同知识点再练', 1, 1, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(2, 'EL_DEMO_2_1', 'Q_0203', 'VARIANT', '[演示]换个问法巩固', 2, 0, DATE_SUB(NOW(), INTERVAL 4 HOUR)),
(2, 'EL_DEMO_2_1', 'Q_0220', 'ADVANCE', '[演示]衔接下一知识点', 3, 0, DATE_SUB(NOW(), INTERVAL 5 HOUR)),
(9001, 'EL_DEMO_9001_1', 'Q_0213', 'CONSOLIDATE', '[演示]同知识点再练', 1, 1, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(9001, 'EL_DEMO_9001_1', 'Q_0203', 'VARIANT', '[演示]换个问法巩固', 2, 0, DATE_SUB(NOW(), INTERVAL 4 HOUR)),
(9001, 'EL_DEMO_9001_1', 'Q_0220', 'ADVANCE', '[演示]衔接下一知识点', 3, 0, DATE_SUB(NOW(), INTERVAL 5 HOUR)),
(9002, 'EL_DEMO_9002_1', 'Q_0213', 'CONSOLIDATE', '[演示]同知识点再练', 1, 1, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(9002, 'EL_DEMO_9002_1', 'Q_0203', 'VARIANT', '[演示]换个问法巩固', 2, 0, DATE_SUB(NOW(), INTERVAL 4 HOUR)),
(9002, 'EL_DEMO_9002_1', 'Q_0220', 'ADVANCE', '[演示]衔接下一知识点', 3, 0, DATE_SUB(NOW(), INTERVAL 5 HOUR)),
(9003, 'EL_DEMO_9003_6', 'Q_0201', 'CONSOLIDATE', '[演示]同知识点再练', 1, 1, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(9003, 'EL_DEMO_9003_6', 'Q_0213', 'VARIANT', '[演示]换个问法巩固', 2, 0, DATE_SUB(NOW(), INTERVAL 4 HOUR)),
(9003, 'EL_DEMO_9003_6', 'Q_0203', 'ADVANCE', '[演示]衔接下一知识点', 3, 0, DATE_SUB(NOW(), INTERVAL 5 HOUR)),
(9004, 'EL_DEMO_9004_6', 'Q_0202', 'CONSOLIDATE', '[演示]同知识点再练', 1, 1, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(9004, 'EL_DEMO_9004_6', 'Q_0219', 'VARIANT', '[演示]换个问法巩固', 2, 0, DATE_SUB(NOW(), INTERVAL 4 HOUR)),
(9004, 'EL_DEMO_9004_6', 'Q_0225', 'ADVANCE', '[演示]衔接下一知识点', 3, 0, DATE_SUB(NOW(), INTERVAL 5 HOUR)),
(9005, 'EL_DEMO_9005_6', 'Q_0202', 'CONSOLIDATE', '[演示]同知识点再练', 1, 1, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(9005, 'EL_DEMO_9005_6', 'Q_0218', 'VARIANT', '[演示]换个问法巩固', 2, 0, DATE_SUB(NOW(), INTERVAL 4 HOUR)),
(9005, 'EL_DEMO_9005_6', 'Q_0225', 'ADVANCE', '[演示]衔接下一知识点', 3, 0, DATE_SUB(NOW(), INTERVAL 5 HOUR)),
(9006, 'EL_DEMO_9006_6', 'Q_0202', 'CONSOLIDATE', '[演示]同知识点再练', 1, 1, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(9006, 'EL_DEMO_9006_6', 'Q_0218', 'VARIANT', '[演示]换个问法巩固', 2, 0, DATE_SUB(NOW(), INTERVAL 4 HOUR)),
(9006, 'EL_DEMO_9006_6', 'Q_0219', 'ADVANCE', '[演示]衔接下一知识点', 3, 0, DATE_SUB(NOW(), INTERVAL 5 HOUR)),
(9007, 'EL_DEMO_9007_1', 'Q_0218', 'CONSOLIDATE', '[演示]同知识点再练', 1, 1, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(9007, 'EL_DEMO_9007_1', 'Q_0219', 'VARIANT', '[演示]换个问法巩固', 2, 0, DATE_SUB(NOW(), INTERVAL 4 HOUR)),
(9007, 'EL_DEMO_9007_1', 'Q_0225', 'ADVANCE', '[演示]衔接下一知识点', 3, 0, DATE_SUB(NOW(), INTERVAL 5 HOUR)),
(9008, 'EL_DEMO_9008_3', 'Q_0116', 'CONSOLIDATE', '[演示]同知识点再练', 1, 1, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(9008, 'EL_DEMO_9008_3', 'Q_0117', 'VARIANT', '[演示]换个问法巩固', 2, 0, DATE_SUB(NOW(), INTERVAL 4 HOUR)),
(9008, 'EL_DEMO_9008_3', 'Q_0205', 'ADVANCE', '[演示]衔接下一知识点', 3, 0, DATE_SUB(NOW(), INTERVAL 5 HOUR)),
(9009, 'EL_DEMO_9009_1', 'Q_0116', 'CONSOLIDATE', '[演示]同知识点再练', 1, 1, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(9009, 'EL_DEMO_9009_1', 'Q_0117', 'VARIANT', '[演示]换个问法巩固', 2, 0, DATE_SUB(NOW(), INTERVAL 4 HOUR)),
(9009, 'EL_DEMO_9009_1', 'Q_0118', 'ADVANCE', '[演示]衔接下一知识点', 3, 0, DATE_SUB(NOW(), INTERVAL 5 HOUR)),
(9010, 'EL_DEMO_9010_3', 'Q_0116', 'CONSOLIDATE', '[演示]同知识点再练', 1, 1, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(9010, 'EL_DEMO_9010_3', 'Q_0117', 'VARIANT', '[演示]换个问法巩固', 2, 0, DATE_SUB(NOW(), INTERVAL 4 HOUR)),
(9010, 'EL_DEMO_9010_3', 'Q_0118', 'ADVANCE', '[演示]衔接下一知识点', 3, 0, DATE_SUB(NOW(), INTERVAL 5 HOUR)),
(9011, 'EL_DEMO_9011_3', 'Q_0116', 'CONSOLIDATE', '[演示]同知识点再练', 1, 1, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(9011, 'EL_DEMO_9011_3', 'Q_0117', 'VARIANT', '[演示]换个问法巩固', 2, 0, DATE_SUB(NOW(), INTERVAL 4 HOUR)),
(9011, 'EL_DEMO_9011_3', 'Q_0118', 'ADVANCE', '[演示]衔接下一知识点', 3, 0, DATE_SUB(NOW(), INTERVAL 5 HOUR))
ON DUPLICATE KEY UPDATE layer = VALUES(layer), reason = VALUES(reason), rank_no = VALUES(rank_no), is_adopted = VALUES(is_adopted), created_at = VALUES(created_at);

-- 今日/昨日计划主表；done_count 稍后按明细重算。
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

-- 今日/昨日计划各 3 条明细：今日 2 做 1 未做，昨日全做；随后重算 done_count。
INSERT INTO study_plan_item (plan_id, q_id, kp_id, sort_no, is_done, practice_id) VALUES
((SELECT plan_id FROM study_plan WHERE user_id=2 AND plan_date=CURDATE()), 'Q_0201', 'KP0201', 1, 1, (SELECT practice_id FROM practice_record WHERE user_id=2 AND client_token='demo-prac-2-1-1')),
((SELECT plan_id FROM study_plan WHERE user_id=2 AND plan_date=CURDATE()), 'Q_0203', 'KP0203', 2, 1, (SELECT practice_id FROM practice_record WHERE user_id=2 AND client_token='demo-prac-2-2-1')),
((SELECT plan_id FROM study_plan WHERE user_id=2 AND plan_date=CURDATE()), 'Q_0302', 'KP0302', 3, 0, NULL),
((SELECT plan_id FROM study_plan WHERE user_id=2 AND plan_date=DATE_SUB(CURDATE(), INTERVAL 1 DAY)), 'Q_0201', 'KP0201', 1, 1, (SELECT practice_id FROM practice_record WHERE user_id=2 AND client_token='demo-prac-2-1-1')),
((SELECT plan_id FROM study_plan WHERE user_id=2 AND plan_date=DATE_SUB(CURDATE(), INTERVAL 1 DAY)), 'Q_0203', 'KP0203', 2, 1, (SELECT practice_id FROM practice_record WHERE user_id=2 AND client_token='demo-prac-2-2-1')),
((SELECT plan_id FROM study_plan WHERE user_id=2 AND plan_date=DATE_SUB(CURDATE(), INTERVAL 1 DAY)), 'Q_0302', 'KP0302', 3, 1, (SELECT practice_id FROM practice_record WHERE user_id=2 AND client_token='demo-prac-2-3-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9001 AND plan_date=CURDATE()), 'Q_0201', 'KP0201', 1, 1, (SELECT practice_id FROM practice_record WHERE user_id=9001 AND client_token='demo-prac-9001-1-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9001 AND plan_date=CURDATE()), 'Q_0203', 'KP0203', 2, 1, (SELECT practice_id FROM practice_record WHERE user_id=9001 AND client_token='demo-prac-9001-2-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9001 AND plan_date=CURDATE()), 'Q_0302', 'KP0302', 3, 0, NULL),
((SELECT plan_id FROM study_plan WHERE user_id=9001 AND plan_date=DATE_SUB(CURDATE(), INTERVAL 1 DAY)), 'Q_0201', 'KP0201', 1, 1, (SELECT practice_id FROM practice_record WHERE user_id=9001 AND client_token='demo-prac-9001-1-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9001 AND plan_date=DATE_SUB(CURDATE(), INTERVAL 1 DAY)), 'Q_0203', 'KP0203', 2, 1, (SELECT practice_id FROM practice_record WHERE user_id=9001 AND client_token='demo-prac-9001-2-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9001 AND plan_date=DATE_SUB(CURDATE(), INTERVAL 1 DAY)), 'Q_0302', 'KP0302', 3, 1, (SELECT practice_id FROM practice_record WHERE user_id=9001 AND client_token='demo-prac-9001-3-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9002 AND plan_date=CURDATE()), 'Q_0201', 'KP0201', 1, 1, (SELECT practice_id FROM practice_record WHERE user_id=9002 AND client_token='demo-prac-9002-1-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9002 AND plan_date=CURDATE()), 'Q_0203', 'KP0203', 2, 1, (SELECT practice_id FROM practice_record WHERE user_id=9002 AND client_token='demo-prac-9002-2-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9002 AND plan_date=CURDATE()), 'Q_0302', 'KP0302', 3, 0, NULL),
((SELECT plan_id FROM study_plan WHERE user_id=9002 AND plan_date=DATE_SUB(CURDATE(), INTERVAL 1 DAY)), 'Q_0201', 'KP0201', 1, 1, (SELECT practice_id FROM practice_record WHERE user_id=9002 AND client_token='demo-prac-9002-1-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9002 AND plan_date=DATE_SUB(CURDATE(), INTERVAL 1 DAY)), 'Q_0203', 'KP0203', 2, 1, (SELECT practice_id FROM practice_record WHERE user_id=9002 AND client_token='demo-prac-9002-2-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9002 AND plan_date=DATE_SUB(CURDATE(), INTERVAL 1 DAY)), 'Q_0302', 'KP0302', 3, 1, (SELECT practice_id FROM practice_record WHERE user_id=9002 AND client_token='demo-prac-9002-3-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9003 AND plan_date=CURDATE()), 'Q_0201', 'KP0201', 1, 1, (SELECT practice_id FROM practice_record WHERE user_id=9003 AND client_token='demo-prac-9003-1-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9003 AND plan_date=CURDATE()), 'Q_0203', 'KP0203', 2, 1, (SELECT practice_id FROM practice_record WHERE user_id=9003 AND client_token='demo-prac-9003-2-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9003 AND plan_date=CURDATE()), 'Q_0302', 'KP0302', 3, 0, NULL),
((SELECT plan_id FROM study_plan WHERE user_id=9003 AND plan_date=DATE_SUB(CURDATE(), INTERVAL 1 DAY)), 'Q_0201', 'KP0201', 1, 1, (SELECT practice_id FROM practice_record WHERE user_id=9003 AND client_token='demo-prac-9003-1-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9003 AND plan_date=DATE_SUB(CURDATE(), INTERVAL 1 DAY)), 'Q_0203', 'KP0203', 2, 1, (SELECT practice_id FROM practice_record WHERE user_id=9003 AND client_token='demo-prac-9003-2-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9003 AND plan_date=DATE_SUB(CURDATE(), INTERVAL 1 DAY)), 'Q_0302', 'KP0302', 3, 1, (SELECT practice_id FROM practice_record WHERE user_id=9003 AND client_token='demo-prac-9003-3-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9004 AND plan_date=CURDATE()), 'Q_0202', 'KP0202', 1, 1, (SELECT practice_id FROM practice_record WHERE user_id=9004 AND client_token='demo-prac-9004-1-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9004 AND plan_date=CURDATE()), 'Q_0206', 'KP0206', 2, 1, (SELECT practice_id FROM practice_record WHERE user_id=9004 AND client_token='demo-prac-9004-2-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9004 AND plan_date=CURDATE()), 'Q_0311', 'KP0303', 3, 0, NULL),
((SELECT plan_id FROM study_plan WHERE user_id=9004 AND plan_date=DATE_SUB(CURDATE(), INTERVAL 1 DAY)), 'Q_0202', 'KP0202', 1, 1, (SELECT practice_id FROM practice_record WHERE user_id=9004 AND client_token='demo-prac-9004-1-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9004 AND plan_date=DATE_SUB(CURDATE(), INTERVAL 1 DAY)), 'Q_0206', 'KP0206', 2, 1, (SELECT practice_id FROM practice_record WHERE user_id=9004 AND client_token='demo-prac-9004-2-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9004 AND plan_date=DATE_SUB(CURDATE(), INTERVAL 1 DAY)), 'Q_0311', 'KP0303', 3, 1, (SELECT practice_id FROM practice_record WHERE user_id=9004 AND client_token='demo-prac-9004-3-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9005 AND plan_date=CURDATE()), 'Q_0202', 'KP0202', 1, 1, (SELECT practice_id FROM practice_record WHERE user_id=9005 AND client_token='demo-prac-9005-1-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9005 AND plan_date=CURDATE()), 'Q_0206', 'KP0206', 2, 1, (SELECT practice_id FROM practice_record WHERE user_id=9005 AND client_token='demo-prac-9005-2-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9005 AND plan_date=CURDATE()), 'Q_0311', 'KP0303', 3, 0, NULL),
((SELECT plan_id FROM study_plan WHERE user_id=9005 AND plan_date=DATE_SUB(CURDATE(), INTERVAL 1 DAY)), 'Q_0202', 'KP0202', 1, 1, (SELECT practice_id FROM practice_record WHERE user_id=9005 AND client_token='demo-prac-9005-1-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9005 AND plan_date=DATE_SUB(CURDATE(), INTERVAL 1 DAY)), 'Q_0206', 'KP0206', 2, 1, (SELECT practice_id FROM practice_record WHERE user_id=9005 AND client_token='demo-prac-9005-2-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9005 AND plan_date=DATE_SUB(CURDATE(), INTERVAL 1 DAY)), 'Q_0311', 'KP0303', 3, 1, (SELECT practice_id FROM practice_record WHERE user_id=9005 AND client_token='demo-prac-9005-3-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9006 AND plan_date=CURDATE()), 'Q_0202', 'KP0202', 1, 1, (SELECT practice_id FROM practice_record WHERE user_id=9006 AND client_token='demo-prac-9006-1-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9006 AND plan_date=CURDATE()), 'Q_0206', 'KP0206', 2, 1, (SELECT practice_id FROM practice_record WHERE user_id=9006 AND client_token='demo-prac-9006-2-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9006 AND plan_date=CURDATE()), 'Q_0311', 'KP0303', 3, 0, NULL),
((SELECT plan_id FROM study_plan WHERE user_id=9006 AND plan_date=DATE_SUB(CURDATE(), INTERVAL 1 DAY)), 'Q_0202', 'KP0202', 1, 1, (SELECT practice_id FROM practice_record WHERE user_id=9006 AND client_token='demo-prac-9006-1-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9006 AND plan_date=DATE_SUB(CURDATE(), INTERVAL 1 DAY)), 'Q_0206', 'KP0206', 2, 1, (SELECT practice_id FROM practice_record WHERE user_id=9006 AND client_token='demo-prac-9006-2-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9006 AND plan_date=DATE_SUB(CURDATE(), INTERVAL 1 DAY)), 'Q_0311', 'KP0303', 3, 1, (SELECT practice_id FROM practice_record WHERE user_id=9006 AND client_token='demo-prac-9006-3-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9007 AND plan_date=CURDATE()), 'Q_0202', 'KP0202', 1, 1, (SELECT practice_id FROM practice_record WHERE user_id=9007 AND client_token='demo-prac-9007-1-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9007 AND plan_date=CURDATE()), 'Q_0206', 'KP0206', 2, 1, (SELECT practice_id FROM practice_record WHERE user_id=9007 AND client_token='demo-prac-9007-2-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9007 AND plan_date=CURDATE()), 'Q_0311', 'KP0303', 3, 0, NULL),
((SELECT plan_id FROM study_plan WHERE user_id=9007 AND plan_date=DATE_SUB(CURDATE(), INTERVAL 1 DAY)), 'Q_0202', 'KP0202', 1, 1, (SELECT practice_id FROM practice_record WHERE user_id=9007 AND client_token='demo-prac-9007-1-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9007 AND plan_date=DATE_SUB(CURDATE(), INTERVAL 1 DAY)), 'Q_0206', 'KP0206', 2, 1, (SELECT practice_id FROM practice_record WHERE user_id=9007 AND client_token='demo-prac-9007-2-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9007 AND plan_date=DATE_SUB(CURDATE(), INTERVAL 1 DAY)), 'Q_0311', 'KP0303', 3, 1, (SELECT practice_id FROM practice_record WHERE user_id=9007 AND client_token='demo-prac-9007-3-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9008 AND plan_date=CURDATE()), 'Q_0116', 'KP0107', 1, 1, (SELECT practice_id FROM practice_record WHERE user_id=9008 AND client_token='demo-prac-9008-1-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9008 AND plan_date=CURDATE()), 'Q_0205', 'KP0205', 2, 1, (SELECT practice_id FROM practice_record WHERE user_id=9008 AND client_token='demo-prac-9008-2-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9008 AND plan_date=CURDATE()), 'Q_0309', 'KP0306', 3, 0, NULL),
((SELECT plan_id FROM study_plan WHERE user_id=9008 AND plan_date=DATE_SUB(CURDATE(), INTERVAL 1 DAY)), 'Q_0116', 'KP0107', 1, 1, (SELECT practice_id FROM practice_record WHERE user_id=9008 AND client_token='demo-prac-9008-1-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9008 AND plan_date=DATE_SUB(CURDATE(), INTERVAL 1 DAY)), 'Q_0205', 'KP0205', 2, 1, (SELECT practice_id FROM practice_record WHERE user_id=9008 AND client_token='demo-prac-9008-2-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9008 AND plan_date=DATE_SUB(CURDATE(), INTERVAL 1 DAY)), 'Q_0309', 'KP0306', 3, 1, (SELECT practice_id FROM practice_record WHERE user_id=9008 AND client_token='demo-prac-9008-3-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9009 AND plan_date=CURDATE()), 'Q_0116', 'KP0107', 1, 1, (SELECT practice_id FROM practice_record WHERE user_id=9009 AND client_token='demo-prac-9009-1-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9009 AND plan_date=CURDATE()), 'Q_0205', 'KP0205', 2, 1, (SELECT practice_id FROM practice_record WHERE user_id=9009 AND client_token='demo-prac-9009-2-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9009 AND plan_date=CURDATE()), 'Q_0309', 'KP0306', 3, 0, NULL),
((SELECT plan_id FROM study_plan WHERE user_id=9009 AND plan_date=DATE_SUB(CURDATE(), INTERVAL 1 DAY)), 'Q_0116', 'KP0107', 1, 1, (SELECT practice_id FROM practice_record WHERE user_id=9009 AND client_token='demo-prac-9009-1-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9009 AND plan_date=DATE_SUB(CURDATE(), INTERVAL 1 DAY)), 'Q_0205', 'KP0205', 2, 1, (SELECT practice_id FROM practice_record WHERE user_id=9009 AND client_token='demo-prac-9009-2-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9009 AND plan_date=DATE_SUB(CURDATE(), INTERVAL 1 DAY)), 'Q_0309', 'KP0306', 3, 1, (SELECT practice_id FROM practice_record WHERE user_id=9009 AND client_token='demo-prac-9009-3-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9010 AND plan_date=CURDATE()), 'Q_0116', 'KP0107', 1, 1, (SELECT practice_id FROM practice_record WHERE user_id=9010 AND client_token='demo-prac-9010-1-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9010 AND plan_date=CURDATE()), 'Q_0205', 'KP0205', 2, 1, (SELECT practice_id FROM practice_record WHERE user_id=9010 AND client_token='demo-prac-9010-2-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9010 AND plan_date=CURDATE()), 'Q_0309', 'KP0306', 3, 0, NULL),
((SELECT plan_id FROM study_plan WHERE user_id=9010 AND plan_date=DATE_SUB(CURDATE(), INTERVAL 1 DAY)), 'Q_0116', 'KP0107', 1, 1, (SELECT practice_id FROM practice_record WHERE user_id=9010 AND client_token='demo-prac-9010-1-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9010 AND plan_date=DATE_SUB(CURDATE(), INTERVAL 1 DAY)), 'Q_0205', 'KP0205', 2, 1, (SELECT practice_id FROM practice_record WHERE user_id=9010 AND client_token='demo-prac-9010-2-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9010 AND plan_date=DATE_SUB(CURDATE(), INTERVAL 1 DAY)), 'Q_0309', 'KP0306', 3, 1, (SELECT practice_id FROM practice_record WHERE user_id=9010 AND client_token='demo-prac-9010-3-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9011 AND plan_date=CURDATE()), 'Q_0116', 'KP0107', 1, 1, (SELECT practice_id FROM practice_record WHERE user_id=9011 AND client_token='demo-prac-9011-1-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9011 AND plan_date=CURDATE()), 'Q_0205', 'KP0205', 2, 1, (SELECT practice_id FROM practice_record WHERE user_id=9011 AND client_token='demo-prac-9011-2-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9011 AND plan_date=CURDATE()), 'Q_0309', 'KP0306', 3, 0, NULL),
((SELECT plan_id FROM study_plan WHERE user_id=9011 AND plan_date=DATE_SUB(CURDATE(), INTERVAL 1 DAY)), 'Q_0116', 'KP0107', 1, 1, (SELECT practice_id FROM practice_record WHERE user_id=9011 AND client_token='demo-prac-9011-1-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9011 AND plan_date=DATE_SUB(CURDATE(), INTERVAL 1 DAY)), 'Q_0205', 'KP0205', 2, 1, (SELECT practice_id FROM practice_record WHERE user_id=9011 AND client_token='demo-prac-9011-2-1')),
((SELECT plan_id FROM study_plan WHERE user_id=9011 AND plan_date=DATE_SUB(CURDATE(), INTERVAL 1 DAY)), 'Q_0309', 'KP0306', 3, 1, (SELECT practice_id FROM practice_record WHERE user_id=9011 AND client_token='demo-prac-9011-3-1'))
ON DUPLICATE KEY UPDATE q_id = VALUES(q_id), kp_id = VALUES(kp_id), sort_no = VALUES(sort_no), is_done = VALUES(is_done), practice_id = VALUES(practice_id);

UPDATE study_plan p SET p.done_count = (SELECT COUNT(*) FROM study_plan_item i WHERE i.plan_id = p.plan_id AND i.is_done = 1), p.status = CASE WHEN (SELECT COUNT(*) FROM study_plan_item i WHERE i.plan_id = p.plan_id AND i.is_done = 1) >= p.plan_count THEN 'DONE' ELSE 'PENDING' END WHERE p.user_id IN (2,9001,9002,9003,9004,9005,9006,9007,9008,9009,9010,9011);

-- 每人 2 条画像快照（7 天前 + 今日）；student01 旧快照更低，形成“低→回升”。
INSERT INTO learning_portrait_snapshot (user_id, stat_date, dimension_scores, weakest_kps, error_dist, total_practice, total_error) VALUES
(2, DATE_SUB(CURDATE(), INTERVAL 7 DAY), '{"concept": 0.183, "sql": 0.25, "schema": 0.317, "transaction": null, "boundary": 0.517}', '[{"kpId": "KP0305", "name": "嵌套查询与相关子查询", "mastery": 0.05, "sampleCount": 3}, {"kpId": "KP0201", "name": "关系模型的基本概念与三要素", "mastery": 0.183, "sampleCount": 3}, {"kpId": "KP0203", "name": "候选码、主码、主属性与非主属性", "mastery": 0.317, "sampleCount": 3}, {"kpId": "KP0302", "name": "数据定义：模式、基本表、索引、数据字典", "mastery": 0.45, "sampleCount": 3}, {"kpId": "KP0404", "name": "GRANT 授权与 WITH GRANT OPTION", "mastery": 0.517, "sampleCount": 3}]', '{"CONCEPT_CONFUSION": 2, "BOUNDARY_ERROR": 2, "CALCULATION_ERROR": 2, "STEP_OMISSION": 3, "METHOD_MISSING": 1}', 15, 10),
(2, CURDATE(), '{"concept": 0.333, "sql": 0.4, "schema": 0.467, "transaction": null, "boundary": 0.667}', '[{"kpId": "KP0305", "name": "嵌套查询与相关子查询", "mastery": 0.2, "sampleCount": 3}, {"kpId": "KP0201", "name": "关系模型的基本概念与三要素", "mastery": 0.333, "sampleCount": 3}, {"kpId": "KP0203", "name": "候选码、主码、主属性与非主属性", "mastery": 0.467, "sampleCount": 3}, {"kpId": "KP0302", "name": "数据定义：模式、基本表、索引、数据字典", "mastery": 0.6, "sampleCount": 3}, {"kpId": "KP0404", "name": "GRANT 授权与 WITH GRANT OPTION", "mastery": 0.667, "sampleCount": 3}]', '{"CONCEPT_CONFUSION": 2, "BOUNDARY_ERROR": 2, "CALCULATION_ERROR": 2, "STEP_OMISSION": 3, "METHOD_MISSING": 1}', 15, 10),
(9001, DATE_SUB(CURDATE(), INTERVAL 7 DAY), '{"concept": 0.183, "sql": 0.25, "schema": 0.317, "transaction": null, "boundary": 0.517}', '[{"kpId": "KP0305", "name": "嵌套查询与相关子查询", "mastery": 0.05, "sampleCount": 3}, {"kpId": "KP0201", "name": "关系模型的基本概念与三要素", "mastery": 0.183, "sampleCount": 3}, {"kpId": "KP0203", "name": "候选码、主码、主属性与非主属性", "mastery": 0.317, "sampleCount": 3}, {"kpId": "KP0302", "name": "数据定义：模式、基本表、索引、数据字典", "mastery": 0.45, "sampleCount": 3}, {"kpId": "KP0404", "name": "GRANT 授权与 WITH GRANT OPTION", "mastery": 0.517, "sampleCount": 3}]', '{"CONCEPT_CONFUSION": 2, "BOUNDARY_ERROR": 2, "CALCULATION_ERROR": 2, "STEP_OMISSION": 3, "METHOD_MISSING": 1}', 15, 10),
(9001, CURDATE(), '{"concept": 0.333, "sql": 0.4, "schema": 0.467, "transaction": null, "boundary": 0.667}', '[{"kpId": "KP0305", "name": "嵌套查询与相关子查询", "mastery": 0.2, "sampleCount": 3}, {"kpId": "KP0201", "name": "关系模型的基本概念与三要素", "mastery": 0.333, "sampleCount": 3}, {"kpId": "KP0203", "name": "候选码、主码、主属性与非主属性", "mastery": 0.467, "sampleCount": 3}, {"kpId": "KP0302", "name": "数据定义：模式、基本表、索引、数据字典", "mastery": 0.6, "sampleCount": 3}, {"kpId": "KP0404", "name": "GRANT 授权与 WITH GRANT OPTION", "mastery": 0.667, "sampleCount": 3}]', '{"CONCEPT_CONFUSION": 2, "BOUNDARY_ERROR": 2, "CALCULATION_ERROR": 2, "STEP_OMISSION": 3, "METHOD_MISSING": 1}', 15, 10),
(9002, DATE_SUB(CURDATE(), INTERVAL 7 DAY), '{"concept": 0.183, "sql": 0.25, "schema": 0.317, "transaction": null, "boundary": 0.517}', '[{"kpId": "KP0305", "name": "嵌套查询与相关子查询", "mastery": 0.05, "sampleCount": 3}, {"kpId": "KP0201", "name": "关系模型的基本概念与三要素", "mastery": 0.183, "sampleCount": 3}, {"kpId": "KP0203", "name": "候选码、主码、主属性与非主属性", "mastery": 0.317, "sampleCount": 3}, {"kpId": "KP0302", "name": "数据定义：模式、基本表、索引、数据字典", "mastery": 0.45, "sampleCount": 3}, {"kpId": "KP0404", "name": "GRANT 授权与 WITH GRANT OPTION", "mastery": 0.517, "sampleCount": 3}]', '{"CONCEPT_CONFUSION": 2, "BOUNDARY_ERROR": 2, "CALCULATION_ERROR": 2, "STEP_OMISSION": 3, "METHOD_MISSING": 1}', 15, 10),
(9002, CURDATE(), '{"concept": 0.333, "sql": 0.4, "schema": 0.467, "transaction": null, "boundary": 0.667}', '[{"kpId": "KP0305", "name": "嵌套查询与相关子查询", "mastery": 0.2, "sampleCount": 3}, {"kpId": "KP0201", "name": "关系模型的基本概念与三要素", "mastery": 0.333, "sampleCount": 3}, {"kpId": "KP0203", "name": "候选码、主码、主属性与非主属性", "mastery": 0.467, "sampleCount": 3}, {"kpId": "KP0302", "name": "数据定义：模式、基本表、索引、数据字典", "mastery": 0.6, "sampleCount": 3}, {"kpId": "KP0404", "name": "GRANT 授权与 WITH GRANT OPTION", "mastery": 0.667, "sampleCount": 3}]', '{"CONCEPT_CONFUSION": 2, "BOUNDARY_ERROR": 2, "CALCULATION_ERROR": 2, "STEP_OMISSION": 3, "METHOD_MISSING": 1}', 15, 10),
(9003, DATE_SUB(CURDATE(), INTERVAL 7 DAY), '{"concept": 0.183, "sql": 0.25, "schema": 0.317, "transaction": null, "boundary": 0.517}', '[{"kpId": "KP0305", "name": "嵌套查询与相关子查询", "mastery": 0.05, "sampleCount": 3}, {"kpId": "KP0201", "name": "关系模型的基本概念与三要素", "mastery": 0.183, "sampleCount": 3}, {"kpId": "KP0203", "name": "候选码、主码、主属性与非主属性", "mastery": 0.317, "sampleCount": 3}, {"kpId": "KP0302", "name": "数据定义：模式、基本表、索引、数据字典", "mastery": 0.45, "sampleCount": 3}, {"kpId": "KP0404", "name": "GRANT 授权与 WITH GRANT OPTION", "mastery": 0.517, "sampleCount": 3}]', '{"CONCEPT_CONFUSION": 2, "BOUNDARY_ERROR": 2, "CALCULATION_ERROR": 2, "STEP_OMISSION": 3, "METHOD_MISSING": 1}', 15, 10),
(9003, CURDATE(), '{"concept": 0.333, "sql": 0.4, "schema": 0.467, "transaction": null, "boundary": 0.667}', '[{"kpId": "KP0305", "name": "嵌套查询与相关子查询", "mastery": 0.2, "sampleCount": 3}, {"kpId": "KP0201", "name": "关系模型的基本概念与三要素", "mastery": 0.333, "sampleCount": 3}, {"kpId": "KP0203", "name": "候选码、主码、主属性与非主属性", "mastery": 0.467, "sampleCount": 3}, {"kpId": "KP0302", "name": "数据定义：模式、基本表、索引、数据字典", "mastery": 0.6, "sampleCount": 3}, {"kpId": "KP0404", "name": "GRANT 授权与 WITH GRANT OPTION", "mastery": 0.667, "sampleCount": 3}]', '{"CONCEPT_CONFUSION": 2, "BOUNDARY_ERROR": 2, "CALCULATION_ERROR": 2, "STEP_OMISSION": 3, "METHOD_MISSING": 1}', 15, 10),
(9004, DATE_SUB(CURDATE(), INTERVAL 7 DAY), '{"concept": 0.605, "sql": 0.65, "schema": 0.583, "transaction": null, "boundary": null}', '[{"kpId": "KP0206", "name": "实体完整性", "mastery": 0.583, "sampleCount": 3}, {"kpId": "KP0402", "name": "用户身份鉴别", "mastery": 0.583, "sampleCount": 3}, {"kpId": "KP0406", "name": "数据库角色", "mastery": 0.583, "sampleCount": 3}, {"kpId": "KP0202", "name": "关系的形式化定义（域、笛卡儿积、关系）", "mastery": 0.65, "sampleCount": 3}, {"kpId": "KP0303", "name": "单表查询（WHERE、ORDER BY、聚合、分组）", "mastery": 0.65, "sampleCount": 3}]', '{"CALCULATION_ERROR": 2, "STEP_OMISSION": 2, "BOUNDARY_ERROR": 1}', 15, 5),
(9004, CURDATE(), '{"concept": 0.755, "sql": 0.8, "schema": 0.733, "transaction": null, "boundary": null}', '[{"kpId": "KP0206", "name": "实体完整性", "mastery": 0.733, "sampleCount": 3}, {"kpId": "KP0402", "name": "用户身份鉴别", "mastery": 0.733, "sampleCount": 3}, {"kpId": "KP0406", "name": "数据库角色", "mastery": 0.733, "sampleCount": 3}, {"kpId": "KP0202", "name": "关系的形式化定义（域、笛卡儿积、关系）", "mastery": 0.8, "sampleCount": 3}, {"kpId": "KP0303", "name": "单表查询（WHERE、ORDER BY、聚合、分组）", "mastery": 0.8, "sampleCount": 3}]', '{"CALCULATION_ERROR": 2, "STEP_OMISSION": 2, "BOUNDARY_ERROR": 1}', 15, 5),
(9005, DATE_SUB(CURDATE(), INTERVAL 7 DAY), '{"concept": 0.605, "sql": 0.65, "schema": 0.583, "transaction": null, "boundary": null}', '[{"kpId": "KP0206", "name": "实体完整性", "mastery": 0.583, "sampleCount": 3}, {"kpId": "KP0402", "name": "用户身份鉴别", "mastery": 0.583, "sampleCount": 3}, {"kpId": "KP0406", "name": "数据库角色", "mastery": 0.583, "sampleCount": 3}, {"kpId": "KP0202", "name": "关系的形式化定义（域、笛卡儿积、关系）", "mastery": 0.65, "sampleCount": 3}, {"kpId": "KP0303", "name": "单表查询（WHERE、ORDER BY、聚合、分组）", "mastery": 0.65, "sampleCount": 3}]', '{"CALCULATION_ERROR": 2, "STEP_OMISSION": 2, "BOUNDARY_ERROR": 1}', 15, 5),
(9005, CURDATE(), '{"concept": 0.755, "sql": 0.8, "schema": 0.733, "transaction": null, "boundary": null}', '[{"kpId": "KP0206", "name": "实体完整性", "mastery": 0.733, "sampleCount": 3}, {"kpId": "KP0402", "name": "用户身份鉴别", "mastery": 0.733, "sampleCount": 3}, {"kpId": "KP0406", "name": "数据库角色", "mastery": 0.733, "sampleCount": 3}, {"kpId": "KP0202", "name": "关系的形式化定义（域、笛卡儿积、关系）", "mastery": 0.8, "sampleCount": 3}, {"kpId": "KP0303", "name": "单表查询（WHERE、ORDER BY、聚合、分组）", "mastery": 0.8, "sampleCount": 3}]', '{"CALCULATION_ERROR": 2, "STEP_OMISSION": 2, "BOUNDARY_ERROR": 1}', 15, 5),
(9006, DATE_SUB(CURDATE(), INTERVAL 7 DAY), '{"concept": 0.605, "sql": 0.65, "schema": 0.583, "transaction": null, "boundary": null}', '[{"kpId": "KP0206", "name": "实体完整性", "mastery": 0.583, "sampleCount": 3}, {"kpId": "KP0402", "name": "用户身份鉴别", "mastery": 0.583, "sampleCount": 3}, {"kpId": "KP0406", "name": "数据库角色", "mastery": 0.583, "sampleCount": 3}, {"kpId": "KP0202", "name": "关系的形式化定义（域、笛卡儿积、关系）", "mastery": 0.65, "sampleCount": 3}, {"kpId": "KP0303", "name": "单表查询（WHERE、ORDER BY、聚合、分组）", "mastery": 0.65, "sampleCount": 3}]', '{"CALCULATION_ERROR": 2, "STEP_OMISSION": 2, "BOUNDARY_ERROR": 1}', 15, 5),
(9006, CURDATE(), '{"concept": 0.755, "sql": 0.8, "schema": 0.733, "transaction": null, "boundary": null}', '[{"kpId": "KP0206", "name": "实体完整性", "mastery": 0.733, "sampleCount": 3}, {"kpId": "KP0402", "name": "用户身份鉴别", "mastery": 0.733, "sampleCount": 3}, {"kpId": "KP0406", "name": "数据库角色", "mastery": 0.733, "sampleCount": 3}, {"kpId": "KP0202", "name": "关系的形式化定义（域、笛卡儿积、关系）", "mastery": 0.8, "sampleCount": 3}, {"kpId": "KP0303", "name": "单表查询（WHERE、ORDER BY、聚合、分组）", "mastery": 0.8, "sampleCount": 3}]', '{"CALCULATION_ERROR": 2, "STEP_OMISSION": 2, "BOUNDARY_ERROR": 1}', 15, 5),
(9007, DATE_SUB(CURDATE(), INTERVAL 7 DAY), '{"concept": 0.605, "sql": 0.65, "schema": 0.583, "transaction": null, "boundary": null}', '[{"kpId": "KP0206", "name": "实体完整性", "mastery": 0.583, "sampleCount": 3}, {"kpId": "KP0402", "name": "用户身份鉴别", "mastery": 0.583, "sampleCount": 3}, {"kpId": "KP0406", "name": "数据库角色", "mastery": 0.583, "sampleCount": 3}, {"kpId": "KP0202", "name": "关系的形式化定义（域、笛卡儿积、关系）", "mastery": 0.65, "sampleCount": 3}, {"kpId": "KP0303", "name": "单表查询（WHERE、ORDER BY、聚合、分组）", "mastery": 0.65, "sampleCount": 3}]', '{"CALCULATION_ERROR": 2, "STEP_OMISSION": 2, "BOUNDARY_ERROR": 1}', 15, 5),
(9007, CURDATE(), '{"concept": 0.755, "sql": 0.8, "schema": 0.733, "transaction": null, "boundary": null}', '[{"kpId": "KP0206", "name": "实体完整性", "mastery": 0.733, "sampleCount": 3}, {"kpId": "KP0402", "name": "用户身份鉴别", "mastery": 0.733, "sampleCount": 3}, {"kpId": "KP0406", "name": "数据库角色", "mastery": 0.733, "sampleCount": 3}, {"kpId": "KP0202", "name": "关系的形式化定义（域、笛卡儿积、关系）", "mastery": 0.8, "sampleCount": 3}, {"kpId": "KP0303", "name": "单表查询（WHERE、ORDER BY、聚合、分组）", "mastery": 0.8, "sampleCount": 3}]', '{"CALCULATION_ERROR": 2, "STEP_OMISSION": 2, "BOUNDARY_ERROR": 1}', 15, 5),
(9008, DATE_SUB(CURDATE(), INTERVAL 7 DAY), '{"concept": 0.761, "sql": 0.65, "schema": 0.85, "transaction": null, "boundary": null}', '[{"kpId": "KP0401", "name": "数据库安全性概述、不安全因素与安全标准", "mastery": 0.583, "sampleCount": 3}, {"kpId": "KP0306", "name": "集合查询与基于派生表的查询", "mastery": 0.65, "sampleCount": 3}]', '{"CALCULATION_ERROR": 1, "STEP_OMISSION": 1}', 15, 2),
(9008, CURDATE(), '{"concept": 0.911, "sql": 0.8, "schema": 1.0, "transaction": null, "boundary": null}', '[{"kpId": "KP0401", "name": "数据库安全性概述、不安全因素与安全标准", "mastery": 0.733, "sampleCount": 3}, {"kpId": "KP0306", "name": "集合查询与基于派生表的查询", "mastery": 0.8, "sampleCount": 3}]', '{"CALCULATION_ERROR": 1, "STEP_OMISSION": 1}', 15, 2),
(9009, DATE_SUB(CURDATE(), INTERVAL 7 DAY), '{"concept": 0.761, "sql": 0.65, "schema": 0.85, "transaction": null, "boundary": null}', '[{"kpId": "KP0401", "name": "数据库安全性概述、不安全因素与安全标准", "mastery": 0.583, "sampleCount": 3}, {"kpId": "KP0306", "name": "集合查询与基于派生表的查询", "mastery": 0.65, "sampleCount": 3}]', '{"CALCULATION_ERROR": 1, "STEP_OMISSION": 1}', 15, 2),
(9009, CURDATE(), '{"concept": 0.911, "sql": 0.8, "schema": 1.0, "transaction": null, "boundary": null}', '[{"kpId": "KP0401", "name": "数据库安全性概述、不安全因素与安全标准", "mastery": 0.733, "sampleCount": 3}, {"kpId": "KP0306", "name": "集合查询与基于派生表的查询", "mastery": 0.8, "sampleCount": 3}]', '{"CALCULATION_ERROR": 1, "STEP_OMISSION": 1}', 15, 2),
(9010, DATE_SUB(CURDATE(), INTERVAL 7 DAY), '{"concept": 0.761, "sql": 0.65, "schema": 0.85, "transaction": null, "boundary": null}', '[{"kpId": "KP0401", "name": "数据库安全性概述、不安全因素与安全标准", "mastery": 0.583, "sampleCount": 3}, {"kpId": "KP0306", "name": "集合查询与基于派生表的查询", "mastery": 0.65, "sampleCount": 3}]', '{"CALCULATION_ERROR": 1, "STEP_OMISSION": 1}', 15, 2),
(9010, CURDATE(), '{"concept": 0.911, "sql": 0.8, "schema": 1.0, "transaction": null, "boundary": null}', '[{"kpId": "KP0401", "name": "数据库安全性概述、不安全因素与安全标准", "mastery": 0.733, "sampleCount": 3}, {"kpId": "KP0306", "name": "集合查询与基于派生表的查询", "mastery": 0.8, "sampleCount": 3}]', '{"CALCULATION_ERROR": 1, "STEP_OMISSION": 1}', 15, 2),
(9011, DATE_SUB(CURDATE(), INTERVAL 7 DAY), '{"concept": 0.761, "sql": 0.65, "schema": 0.85, "transaction": null, "boundary": null}', '[{"kpId": "KP0401", "name": "数据库安全性概述、不安全因素与安全标准", "mastery": 0.583, "sampleCount": 3}, {"kpId": "KP0306", "name": "集合查询与基于派生表的查询", "mastery": 0.65, "sampleCount": 3}]', '{"CALCULATION_ERROR": 1, "STEP_OMISSION": 1}', 15, 2),
(9011, CURDATE(), '{"concept": 0.911, "sql": 0.8, "schema": 1.0, "transaction": null, "boundary": null}', '[{"kpId": "KP0401", "name": "数据库安全性概述、不安全因素与安全标准", "mastery": 0.733, "sampleCount": 3}, {"kpId": "KP0306", "name": "集合查询与基于派生表的查询", "mastery": 0.8, "sampleCount": 3}]', '{"CALCULATION_ERROR": 1, "STEP_OMISSION": 1}', 15, 2)
ON DUPLICATE KEY UPDATE dimension_scores = VALUES(dimension_scores), weakest_kps = VALUES(weakest_kps), error_dist = VALUES(error_dist), total_practice = VALUES(total_practice), total_error = VALUES(total_error);

-- 12 条 RAG 讲解留痕：9 条待评阅、3 条已评阅；覆盖不同 KP/题型/错因。
INSERT INTO ai_generation_log (explain_id, trace_id, user_id, log_id, scene, model, prompt_version, is_degraded, degrade_level, latency_ms, prompt_tokens, completion_tokens, source_ids, content_text, is_blocked, teacher_score, teacher_comment, review_result, reviewed_at, created_at)
SELECT 'DEMO_EX_EL_DEMO_2_1', 'DEMO_TRACE_EL_DEMO_2_1', 2, 'EL_DEMO_2_1', 'RAG', 'deepseek-chat', 'demo-v1', 0, NULL, 1270, 620, 380, JSON_ARRAY('KP0201'), '[演示数据] 本段为答辩演示占位讲解，不是真实模型输出。关系模型的基本概念与三要素：关系模型是用单一结构——关系——描述现实世界实体及其联系的模型。用户看到的关系数据结构是一张二维表：每一行是一个元组，每一列是一个属性，列的取值范围是域，元组中的每一个值是分量。关系模型由三部分组成：关系数据结构、关系操', 0, NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 5 HOUR) FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ai_generation_log WHERE explain_id = 'DEMO_EX_EL_DEMO_2_1');

INSERT INTO ai_generation_log (explain_id, trace_id, user_id, log_id, scene, model, prompt_version, is_degraded, degrade_level, latency_ms, prompt_tokens, completion_tokens, source_ids, content_text, is_blocked, teacher_score, teacher_comment, review_result, reviewed_at, created_at)
SELECT 'DEMO_EX_EL_DEMO_9001_3', 'DEMO_TRACE_EL_DEMO_9001_3', 9001, 'EL_DEMO_9001_3', 'RAG', 'deepseek-chat', 'demo-v1', 0, NULL, 1340, 620, 380, JSON_ARRAY('KP0302'), '[演示数据] 本段为答辩演示占位讲解，不是真实模型输出。数据定义：模式、基本表、索引、数据字典：数据定义包括数据库模式、表、视图和索引的定义。模式：CREATE SCHEMA [<模式名>] AUTHORIZATION <用户名>；DROP SCHEMA <模式名><CASCADE|RESTRICT>，CASCAD', 0, NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 4 HOUR) FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ai_generation_log WHERE explain_id = 'DEMO_EX_EL_DEMO_9001_3');

INSERT INTO ai_generation_log (explain_id, trace_id, user_id, log_id, scene, model, prompt_version, is_degraded, degrade_level, latency_ms, prompt_tokens, completion_tokens, source_ids, content_text, is_blocked, teacher_score, teacher_comment, review_result, reviewed_at, created_at)
SELECT 'DEMO_EX_EL_DEMO_9002_4', 'DEMO_TRACE_EL_DEMO_9002_4', 9002, 'EL_DEMO_9002_4', 'RAG', 'local-template', 'demo-v1', 1, 'L3', 45, 0, 0, JSON_ARRAY('KP0305'), '[演示数据] 本段为答辩演示占位讲解，不是真实模型输出。嵌套查询与相关子查询：一个 SELECT-FROM-WHERE 语句称为一个查询块，将一个查询块嵌套在另一个查询块的 WHERE 或 HAVING 条件中称为嵌套查询；上层为父查询，下层为子查询。子查询的 SELECT 中不能使用 ORDER', 0, NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 3 HOUR) FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ai_generation_log WHERE explain_id = 'DEMO_EX_EL_DEMO_9002_4');

INSERT INTO ai_generation_log (explain_id, trace_id, user_id, log_id, scene, model, prompt_version, is_degraded, degrade_level, latency_ms, prompt_tokens, completion_tokens, source_ids, content_text, is_blocked, teacher_score, teacher_comment, review_result, reviewed_at, created_at)
SELECT 'DEMO_EX_EL_DEMO_9003_6', 'DEMO_TRACE_EL_DEMO_9003_6', 9003, 'EL_DEMO_9003_6', 'RAG', 'deepseek-chat', 'demo-v1', 0, NULL, 1480, 620, 380, JSON_ARRAY('KP0203'), '[演示数据] 本段为答辩演示占位讲解，不是真实模型输出。候选码、主码、主属性与非主属性：在关系模式中，若某一个属性或一组属性的值能唯一标识一个元组，而它的真子集不能唯一标识一个元组，则称该属性或属性组为候选码。一个关系可以有多个候选码，选定其中一个作为主码（主键），并在其名称下加下划线标识。候选码所包含的属', 0, NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 6 HOUR) FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ai_generation_log WHERE explain_id = 'DEMO_EX_EL_DEMO_9003_6');

INSERT INTO ai_generation_log (explain_id, trace_id, user_id, log_id, scene, model, prompt_version, is_degraded, degrade_level, latency_ms, prompt_tokens, completion_tokens, source_ids, content_text, is_blocked, teacher_score, teacher_comment, review_result, reviewed_at, created_at)
SELECT 'DEMO_EX_EL_DEMO_9004_6', 'DEMO_TRACE_EL_DEMO_9004_6', 9004, 'EL_DEMO_9004_6', 'RAG', 'deepseek-chat', 'demo-v1', 0, NULL, 1550, 620, 380, JSON_ARRAY('KP0202'), '[演示数据] 本段为答辩演示占位讲解，不是真实模型输出。关系的形式化定义（域、笛卡儿积、关系）：域是一组具有相同数据类型的值的集合。给定一组域 D1,D2,…,Dn，允许其中某些域相同，其笛卡儿积定义为 D1×D2×…×Dn={(d1,d2,…,dn) | di∈Di, i=1,2,…,n}。每个元素 (d1,d2', 0, NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 7 HOUR) FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ai_generation_log WHERE explain_id = 'DEMO_EX_EL_DEMO_9004_6');

INSERT INTO ai_generation_log (explain_id, trace_id, user_id, log_id, scene, model, prompt_version, is_degraded, degrade_level, latency_ms, prompt_tokens, completion_tokens, source_ids, content_text, is_blocked, teacher_score, teacher_comment, review_result, reviewed_at, created_at)
SELECT 'DEMO_EX_EL_DEMO_9005_6', 'DEMO_TRACE_EL_DEMO_9005_6', 9005, 'EL_DEMO_9005_6', 'RAG', 'deepseek-chat', 'demo-v1', 0, NULL, 1620, 620, 380, JSON_ARRAY('KP0202'), '[演示数据] 本段为答辩演示占位讲解，不是真实模型输出。关系的形式化定义（域、笛卡儿积、关系）：域是一组具有相同数据类型的值的集合。给定一组域 D1,D2,…,Dn，允许其中某些域相同，其笛卡儿积定义为 D1×D2×…×Dn={(d1,d2,…,dn) | di∈Di, i=1,2,…,n}。每个元素 (d1,d2', 0, NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 8 HOUR) FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ai_generation_log WHERE explain_id = 'DEMO_EX_EL_DEMO_9005_6');

INSERT INTO ai_generation_log (explain_id, trace_id, user_id, log_id, scene, model, prompt_version, is_degraded, degrade_level, latency_ms, prompt_tokens, completion_tokens, source_ids, content_text, is_blocked, teacher_score, teacher_comment, review_result, reviewed_at, created_at)
SELECT 'DEMO_EX_EL_DEMO_9006_6', 'DEMO_TRACE_EL_DEMO_9006_6', 9006, 'EL_DEMO_9006_6', 'RAG', 'local-template', 'demo-v1', 1, 'L3', 45, 0, 0, JSON_ARRAY('KP0202'), '[演示数据] 本段为答辩演示占位讲解，不是真实模型输出。关系的形式化定义（域、笛卡儿积、关系）：域是一组具有相同数据类型的值的集合。给定一组域 D1,D2,…,Dn，允许其中某些域相同，其笛卡儿积定义为 D1×D2×…×Dn={(d1,d2,…,dn) | di∈Di, i=1,2,…,n}。每个元素 (d1,d2', 0, NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 9 HOUR) FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ai_generation_log WHERE explain_id = 'DEMO_EX_EL_DEMO_9006_6');

INSERT INTO ai_generation_log (explain_id, trace_id, user_id, log_id, scene, model, prompt_version, is_degraded, degrade_level, latency_ms, prompt_tokens, completion_tokens, source_ids, content_text, is_blocked, teacher_score, teacher_comment, review_result, reviewed_at, created_at)
SELECT 'DEMO_EX_EL_DEMO_9007_2', 'DEMO_TRACE_EL_DEMO_9007_2', 9007, 'EL_DEMO_9007_2', 'RAG', 'deepseek-chat', 'demo-v1', 0, NULL, 1760, 620, 380, JSON_ARRAY('KP0206'), '[演示数据] 本段为答辩演示占位讲解，不是真实模型输出。实体完整性：关系模型有三类完整性约束：实体完整性、参照完整性和用户定义的完整性。其中实体完整性和参照完整性是关系模型必须满足的完整性约束，被称为关系的两个不变性，应由关系系统自动支持。实体完整性规则：若属性 A（一个或一组属性）是基', 0, NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 10 HOUR) FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ai_generation_log WHERE explain_id = 'DEMO_EX_EL_DEMO_9007_2');

INSERT INTO ai_generation_log (explain_id, trace_id, user_id, log_id, scene, model, prompt_version, is_degraded, degrade_level, latency_ms, prompt_tokens, completion_tokens, source_ids, content_text, is_blocked, teacher_score, teacher_comment, review_result, reviewed_at, created_at)
SELECT 'DEMO_EX_EL_DEMO_9008_3', 'DEMO_TRACE_EL_DEMO_9008_3', 9008, 'EL_DEMO_9008_3', 'RAG', 'deepseek-chat', 'demo-v1', 0, NULL, 1830, 620, 380, JSON_ARRAY('KP0107'), '[演示数据] 本段为答辩演示占位讲解，不是真实模型输出。数据库系统的三级模式结构（外模式、模式、内模式）：从 DBMS 角度看，数据库系统通常采用模式、外模式、内模式三级模式结构。模式也称逻辑模式，是数据库中全体数据的逻辑结构和特征的描述，是所有用户的公共数据视图，处于三级结构的中间层，不涉及物理存储细节，与具体应用、开发工', 0, NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 11 HOUR) FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ai_generation_log WHERE explain_id = 'DEMO_EX_EL_DEMO_9008_3');

INSERT INTO ai_generation_log (explain_id, trace_id, user_id, log_id, scene, model, prompt_version, is_degraded, degrade_level, latency_ms, prompt_tokens, completion_tokens, source_ids, content_text, is_blocked, teacher_score, teacher_comment, review_result, reviewed_at, created_at)
SELECT 'DEMO_EX_EL_DEMO_9009_1', 'DEMO_TRACE_EL_DEMO_9009_1', 9009, 'EL_DEMO_9009_1', 'RAG', 'deepseek-chat', 'demo-v1', 0, NULL, 1900, 620, 380, JSON_ARRAY('KP0306'), '[演示数据] 本段为答辩演示占位讲解，不是真实模型输出。集合查询与基于派生表的查询：SELECT 的查询结果是元组的集合，可对多个 SELECT 结果做集合操作：并 UNION、交 INTERSECT、差 EXCEPT。参加操作的各结果列数必须相同，对应项数据类型也必须相同。UNION 合并时系统自动去', 0, 4, '[演示数据] 结构完整，建议补充一个反例。', 'PASS', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 30 HOUR) FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ai_generation_log WHERE explain_id = 'DEMO_EX_EL_DEMO_9009_1');

INSERT INTO ai_generation_log (explain_id, trace_id, user_id, log_id, scene, model, prompt_version, is_degraded, degrade_level, latency_ms, prompt_tokens, completion_tokens, source_ids, content_text, is_blocked, teacher_score, teacher_comment, review_result, reviewed_at, created_at)
SELECT 'DEMO_EX_EL_DEMO_9010_3', 'DEMO_TRACE_EL_DEMO_9010_3', 9010, 'EL_DEMO_9010_3', 'RAG', 'local-template', 'demo-v1', 1, 'L3', 45, 0, 0, JSON_ARRAY('KP0401'), '[演示数据] 本段为答辩演示占位讲解，不是真实模型输出。数据库安全性概述、不安全因素与安全标准：数据库安全性是指保护数据库，防止不合法使用造成数据泄露、篡改或破坏。威胁主要来自三类：非授权用户恶意存取和破坏，如窃取口令、假冒合法用户、锁定数据勒索；重要或敏感数据被泄露，如 SQL 注入在查询语句末尾追加语句，骗取数', 0, 4, '[演示数据] 结构完整，建议补充一个反例。', 'PASS', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 31 HOUR) FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ai_generation_log WHERE explain_id = 'DEMO_EX_EL_DEMO_9010_3');

INSERT INTO ai_generation_log (explain_id, trace_id, user_id, log_id, scene, model, prompt_version, is_degraded, degrade_level, latency_ms, prompt_tokens, completion_tokens, source_ids, content_text, is_blocked, teacher_score, teacher_comment, review_result, reviewed_at, created_at)
SELECT 'DEMO_EX_EL_DEMO_9011_3', 'DEMO_TRACE_EL_DEMO_9011_3', 9011, 'EL_DEMO_9011_3', 'RAG', 'deepseek-chat', 'demo-v1', 0, NULL, 2040, 620, 380, JSON_ARRAY('KP0408'), '[演示数据] 本段为答辩演示占位讲解，不是真实模型输出。视图机制：视图具有安全保护功能。为不同用户定义不同视图，可以把数据对象限制在一定范围内，把保密数据对无权存取的用户隐藏起来，从而自动提供一定程度的安全保护。视图还能与授权机制配合，间接实现支持存取谓词的用户权限定义。例如王平老师只', 0, 4, '[演示数据] 结构完整，建议补充一个反例。', 'PASS', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 32 HOUR) FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ai_generation_log WHERE explain_id = 'DEMO_EX_EL_DEMO_9011_3');
