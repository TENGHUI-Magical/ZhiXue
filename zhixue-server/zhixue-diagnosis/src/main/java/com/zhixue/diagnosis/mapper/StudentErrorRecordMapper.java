package com.zhixue.diagnosis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixue.diagnosis.entity.StudentErrorRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 错题记录 Mapper。
 * A3：移除全仓唯一的 wrapper 片段（customSqlSegment）原样拼接，后续任何人给 wrapper
 * 加 .last/.apply/.exists 即成注入点，现改为全参数化显式 SQL；
 * 过滤条件与排序在 SQL 内固定，值全部 #{} 绑定。
 */
public interface StudentErrorRecordMapper extends BaseMapper<StudentErrorRecord> {

    @Select("""
            <script>
            SELECT * FROM student_error_record
            WHERE user_id = #{userId}
              <if test="kpId != null and kpId != ''">AND kp_id = #{kpId}</if>
              <if test="status != null and status != ''">AND status = #{status}</if>
            ORDER BY created_at DESC, log_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<StudentErrorRecord> selectPageByUser(@Param("userId") long userId,
                                               @Param("kpId") String kpId,
                                               @Param("status") String status,
                                               @Param("limit") int limit,
                                               @Param("offset") long offset);
}
