package com.zhixue.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixue.course.entity.KnowledgePoint;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 知识点 Mapper（MyBatis-Plus BaseMapper，CRUD 免写 SQL）。
 */
public interface KnowledgePointMapper extends BaseMapper<KnowledgePoint> {

    /** 契约 KnowledgePoint.chapterName 来源：按章节 ID 取标题 */
    @Select("SELECT title FROM course_chapter WHERE chapter_id = #{chapterId} LIMIT 1")
    String selectChapterTitle(@Param("chapterId") Long chapterId);
}