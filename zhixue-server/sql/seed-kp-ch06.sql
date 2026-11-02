-- 智学领航 · 第6章知识点种子（由 csv_to_sql.py 生成）
-- 前置：init-v2.sql；可重复执行（upsert）。
USE zhixue;
SET NAMES utf8mb4;

INSERT INTO course_chapter (course_id, chapter_no, title, sort_no) VALUES
(1, 6, '关系数据理论', 6)
ON DUPLICATE KEY UPDATE title = VALUES(title), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0601', 1, chapter_id, '问题的提出与数据依赖', '关系数据库逻辑设计要解决两个问题：什么样的关系模式是“好”的，以及如何把“不好”的模式改造为“好”的模式。关系模式可形式化表示为 R(U,F)，U 为属性集，F 为 U 上的一组数据依赖。数据依赖是属性间通过值相等与否体现的约束，最重要的是函数依赖和多值依赖。以例6.1 Student(Sno,School,Mname,Cno,Grade) 为例，F={Sno→School,School→Mname,(Sno,Cno)→Grade}，该模式存在数据冗余、更新异常、插入异常、删除异常；原因是函数依赖存在“不好”的性质。好模式应尽量避免这些异常、减少冗余；改造方法是模式分解，如分解为 S(Sno,School)、SC(Sno,Cno,Grade)、SCHOOL(School,Mname)。', '①把数据冗余当成唯一问题，忽略插入/删除/更新异常\n②认为范式越高越好，不考虑应用语义与查询需求\n③把函数依赖当成某个具体关系实例的约束\n④认为模式分解一定无损，忽略分解准则', 'P171', 'concept', '1', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 6
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0602', 1, chapter_id, '函数依赖', '函数依赖定义：对 R(U,F) 的任意可能关系 r，不可能存在两个元组在 X 上值相等而在 Y 上值不等，称 X 函数决定 Y，记 X→Y。函数依赖是语义范畴，只能由语义确定，且要求 R 的所有关系实例都满足，不是某一个实例满足。术语：X→Y 且 Y⊄X 为非平凡函数依赖；Y⊆X 为平凡函数依赖；X 为决定因素。完全函数依赖：X→Y 且 X 的任何真子集 X'' 都有 X''↛Y；否则为部分函数依赖。传递函数依赖：X→Y、Y→Z，且 Y⊄X、Y≠X、Z⊄Y，记 X→Z（传递）。', '①认为函数依赖可由表中数据统计得出，忽略语义\n②把部分函数依赖当成完全函数依赖\n③把平凡函数依赖当成有信息量的依赖\n④认为 X→Y、Y→Z 一定有 X→Z，忽略传递依赖的附加条件', 'P174', 'concept', '2', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 6
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0603', 1, chapter_id, '码', '码用函数依赖定义：设 K 为 R(U,F) 的属性或属性组，若 K→U，则 K 为候选码；若 U 部分函数依赖于 K（K→U 但非完全），K 为超码。候选码的超集一定是超码，候选码的任何真子集一定不是超码。候选码多于一个时选一个为主码；包含在任一候选码中的属性为主属性，否则为非主属性；整个属性组是码时称为全码。若 X 不是 R 的码但是另一个关系模式的码，则 X 是 R 的外码。例：Student 的 Sno 是码；SC 的 (Sno,Cno) 是码，Sno 是外码；R(P,W,A) 全码 (P,W,A)。', '①把候选码与超码混为一谈\n②认为主属性只属于主码\n③认为全码不是码\n④求码时只看单个属性，忽略属性组', 'P176', 'concept', '3', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 6
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0604', 1, chapter_id, '范式与 2NF', '1NF：关系每个分量都是不可分的数据项。2NF：R∈1NF，且每个非主属性都完全函数依赖于任一候选码。例6.4 S-L-C(Sno,School,Sloc,Cno,Grade)，码 (Sno,Cno)，但 Sno→School、Sno→Sloc，School、Sloc 是部分函数依赖，故 S-L-C 不是 2NF；会产生插入异常（新生未选课无法插入）、删除异常（删除课程连带学生信息）、修改复杂（转学院要改多条）。分解为 SC(Sno,Cno,Grade) 和 S-L(Sno,School,Sloc) 后，非主属性对码完全函数依赖。', '①认为 1NF 已足够，忽略部分函数依赖\n②把 2NF 说成“每个属性都完全依赖码”\n③认为 2NF 一定消除所有异常\n④分解时只按属性个数拆，不按依赖关系拆', 'P177', 'concept', '4', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 6
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0605', 1, chapter_id, '3NF', '3NF：R∈1NF，若 R 中不存在码 X、属性组 Y 及非主属性 Z（Z⊄Y），使 X→Y、Y→Z 成立且 Y⊄X、Y 不是码，则 R∈3NF。等价地，3NF 要求每个非主属性既不传递依赖于码，也不部分依赖于码；因此 3NF 一定 2NF。例6.4 S-L(Sno,School,Sloc) 中 Sno→School、School→Sloc，Sloc 传递依赖于 Sno，故 S-L 不是 3NF；分解为 S-SS(Sno,School) 与 S-SL(School,Sloc) 后消除传递依赖。', '①认为 3NF 只要求没有部分依赖\n②认为 3NF 一定 BCNF\n③把主属性之间的传递依赖也当成 3NF 禁止项\n④分解后不检查新模式的范式', 'P178', 'concept', '5', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 6
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0606', 1, chapter_id, 'BCNF', 'BCNF：R∈1NF，若 X→Y 且 Y⊄X 时 X 必含有码，则 R∈BCNF；即每个决定因素都包含码。BCNF 满足：所有非主属性对每个码完全函数依赖；所有主属性对不包含它的码完全函数依赖；没有任何属性完全函数依赖于非码属性组。BCNF 一定 3NF，但 3NF 不一定 BCNF。例 STJ(S,T,J)，F={(S,J)→T,(S,T)→J,T→J}，(S,J)、(S,T) 是候选码，STJ 是 3NF；但 T 是决定因素且不含码，故不是 BCNF。STJ 可分解为 ST(S,T) 与 TJ(T,J)，均为 BCNF。', '①认为 3NF 与 BCNF 等价\n②只检查非主属性，漏掉主属性对码的依赖\n③认为 BCNF 分解一定保持函数依赖\n④把“决定因素含码”误写成“右部含码”', 'P179', 'concept', '6', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 6
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0607', 1, chapter_id, '多值依赖与 4NF', '多值依赖：设 X、Y、Z 是 U 的子集，Z=U-X-Y。若对任一关系 r，给定一对 (x,z) 值，Y 的值仅由 x 决定而与 z 无关，则 X→→Y。若 Z=∅，称为平凡多值依赖。函数依赖是多值依赖的特例：X→Y 则 X→→Y。例 Teaching(C,T,B) 中课程 C 决定一组教师 T，也决定一组参考书 B，C→→T、C→→B；该模式全码 (C,T,B)，属于 BCNF，但增删教师/参考书要动多个元组。4NF：R∈1NF，若对每个非平凡且非函数依赖的多值依赖 X→→Y，X 都含有码，则 R∈4NF。Teaching 可分解为 CT(C,T) 与 CB(C,B) 消去非平凡 MVD。', '①把多值依赖当成函数依赖\n②认为 BCNF 一定没有异常\n③把平凡多值依赖当成需要消除的依赖\n④认为 4NF 条件只要求 X 含属性即可', 'P180', 'concept', '7', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 6
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0608', 1, chapter_id, '函数依赖公理系统与闭包', 'Armstrong 公理系统：A1 自反律（若 Y⊆X⊆U，则 X→Y）、A2 增广律（若 X→Y 且 Z⊆U，则 XZ→YZ）、A3 传递律（若 X→Y、Y→Z，则 X→Z）。由 A1—A3 可推出合并规则（X→Y、X→Z 则 X→YZ）、伪传递规则（X→Y、WY→Z 则 XW→Z）、分解规则（X→Y、Z⊆Y 则 X→Z）。F 的逻辑蕴涵全体称 F 的闭包 F+；X 关于 F 的闭包 X+ = {A | X→A 能由 F 导出}。判定 X→Y 是否可由 F 导出，只需求 X+ 并判断 Y⊆X+。算法6.1 迭代求 X+，最多 |U|-|X| 次循环。Armstrong 公理有效且完备。', '①把自反律当成依赖 F 才成立\n②认为 F+ 可以手工全部列出\n③求闭包时只扫一遍函数依赖\n④把“导出”与“蕴涵”当成两套不同语义', 'P185', 'concept', '8', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 6
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0609', 1, chapter_id, '最小依赖集与保持函数依赖的分解', '若 F+ = G+，称 F 与 G 等价（F 覆盖 G）。F 是最小依赖集（最小覆盖）当且仅当：①每个函数依赖右部只含一个属性；②F 中不存在 X→A 使 F 与 F-{X→A} 等价；③F 中不存在 X→A 及 X 的真子集 Z 使 F-{X→A}∪{Z→A} 与 F 等价。每个 F 都等价于一个最小依赖集，但最小依赖集不唯一。保持函数依赖的分解：分解后的各子模式投影之并 F+ 等于原 F+。算法6.2（合成法）先把 F 极小化，去掉不出现在 F 中的属性，按相同左部原则分组，得到保持函数依赖的 3NF 分解。', '①认为最小依赖集唯一\n②只检查右部单属性，忽略左部冗余\n③认为任意分解都保持函数依赖\n④把保持函数依赖与无损连接当成同一准则', 'P188', 'concept', '9', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 6
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

INSERT INTO knowledge_point (kp_id, course_id, chapter_id, name, concept_text, misconception, textbook_page, dimension_key, sort_no, created_by)
SELECT 'KP0610', 1, chapter_id, '无损连接的模式分解', '无损连接分解：分解 ρ={R1,...,Rk}，若对 R 的任一关系 r 都有 r = mρ(r)（各子模式投影的自然连接），则称 ρ 具有无损连接性。直接用定义验证不可能（关系实例无穷多）。算法6.3 表算法：建 k 行 n 列表，属性属于子模式填 aj，否则填 bij；对每个 FD X→A，找 X 列相同符号的行，若某行 A 列为 aj 则全改 aj，否则全改 bmj；重复扫描直到表不变；若出现一行全为 a，则无损。例 R(A,B,C,D,E)、F={AB→C,C→D,D→E}，分解 R1(A,B,C)、R2(C,D)、R3(D,E) 可判为无损。无损连接与保持函数依赖是两种不同准则，可同时满足或只满足其一。', '①把无损连接理解为“不丢属性”\n②认为保持函数依赖一定无损\n③直接按定义逐个关系实例验证\n④表算法只扫描一遍就下结论', 'P191', 'concept', '10', 1
FROM course_chapter WHERE course_id = 1 AND chapter_no = 6
ON DUPLICATE KEY UPDATE name = VALUES(name), concept_text = VALUES(concept_text), misconception = VALUES(misconception), textbook_page = VALUES(textbook_page), dimension_key = VALUES(dimension_key), sort_no = VALUES(sort_no);

