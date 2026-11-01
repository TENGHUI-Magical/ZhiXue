package com.zhixue.diagnosis.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixue.diagnosis.entity.StudentErrorRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 错题记录 Mapper。
 */
public interface StudentErrorRecordMapper extends BaseMapper<StudentErrorRecord> {

    /**
     * 按 wrapper 过滤并分页；LIMIT/OFFSET 一律参数绑定，替代 `.last("LIMIT ...")` 字符串拼接。
     * `${ew.customSqlSegment}` 为 MyBatis-Plus 生成的 WHERE/ORDER BY 片段，不含 last()。
     */
    @Select("SELECT * FROM student_error_record ${ew.customSqlSegment} LIMIT #{limit} OFFSET #{offset}")
    List<StudentErrorRecord> selectPageByWrapper(@Param("ew") Wrapper<StudentErrorRecord> wrapper,
                                                 @Param("limit") int limit,
                                                 @Param("offset") long offset);
}
