package com.zhixue.diagnosis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixue.diagnosis.entity.AiGenerationLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 调用留痕 Mapper。
 */
public interface AiGenerationLogMapper extends BaseMapper<AiGenerationLog> {

    /**
     * 教师评阅队列计数（C3）。
     *
     * <p>kpId 筛选改用 EXISTS 子查询，避免“先全量查错题再 IN 一批 logId”：
     * IN 集合随课程历史增长无上限，且会把排序/分页压力留在 JVM。
     * 所有条件参数绑定，分页 LIMIT/OFFSET 也由这里绑定，不拼接 SQL 字符串。</p>
     *
     * @param reviewed null=全部；true=已评阅；false=待评阅
     * @param kpId     知识点筛选；null/空 表示不限
     * @param from     创建时间下界（含）
     * @param to       创建时间上界（含）
     */
    @Select("""
            <script>
            SELECT COUNT(*)
            FROM ai_generation_log l
            WHERE l.scene = 'RAG'
              <if test="reviewed != null">
                <choose>
                  <when test="reviewed">AND l.reviewed_at IS NOT NULL</when>
                  <otherwise>AND l.reviewed_at IS NULL</otherwise>
                </choose>
              </if>
              <if test="from != null">AND l.created_at &gt;= #{from}</if>
              <if test="to != null">AND l.created_at &lt;= #{to}</if>
              <if test="kpId != null and kpId != ''">
                AND EXISTS (SELECT 1 FROM student_error_record e
                            WHERE e.log_id = l.log_id AND e.kp_id = #{kpId})
              </if>
            </script>
            """)
    long countForReview(@Param("reviewed") Boolean reviewed,
                        @Param("kpId") String kpId,
                        @Param("from") LocalDateTime from,
                        @Param("to") LocalDateTime to);

    /**
     * 教师评阅队列分页（C3）。筛选条件与 {@link #countForReview} 完全一致。
     */
    @Select("""
            <script>
            SELECT l.*
            FROM ai_generation_log l
            WHERE l.scene = 'RAG'
              <if test="reviewed != null">
                <choose>
                  <when test="reviewed">AND l.reviewed_at IS NOT NULL</when>
                  <otherwise>AND l.reviewed_at IS NULL</otherwise>
                </choose>
              </if>
              <if test="from != null">AND l.created_at &gt;= #{from}</if>
              <if test="to != null">AND l.created_at &lt;= #{to}</if>
              <if test="kpId != null and kpId != ''">
                AND EXISTS (SELECT 1 FROM student_error_record e
                            WHERE e.log_id = l.log_id AND e.kp_id = #{kpId})
              </if>
            ORDER BY l.created_at DESC, l.log_pk DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AiGenerationLog> selectForReview(@Param("reviewed") Boolean reviewed,
                                          @Param("kpId") String kpId,
                                          @Param("from") LocalDateTime from,
                                          @Param("to") LocalDateTime to,
                                          @Param("limit") int limit,
                                          @Param("offset") int offset);
}
