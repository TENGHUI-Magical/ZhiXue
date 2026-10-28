package com.zhixue.plan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixue.plan.entity.StudyPlan;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

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
}
