package com.zhixue.diagnosis.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixue.common.api.ErrorCode;
import com.zhixue.common.exception.BizException;
import com.zhixue.course.entity.KnowledgePoint;
import com.zhixue.course.service.CourseService;
import com.zhixue.course.service.KnowledgePointService;
import com.zhixue.diagnosis.dto.ExplainLogView;
import com.zhixue.diagnosis.entity.AiGenerationLog;
import com.zhixue.diagnosis.mapper.AiGenerationLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 讲解记录查询（契约 V2 §5.4：刷新页面后按 explainId 取已生成全文）。
 * <p>
 * 学生仅可查询本人记录；教师仅可查询评阅所需记录（课程范围与写侧评阅同一口径，
 * 避免 explainId 越权泄露）。
 */
@Service
public class ExplainQueryService {

    private static final Logger log = LoggerFactory.getLogger(ExplainQueryService.class);

    /** 契约 §1：日期时间统一 `yyyy-MM-dd HH:mm:ss` */
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AiGenerationLogMapper logMapper;
    private final KnowledgePointService knowledgePointService;
    private final ObjectMapper objectMapper;
    private final CourseService courseService;

    public ExplainQueryService(AiGenerationLogMapper logMapper,
                               KnowledgePointService knowledgePointService,
                               ObjectMapper objectMapper,
                               CourseService courseService) {
        this.logMapper = logMapper;
        this.knowledgePointService = knowledgePointService;
        this.objectMapper = objectMapper;
        this.courseService = courseService;
    }

    /**
     * 按 explainId 查询当前用户的讲解记录。
     *
     * @param explainId 讲解编号
     * @return 讲解全文与元信息
     * @throws BizException 记录不存在或不属于当前用户时返回 40400；
     *                      教师读取自己可管理课程之外的记录时返回 40300
     */
    public ExplainLogView get(String explainId) {
        boolean teacher = StpUtil.hasRole("TEACHER");
        LambdaQueryWrapper<AiGenerationLog> query = new LambdaQueryWrapper<AiGenerationLog>()
                .eq(AiGenerationLog::getExplainId, explainId);
        if (!teacher) {
            query.eq(AiGenerationLog::getUserId, StpUtil.getLoginIdAsLong());
        }
        AiGenerationLog row = logMapper.selectOne(query.last("LIMIT 1"));
        if (row == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "讲解记录不存在");
        }
        checkTeacherScope(row, teacher);
        // 评阅回流三字段（05 §4.6）：该讲解自身的复核状态，实时读库不缓存
        return new ExplainLogView(
                row.getExplainId(),
                row.getTraceId(),
                row.getModel(),
                Objects.equals(row.getIsDegraded(), 1),
                row.getDegradeLevel(),
                row.getContentText() == null ? "" : row.getContentText(),
                sources(row.getSourceIds()),
                Objects.equals(row.getIsBlocked(), 1),
                row.getCreatedAt() == null ? null : row.getCreatedAt().format(DATE_TIME),
                row.getReviewResult(),
                row.getTeacherComment(),
                row.getReviewedAt() == null ? null : row.getReviewedAt().format(DATE_TIME));
    }

    /**
     * 教师读侧课程范围（与写侧 {@link TeacherReviewService#submit} 同一口径）：
     * 课程来源用 {@link CourseService#listActiveCourseIdsByTeacher}，记录归属用
     * {@link AiGenerationLogMapper#selectLinkedCourseIds}。
     * <p>
     * 可归属（至少能定位到一个课程）：记录可定位到登录教师可管理课程之外 → 40300，行为不变。
     * 归属不明（无错题关联/题库查不到课程）：不归属任何教师课程，只有创建者本人可读，
     * 其他教师一律 40300（P1-6）。系统角色目前只有 STUDENT/TEACHER（openapi
     * {@code allowableValues}），没有管理员角色，管理员放行需先升契约，本轮不实现。
     * </p>
     */
    private void checkTeacherScope(AiGenerationLog row, boolean teacher) {
        if (!teacher) {
            return;
        }
        long loginId = StpUtil.getLoginIdAsLong();
        List<Long> linkedCourseIds = logMapper.selectLinkedCourseIds(row.getLogId());
        if (linkedCourseIds.isEmpty()) {
            if (!Objects.equals(row.getUserId(), loginId)) {
                throw new BizException(ErrorCode.FORBIDDEN,
                        "无权查看归属不明的讲解: " + row.getExplainId());
            }
            return;
        }
        List<Long> scope = courseService.listActiveCourseIdsByTeacher(loginId);
        for (Long courseId : linkedCourseIds) {
            if (!scope.contains(courseId)) {
                throw new BizException(ErrorCode.FORBIDDEN,
                        "无权查看自己可管理课程之外的讲解: " + row.getExplainId());
            }
        }
    }

    /** source_ids 是 kp_id 数组 JSON；名称与页码从知识点表补齐，缺失时降级为 null。 */
    private List<ExplainLogView.SourceView> sources(String sourceIds) {
        if (sourceIds == null || sourceIds.isBlank()) {
            return List.of();
        }
        List<String> kpIds;
        try {
            kpIds = objectMapper.readValue(sourceIds, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            log.warn("source_ids 解析失败，返回空来源：{}", e.toString());
            return List.of();
        }
        List<ExplainLogView.SourceView> views = new ArrayList<>(kpIds.size());
        for (String kpId : kpIds) {
            KnowledgePoint kp = knowledgePointService.findById(kpId);
            views.add(new ExplainLogView.SourceView(kpId,
                    kp == null ? null : kp.getTextbookPage(),
                    kp == null ? null : kp.getName()));
        }
        return views;
    }
}
