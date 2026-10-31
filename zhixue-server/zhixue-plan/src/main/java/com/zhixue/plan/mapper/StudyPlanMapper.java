package com.zhixue.plan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixue.plan.dto.UserPlanAgg;
import com.zhixue.plan.entity.StudyPlan;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 学习计划 Mapper。
 */
public interface StudyPlanMapper extends BaseMapper<StudyPlan> {

    /**
     * 打卡后原子重算完成数与状态。
     * <p>
     * 不能用“先 selectCount 再 update”：REPEATABLE READ 下并发打卡各自看到旧快照，
     * 会把 done_count 写小、状态永远停在 PENDING。这里用单条 UPDATE 收敛。
     */
    @Update("""
            UPDATE study_plan
            SET done_count = (SELECT COUNT(*) FROM study_plan_item
                              WHERE plan_id = #{planId} AND is_done = 1),
                status = CASE
                    WHEN (SELECT COUNT(*) FROM study_plan_item
                          WHERE plan_id = #{planId} AND is_done = 1) >= plan_count THEN 'DONE'
                    WHEN status = 'OVERDUE' THEN 'OVERDUE'
                    ELSE 'PENDING'
                END
            WHERE plan_id = #{planId}
            """)
    int refreshProgress(@Param("planId") Long planId);

    /** 按用户聚合一周计划进度（C12；SQL GROUP BY，避免拉明细）。 */
    @Select("""
            <script>
            SELECT user_id AS userId,
                   SUM(plan_count) AS planTotal,
                   SUM(done_count) AS planDone
            FROM study_plan
            WHERE user_id IN
              <foreach collection="userIds" item="u" open="(" separator="," close=")">#{u}</foreach>
              AND plan_date BETWEEN #{from} AND #{to}
            GROUP BY user_id
            </script>
            """)
    List<UserPlanAgg> aggregateProgress(@Param("userIds") Collection<Long> userIds,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to);
}
