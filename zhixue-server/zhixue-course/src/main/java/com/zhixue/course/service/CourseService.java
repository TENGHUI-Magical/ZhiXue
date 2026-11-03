package com.zhixue.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixue.common.api.ErrorCode;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 课程服务：课程列表、章节、知识图谱。
 */
@Service
public class CourseService {

    private static final Logger log = LoggerFactory.getLogger(CourseService.class);

    private final CourseMapper courseMapper;
    private final CourseChapterMapper chapterMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final KnowledgePrereqMapper knowledgePrereqMapper;
    private final ObjectMapper objectMapper;

    public CourseService(CourseMapper courseMapper,
                         CourseChapterMapper chapterMapper,
                         KnowledgePointMapper knowledgePointMapper,
                         KnowledgePrereqMapper knowledgePrereqMapper,
                         ObjectMapper objectMapper) {
        this.courseMapper = courseMapper;
        this.chapterMapper = chapterMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.knowledgePrereqMapper = knowledgePrereqMapper;
        this.objectMapper = objectMapper;
    }

    /** 全部启用课程，按 courseId 升序。 */
    public List<CourseView> listActive() {
        return courseMapper.selectList(new LambdaQueryWrapper<Course>()
                        .eq(Course::getIsActive, 1)
                        .orderByAsc(Course::getCourseId))
                .stream().map(this::toView).toList();
    }

    /** 取启用课程；不存在抛 40400。 */
    public Course getActiveById(Long courseId) {
        Course course = courseId == null ? null : courseMapper.selectOne(
                new LambdaQueryWrapper<Course>()
                        .eq(Course::getCourseId, courseId)
                        .eq(Course::getIsActive, 1)
                        .last("LIMIT 1"));
        if (course == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "课程不存在: " + courseId);
        }
        return course;
    }

    /**
     * 教师的可管理课程 ID 集合（启用课程）。
     * 教师端数据范围的单一事实源：评阅队列/留痕导出按此隔离（明确属于其他教师课程的记录不可见）。
     */
    public List<Long> listActiveCourseIdsByTeacher(long teacherId) {
        return courseMapper.selectList(new LambdaQueryWrapper<Course>()
                        .eq(Course::getIsActive, 1)
                        .eq(Course::getTeacherId, teacherId))
                .stream().map(Course::getCourseId).toList();
    }

    /** 章节列表，按 chapterNo 升序。 */
    public List<ChapterView> listChapters(Long courseId) {
        getActiveById(courseId);
        return chapterMapper.selectList(new LambdaQueryWrapper<CourseChapter>()
                        .eq(CourseChapter::getCourseId, courseId)
                        .orderByAsc(CourseChapter::getChapterNo))
                .stream()
                .map(c -> new ChapterView(c.getChapterId(), c.getChapterNo(), c.getTitle()))
                .toList();
    }

    /** 知识图谱：启用知识点节点 + 两端都在本课程的先修边。 */
    public KnowledgeGraphView knowledgeGraph(Long courseId) {
        getActiveById(courseId);
        List<KnowledgePoint> kps = listActiveKnowledgePoints(courseId);
        List<KnowledgeGraphView.Node> nodes = kps.stream()
                .map(kp -> new KnowledgeGraphView.Node(kp.getKpId(), kp.getName(), kp.getChapterId()))
                .toList();
        if (kps.isEmpty()) {
            return new KnowledgeGraphView(nodes, List.of());
        }
        List<String> kpIds = kps.stream().map(KnowledgePoint::getKpId).toList();
        List<KnowledgePrereq> edges = knowledgePrereqMapper.selectList(
                new LambdaQueryWrapper<KnowledgePrereq>()
                        .in(KnowledgePrereq::getKpId, kpIds)
                        .in(KnowledgePrereq::getPrereqKpId, kpIds));
        List<KnowledgeGraphView.Edge> edgeViews = new ArrayList<>(edges.size());
        for (KnowledgePrereq edge : edges) {
            edgeViews.add(new KnowledgeGraphView.Edge(
                    edge.getPrereqKpId(), edge.getKpId(), "PREREQ"));
        }
        return new KnowledgeGraphView(nodes, edgeViews);
    }

    /** 课程下启用知识点，按章节与章内顺序排序；供计划/画像复用。 */
    public List<KnowledgePoint> listActiveKnowledgePoints(Long courseId) {
        return knowledgePointMapper.selectList(new LambdaQueryWrapper<KnowledgePoint>()
                .eq(KnowledgePoint::getCourseId, courseId)
                .eq(KnowledgePoint::getIsActive, 1)
                .orderByAsc(KnowledgePoint::getChapterId)
                .orderByAsc(KnowledgePoint::getSortNo));
    }

    private CourseView toView(Course course) {
        return new CourseView(course.getCourseId(), course.getCourseName(), course.getTextbook(),
                course.getDescription(), parseDimensions(course.getDimensionJson()));
    }

    /** dimension_json 只存 key/name；脏数据降级为空数组，不阻断课程列表。 */
    @SuppressWarnings("unchecked")
    private List<CourseView.DimensionDef> parseDimensions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(json, List.class);
            List<CourseView.DimensionDef> result = new ArrayList<>(raw.size());
            for (Map<String, Object> item : raw) {
                Object key = item.get("key");
                Object name = item.get("name");
                if (key != null && name != null) {
                    result.add(new CourseView.DimensionDef(
                            String.valueOf(key), String.valueOf(name)));
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("课程维度配置 JSON 解析失败，按空列表降级", e);
            return List.of();
        }
    }
}
