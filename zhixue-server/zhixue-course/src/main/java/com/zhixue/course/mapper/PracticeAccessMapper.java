package com.zhixue.course.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 作答回执查询（P0 修复：答案接口访问控制用）。
 * 查询 practice_record 表判断学生是否已对某题提交非空作答。
 * 跨模块只读查询，不引入 diagnosis 模块 Java 依赖。
 */
@Mapper
public interface PracticeAccessMapper {

    /**
     * 统计用户对某题的非空作答记录数。
     * 仅统计 my_answer 非空且非纯空白的记录，防止空作答绕过。
     */
    @Select("SELECT COUNT(*) FROM practice_record WHERE user_id = #{userId} AND q_id = #{qId} "
            + "AND my_answer IS NOT NULL AND TRIM(my_answer) <> ''")
    int countSubmitted(@Param("userId") long userId, @Param("qId") String qId);
}
