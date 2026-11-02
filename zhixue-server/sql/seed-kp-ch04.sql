-- 智学领航 · 第4章知识点种子（由 csv_to_sql.py 生成）
-- 前置：init-v2.sql；可重复执行（upsert）。
USE zhixue;
SET NAMES utf8mb4;

INSERT INTO course_chapter (course_id, chapter_no, title, sort_no) VALUES
(1, 4, '数据库安全性', 4)
ON DUPLICATE KEY UPDATE title = VALUES(title), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0401', 1, chapter_id, '数据库安全性概述、不安全因素与安全标准', '数据库安全性是指保护数据库，防止不合法使用造成数据泄露、篡改或破坏。威胁主要来自三类：非授权用户恶意存取和破坏，如窃取口令、假冒合法用户、锁定数据勒索；重要或敏感数据被泄露，如 SQL 注入在查询语句末尾追加语句，骗取数据库执行非授权查询；安全环境脆弱，硬件、操作系统、网络协议的安全缺陷都会波及数据库。计算机系统安全问题分技术安全、管理安全、政策法律三类，本章只讨论技术安全。安全标准方面，TCSEC 与 TCSEC/TDI 把系统分为 D、C1、C2、B1、B2、B3、A1 四组七级，可信程度递增；C2 支持自主存取控制与审计，B1 增加标记与强制存取控制，是真正意义上的安全产品。CC 2.1 于 1999 年成为 ISO 国际标准、2001 年成为我国国家标准，用 EAL1—EAL7 评估保证级衡量保证程度。', '①把数据库安全性等同于完整性，忽视“防非法存取”与“防不合语义数据”的区别\n②只把威胁理解为黑客攻击，漏掉 SQL 注入、环境脆弱和内部人员\n③认为安全级别越高越好，不考虑成本与可用性\n④把 TCSEC 的 D 级当作最高级，记反等级顺序', 'P127', 'concept', '1', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 4
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0402', 1, chapter_id, '用户身份鉴别', '用户身份鉴别是 DBMS 提供的最外层安全保护措施。每个用户在系统中都有用户标识，由用户名和用户标识号（UID）组成，UID 在系统整个生命周期内唯一；系统内部记录所有合法用户标识，用户进入系统时由系统核对，通过后才提供使用权限。常用方法有五类：静态口令鉴别简单但易被攻击，通常配合双因子（口令+数字证书）、口令复杂度/过期/锁定/历史策略，并以密文存储传输；动态口令一次一密，验证码、动态令牌安全性更高；生物特征鉴别用指纹、虹膜、掌纹、人脸等；智能卡内置芯片、不可复制，但读取数据是静态的，可能被内存扫描或网络监听截取，实际常与 PIN 结合；入侵检测按预设规则实时分析并处理，如邮件报警、断开会话、锁定用户。', '①把身份鉴别当成存取控制，认为登录成功就拥有所有权限\n②认为静态口令足够安全，忽略双因子与口令策略\n③认为智能卡不可复制就绝对安全，忽略静态数据可被截取\n④把入侵检测当成事前预防，忽略它是实时检测与处置', 'P132', 'concept', '2', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 4
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0403', 1, chapter_id, '存取控制与自主存取控制', '存取控制是数据库安全控制的核心，确保只授权有资格的用户访问数据，未被授权者无法接近数据。机制由两部分组成：一是定义用户权限并把权限登记到数据字典，形成安全规则或授权规则；二是合法权限检查，用户每次请求（操作类型、对象、用户）时查数据字典，超出权限则拒绝。自主存取控制（DAC）中，不同用户对不同对象有不同权限，同一对象对不同用户也有不同权限，且用户可把自己拥有的权限转授他人，因而灵活；强制存取控制（MAC）中，每个对象标密级、每个用户发许可证，只有合法许可证才能存取，因而严格。C2 级 DBMS 支持 DAC，B1 级 DBMS 支持 MAC。关系数据库中授权对象不仅包括数据（基本表、视图、属性列），还包括数据库、模式、基本表、视图、索引的创建等。', '①把存取控制等同于身份鉴别\n②认为 DAC 中任何用户都能转授权，忽略 WITH GRANT OPTION\n③认为 C2 级已支持强制存取控制\n④认为权限只作用于表中的数据，忽略模式、表、视图、索引等对象', 'P133', 'concept', '3', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 4
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0404', 1, chapter_id, 'GRANT 授权与 WITH GRANT OPTION', 'SQL 用 GRANT 授予权限，一般格式为 GRANT <权限>[,<权限>] ON <对象类型><对象名>[,<对象类型><对象名>] TO <用户>[,<用户>] [WITH GRANT OPTION]。语义是把指定对象的指定操作权限授予指定用户。发出者可以是 DBA、数据库属主，或已拥有该权限的用户；接受者可以是一个或多个具体用户，也可以是 PUBLIC（全体用户）。指定 WITH GRANT OPTION 后，被授权者还能把权限再授予他人；未指定则只能使用、不能传播。SQL 标准允许传递权限或其子集，但不允许循环授权，被授权者不能把权限授回授权者或其祖先。对属性列授权必须写明列名，如 GRANT UPDATE(Sno), SELECT ON TABLE Student TO U4；给列 INSERT 权限时必须包含主码列的 INSERT 权限，否则插入会因主码为空被拒绝。', '①认为 GRANT 默认带 WITH GRANT OPTION\n②认为被授权者可以把权限授回授权者，形成循环\n③对属性列授权不写列名\n④认为给列 INSERT 权限可以不包含主码列', 'P135', 'sql', '4', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 4
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0405', 1, chapter_id, 'REVOKE 收回与级联收回', 'REVOKE 用于收回已授予的权限，一般格式为 REVOKE <权限>[,<权限>] ON <对象类型><对象名>[,<对象类型><对象名>] FROM <用户>[,<用户>] [CASCADE|RESTRICT]。指定 CASCADE 时级联收回被授权者转授出去的权限；指定 RESTRICT 时，若权限已被转授则不能收回；默认值为 RESTRICT。例：U5 把 SC 表 INSERT 权限授予 U6，U6 又授予 U7；REVOKE INSERT ON TABLE SC FROM U5 CASCADE 会同时收回 U6、U7 的 INSERT 权限。若 U6 或 U7 还从其他用户处获得同一权限，则他们仍保留该权限，系统只收回直接或间接从 U5 处获得的部分。DBA 可以收回其授予的权限，用户也可以收回自己授出的权限。', '①认为 REVOKE 默认级联收回\n②认为级联收回会把被授权者从其他来源获得的同一权限也收回\n③认为收回角色等于删除角色\n④认为 RESTRICT 与 CASCADE 只是语法差异，结果相同', 'P137', 'sql', '5', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 4
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0406', 1, chapter_id, '数据库角色', '数据库角色是被命名的一组与数据库操作相关的权限，本质是权限的集合。为一组具有相同权限的用户创建一个角色，可以简化授权过程。SQL 中先用 CREATE ROLE <角色名> 创建空角色；再用 GRANT <权限> ON <对象类型><对象名> TO <角色> 给角色授权；用 GRANT <角色> TO <用户或角色> [WITH ADMIN OPTION] 把角色授予用户或其他角色，角色包含直接授予它的权限加上其他角色授予它的权限；指定 WITH ADMIN OPTION 后，获得者还能把角色再授予他人；用 REVOKE <权限> FROM <角色> 收回角色权限，或用 REVOKE <角色> FROM <用户> 收回用户的角色。例 4.14 用 R1 一次性给王平、张明、赵玲授予 Student 表三个权限，再 REVOKE R1 FROM 王平 一次收回。', '①把角色当成一个数据库用户\n②认为授予角色默认带 WITH ADMIN OPTION\n③认为修改角色权限不会影响已授予该角色的用户\n④认为收回用户的角色会删除角色本身', 'P139', 'sql', '6', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 4
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0407', 1, chapter_id, '强制存取控制（MAC）', '强制存取控制是系统按 TCSEC/TDI 安全策略要求实施的强制存取检查，用户不能直接感知或控制，适用于对数据严格且固定密级分类的部门，如军事、政府部门。DBMS 管理的实体分为主体（活动实体：实际用户及代表用户的进程）和客体（被动实体：文件、基本表、索引、视图等），每个实例被指派敏感度标记。主体标记称许可证级别，客体标记称密级，级别如绝密 TS、机密 S、秘密 C、公开 P，次序 TS>=S>=C>=P。存取规则有两条：仅当主体许可证级别大于或等于客体密级时才能读；仅当主体许可证级别小于或等于客体密级时才能写。写规则可防止高密级数据降密后写回造成泄露。实现 MAC 时要先实现 DAC，DAC 与 MAC 共同构成 DBMS 安全机制，系统先做 DAC 检查，通过后再做 MAC 检查。', '①认为 MAC 可以替代 DAC\n②把读写规则记成同一方向，认为高许可证可写低密级\n③认为用户可以自主决定自己的许可证级别\n④忽略“先 DAC 后 MAC”的检查顺序', 'P141', 'concept', '7', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 4
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0408', 1, chapter_id, '视图机制', '视图具有安全保护功能。为不同用户定义不同视图，可以把数据对象限制在一定范围内，把保密数据对无权存取的用户隐藏起来，从而自动提供一定程度的安全保护。视图还能与授权机制配合，间接实现支持存取谓词的用户权限定义。例如王平老师只能查询计算机科学与技术专业学生信息，张明老师具有该专业学生信息的查询和增删改权限；在不直接支持存取谓词的系统中，可先建立视图 CS_Student（SELECT * FROM Student WHERE Smajor=''计算机科学与技术'' WITH CHECK OPTION），再在视图上分别授予王平 SELECT、授予张明 ALL PRIVILEGES。WITH CHECK OPTION 保证对该视图进行增删改时必须满足视图定义中的条件。', '①把视图当成独立存储的物理表\n②认为授予视图权限等于授予基本表的全部权限\n③把 WITH CHECK OPTION 当成授权选项\n④认为视图能提供完全的逻辑独立性', 'P142', 'schema', '8', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 4
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0409', 1, chapter_id, '审计', '审计是 DBMS 提供的事后检查安全机制，是达到 C2 以上安全级别必不可少的指标。它把用户对数据库的所有操作自动记录到审计日志，审计员可据此监控行为、重现导致数据库现状的事件、找出非法存取的人/时间/内容，并分析潜在威胁。审计费时费空间，通常作为可选特征，由 DBA 按安全需求开关，主要用于安全要求较高的部门；既能审计成功操作，也能审计失败操作。审计事件分服务器事件、系统权限、语句事件、模式对象事件四类。审计功能包括多种查阅方式、多套审计规则、审计分析与报表、审计日志管理：先转储后删除，只允许审计员查阅和转储，禁止普通用户新增修改。AUDIT 设置审计，NOAUDIT 取消；BY ACCESS 记录每次操作，BY SESSION 每次会话只记录最早一次。用户级审计任何用户可设，系统级审计只能由 DBA 设置。', '①认为审计能事前阻止非法操作，忽略它是事后检查\n②认为审计日志任何用户都能修改\n③认为审计默认常开，忽略其时间空间成本\n④把 BY ACCESS 与 BY SESSION 记反', 'P143', 'concept', '9', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 4
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0410', 1, chapter_id, '数据加密（存储加密与传输加密）', '数据加密是防止数据在存储和传输中失密的有效手段，把明文按算法变换为不可直接识别的密文。存储加密分透明与非透明：透明存储加密是内核级、对用户隐蔽，建表时说明加密字段即可，应用无需修改；非透明存储加密通过多个加密函数实现。传输加密常用链路加密与端到端加密：链路加密对报头和报文都加密，需要中间节点密码设备；端到端加密只在发送端加密、接收端解密，只加密报文不加密报头，所需密码设备较少，但报头可能被监听。SSL 是端到端方案，包含创建可信连接、确认端点可靠性（CA 证书与 CRL）、协商算法与密钥（PKI）、可信传输、关闭连接，会话密钥仅限本次通信。加密会增加查询处理复杂性并影响效率，密钥管理也是需要考虑的问题。', '①认为加密可以完全消除数据泄露风险\n②认为端到端加密也加密报头\n③认为链路加密不需要中间节点密码设备\n④忽略加密对查询效率与密钥管理的影响', 'P145', 'boundary', '10', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 4
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0411', 1, chapter_id, '其他安全性保护（推理控制、隐蔽信道、数据隐私、三权分立）', '其他安全性保护针对自主存取控制与强制存取控制之外或未解决的问题。推理控制防止用户利用属性间的函数依赖、敏感关联等，从可访问的低密级信息推导出无权访问的高密级信息；例如职务→工资的函数依赖，同级职务用户可由自己的工资推出他人工资。隐蔽信道防止高安全等级用户利用系统机制向低安全等级用户传递信息，例如利用 UNIQUE 约束列的插入成功/失败约定信息位，使信息从高密级流向低密级。数据隐私控制个人数据在收集、存储、处理、发布各阶段不被非法获取；发布时先去掉候选码并考虑准标识符，常用 k 匿名（相同准标识符组至少 k 条记录）、l 多样性、t 临近等标准。三权分立按 GB/T 20273—2019 把数据库管理员过于集中的权限拆分，避免超级用户不受检查。', '①把推理控制当成强制存取控制的一部分\n②认为隐蔽信道只存在于网络层\n③把 k 匿名理解成“每组共 k 条记录”\n④认为三权分立只是把 DBA 改个名字', 'P147', 'boundary', '11', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 4
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

