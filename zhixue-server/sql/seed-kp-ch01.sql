-- 智学领航 · 第1章知识点种子（由 csv_to_sql.py 生成）
-- 前置：init-v2.sql；可重复执行（upsert）。
USE zhixue;
SET NAMES utf8mb4;

INSERT INTO course_chapter (course_id, chapter_no, title, sort_no) VALUES
(1, 1, '绪论', 0)
ON DUPLICATE KEY UPDATE title = VALUES(title), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0101', 1, chapter_id, '数据库的4个基本概念（数据、数据库、DBMS、DBS）', '数据是描述事物的符号记录，数字、文字、图形、图像、音频、视频等都可经数字化后存入计算机；数据与其语义不可分，同一数值在不同语境下含义不同。数据库是长期存储在计算机内、有组织、可共享的大量数据的集合，数据按数据模型组织、描述和存储，具有较小冗余、较高数据独立性和可扩展性。数据库管理系统是位于用户与操作系统之间的一层数据管理软件，提供数据定义、组织存储、数据操纵、事务管理与运行管理、建立与维护等功能。数据库系统是引入数据库后的计算机系统，一般由数据库、数据库管理系统（及其应用开发工具）、应用系统和数据库管理员组成。四者按“被管理对象—数据集合—管理软件—整体系统”层层递进，不能混同。', '①把数据等同于数字，忽略文本、图形、图像、音频、视频等广义数据
②把数据库当成普通文件夹，忽略“有组织、可共享、按数据模型组织”
③把数据库系统与数据库混为一谈，漏掉 DBMS、应用系统与 DBA
④认为 DBMS 就是数据库', 'P4', 'concept', '1', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 1
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0102', 1, chapter_id, '数据管理技术的产生与发展（人工管理、文件系统、数据库系统）', '数据管理是对数据进行分类、组织、编码、存储、检索和维护，是数据处理的中心问题；数据处理则是对数据收集、存储、加工和传播的一系列活动。数据管理技术经历三个阶段。人工管理阶段（20世纪50年代中期以前）硬件只有纸带、卡片、磁带，没有操作系统和数据管理软件，数据不保存、由应用程序管理、不共享、不具备独立性。文件系统阶段（50年代后期至60年代中期）有磁盘、磁鼓和文件系统，数据可长期保存、由专门软件管理、记录内有结构，但共享性弱、冗余度高、独立性弱、整体无结构。数据库系统阶段（60年代后期以来）由 DBMS 统一管理，实现整体数据结构化、共享性强冗余低、独立性强，并提供安全性、完整性、并发控制与恢复。三阶段的核心差异是数据是否结构化、是否共享、独立性高低以及由谁管理数据。', '①认为文件系统阶段数据已完全独立、可共享
②把“数据管理”与“数据处理”当成同一概念
③认为人工管理阶段的数据可长期保存
④认为三阶段只是存储介质变化，忽略管理方式与独立性差异', 'P6', 'concept', '2', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 1
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0103', 1, chapter_id, '数据库系统的组成与人员职责', '数据库系统是引入数据库后的计算机系统，是一个人机系统。按教材 1.1.1，一般由数据库、数据库管理系统（及其应用开发工具）、应用系统和数据库管理员组成；按 1.4 的支撑视角，还包括硬件平台、软件平台和人员三类。硬件平台提供存储与处理能力；软件平台包括操作系统、DBMS、高级语言及其编译系统、应用开发工具和应用系统；人员包括数据库管理员、系统分析员和数据库设计员、应用程序员与最终用户。数据库管理员负责设计与定义数据库、培训最终用户、负责系统运维、监视与调优性能、转储与恢复数据库、必要时重构数据库。不同角色对应不同的数据抽象级别与数据视图。', '①认为数据库系统只由数据库和 DBMS 组成，漏掉应用系统与 DBA
②把 DBA 当成编写应用程序的人
③认为数据库系统只是软件，忽略硬件平台与人员
④认为最终用户不需要了解数据库', 'P27', 'concept', '3', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 1
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0104', 1, chapter_id, '数据模型的三要素（数据结构、数据操纵、完整性约束）', '数据模型是对现实世界数据特征的抽象，用来描述数据、组织数据和对数据进行操作，是数据库系统的核心和基础。数据模型通常由数据结构、数据操纵和完整性约束三部分组成，称为三要素。数据结构描述数据库的组成对象以及对象之间的联系，是对系统静态特性的描述，也是刻画数据模型性质最重要的方面，人们常按数据结构类型命名数据模型（层次、网状、关系）。数据操纵是对数据库中各种对象的实例允许执行的操作集合，包括查询与更新（插入、删除、修改）及操作规则，是对系统动态特性的描述。完整性约束是一组完整性规则，限定数据库状态及其变化，以保证数据正确、有效和相容，如关系模型必须满足实体完整性与参照完整性。三要素完整描述一个数据模型，缺一不可。', '①只把数据模型理解为表结构，漏掉数据操纵与完整性约束
②认为数据结构描述动态特性
③把概念模型与逻辑数据模型混同
④认为完整性约束只与具体应用有关，不是数据模型的组成部分', 'P14', 'concept', '4', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 1
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0105', 1, chapter_id, '概念模型与 E-R 图（实体、属性、联系）', '数据建模分两步：先把现实世界抽象为信息世界，建立概念模型；再把概念模型转换为计算机上某一 DBMS 支持的数据模型。概念模型按用户观点建模，不依赖具体计算机系统，主要用于数据库设计，是数据库设计人员与用户交流的语言，应具备较强的语义表达能力且简单清晰。信息世界的基本概念包括：实体（客观存在并可相互区别的事物）、属性（实体所具有的某一特性）、码（唯一标识实体的属性集）、实体型（用实体名及属性名集合抽象同类实体）、实体集（同一类型实体的集合）、联系（实体内部属性之间以及不同实体集之间的联系）。联系有一对一、一对多、多对多。最常用的概念模型表示方法是 P.P.S.Chen 于 1976 年提出的实体-联系模型（E-R 模型），用 E-R 图描述现实世界的概念模型。', '①认为概念模型依赖具体 DBMS
②把实体型与实体集、实体与属性混为一谈
③认为 E-R 图可以直接在 DBMS 中实现
④忽略实体间联系的类型区分', 'P13', 'schema', '5', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 1
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0106', 1, chapter_id, '常用逻辑数据模型（层次、网状、关系等）', '逻辑数据模型是按计算机系统观点建模、由具体 DBMS 支持的数据模型。层次模型用树形结构表示实体及联系，满足“有且只有一个结点无双亲（根）”和“其余结点有且只有一个双亲”，典型代表是 IBM 的 IMS；优点是结构清晰、查询效率高、完整性约束支持好，缺点是难以自然表示多对多联系、查询子女必须经过双亲。网状模型允许一个以上结点无双亲、一个结点可有多个双亲，典型代表是 DBTG/CODASYL 系统；它更直接地描述现实世界、存取效率高，但结构复杂、语言复杂、用户须了解存取路径。关系模型用二维表表示实体与联系，1970 年由 E.F.Codd 提出，建立在严格数学概念上，概念单一、存取路径对用户隐蔽，但查询效率需靠优化器保证。此外还有面向对象、对象关系、半结构化 XML 以及键值、文档、图、时序等新型数据模型。', '①认为层次模型可以自然表示多对多联系
②把网状模型与层次模型的成立条件记反
③认为关系模型没有完整性约束
④认为新型数据模型都已具备严格的三要素', 'P15', 'concept', '6', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 1
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0107', 1, chapter_id, '数据库系统的三级模式结构（外模式、模式、内模式）', '从 DBMS 角度看，数据库系统通常采用模式、外模式、内模式三级模式结构。模式也称逻辑模式，是数据库中全体数据的逻辑结构和特征的描述，是所有用户的公共数据视图，处于三级结构的中间层，不涉及物理存储细节，与具体应用、开发工具和语言无关；一个数据库只有一个模式。外模式也称子模式或用户模式，是数据库用户能够看见和使用的局部数据的逻辑结构和特征的描述，是用户的数据视图；一个数据库可以有多个外模式，同一外模式可为多个应用使用，但一个应用程序只能使用一个外模式；外模式是保证数据库安全性的有力措施。内模式也称物理模式或存储模式，是对数据物理结构和存储方式的描述，如记录存储方式、索引组织方式、是否压缩加密等；一个数据库只有一个内模式。三级模式是对数据的三个抽象级别。', '①认为一个数据库可以有多个内模式
②把模式与内模式混同
③认为外模式越多越不安全
④认为模式涉及物理存储细节', 'P23', 'schema', '7', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 1
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0108', 1, chapter_id, '数据库的二级映像与数据独立性', '数据独立性指应用程序与数据（定义）相互独立，即数据的结构发生变化后应用程序不必修改；它包括数据的逻辑独立性与物理独立性——逻辑独立性指应用程序与数据库的逻辑结构相互独立，物理独立性指应用程序与数据库的物理存储相互独立，二者由 DBMS 在三级模式之间提供的两级映像（外模式/模式映像、模式/内模式映像）保证。外模式/模式映像定义外模式与模式之间的对应关系，通常包含在各自外模式的描述中；同一个模式可以有任意多个外模式，故外模式/模式映像不唯一。当模式改变（如增加关系、属性或改变数据类型）时，DBA 修改映像可使外模式保持不变，依据外模式编写的应用程序不必修改，从而保证数据的逻辑独立性。模式/内模式映像定义数据全局逻辑结构与存储结构之间的对应关系，通常包含在模式描述中；当存储结构改变时，DBA 修改该映像可使模式保持不变，应用程序也不必改变，从而保证数据的物理独立性。模式/内模式映像是唯一的，因为一个数据库只有一个模式和一个内模式。数据独立性使数据的定义与描述从应用程序中分离出去，数据的组织与存取交由 DBMS 负责，简化了应用程序的编制，减少了应用程序的维护与修改。', '①把逻辑独立性与物理独立性记反
②认为模式改变时应用程序必须修改
③认为两级映像是可选的
④认为外模式/模式映像也是唯一的', 'P25', 'schema', '8', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 1
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0109', 1, chapter_id, '数据库系统的体系结构（集中式、C/S、并行、分布式、云）', '从最终用户角度看，数据库系统可分为集中式、客户-服务器（浏览器/应用服务器/数据库服务器）、并行、分布式和云数据库系统等，这是数据库系统的外部体系结构。集中式系统的 DBMS、数据库和应用程序都在一台计算机上，小型机/大型机上一般为多用户，微机上的常为单用户。客户-服务器系统中 DBMS 与数据库驻留服务器，应用程序放在客户机，通过网络通信；互联网应用常发展为三层结构，界面用浏览器，应用服务器部署应用模块，数据库服务器部署 DBMS 与数据库。并行数据库系统运行在并行计算机上，利用多处理机并行处理提高性能，具有高性能、高可用、高扩展性。分布式数据库的数据逻辑上是一个整体、物理上分布在网络不同节点，各节点场地自治，也可执行全局应用。云数据库把数据库部署或虚拟化在云计算环境，以服务形式提供存储、更新、查询、事务管理等功能，但仍面临安全可信与隐私保护问题。', '①把分布式数据库与并行数据库混同
②认为客户-服务器结构只有两层
③认为集中式系统一定是单用户
④认为云数据库没有安全与隐私问题', 'P28', 'concept', '9', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 1
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0110', 1, chapter_id, '数据库系统的特点', '数据库系统阶段的数据具有四个主要特点。第一，整体数据的结构化：数据不再只面向某一应用，而是面向整个组织或企业的多种应用，不仅数据本身结构化，数据之间也有联系，这是数据库系统与文件系统的本质区别；DBMS 维护记录结构及记录间联系，减轻程序员负担。第二，共享性强、冗余度低且易于扩充：数据面向整个系统，可被多个用户和应用通过不同接口与语言共享，降低冗余、避免不相容与不一致，并便于增加新应用。第三，数据与程序相互分离：应用程序不随存储结构改变而修改。第四，数据由 DBMS 统一管理和控制：提供安全性保护、完整性检查、并发控制与数据库恢复。数据库系统的出现使信息系统从以程序为中心转向以共享数据库为中心。', '①把“整体数据结构化”理解为单张表有结构
②认为共享性强必然导致无法控制安全性
③把数据共享性与数据冗余混为一谈
④认为数据库系统不需要并发控制与恢复', 'P9', 'boundary', '10', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 1
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

