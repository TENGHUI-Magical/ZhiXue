package com.zhixue.diagnosis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixue.diagnosis.dto.UserKpAgg;
import com.zhixue.diagnosis.dto.UserPracticeAgg;
import com.zhixue.diagnosis.entity.PracticeRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 练习作答记录 Mapper（MyBatis-Plus BaseMapper）。
 */
public interface PracticeRecordMapper extends BaseMapper<PracticeRecord> {

    /**
     * 按课程过滤的近期已练题目（05 §3.3 冷却：7 天 / 3 天）。
     * practice_record 不冗余 course_id，用 question_bank 关联过滤，SQL 全参数绑定。
     * LEFT JOIN + qb.q_id IS NULL：P4 自录题可能尚未入库，未知课程归属时保守纳入冷却，
     * 避免因 join 丢弃而漏掉已练题。
     *
     * @param userId   学生 user_id
     * @param courseId 课程 ID
     * @param since    起始时间（created_at >= since）
     * @return 去重后的题目编号
     */
    @Select("""
            SELECT DISTINCT pr.q_id
            FROM practice_record pr
            LEFT JOIN question_bank qb ON qb.q_id = pr.q_id
            WHERE pr.user_id = #{userId}
              AND pr.created_at >= #{since}
              AND (qb.course_id = #{courseId} OR qb.q_id IS NULL)
            """)
    List<String> selectPracticedQids(@Param("userId") long userId,
                                     @Param("courseId") long courseId,
                                     @Param("since") LocalDateTime since);

    /**
     * 按用户聚合练习统计（C12 学生列表；SQL GROUP BY，避免把明细拉进 JVM）。
     * userIds 为空表示课程内全部学生。返回行数受学生数约束。
     */
    @Select("""
            <script>
            SELECT user_id AS userId,
                   COUNT(*) AS practiceCount,
                   SUM(CASE WHEN is_correct = 0 THEN 1 ELSE 0 END) AS wrongCount,
                   COUNT(DISTINCT CASE WHEN is_correct = 0 THEN q_id END) AS errorQidCount,
                   AVG(cost_seconds) AS avgCostSeconds,
                   MAX(created_at) AS lastActiveAt
            FROM practice_record
            WHERE q_id IN
              <foreach collection="qIds" item="q" open="(" separator="," close=")">#{q}</foreach>
              <if test="userIds != null and userIds.size() > 0">
                AND user_id IN
                <foreach collection="userIds" item="u" open="(" separator="," close=")">#{u}</foreach>
              </if>
            GROUP BY user_id
            </script>
            """)
    List<UserPracticeAgg> aggregateByUser(@Param("qIds") List<String> qIds,
                                          @Param("userIds") Collection<Long> userIds);

    /**
     * 按（用户, 知识点）聚合掌握度所需事件数与严重度和（C12）。
     * severitySum 的权重与时间半衰与 MasteryCalculator.severity/decay 保持一致（docs/05 §2.2），
     * 半衰期由调用方传入，避免两处常量漂移。返回行数受 学生数 × 知识点数 约束。
     */
    @Select("""
            <script>
            SELECT user_id AS userId,
                   kp_id AS kpId,
                   COUNT(*) AS events,
                   SUM(CASE WHEN is_correct = 0 THEN
                         POW(0.5, COALESCE(GREATEST(0, DATEDIFF(NOW(), created_at)), 0) / #{halfLifeDays}) *
                         CASE error_type
                           WHEN 'CONCEPT_CONFUSION' THEN 1.0
                           WHEN 'METHOD_MISSING' THEN 1.0
                           WHEN 'STEP_OMISSION' THEN 0.8
                           WHEN 'CALCULATION_ERROR' THEN 0.6
                           WHEN 'BOUNDARY_ERROR' THEN 0.8
                           ELSE 0.8
                         END
                       ELSE 0 END) AS severitySum,
                   SUM(CASE WHEN is_correct = 0 THEN 1 ELSE 0 END) AS errorCount
            FROM practice_record
            WHERE q_id IN
              <foreach collection="qIds" item="q" open="(" separator="," close=")">#{q}</foreach>
              AND kp_id IS NOT NULL AND kp_id &lt;&gt; ''
              <if test="userIds != null and userIds.size() > 0">
                AND user_id IN
                <foreach collection="userIds" item="u" open="(" separator="," close=")">#{u}</foreach>
              </if>
            GROUP BY user_id, kp_id
            </script>
            """)
    List<UserKpAgg> aggregateByUserKp(@Param("qIds") List<String> qIds,
                                      @Param("userIds") Collection<Long> userIds,
                                      @Param("halfLifeDays") double halfLifeDays);

    /** 单学生作答明细计数（C12 详情分页，参数绑定，不拼接 LIMIT）。 */
    @Select("""
            <script>
            SELECT COUNT(*) FROM practice_record
            WHERE user_id = #{userId}
              AND q_id IN
              <foreach collection="qIds" item="q" open="(" separator="," close=")">#{q}</foreach>
              <if test="from != null">AND created_at &gt;= #{from}</if>
              <if test="to != null">AND created_at &lt;= #{to}</if>
            </script>
            """)
    long countByUserPaged(@Param("userId") long userId,
                          @Param("qIds") List<String> qIds,
                          @Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to);

    /** 单学生作答明细分页（C12）。 */
    @Select("""
            <script>
            SELECT * FROM practice_record
            WHERE user_id = #{userId}
              AND q_id IN
              <foreach collection="qIds" item="q" open="(" separator="," close=")">#{q}</foreach>
              <if test="from != null">AND created_at &gt;= #{from}</if>
              <if test="to != null">AND created_at &lt;= #{to}</if>
            ORDER BY created_at DESC, practice_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<PracticeRecord> selectByUserPaged(@Param("userId") long userId,
                                           @Param("qIds") List<String> qIds,
                                           @Param("from") LocalDateTime from,
                                           @Param("to") LocalDateTime to,
                                           @Param("limit") int limit,
                                           @Param("offset") int offset);
}
