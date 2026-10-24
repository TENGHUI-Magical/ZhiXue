-- 智学领航 · 第2—3章题库种子（竖切 60 题）
-- 数据来源：技术学习/13-第2章题目-核对版.md、14-第3章题目-核对版.md
-- 前置：先执行 init-v2.sql、seed-kp-ch02-ch03.sql。本文件可重复执行，全部为 upsert。
-- 题目由教材/习题册原题改写，答案已按页面图像核对；教师复核前不得作为正式评测数据。
-- 待教师复核：Q_0220、Q_0224、Q_0326

USE zhixue;
-- Windows 下 mysql 客户端默认字符集可能是 GBK，显式声明避免中文乱码/1366 错误
SET NAMES utf8mb4;

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0201', 1, 'KP0201', '单选',
       '关于关系模型，下列叙述不正确的是（ ）。',
       '[{"key":"A","text":"关系模型由关系数据结构、关系操作集合和关系完整性约束三部分组成"},{"key":"B","text":"关系模型只包含单一的数据结构——关系，用户看到的是二维表"},{"key":"C","text":"关系模型只规定数据的逻辑结构，关系操作与完整性约束由应用程序自行实现"},{"key":"D","text":"关系中实体以及实体之间的联系都用同一种结构类型表示"}]',
       'C',
       '关系模型由关系数据结构、关系操作集合、关系完整性约束三部分组成，缺一不可。C 把后两部分推给应用程序，与“完整性约束应由关系系统自动支持”相矛盾；A 是三要素的完整表述，B、D 是关系模型“单一结构、可表达实体间联系”的特点。',
       1, '习题册', '把本题改成填空，要求写出关系模型三个组成部分并各举一例。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0202', 1, 'KP0202', '单选',
       '设 D1=导师={张清玫,刘逸}，D2=专业={计算机科学与技术,信息管理与信息系统}，D3=研究生={李勇,刘晨,王敏}，则笛卡儿积 D1×D2×D3 的基数为（ ）。',
       '[{"key":"A","text":"12"},{"key":"B","text":"7"},{"key":"C","text":"6"},{"key":"D","text":"9"}]',
       'A',
       '笛卡儿积的基数为各域基数之积，即 2×2×3=12。7 是三个域基数之和，属于把乘法误当加法的典型错误；6、9 是少乘或多乘一个域基数的结果。',
       1, '教材', '把三个域改为 {男,女}、0～100 的整数、3 个学号，再求基数。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0203', 1, 'KP0203', '单选',
       '下列关于候选码的叙述，正确的是（ ）。',
       '[{"key":"A","text":"候选码只能由一个属性组成"},{"key":"B","text":"候选码的值能唯一标识元组，且它的真子集不能唯一标识元组"},{"key":"C","text":"一个关系最多只能有一个候选码"},{"key":"D","text":"候选码所包含的属性可以取空值"}]',
       'B',
       '候选码的定义包含两个条件：能唯一标识元组、真子集不能唯一标识，B 完整表述了这两点。A 忽略了多属性候选码，C 与“一个关系可以有多个候选码”矛盾，D 违反实体完整性。',
       1, '习题册', '给出一个关系模式，让学生列出全部候选码并指出主属性。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0204', 1, 'KP0204', '单选',
       '下列关于关系模式与关系的叙述，正确的是（ ）。',
       '[{"key":"A","text":"关系模式随元组的插入、删除而变化"},{"key":"B","text":"关系模式是值，关系是型"},{"key":"C","text":"关系模式只描述属性名，不包含域名与映像"},{"key":"D","text":"关系模式是型，关系是关系模式在某一时刻的状态或内容"}]',
       'D',
       '关系模式是对关系的描述，属于型，一经定义就稳定；关系是元组集合，属于值，随时间变化，故 D 正确。A 把型的稳定性说成动态，B 恰好颠倒，C 漏掉了 R(U,D,DOM) 中的 D 与 DOM。',
       2, '教材', '用 Student 表举例说明“型不变、值可变”。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0205', 1, 'KP0205', '单选',
       '关系操作的特点是集合操作方式，其操作的对象和结果都是（ ）。',
       '[{"key":"A","text":"记录"},{"key":"B","text":"集合"},{"key":"C","text":"元组"},{"key":"D","text":"列"}]',
       'B',
       '关系操作一次处理一个集合，输入、输出以及中间结果都是关系（集合），故对象和结果都是集合。元组和列只是关系的组成成分，不能概括操作对象与结果；记录是文件系统的术语。',
       1, '习题册', '追问“一次一个集合”与“一次一个记录”的处理方式差别。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0206', 1, 'KP0206', '单选',
       '学生选课关系 SC(学号,课程号,成绩)，主码为(学号,课程号)。根据实体完整性规则，下列叙述正确的是（ ）。',
       '[{"key":"A","text":"学号、课程号中只要有一个非空即可"},{"key":"B","text":"只有学号不能取空值"},{"key":"C","text":"学号和课程号都不能取空值"},{"key":"D","text":"成绩作为非主属性也不能取空值"}]',
       'C',
       '实体完整性要求主属性不能取空值；主码由多个属性组成时，每个组成属性都不能为空，故 C 正确，A、B 都只覆盖了主码的一部分。D 把实体完整性扩大到非主属性，成绩是否可空属于用户定义的完整性范畴。',
       2, '习题册', '把主码改为只含学号，再判断哪些属性不能为空。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0207', 1, 'KP0207', '单选',
       '职工(职工号,姓名,年龄,部门号)与部门(部门号,部门名称)，职工.部门号是外码。下列叙述正确的是（ ）。',
       '[{"key":"A","text":"职工是参照关系，部门是被参照关系"},{"key":"B","text":"部门是参照关系，职工是被参照关系"},{"key":"C","text":"外码必须与被参照关系的主码同名"},{"key":"D","text":"职工.部门号在任何情况下都不能取空值"}]',
       'A',
       '外码所在的关系 R 是参照关系，被引用主码所在的关系 S 是被参照关系（目标关系），故 A 正确。C 错，外码不必与主码同名，如“主修专业”引用“专业名”；D 错，参照完整性允许外码取空值。',
       2, '习题册', '让学生指出本例中 F 与 K_s 分别对应哪个属性。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0208', 1, 'KP0208', '单选',
       '职工表中“性别”属性只能取“男”或“女”，这一约束属于（ ）。',
       '[{"key":"A","text":"实体完整性"},{"key":"B","text":"参照完整性"},{"key":"C","text":"关系不变性"},{"key":"D","text":"用户定义的完整性"}]',
       'D',
       '该约束是具体应用对某一属性取值范围的语义要求，属于用户定义的完整性。实体完整性只管主属性非空，参照完整性管外码引用，“关系不变性”指实体完整性与参照完整性两者，都不针对取值范围。',
       1, '习题册', '换成“成绩 0～100”或“姓名不能为空”，再判断类别。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0209', 1, 'KP0209', '单选',
       '设 R、S 是同目的关系，下列叙述正确的是（ ）。',
       '[{"key":"A","text":"R−S 的结果列数少于 R 的列数"},{"key":"B","text":"R∩S 的结果元组数可能大于 R 与 S 的元组数之和"},{"key":"C","text":"R∩S 可用差运算表示，即 R∩S=R−(R−S)"},{"key":"D","text":"R×S 的结果列数为 R 与 S 的列数之和减去公共属性数"}]',
       'C',
       '交可由差导出，R−(R−S) 恰为既属于 R 又属于 S 的元组，C 正确。并、差、交都是同目运算，不改变列数，A 错；交的结果必是 R 的子集，元组数不会超过 R，B 错；笛卡儿积列数为 n+m 且不去掉公共属性，D 描述的是自然连接。',
       2, '教材', '给出具体元组集合，分别计算 R∪S、R−S、R∩S 并核对元组数。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0210', 1, 'KP0210', '单选',
       '关系 R(A,B,C) 与 S(B,C,D) 进行自然连接，结果包含的列数为（ ）。',
       '[{"key":"A","text":"6"},{"key":"B","text":"4"},{"key":"C","text":"5"},{"key":"D","text":"2"}]',
       'B',
       '自然连接在公共属性 B、C 上做等值比较，并在结果中去掉重复的公共属性列，故结果为 A、B、C、D 共 4 列。6 是直接做笛卡儿积的列数，5 是只去掉一列公共属性的错误算法。',
       2, '习题册', '改为 R(A,B) 与 S(B,C)，或公共属性有三个的情形。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0211', 1, 'KP0210', '单选',
       '对关系 R(A,B,C) 做投影 π_A,B(R)，下列叙述正确的是（ ）。',
       '[{"key":"A","text":"结果的元组数可能少于 R，且需取消重复元组"},{"key":"B","text":"结果的列数与 R 相同"},{"key":"C","text":"投影是从行的角度选取满足条件的元组"},{"key":"D","text":"结果的元组数一定等于 R 的元组数"}]',
       'A',
       '投影从列的角度选取属性，取消某些列后可能出现重复行，必须取消重复元组，故结果元组数可能减少，A 对、D 错。投影后列数减少，B 错；从行角度选取元组的是选择，C 错。',
       2, '教材', '给 5 行 R，写出 π_A(R) 并手工去重。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0212', 1, 'KP0207', '单选',
       '课程(课程号,课程名,学分,先修课)，课程号为主码，先修课为外码，参照本关系的主码。下列叙述正确的是（ ）。',
       '[{"key":"A","text":"参照关系与被参照关系是同一个关系，因此不存在参照完整性"},{"key":"B","text":"先修课只能取空值，不能取本关系中已存在的课程号"},{"key":"C","text":"先修课必须非空，否则违反参照完整性"},{"key":"D","text":"先修课可以取空值，也可以取本关系中某个元组的课程号值"}]',
       'D',
       '参照完整性不要求参照关系与被参照关系不同，同一关系内部属性之间也可引用，A 错。规则允许外码取空值（表示该课程无先修课），也允许取被参照关系中已存在的主码值，故 D 对、B 错；C 把“可为空”误当成“必须非空”。',
       3, '习题册', '把“先修课”换成“系主任”，再判断该外码能否取空值。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0213', 1, 'KP0201', '填空',
       '在关系模型中，二维表的每一行称为一个____，每一列称为一个____，列中值的取值范围称为____，元组中的每一个值称为____。',
       NULL,
       '元组；属性；域；分量',
       '四者依次对应行、列、列的取值集合、行中的单个值，是最基础的一组术语。域是“一组具有相同数据类型的值的集合”，不能与属性混为一谈。',
       1, '教材', '再补填“目（度）”与“基数”，并说明二者区别。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0214', 1, 'KP0205', '填空',
       '关系模型中有 8 种查询操作，其中____、____、____、____和____是 5 种基本操作，其他操作可由它们定义和导出。',
       NULL,
       '选择、投影、并、差、笛卡儿积（顺序可互换）',
       '教材 2.2 节明确这 5 种为基本操作，交、连接、除都可由它们导出，就像乘法可由加法定义。把交、连接、除填入即错。',
       1, '习题册', '追问交、连接、除分别如何用基本操作表示。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0215', 1, 'KP0206', '填空',
       '实体完整性规则：若属性 A（一个或一组属性）是基本关系 R 的____，则 A 不能取____。',
       NULL,
       '主属性；空值',
       '该规则只针对主属性，空值指“不知道”“不存在”或“无意义”的值。主码由若干属性组成时，所有组成属性都不能取空值。',
       1, '教材', '把“主属性”改成“非主属性”，再判断命题真假。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0216', 1, 'KP0207', '填空',
       '设 F 是基本关系 R 的一个或一组属性，但不是 R 的码；K_s 是基本关系 S 的主码。若 F 与 K_s 相对应，则称 F 是 R 的____，R 称为____，S 称为____。',
       NULL,
       '外码；参照关系；被参照关系（目标关系）',
       '三者成对出现，且 F 与 K_s 必须定义在同一（或同一组）域上；外码不必与主码同名。',
       2, '教材', '给出“主修专业”引用“专业名”的例子，填出 F 与 K_s。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0217', 1, 'KP0208', '填空',
       '学生选课关系中规定“成绩”的取值范围为 0～100，这类约束属于关系模型的____完整性。',
       NULL,
       '用户定义的完整性（用户定义完整性）',
       '它不属于关系模型固有的两个不变性，而是具体应用提出的语义要求。实体完整性管主属性非空，参照完整性管外码引用，都不涉及取值范围。',
       1, '习题册', '再举“姓名不能为空”，判断它属于哪类完整性。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0218', 1, 'KP0202', '填空',
       '若域 D_i(i=1,2,…,n) 为有限集、其基数为 m_i，则笛卡儿积 D1×D2×…×Dn 的基数为____。',
       NULL,
       '∏(i=1…n) m_i，即 m1×m2×…×mn',
       '每个元组由各域各取一个分量组成，故总数为各域基数之积。基数指一个域允许的不同取值个数，不是元组个数。',
       1, '教材', '给 m1=2、m2=101、m3=3 代入求值。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0219', 1, 'KP0202', '简答',
       '说明域、笛卡儿积、关系、元组、属性之间的联系与区别。',
       NULL,
       '域是一组具有相同数据类型的值的集合；笛卡儿积是给定一组域后所有有序组合的集合，其基数为各域基数之积；关系是笛卡儿积的有限子集，只有这样的子集才可能有实际语义；元组是笛卡儿积的一个元素，也是关系中的一行；属性是给关系每一列起的名字，n 目关系必有 n 个属性。区别在于：域是值的集合，属性是列名并对应一个域，元组是分量的有序组合，关系是元组的有限集合。',
       '答题要按“值集合—组合全体—有语义的子集—行—列名”的顺序串起来，并点明“关系的目”是属性个数而非元组个数。',
       2, '教材', '用导师、专业、研究生三个域举例，指出哪些元组才能构成关系。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0220', 1, 'KP0203', '简答',
       '定义并说明候选码、主码、主属性、非主属性、全码之间的联系与区别。',
       NULL,
       '能唯一标识元组且其真子集不能唯一标识的属性（组）称为候选码；从多个候选码中选定一个作为主码；候选码中包含的属性称为主属性，不包含在任何候选码中的属性称为非主属性（非码属性）；若关系的所有属性共同构成候选码，则称为全码。联系：主码必是候选码之一，主属性由全部候选码决定。区别：候选码强调最小性，主码强调在候选码中“选定一个”，并用于实体完整性约束。',
       '候选码的两个条件必须同时写出，只答“能唯一标识”会漏掉最小性；主属性看的是全部候选码，而不是只看主码。',
       2, '教材', '给出一个具体关系模式，要求写出全部候选码、主码、主属性、非主属性。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0221', 1, 'KP0204', '简答',
       '举例说明关系模式和关系的区别。',
       NULL,
       '关系模式是型，例如 Student(Sno,Sname,Ssex,Sbirthdate,Smajor)，它描述关系的结构，一经定义就稳定不变；关系是值，例如某一时刻 Student 中的 3 个学生元组，它随插入、删除、修改而变化。同一关系模式在不同时刻可以对应不同的关系，因此日常所说的“关系”要结合上下文区分型与值。',
       '判断关键是“是否随元组变化”。关系模式是静态的，关系是动态的；关系数据库模式是型的集合，关系数据库是某一时刻值的集合。',
       2, '教材', '追问关系数据库模式与某一时刻关系数据库的区别。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0222', 1, 'KP0206', '简答',
       '为什么主码不能取空值？若主码由多个属性组成，是否允许其中某个属性为空？',
       NULL,
       '空值是“不知道”“不存在”或“无意义”的值。主码的作用是唯一标识元组，若取空值就会出现无法标识的实体，与“现实世界中的实体是可区分的”这一前提矛盾，因此实体完整性规定主属性不能取空值。主码由多个属性组成时，所有组成属性都不能为空，只要求其中一个非空是错误的。实体完整性只约束主属性，不约束非主属性。',
       '要从“实体可区分性”推出“主码非空”，而不是只背规则；还要说明规则的作用范围仅限主属性。',
       2, '教材', '讨论“允许主码为空、另加唯一约束”是否会破坏实体可区分性。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0223', 1, 'KP0207', '简答',
       '如果数据库只有一个关系，是否就不存在参照完整性？请举例说明。',
       NULL,
       '仍然可能存在。课程(课程号,课程名,学分,先修课)中，课程号是主码，先修课是外码，它参照本关系的主码课程号。该关系既是参照关系又是被参照关系，刻画的是关系内部属性之间的引用约束。有些课程不要求先修课，“先修课”可取空值；若取非空值，则必须是本关系中确实存在的课程号。',
       '参照完整性刻画的是“引用”，不要求两个关系不同；同一关系内部的自引用同样受参照完整性约束。答“只有一个关系就没有参照完整性”是把参照完整性误解为必须跨表。',
       2, '习题册', '把“先修课”换成“系主任”，讨论自引用外码的空值与取值。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0224', 1, 'KP0206', '简答',
       '试述关系模型的完整性约束，并说明在参照完整性中，什么情况下外码属性的值可以为空值。',
       NULL,
       '关系模型有三类完整性约束：实体完整性、参照完整性和用户定义的完整性。实体完整性与参照完整性是关系模型必须满足的约束，称为关系的两个不变性，应由关系系统自动支持；用户定义的完整性针对具体应用。参照完整性允许外码取空值，条件是 F 的每个属性值均为空值（不能部分为空），此时表示该元组在外码上的取值“不知道”或“不存在”，例如某门课程没有先修课、新教师尚未确定所属系；若外码取非空值，则必须等于被参照关系中某个元组的主码值。',
       '答案须同时给出三类约束、两个不变性、外码空值的“全空”条件和非空时的取值要求，只答“可以为空”会漏掉“每个属性值均为空”。',
       2, '教材', '把“外码可为空”改成“外码能否部分为空”，要求举例判断。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0225', 1, 'KP0202', '简答',
       '设 D1={男,女}，D2={0,1,2,…,100}（共 101 个整数），D3={20180001,20180002,20180003}。求 D1×D2×D3 的基数；并说明能否把该笛卡儿积直接当作一个关系使用。',
       NULL,
       '基数 M=2×101×3=606。不能直接当作关系使用：笛卡儿积中的元组大多没有实际语义（如“男,0,20180002”），关系模型中的关系是笛卡儿积的有限子集，只取有实际含义的元组；同时还要为每一列命名属性（如性别、年龄、学号），因为域可以相同，不加属性名无法区分列。',
       '基数按各域基数相乘；易错点是把 0～100 当成 100 个数而算得 600。第二问要落到“关系是子集”和“n 目关系必有 n 个属性”两点。',
       3, '教材', '把 D2 改为 {0,1,…,99}，再求基数并说明关系应如何取名。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0226', 1, 'KP0209', '简答',
       '设关系 S(A,B,C,D) 有 N1 个元组，关系 T(C,D,E,F) 有 N2 个元组，且 N2>N1>0。分别写出 S∪T、S∩T、S−T、S×T 在一般情况下可得到的最大、最小元组数及结果列数。',
       NULL,
       'S∪T：最大 N1+N2，最小 N2，4 列；S∩T：最大 N1，最小 0，4 列；S−T：最大 N1，最小 0，4 列；S×T：最大 N1×N2，最小 N1×N2，8 列。',
       '并、差、交要求同目且相应属性同域，列数保持 4 不变。并的最小值出现在 S⊆T 时，结果为 T 的 N2 个元组；差的最小值出现在 S⊆T 时为空；笛卡儿积元组数恒为乘积，列数为 4+4=8。',
       2, '习题册', '改为 N1>N2，再问 R∪S 的最小元组数与 R∩S 的最大元组数。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0227', 1, 'KP0210', '简答',
       '设 Teacher(Tno,Tname,Tage,Tsex)、Department(Dno,Dname,DTno)、Work(Tno,Dno,Year,Salary)。用关系代数表示：(a) 工资超过 5000 元的教师的不同年龄；(b) 不在计算机系工作的教师代码。',
       NULL,
       '(a) π_Tage(Teacher ⋈ σ_Salary>5000(Work))；(b) π_Tno(Teacher) − π_Tno(Work ⋈ π_Dno(σ_Dname=''计算机系''(Department)))。',
       '(a) 先选出工资大于 5000 的 Work 元组，与 Teacher 自然连接后只在 Tage 上投影，投影自动取消重复年龄，故得到“不同年龄”。(b) 先求出计算机系的系代码集合，再求在该系工作的教师代码，最后用全体教师代码做差；漏掉最后的差运算就得不到“不在”的语义。',
       2, '习题册', '把 (b) 改成“不在计算机系且尚未参加任何工作的教师代码”。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0228', 1, 'KP0210', '简答',
       '已知 Teacher：T1 张丽 42 女、T2 李波 45 男、T3 王艳 33 女、T4 赵明 29 男；Department：D1 计算机系（系主任 T1）、D2 数学系（T2）、D3 电子系（NULL）；Work：T1 在 D1、1995 年、6000 元，T2 在 D2、1992 年、6500 元，T3 在 D1、2005 年、4500 元。用左外连接写出“所有教师的姓名及其所在系名”的关系代数表达式，并给出结果。',
       NULL,
       'π_Tname,Dname(Teacher ⟕ (Work ⋈ Department))；结果为：张丽—计算机系、李波—数学系、王艳—计算机系、赵明—NULL。',
       '先把 Work 与 Department 自然连接得到教师所在系，再与 Teacher 做左外连接。左外连接保留左边关系 Teacher 的全部元组，T4 赵明没有工作记录，其 Dname 补空值，因此仍出现在结果中且系名为 NULL；若改用内连接，赵明会被丢掉。',
       3, '习题册', '改为右外连接，说明结果中为什么会出现“NULL—电子系”。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0229', 1, 'KP0210', '简答',
       'SPJ 数据库：S(SNO,SNAME,STATUS,CITY)、P(PNO,PNAME,COLOR,WEIGHT)、J(JNO,JNAME,CITY)、SPJ(SNO,PNO,JNO,QTY)。用关系代数表示：① 供应工程 J1 零件的供应商代码 SNO；② 供应工程 J1 零件 P1 的供应商代码 SNO；③ 供应工程 J1 红色零件的供应商代码 SNO。',
       NULL,
       '① π_SNO(σ_JNO=''J1''(SPJ))；② π_SNO(σ_JNO=''J1'' ∧ PNO=''P1''(SPJ))；③ π_SNO(σ_JNO=''J1''(SPJ) ⋈ σ_COLOR=''红''(P))。',
       '①② 的条件都只涉及 SPJ 一张表，可直接在选择中并列；③ 需先分别选出 J1 的供应记录和红色零件，再按公共属性 PNO 自然连接，最后投影 SNO。漏掉与 P 的连接就无法判断零件颜色，漏掉与 SPJ 的连接则无法判断供应工程。',
       3, '教材', '再加一小题“没有使用天津供应商生产的红色零件的工程号 JNO”。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0230', 1, 'KP0210', '简答',
       '关系 R(A,B,C) 的数据为 (a1,b1,c2)、(a2,b3,c7)、(a3,b4,c6)、(a1,b2,c3)、(a4,b6,c6)、(a2,b2,c3)、(a1,b2,c1)；关系 S(B,C,D) 的数据为 (b1,c2,d1)、(b2,c1,d1)、(b2,c3,d2)。求 R÷S，并说明判断过程。',
       NULL,
       'R÷S={a1}。S 在 (B,C) 上的投影为 {(b1,c2),(b2,c1),(b2,c3)}；a1 的象集 {(b1,c2),(b2,c1),(b2,c3)} 包含该投影，a2 的象集 {(b3,c7),(b2,c3)}、a3 的象集 {(b4,c6)}、a4 的象集 {(b6,c6)} 都不包含该投影，故只有 a1 满足条件，结果为 {a1}。',
       '除运算按“象集是否包含 S 在公共属性上的投影”逐值判断，结果属性为 R 中去掉 S 属性后的 X（此处为 A）。易错点是只检查公共属性是否出现过，而不检查是否覆盖 S 的全部 (B,C) 组合。',
       3, '教材', '把 S 改为只含 (b2,c3) 一个元组，再求 R÷S 并说明结果变化。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0301', 1, 'KP0301', '单选',
       '下列关于 SQL 语言特点的叙述中，正确的是（ ）。',
       '[{"key":"A","text":"SQL 只有数据查询和数据操纵两类功能，不包含数据定义与数据控制"},{"key":"B","text":"SQL 是过程化语言，用户必须指出数据的存取路径"},{"key":"C","text":"SQL 是非过程化语言，用户只说明\\"要什么\\"，存取路径由 DBMS 自动选择"},{"key":"D","text":"嵌入式 SQL 与独立使用的 SQL 语法差别很大，需另学一套语法"}]',
       'C',
       'SQL 集数据定义、数据查询、数据操纵、数据控制于一体，故 A 错；它只描述目标而不描述过程，存取路径由 DBMS 决定，故 B 错、C 对；独立使用与嵌入式使用采用统一的语法结构，只是嵌入式需加主语言接口，故 D 错。',
       2, '习题册 P38 补充习题 选择①（改写）', '把选项改为"核心功能只用 9 个动词"判断正误。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0302', 1, 'KP0302', '单选',
       '在 SQL 中，删除表中已有数据但保留该表结构，应使用的语句是（ ）。',
       '[{"key":"A","text":"DELETE"},{"key":"B","text":"DROP TABLE"},{"key":"C","text":"ALTER TABLE"},{"key":"D","text":"CREATE TABLE"}]',
       'A',
       'DELETE 只删除元组，表定义仍保存在数据字典中；DROP TABLE 会把表定义连同数据一起删除；ALTER TABLE 用于修改表结构，CREATE TABLE 用于建表，三者都不符合"只删数据"的要求。',
       1, '习题册 P38 补充习题 选择②', '改问"删除表及其定义用哪条语句"。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0303', 1, 'KP0302', '单选',
       '在数据库中建立索引的主要目的是（ ）。',
       '[{"key":"A","text":"加快建表速度"},{"key":"B","text":"加快查询（存取）速度"},{"key":"C","text":"提高数据安全性"},{"key":"D","text":"节省存储空间"}]',
       'B',
       '索引按指定列建立额外的存取路径，以增加存储空间和维护代价换取查询速度的提升。索引既不负责建表，也不承担安全性控制，反而占用更多空间，故 A、C、D 均错。',
       1, '习题册 P38 补充习题 选择③', '改问 UNIQUE 索引与 CLUSTER 索引的区别。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0304', 1, 'KP0301', '单选',
       '在 SQL 支持的三级模式结构中，视图对应于（ ）。',
       '[{"key":"A","text":"外模式"},{"key":"B","text":"模式"},{"key":"C","text":"内模式"},{"key":"D","text":"模式映像"}]',
       'A',
       'SQL 支持三级模式：外模式对应视图和部分基本表，模式对应基本表，内模式对应存储文件。模式映像只是层次之间的转换规则，不是某一级模式本身，故 D 错。',
       1, '习题册 P38 补充习题 选择④', '改问"基本表对应哪一级模式"。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0305', 1, 'KP0309', '单选',
       '下列关于基本表和视图的叙述中，不正确的是（ ）。',
       '[{"key":"A","text":"基本表和视图都是关系"},{"key":"B","text":"可以用 SQL 对基本表和视图进行查询等操作"},{"key":"C","text":"可以在基本表或已定义的视图之上再定义视图"},{"key":"D","text":"基本表和视图在数据库中都存储了各自对应的数据"}]',
       'D',
       '视图是从基本表（或其他视图）导出的虚表，数据库中只保存视图定义，不保存视图数据，数据仍在导出它的基本表中，故 D 不正确。A、B、C 均符合教材对视图的说明。',
       2, '习题册 P38 补充习题 选择⑤', '改问"视图的数据存放在哪里"。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0306', 1, 'KP0308', '单选',
       '按 SQL 的三值逻辑，当 Grade 取值为空值时，条件表达式 `Grade > 60 OR FALSE` 的值为（ ）。',
       '[{"key":"A","text":"TRUE"},{"key":"B","text":"UNKNOWN"},{"key":"C","text":"FALSE"},{"key":"D","text":"NULL"}]',
       'B',
       '空值参与比较运算得到 UNKNOWN；按表 3.7，UNKNOWN OR FALSE 的结果仍为 UNKNOWN，只有 UNKNOWN OR TRUE 才为 TRUE，故 A 错、B 对。C 只在两侧均为 FALSE 时成立；NULL 是数据值而不是真值，故 D 错。',
       2, '教材 P113 表 3.7 三值逻辑真值表（改写）', '把 OR FALSE 换成 AND FALSE 再判断。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0307', 1, 'KP0305', '单选',
       '关于嵌套查询，下列说法正确的是（ ）。',
       '[{"key":"A","text":"子查询的 SELECT 语句中可以写 ORDER BY，先对子查询结果排序"},{"key":"B","text":"不相关子查询和相关子查询都只需由内向外求值一次"},{"key":"C","text":"子查询返回多个值时，必须使用 ANY（或 SOME）或 ALL 并同时配合比较运算符"},{"key":"D","text":"EXISTS 谓词返回子查询的结果集，可以再与 =、> 等比较运算符连用"}]',
       'C',
       '子查询中不能使用 ORDER BY，排序只能对最终结果进行，A 错；相关子查询依赖父查询的元组变量，必须反复求值，B 错；EXISTS 只产生 true/false，不返回结果集，D 错；返回多值必须用 ANY/ALL 且前面必须带比较运算符，C 对。',
       3, '教材 P99—P105 嵌套查询与 ANY/ALL、EXISTS（改写）', '改问 `<> ALL` 与 `NOT IN` 的等价关系。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0308', 1, 'KP0304', '单选',
       '下列连接操作中，结果里可能出现悬浮元组（未匹配的元组用空值补齐）的是（ ）。',
       '[{"key":"A","text":"等值连接"},{"key":"B","text":"自然连接"},{"key":"C","text":"自身连接"},{"key":"D","text":"左外连接"}]',
       'D',
       '等值连接、自然连接、自身连接都只保留满足连接条件的匹配元组，不匹配的元组被丢弃；外连接才用空值填补未匹配元组，左外连接列出左关系的全部元组，故只有 D 符合。',
       2, '教材 P94—P96 外连接（改写）', '改问"右外连接列出哪一侧的全部元组"。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0309', 1, 'KP0306', '单选',
       '对两个 SELECT 语句的结果做并操作，若要求保留重复元组，应使用（ ）。',
       '[{"key":"A","text":"UNION ALL"},{"key":"B","text":"UNION"},{"key":"C","text":"INTERSECT"},{"key":"D","text":"EXCEPT"}]',
       'A',
       'UNION 合并时由系统自动去掉重复元组，UNION ALL 才保留重复元组。INTERSECT 求交、EXCEPT 求差，都不是并操作，故 B、C、D 错。',
       1, '教材 P105—P106 集合查询（改写）', '改问"参加集合操作的各结果必须满足什么条件"。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0310', 1, 'KP0307', '单选',
       '执行语句 `DELETE FROM Student;` 后，结果是（ ）。',
       '[{"key":"A","text":"Student 表连同其定义一起被删除"},{"key":"B","text":"只删除 Sno 取空值的那些元组"},{"key":"C","text":"Student 表的全部元组被删除，表定义仍保存在数据字典中"},{"key":"D","text":"语句语法错误，DELETE 必须带 WHERE 子句"}]',
       'C',
       'DELETE 省略 WHERE 表示删除表中所有元组，删除的是数据而不是表的定义，表定义仍在数据字典中，故 C 对、A 错；该语句与空值无关，B 错；WHERE 子句可省略，D 错。',
       2, '教材 P110—P111 删除数据（改写）', '改问"删除表定义应使用哪条语句"。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0311', 1, 'KP0303', '填空',
       '在 SELECT 语句中若省略 DISTINCT 关键字，则默认使用关键字____，即查询结果中允许出现重复元组。',
       NULL,
       'ALL',
       'SELECT 的一般格式为 `SELECT [ALL|DISTINCT] <目标列表达式>`，不写 DISTINCT 时默认为 ALL，保留重复行；只有显式写 DISTINCT 才消除重复元组。',
       1, '教材 P81 SELECT 语句一般格式（改写）', '改问"消除重复元组用哪个关键字，它作用于哪些列"。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0312', 1, 'KP0306', '填空',
       '在 FROM 子句中使用子查询生成派生表时，必须为派生表指定____；若子查询的目标列中含有聚集函数，还必须为派生表指定____。',
       NULL,
       '别名（表别名）；属性列名',
       '派生表是查询过程中生成的中间结果表，必须用别名才能在主查询中引用；含聚集函数时目标列没有可继承的列名，必须显式给出属性列名，否则无法引用该列。',
       2, '教材 P107 基于派生表的查询（改写）', '改问"派生表在查询结束后是否保留"。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0313', 1, 'KP0308', '填空',
       '空值与另一个值（包括另一个空值）的比较运算结果为____；判断某属性是否取空值应使用____谓词，而不能用等号。',
       NULL,
       'UNKNOWN；IS NULL',
       '空值代表"不知道""不存在"或"无意义"，参与比较时无法判定真假，结果为 UNKNOWN；SQL 用 IS NULL / IS NOT NULL 专门判断空值，`= NULL` 的比较结果恒为 UNKNOWN，起不到筛选作用。',
       2, '教材 P111—P113 空值处理（改写）', '改问"NOT UNKNOWN 的结果是什么"。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0314', 1, 'KP0309', '填空',
       '若一个视图是从单个基本表导出的，并且只去掉了基本表的某些行和某些列，但保留了主码，这类视图称为____视图，它一般是可更新的。',
       NULL,
       '行列子集视图',
       '行列子集视图在行、列两个方向上都只是基本表的子集且保留主码，因而更新可唯一确定基本表中的元组，一般能正确转换为对基本表的更新；分组视图、带表达式的视图则不然。',
       1, '习题册 P38 补充习题 填空③', '改问"分组视图为什么必须显式指定列名"。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0315', 1, 'KP0310', '简答',
       '简述视图的四点作用，并各用一句话说明其含义。',
       NULL,
       '① 对机密数据提供安全保护：为不同用户定义不同视图，使机密数据不出现在无权查看者的视图上；② 对重构数据库提供一定程度的逻辑独立性：基本表增加字段或垂直拆分后，可通过重新定义视图保持原有逻辑结构，应用程序不必修改；③ 简化用户操作：把多表连接等复杂操作对用户隐蔽起来，用户只面对一个视图；④ 使用户能以多种角度看待同一数据：同一基本表可按不同需求导出不同视图。',
       '四点分别对应安全性、逻辑独立性、操作简化和多视角，答题时不能只答"方便查询"而漏掉安全保护与逻辑独立性。视图只是提供了"一定程度"的逻辑独立性，并非完全独立。',
       1, '教材习题 3 第 7 题（P123）+ 教材 P121—P122', '改问"用 Student 按院系建 30 个视图说明哪一点作用"。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0316', 1, 'KP0307', '简答',
       'INSERT INTO 语句有"在 INTO 中指定属性列"和"不指定属性列"两种写法，分别说明两种写法下 VALUES 子句必须满足的要求。',
       NULL,
       '指定属性列时，INTO 中未出现的列取空值（有默认值则取默认值），但这些列不能是 NOT NULL 列，因此 NOT NULL 列必须在 INTO 中列出并给值，且 VALUES 的常量个数、次序要与所列属性列一一对应。不指定属性列时，VALUES 必须按 CREATE TABLE 中属性列的定义次序，为每一个属性列给出值，一个也不能省略。',
       '两种写法的差别在于"属性列清单"是否与 VALUES 对齐；漏列 NOT NULL 列或漏给某列赋值都会因违反完整性约束而插入失败。',
       2, '教材习题 3 第 5 题⑪及习题册 P36 解答（改写）', '改问"用 INSERT ... SELECT 一次插入多个元组时的注意点"。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0317', 1, 'KP0304', '简答',
       '说明等值连接与自然连接的区别；并解释自身连接为什么必须为同一张表取两个不同的别名。',
       NULL,
       '等值连接以"="为连接运算符，结果表中保留两个表的全部属性列，包括重复的连接属性列；自然连接是在等值连接的基础上去掉结果表中重复的属性列，如 Student 与 SC 按 Sno 自然连接后只保留一个 Sno。自身连接是把一张表与其自身连接，若不取两个别名，DBMS 无法区分两个副本，也无法在连接条件中分别引用它们的属性，如 `Course FIRST, Course SECOND`。',
       '自然连接 = 等值连接 + 去掉重复列，二者结果列数不同；自身连接的关键是别名，连接条件中必须用别名前缀区分两个副本。',
       2, '教材 P92—P94 等值连接、自然连接与自身连接（改写）', '改问"两表同名列在连接条件中为什么必须加表名前缀"。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0318', 1, 'KP0308', '简答',
       'SC 表中某学生某门课缺考，Grade 取空值。请说明为什么 `SELECT * FROM SC WHERE Grade < 60;` 查不出该缺考记录，并给出能查出"缺考"记录的写法。',
       NULL,
       '空值参与比较运算的结果是 UNKNOWN，而不是 TRUE 或 FALSE；WHERE 子句只选出条件取值为 TRUE 的元组，UNKNOWN 与 FALSE 一样不被选出，因此 `Grade < 60` 查不到缺考记录。应改用 `SELECT * FROM SC WHERE Grade IS NULL;` 专门判断空值，若要连同不及格一起查，可写 `WHERE Grade < 60 OR Grade IS NULL`。',
       '本题的关键是把二值逻辑扩展为三值逻辑：TRUE / FALSE / UNKNOWN，只有 TRUE 能通过 WHERE。用 `Grade = NULL` 或 `Grade <> NULL` 同样得到 UNKNOWN，仍然查不出记录。',
       3, '教材 P111—P114 空值与三值逻辑；习题册 P37 补充习题 10（改写）', '改问"`NOT (Grade >= 60)` 能否查出缺考记录"。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0319', 1, 'KP0305', '简答',
       '解释相关子查询与不相关子查询的区别，并说明二者在求解方式上的不同。',
       NULL,
       '若子查询的查询条件不依赖于父查询，称为不相关子查询；若子查询的查询条件依赖于父查询的某个属性值，则称为相关子查询。不相关子查询由里向外分步求解，先一次求出子查询结果，再用它求解父查询；相关子查询的查询条件中含有父查询的元组变量，父查询每取一个元组就要重新求值一次子查询，必须反复求值。',
       '判断依据是"子查询是否引用父查询的属性"；由此决定执行方式是一次求值还是反复求值。例：`WHERE Sno IN (SELECT Sno FROM SC WHERE Cno=''81001'')` 为不相关子查询，`WHERE Grade > (SELECT AVG(Grade) FROM SC y WHERE y.Sno=x.Sno)` 为相关子查询。',
       2, '习题册 P38 补充习题 问答①（答案见 P39）', '改问"相关子查询能否用连接查询改写"。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0320', 1, 'KP0309', '简答',
       '哪类视图一般是可以更新的？哪类视图是不可更新的？各举一例说明。',
       NULL,
       '基本表的行列子集视图一般是可更新的，例如由 Student 按专业导出的 IS_Student 视图，其行、列均为基本表的子集且保留了主码，对它的更新可转换为对 Student 的更新。属性来自聚集函数或表达式的视图不可更新，例如 `S_GradeAVG(Sno,Gavg)` 由 `SELECT Sno,AVG(Grade) FROM SC GROUP BY Sno` 定义，对 Gavg 的修改无法唯一对应到基本表的元组；由 `Sno,Sname,(...extract...)` 定义的 S_AGE 视图同理。',
       '判断依据是视图能否唯一映射回基本表的元组。还需区分"不可更新视图"与"不允许更新视图"：前者理论上已证明不可更新，后者由各 DBMS 的产品规定决定。',
       1, '教材习题 3 第 8 题（P123）+ 习题册 P36 解答', '改问"含 GROUP BY 的视图为什么不可更新"。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0321', 1, 'KP0303', '编程',
       '设供应商表 S(SNO, SNAME, STATUS, CITY)、零件表 P(PNO, PNAME, COLOR, WEIGHT)、工程项目表 J(JNO, JNAME, CITY)、供应情况表 SPJ(SNO, PNO, JNO, QTY)。写出找出所有供应商的姓名和所在城市的 SQL 语句。',
       NULL,
       'SELECT SNAME, CITY
  FROM S;',
       '查询只涉及 S 一张表，目标列为 SNAME 与 CITY，无须 WHERE 条件；这是最基本的投影操作。',
       1, '教材习题 3 第 5 题①（P123），答案见习题册 P34', '改问"只查 CITY 并去掉重复值"。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0322', 1, 'KP0303', '编程',
       '设 Student(Sno, Sname, Ssex, Sbirthdate, Smajor)、Course(Cno, Cname, Ccredit, Cpno)、SC(Sno, Cno, Grade, Semester, Teachingclass)。写出查询课程号为 ''81001'' 且成绩在 80 至 90 分之间（含 80 和 90）的选课记录，输出学号、课程号和成绩，并按成绩从高到低排列的 SQL 语句。',
       NULL,
       'SELECT Sno, Cno, Grade
  FROM SC
  WHERE Cno = ''81001''
    AND Grade BETWEEN 80 AND 90
  ORDER BY Grade DESC;',
       'BETWEEN 80 AND 90 等价于 `Grade >= 80 AND Grade <= 90`，两端均包含；ORDER BY 默认 ASC，降序须写 DESC。ORDER BY 只改变结果输出顺序，不改变基本表的存储顺序。',
       2, '教材 P83—P87 单表查询（BETWEEN、ORDER BY），按教材表结构改写', '改问"成绩不在 80～90 之间"（NOT BETWEEN）。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0323', 1, 'KP0303', '编程',
       '设 Student(Sno, Sname, Ssex, Sbirthdate, Smajor)、Course(Cno, Cname, Ccredit, Cpno)、SC(Sno, Cno, Grade, Semester, Teachingclass)。写出按课程分组统计每门课程的选课人数和平均成绩，只输出选课人数不少于 3 人且平均成绩不低于 75 分的课程号、人数和平均成绩的 SQL 语句。',
       NULL,
       'SELECT Cno, COUNT(*) AS Cnt, AVG(Grade) AS AvgGrade
  FROM SC
  GROUP BY Cno
  HAVING COUNT(*) >= 3 AND AVG(Grade) >= 75;',
       '分组统计用 GROUP BY Cno；对"组"的筛选条件必须写在 HAVING 中，不能写在 WHERE 中，因为 WHERE 作用于元组且不允许直接使用聚集函数。选课人数用 COUNT(*) 统计全部选课记录，不受 Grade 为空值影响。',
       2, '教材 P89—P91 聚集函数与 GROUP BY / HAVING（改写）', '改问"统计每门课有成绩的人数"（应改用 COUNT(Grade)）。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0324', 1, 'KP0303', '编程',
       '设 Student(Sno, Sname, Ssex, Sbirthdate, Smajor)、Course(Cno, Cname, Ccredit, Cpno)、SC(Sno, Cno, Grade, Semester, Teachingclass)。写出查询 2000 年 1 月 1 日至 2001 年 12 月 31 日出生、且专业为"计算机科学与技术"或"信息安全"的学生的学号、姓名、出生日期和主修专业，并要求出生日期早的排在前的 SQL 语句。',
       NULL,
       'SELECT Sno, Sname, Sbirthdate, Smajor
  FROM Student
  WHERE Sbirthdate BETWEEN ''2000-01-01'' AND ''2001-12-31''
    AND Smajor IN (''计算机科学与技术'', ''信息安全'')
  ORDER BY Sbirthdate ASC;',
       '日期范围用 BETWEEN 表达，两端包含；集合成员判断用 IN，比两个 OR 更简洁。AND 的优先级高于 OR，本题用 IN 后不存在优先级歧义；若改写成 OR 形式必须加括号。ORDER BY Sbirthdate 默认升序，即年龄大的在前。',
       2, '教材 P85—P86 字符匹配、确定范围与 ORDER BY（改写）', '改问"专业不是这两个之一"（NOT IN）。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0325', 1, 'KP0305', '编程',
       '设 Student(Sno, Sname, Ssex, Sbirthdate, Smajor)、Course(Cno, Cname, Ccredit, Cpno)、SC(Sno, Cno, Grade, Semester, Teachingclass)。写出查询选修了全部课程的学生姓名的 SQL 语句，要求用 NOT EXISTS 实现，不得用 COUNT 比较课程门数。',
       NULL,
       'SELECT Sname
  FROM Student
  WHERE NOT EXISTS
        (SELECT *
         FROM Course
         WHERE NOT EXISTS
               (SELECT *
                FROM SC
                WHERE Sno = Student.Sno
                  AND Cno = Course.Cno));',
       'SQL 没有全称量词，按 (∀x)P ≡ ¬(∃x(¬P)) 转换：查询"没有一门课程是他没选的"学生。外层 NOT EXISTS 对每个学生判断，内层 NOT EXISTS 判断该学生是否缺少某门课，两重否定后即为选修全部课程的学生。用 COUNT 比较门数依赖数据一致性，且题目限定不得使用。',
       3, '教材 P104 例 3.64（求选修全部课程的学生姓名）', '改问"至少选修了 20180002 所选全部课程的学生学号"（例 3.65，注意 DISTINCT 与自连接别名）。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0326', 1, 'KP0305', '编程',
       '设 Student(Sno, Sname, Ssex, Sbirthdate, Smajor)、Course(Cno, Cname, Ccredit, Cpno)、SC(Sno, Cno, Grade, Semester, Teachingclass)。写出查询每个学生成绩高于其本人平均成绩的学号和课程号的 SQL 语句，要求用相关子查询实现。',
       NULL,
       'SELECT Sno, Cno
  FROM SC x
  WHERE Grade > (SELECT AVG(Grade)
                 FROM SC y
                 WHERE y.Sno = x.Sno);',
       '外层 SC 取别名 x，内层 SC 取别名 y，内层通过 `y.Sno = x.Sno` 引用外层当前元组的学号，因此是相关子查询，父查询每取一个元组，子查询就要重新求值一次。别名 x、y 称为元组变量，缺一不可，否则无法区分内外两层的 SC。',
       3, '教材 P99—P100 例 3.59（相关子查询）', '改问"成绩不低于本人平均成绩"（把 > 改为 >=）。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0327', 1, 'KP0305', '编程',
       '设供应商表 S(SNO, SNAME, STATUS, CITY)、零件表 P(PNO, PNAME, COLOR, WEIGHT)、工程项目表 J(JNO, JNAME, CITY)、供应情况表 SPJ(SNO, PNO, JNO, QTY)。写出找出没有使用天津产的零件的工程项目代码的 SQL 语句。',
       NULL,
       'SELECT JNO
  FROM J
  WHERE NOT EXISTS
        (SELECT *
         FROM SPJ, S
         WHERE SPJ.JNO = J.JNO
           AND SPJ.SNO = S.SNO
           AND S.CITY = ''天津'');',
       '思路是"不存在这样的供应记录：该工程使用了天津供应商供应的零件"。外层遍历 J，内层用 SPJ 与 S 连接查出天津供应商给该工程供货的记录；NOT EXISTS 在内层为空时返回真，即该工程没用过天津零件。也可先求天津供应商的 SNO 集合再用 NOT IN。',
       3, '教材习题 3 第 5 题⑦（P123），答案见习题册 P35', '改问"找出使用了天津产零件的工程名称"（去掉 NOT，并按 JNO 连接 J 取 JNAME）。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0328', 1, 'KP0308', '编程',
       '设 Student(Sno, Sname, Ssex, Sbirthdate, Smajor)、Course(Cno, Cname, Ccredit, Cpno)、SC(Sno, Cno, Grade, Semester, Teachingclass)，其中 Grade 允许取空值表示缺考未录入。写出查询所有成绩为空值的选课记录（输出学号、课程号、学期）的 SQL 语句，并说明为什么 `Grade < 60` 查不到这些记录。',
       NULL,
       'SELECT Sno, Cno, Semester
  FROM SC
  WHERE Grade IS NULL;',
       '判断空值只能用 IS NULL，写成 `Grade = NULL` 的比较结果为 UNKNOWN，起不到筛选作用。`Grade < 60` 对空值同样求值为 UNKNOWN，而 WHERE 只保留条件为 TRUE 的元组，所以缺考记录不会被选出；若要一并查出不及格，须写 `WHERE Grade < 60 OR Grade IS NULL`。',
       2, '教材 P111—P114 空值处理；习题册 P37 补充习题 10（改写）', '改问"成绩非空且低于 60 分"（`Grade IS NOT NULL AND Grade < 60`）。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0329', 1, 'KP0309', '编程',
       '设 Student(Sno, Sname, Ssex, Sbirthdate, Smajor)、Course(Cno, Cname, Ccredit, Cpno)、SC(Sno, Cno, Grade, Semester, Teachingclass)。先建立视图 S_GradeAVG(Sno, Gavg)，存放每个学生的学号及平均成绩；再查询平均成绩不低于 90 分的学生学号和平均成绩。',
       NULL,
       'CREATE VIEW S_GradeAVG(Sno, Gavg)
  AS
  SELECT Sno, AVG(Grade)
  FROM SC
  GROUP BY Sno;

  SELECT Sno, Gavg
  FROM S_GradeAVG
  WHERE Gavg >= 90;',
       '该视图的目标列含聚集函数，属于分组视图，必须在视图名后显式指定列名，否则第二列没有可继承的名字。这种视图不可更新，但可以查询：查询时系统取出视图定义做视图消解，把条件转换为对 SC 的分组查询。',
       2, '教材 P120 例 3.91（分组视图）', '改问"不建视图直接用 GROUP BY + HAVING 写出等价查询"。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

INSERT INTO question_bank (q_id, course_id, primary_kp_id, question_type, question_text, options_json, answer_text, analysis_text, difficulty, source, variant_hint)
VALUES ('Q_0330', 1, 'KP0310', '编程',
       '设 Student(Sno, Sname, Ssex, Sbirthdate, Smajor)。为使"计算机科学与技术"专业的系主任只能看到本专业学生，请建立视图 CS_Student（仅含学号、姓名、性别、出生日期四列），写出建立该视图并查询其中全部记录的 SQL 语句，并说明它体现的视图作用。',
       NULL,
       'CREATE VIEW CS_Student
  AS
  SELECT Sno, Sname, Ssex, Sbirthdate
  FROM Student
  WHERE Smajor = ''计算机科学与技术'';

  SELECT *
  FROM CS_Student;',
       '视图只暴露本专业的行和部分列，其他专业的数据不出现在该视图上，因此对机密数据起到安全保护作用。该视图是从单个基本表导出、去掉部分行列但保留主码 Sno 的行列子集视图，一般是可更新的；若再加 WITH CHECK OPTION，可防止通过视图插入非本专业的记录。',
       1, '教材 P115 例 3.86（行列子集视图），按"安全保护"作用改写', '改问"加上 WITH CHECK OPTION 后，插入其他专业学生会怎样"。')
ON DUPLICATE KEY UPDATE primary_kp_id = VALUES(primary_kp_id), question_type = VALUES(question_type), question_text = VALUES(question_text), options_json = VALUES(options_json), answer_text = VALUES(answer_text), analysis_text = VALUES(analysis_text), difficulty = VALUES(difficulty), source = VALUES(source), variant_hint = VALUES(variant_hint);

-- 关联关系以本文件为准：先清掉第2—3章题目的旧关联，避免改挂知识点后残留旧边
DELETE FROM question_kp WHERE q_id LIKE 'Q_02%' OR q_id LIKE 'Q_03%';

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
