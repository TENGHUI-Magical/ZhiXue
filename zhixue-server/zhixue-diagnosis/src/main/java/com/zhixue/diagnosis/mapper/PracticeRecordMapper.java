package com.zhixue.diagnosis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixue.diagnosis.entity.PracticeRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 练习作答记录 Mapper（MyBatis-Plus BaseMapper）。
 */
public interface PracticeRecordMapper extends BaseMapper<PracticeRecord> {

    /**
     * 按课程过滤的近期已练题目（05 §3.3 冷却：7 天 / 3 天）。
     * practice_record 不冗余 course_id，用 question_bank 关联过滤，SQL 全参数绑定。
     *
     * @param userId   学生 user_id
     * @param courseId 课程 ID
     * @param since    起始时间（created_at >= since）
     * @return 去重后的题目编号
     */
    @Select("""
            SELECT DISTINCT pr.q_id
            FROM practice_record pr
            JOIN question_bank qb ON qb.q_id = pr.q_id
            WHERE pr.user_id = #{userId}
              AND qb.course_id = #{courseId}
              AND pr.created_at >= #{since}
            """)
    List<String> selectPracticedQids(@Param("userId") long userId,
                                     @Param("courseId") long courseId,
                                     @Param("since") LocalDateTime since);
}
