-- 智学领航 · 第8章知识点种子（由 csv_to_sql.py 生成）
-- 前置：init-v2.sql；可重复执行（upsert）。
USE zhixue;
SET NAMES utf8mb4;

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

