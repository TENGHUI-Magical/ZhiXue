-- 智学领航 · 第8章题库种子（由 csv_to_sql.py 生成）
-- 前置：init-v2.sql、seed-kp-ch08.sql；可重复执行（upsert）。
USE zhixue;
SET NAMES utf8mb4;

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

