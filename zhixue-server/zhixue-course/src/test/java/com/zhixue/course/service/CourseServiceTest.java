package com.zhixue.course.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhixue.common.exception.BizException;
import com.zhixue.course.dto.ChapterView;
import com.zhixue.course.dto.CourseView;
import com.zhixue.course.dto.KnowledgeGraphView;
import com.zhixue.course.entity.Course;
import com.zhixue.course.entity.CourseChapter;
import com.zhixue.course.entity.KnowledgePoint;
import com.zhixue.course.entity.KnowledgePrereq;
import com.zhixue.course.mapper.CourseChapterMapper;
import com.zhixue.course.mapper.CourseMapper;
import com.zhixue.course.mapper.KnowledgePointMapper;
import com.zhixue.course.mapper.KnowledgePrereqMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock
    private CourseMapper courseMapper;
    @Mock
    private CourseChapterMapper chapterMapper;
    @Mock
    private KnowledgePointMapper knowledgePointMapper;
    @Mock
    private KnowledgePrereqMapper knowledgePrereqMapper;

    private CourseService service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        for (Class<?> entity : List.of(Course.class, CourseChapter.class,
                KnowledgePoint.class, KnowledgePrereq.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), entity);
        }
    }

    @BeforeEach
    void setUp() {
        service = new CourseService(courseMapper, chapterMapper, knowledgePointMapper,
                knowledgePrereqMapper, new ObjectMapper());
    }

    @Test
    void listActiveParsesDimensionConfig() {
        Course course = new Course();
        course.setCourseId(1L);
        course.setCourseName("数据库原理");
        course.setDimensionJson("[{\"key\":\"concept\",\"name\":\"概念理解\"}]");
        when(courseMapper.selectList(any())).thenReturn(List.of(course));

        List<CourseView> result = service.listActive();

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).dimensionConfig().size());
        assertEquals("concept", result.get(0).dimensionConfig().get(0).key());
    }

    @Test
    void missingCourseReturns404() {
        when(courseMapper.selectOne(any())).thenReturn(null);
        assertThrows(BizException.class, () -> service.listChapters(99L));
    }

    @Test
    void knowledgeGraphUsesPrereqAsSource() {
        Course course = new Course();
        course.setCourseId(1L);
        course.setIsActive(1);
        when(courseMapper.selectOne(any())).thenReturn(course);

        KnowledgePoint kp = new KnowledgePoint();
        kp.setKpId("KP0202");
        kp.setName("关系");
        kp.setChapterId(1L);
        kp.setCourseId(1L);
        kp.setIsActive(1);
        when(knowledgePointMapper.selectList(any())).thenReturn(List.of(kp));

        KnowledgePrereq edge = new KnowledgePrereq();
        edge.setKpId("KP0202");
        edge.setPrereqKpId("KP0201");
        when(knowledgePrereqMapper.selectList(any())).thenReturn(List.of(edge));

        KnowledgeGraphView graph = service.knowledgeGraph(1L);

        assertEquals(1, graph.nodes().size());
        assertEquals(1, graph.edges().size());
        assertEquals("KP0201", graph.edges().get(0).source());
        assertEquals("KP0202", graph.edges().get(0).target());
        assertEquals("PREREQ", graph.edges().get(0).relation());
    }

    @Test
    void chaptersMapContractFields() {
        Course course = new Course();
        course.setCourseId(1L);
        course.setIsActive(1);
        when(courseMapper.selectOne(any())).thenReturn(course);
        CourseChapter chapter = new CourseChapter();
        chapter.setChapterId(2L);
        chapter.setChapterNo(2);
        chapter.setTitle("关系模型");
        when(chapterMapper.selectList(any())).thenReturn(List.of(chapter));

        List<ChapterView> chapters = service.listChapters(1L);

        assertEquals(1, chapters.size());
        assertEquals(2, chapters.get(0).chapterNo());
        assertEquals("关系模型", chapters.get(0).title());
    }
}
