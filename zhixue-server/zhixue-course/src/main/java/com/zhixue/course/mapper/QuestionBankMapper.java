package com.zhixue.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixue.course.entity.QuestionBank;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 题库 Mapper（MyBatis-Plus BaseMapper）。
 */
public interface QuestionBankMapper extends BaseMapper<QuestionBank> {

    /**
     * 主知识点 + question_kp 多对多关联的并集查询（05 §3.1 变式/拓展池）。
     * 参数一律 #{} 绑定，禁止拼接（安全约束）。
     */
    @Select("SELECT qb.* FROM question_bank qb "
            + "LEFT JOIN question_kp qk ON qk.q_id = qb.q_id "
            + "WHERE qb.course_id = #{courseId} AND qb.is_active = 1 "
            + "AND (qb.primary_kp_id = #{kpId} OR qk.kp_id = #{kpId}) "
            + "GROUP BY qb.q_id "
            + "ORDER BY qb.difficulty ASC, qb.q_id ASC")
    List<QuestionBank> selectByKpIncludeSecondary(@Param("courseId") Long courseId,
                                                  @Param("kpId") String kpId);
}
