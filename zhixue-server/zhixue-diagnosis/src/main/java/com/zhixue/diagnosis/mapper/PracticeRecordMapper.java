package com.zhixue.diagnosis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixue.diagnosis.dto.ErrorTypeAgg;
import com.zhixue.diagnosis.dto.KpPracticeSignal;
import com.zhixue.diagnosis.dto.QuestionPracticeSignal;
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

    /** 该生该题历史作答次数（错因推断的“是否首次作答”，参数绑定）。 */
    @Select("""
            SELECT COUNT(*) FROM practice_record
            WHERE user_id = #{userId} AND q_id = #{qId}
            """)
    int countByUserAndQid(@Param("userId") long userId, @Param("qId") String qId);

    /** 该生该知识点最近一次练习时间（错因推断的“距上次练习天数”）。 */
    @Select("""
            SELECT MAX(created_at) FROM practice_record
            WHERE user_id = #{userId} AND kp_id = #{kpId}
            """)
    LocalDateTime selectLastPracticedAt(@Param("userId") long userId, @Param("kpId") String kpId);

    /**
     * 按用户聚合练习统计（C12 学生列表 / 学生画像总量；SQL GROUP BY，避免把明细拉进 JVM）。
     * <p>
     * 时间窗：since 非空时只聚合窗口内记录，避免学期末全量历史回表（180 天 ≈ 6 个半衰期，
     * 窗口外证据衰减后权重 &lt;1.6%，丢弃不影响口径）；窗口更早的历史走快照，不实时算。
     * qIds 为空表示不按课程过滤（学生画像场景，行数受该生记录数约束）。
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
            WHERE 1 = 1
              <if test="qIds != null and qIds.size() > 0">
                AND q_id IN
                <foreach collection="qIds" item="q" open="(" separator="," close=")">#{q}</foreach>
              </if>
              <if test="userIds != null and userIds.size() > 0">
                AND user_id IN
                <foreach collection="userIds" item="u" open="(" separator="," close=")">#{u}</foreach>
              </if>
              <if test="since != null">AND created_at &gt;= #{since}</if>
            GROUP BY user_id
            </script>
            """)
    List<UserPracticeAgg> aggregateByUser(@Param("qIds") List<String> qIds,
                                          @Param("userIds") Collection<Long> userIds,
                                          @Param("since") LocalDateTime since);

    /**
     * 按（用户, 知识点）聚合掌握度所需的正确权重、错误严重度和与事件数（C12 / 学生画像）。
     * <p>
     * successWeight 与 severitySum 都按每条事件自己的 created_at 衰减后求和（docs/05 §2.3），
     * 教师端与学生端共用同一 SQL 口径，不再使用“最后证据时间”统一衰减。
     * 时间窗：since 非空时只聚合窗口内记录（跨学年历史走快照，见 aggregateByUser javadoc）；
     * qIds 为空表示不按课程过滤（学生画像场景，行数受该生记录数约束）。
     * severitySum 的权重与时间半衰与 MasteryCalculator.severity/decay 保持一致（docs/05 §2.2）：
     * <ul>
     *   <li>{@code TIMESTAMPDIFF(DAY, created_at, now)} 取“满 24 小时天数”，与
     *       {@code Duration.between(...).toDays()} 完全一致；不能用 {@code DATEDIFF}（按日历跨日，
     *       会与 Java 口径差最多 1 天）。</li>
     *   <li>{@code now} 由调用方按 Asia/Shanghai 传入，不依赖 DB 会话时区（{@code NOW()} 可能是 UTC）。</li>
     * </ul>
     * 半衰期同理由调用方传入，避免两处常量漂移。返回行数受 学生数 × 知识点数 约束。
     */
    @Select("""
            <script>
            SELECT user_id AS userId,
                   kp_id AS kpId,
                   COUNT(*) AS events,
                   SUM(CASE WHEN is_correct = 1 THEN
                         POW(0.5, COALESCE(GREATEST(0, TIMESTAMPDIFF(DAY, created_at, #{now})), 0) / #{halfLifeDays})
                       ELSE 0 END) AS successWeight,
                   SUM(CASE WHEN is_correct = 0 THEN
                         POW(0.5, COALESCE(GREATEST(0, TIMESTAMPDIFF(DAY, created_at, #{now})), 0) / #{halfLifeDays}) *
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
            WHERE kp_id IS NOT NULL AND kp_id &lt;&gt; ''
              <if test="qIds != null and qIds.size() > 0">
                AND q_id IN
                <foreach collection="qIds" item="q" open="(" separator="," close=")">#{q}</foreach>
              </if>
              <if test="userIds != null and userIds.size() > 0">
                AND user_id IN
                <foreach collection="userIds" item="u" open="(" separator="," close=")">#{u}</foreach>
              </if>
              <if test="since != null">AND created_at &gt;= #{since}</if>
            GROUP BY user_id, kp_id
            </script>
            """)
    List<UserKpAgg> aggregateByUserKp(@Param("qIds") List<String> qIds,
                                      @Param("userIds") Collection<Long> userIds,
                                      @Param("halfLifeDays") double halfLifeDays,
                                      @Param("now") LocalDateTime now,
                                      @Param("since") LocalDateTime since);

    /**
     * 教师课程作答完整导出：一行一条作答，无 LIMIT/OFFSET；仅包含当前启用题目与聚合窗口内记录。
     * 学生排序由 Service 按班级全局聚合口径完成，这里只保证学生内作答时间倒序。
     */
    @Select("""
            SELECT pr.*
            FROM practice_record pr
            JOIN question_bank qb ON qb.q_id = pr.q_id
            WHERE qb.course_id = #{courseId} AND qb.is_active = 1
              AND pr.created_at >= #{since}
            ORDER BY pr.user_id, pr.created_at DESC, pr.practice_id DESC
            """)
    List<PracticeRecord> selectForCourseExport(@Param("courseId") long courseId,
                                               @Param("since") LocalDateTime since);

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

    /**
     * 单学生课程内按知识点的补漏信号（PRP 排序 + 到期复习判定）：总次数、错误数、
     * 最后一次正确作答时间、最后一次任意作答时间、最近一次错误错因。行数受 学生 × 知识点 约束，不拉明细。
     * <p>
     * lastErrorType 用 GROUP_CONCAT 按 practice_id 倒序取首个（GROUP_CONCAT 忽略 NULL，
     * 因此只保留答错记录）；SUBSTRING_INDEX 只取第一段，默认 1024 字节截断只影响尾部。
     * lastCorrectAt 只取 is_correct=1 的最大时间：遗忘紧迫度按“距上次正确作答”衰减——
     * 刚答错的知识点若很久没有正确证据，恰恰是最该补的；用最近任意作答会把这类点误判为不紧迫。
     * lastPracticedAt 取全部作答的最大时间：到期复习用它做冷却（05 §3.8），
     * 刚复习过的知识点当天不重复出队。
     */
    @Select("""
            <script>
            SELECT pr.kp_id AS kpId,
                   COUNT(*) AS totalCount,
                   SUM(CASE WHEN pr.is_correct = 0 THEN 1 ELSE 0 END) AS wrongCount,
                   MAX(CASE WHEN pr.is_correct = 1 THEN pr.created_at END) AS lastCorrectAt,
                   MAX(pr.created_at) AS lastPracticedAt,
                   SUBSTRING_INDEX(GROUP_CONCAT(
                         CASE WHEN pr.is_correct = 0 THEN pr.error_type END
                         ORDER BY pr.practice_id DESC SEPARATOR ','), ',', 1) AS lastErrorType
            FROM practice_record pr
            LEFT JOIN question_bank qb ON qb.q_id = pr.q_id
            WHERE pr.user_id = #{userId}
              AND (qb.course_id = #{courseId} OR qb.q_id IS NULL)
              AND pr.kp_id IS NOT NULL AND pr.kp_id &lt;&gt; ''
            GROUP BY pr.kp_id
            </script>
            """)
    List<KpPracticeSignal> selectKpSignals(@Param("userId") long userId,
                                           @Param("courseId") long courseId);

    /**
     * 单学生课程内按（知识点, 题目）的复习信号：该题最后一次答错时间与最后一次答对时间。
     * 到期复习选题用（05 §3.8）：优先重做最近答错的题，其次重做最近答对的题（同题重测），
     * 都没有时才用未练过的题兜底。行数受 学生 × 练过题数 约束，不拉明细。
     * <p>
     * 注意：本查询是非 script 的 @Select，比较符必须写字面 {@code <>}——
     * 非 script 注解不做 XML 实体解码，写 {@code &lt;&gt;} 会原样直达 MySQL 报语法错误。
     */
    @Select("""
            SELECT pr.kp_id AS kpId,
                   pr.q_id AS qId,
                   MAX(CASE WHEN pr.is_correct = 0 THEN pr.created_at END) AS lastWrongAt,
                   MAX(CASE WHEN pr.is_correct = 1 THEN pr.created_at END) AS lastCorrectAt
            FROM practice_record pr
            LEFT JOIN question_bank qb ON qb.q_id = pr.q_id
            WHERE pr.user_id = #{userId}
              AND (qb.course_id = #{courseId} OR qb.q_id IS NULL)
              AND pr.kp_id IS NOT NULL AND pr.kp_id <> ''
            GROUP BY pr.kp_id, pr.q_id
            """)
    List<QuestionPracticeSignal> selectQuestionSignals(@Param("userId") long userId,
                                                       @Param("courseId") long courseId);

    /**
     * 错因分布（班级画像 errorDist / 学生画像 errorDist）：SQL GROUP BY error_type，
     * 行数受错因类型数约束，只统计标注了知识点（kp_id 非空）且作答错误的记录，与掌握度事件口径一致。
     * 历史 null/空错因统一归入 UNCLASSIFIED，避免老数据从分布里消失。
     * qIds 非空 = 课程维度（教师端）；userId 非空 = 单学生维度；两者互斥使用。
     * since 非空时只统计窗口内记录（跨学年历史走快照）。
     */
    @Select("""
            <script>
            SELECT COALESCE(NULLIF(error_type, ''), 'UNCLASSIFIED') AS errorType, COUNT(*) AS cnt
            FROM practice_record
            WHERE is_correct = 0
              AND kp_id IS NOT NULL AND kp_id &lt;&gt; ''
              <if test="qIds != null and qIds.size() > 0">
                AND q_id IN
                <foreach collection="qIds" item="q" open="(" separator="," close=")">#{q}</foreach>
              </if>
              <if test="userId != null">AND user_id = #{userId}</if>
              <if test="since != null">AND created_at &gt;= #{since}</if>
            GROUP BY COALESCE(NULLIF(error_type, ''), 'UNCLASSIFIED')
            </script>
            """)
    List<ErrorTypeAgg> aggregateErrorType(@Param("qIds") List<String> qIds,
                                          @Param("userId") Long userId,
                                          @Param("since") LocalDateTime since);

    /**
     * 该生练过的题目编号集合（DISTINCT 投影，不拉明细）。
     * 供学生画像判断“错题记录是否已由练习计入”，行数受该生做过的题目数约束。
     * 不加时间窗：错题与练习可能分属窗口内外，按 qId 全局去重保持原互斥语义。
     */
    @Select("""
            SELECT DISTINCT q_id FROM practice_record
            WHERE user_id = #{userId} AND q_id IS NOT NULL AND q_id <> ''
            """)
    List<String> selectDistinctQidsByUser(@Param("userId") long userId);
}
