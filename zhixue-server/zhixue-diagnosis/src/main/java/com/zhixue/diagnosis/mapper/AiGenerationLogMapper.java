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
     * <p>课程范围隔离：courseIds = 登录教师可管理课程；明确能定位到
     * 其他教师课程的记录不可见；关联不到错题/题库（归属不明）的记录保留——
     * 行消失会被当成丢数据，证据列为空更诚实（docs/01 §7.2）。调用方保证
     * courseIds 非 null 且非空（无课程教师传哨兵 -1，即看不到任何有归属的记录）。</p>
     *
     * @param reviewed   null=全部；true=已评阅；false=待评阅
     * @param kpId       知识点筛选；null/空 表示不限
     * @param from       创建时间下界（含）
     * @param to         创建时间上界（含）
     * @param courseIds  登录教师可管理课程 ID（有界：教师课程数）
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
              <if test="courseIds != null">
                AND NOT EXISTS (SELECT 1 FROM student_error_record e
                                JOIN question_bank q ON q.q_id = e.q_id
                                WHERE e.log_id = l.log_id
                                  AND q.course_id NOT IN
                                  <foreach collection="courseIds" item="cid"
                                            open="(" separator="," close=")">#{cid}</foreach>)
              </if>
            </script>
            """)
    long countForReview(@Param("reviewed") Boolean reviewed,
                        @Param("kpId") String kpId,
                        @Param("from") LocalDateTime from,
                        @Param("to") LocalDateTime to,
                        @Param("courseIds") List<Long> courseIds);

    /**
     * 教师评阅队列分页（C3）。筛选条件（含课程范围）与 {@link #countForReview} 完全一致。
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
              <if test="courseIds != null">
                AND NOT EXISTS (SELECT 1 FROM student_error_record e
                                JOIN question_bank q ON q.q_id = e.q_id
                                WHERE e.log_id = l.log_id
                                  AND q.course_id NOT IN
                                  <foreach collection="courseIds" item="cid"
                                            open="(" separator="," close=")">#{cid}</foreach>)
              </if>
            ORDER BY l.created_at DESC, l.log_pk DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AiGenerationLog> selectForReview(@Param("reviewed") Boolean reviewed,
                                          @Param("kpId") String kpId,
                                          @Param("from") LocalDateTime from,
                                          @Param("to") LocalDateTime to,
                                          @Param("courseIds") List<Long> courseIds,
                                          @Param("limit") int limit,
                                          @Param("offset") int offset);

    /**
     * 教师评阅留痕全量导出（CSV）：筛选条件（含课程范围）与 {@link #selectForReview} 完全一致，仅去掉分页。
     * 导出量有界：调用方先 COUNT，超过 {@code TeacherReviewService.MAX_EXPORT_ROWS}（50,000）即拒绝并
     * 提示缩小日期范围，确认未超限后才调用本方法——本方法本身不设 LIMIT，上限由调用方保证。
     *
     * <p>投影列**不含 {@code content_text}**：CSV 不输出讲解正文，SELECT l.* 会把每行最多
     * 20,000 字符的正文全量拉进堆（50,000 行 × ≥10KB 正文实测 187MB 峰值）。这里只取
     * CSV 用到的 12 列，正文在系统内按讲解 ID 可查。</p>
     */
    @Select("""
            <script>
            SELECT l.log_pk, l.log_id, l.explain_id, l.user_id, l.source_ids, l.model,
                   l.is_degraded, l.created_at, l.review_result, l.teacher_score,
                   l.teacher_comment, l.reviewed_at
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
              <if test="courseIds != null">
                AND NOT EXISTS (SELECT 1 FROM student_error_record e
                                JOIN question_bank q ON q.q_id = e.q_id
                                WHERE e.log_id = l.log_id
                                  AND q.course_id NOT IN
                                  <foreach collection="courseIds" item="cid"
                                            open="(" separator="," close=")">#{cid}</foreach>)
              </if>
            ORDER BY l.created_at DESC, l.log_pk DESC
            </script>
            """)
    List<AiGenerationLog> selectForReviewExport(@Param("reviewed") Boolean reviewed,
                                                @Param("kpId") String kpId,
                                                @Param("from") LocalDateTime from,
                                                @Param("to") LocalDateTime to,
                                                @Param("courseIds") List<Long> courseIds);

    /**
     * 解析单条评阅留痕的课程归属（写侧范围校验专用，2026-11-03）：
     * 经错题记录 → 题库 course_id，与 {@link #countForReview} 等读侧 NOT EXISTS
     * 完全同口径——只认 e.log_id 关联且 q_id 成功连接题库的行；course_id 为 NULL
     * 不算归属（与读侧 {@code NULL NOT IN (...)} 不触发排除一致）。
     * 返回空集 = 归属不明，调用方按读侧同口径放行（诚实留行原则，docs/01 §7.2）。
     *
     * @param logId ai_generation_log.log_id（错题记录关联键）
     */
    @Select("""
            SELECT DISTINCT q.course_id
            FROM student_error_record e
            JOIN question_bank q ON q.q_id = e.q_id
            WHERE e.log_id = #{logId} AND q.course_id IS NOT NULL
            """)
    List<Long> selectLinkedCourseIds(@Param("logId") String logId);

    /**
     * 学生端评阅回流（05 §4.6）：按学生 + 错题编号集合取已评阅讲解。
     * 一条错题可能多次生成讲解（缓存回放/重新生成各落一行），调用方按 logId 分组
     * 取 reviewed_at 最大的一条作为该错题的复核结论。
     * <p>
     * logIds 由调用方保证非空且有界（列表页 ≤50），避免空 IN 语法错误与无界查询；
     * user_id + scene + reviewed_at 组合可走 idx_user_time / idx_pending_review。
     * </p>
     */
    @Select("""
            <script>
            SELECT l.*
            FROM ai_generation_log l
            WHERE l.user_id = #{userId}
              AND l.scene = 'RAG'
              AND l.reviewed_at IS NOT NULL
              AND l.log_id IN
              <foreach collection="logIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<AiGenerationLog> selectReviewedByUserAndLogIds(@Param("userId") long userId,
                                                        @Param("logIds") List<String> logIds);
}
