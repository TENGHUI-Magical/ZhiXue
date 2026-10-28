package com.zhixue.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixue.common.api.ErrorCode;
import com.zhixue.common.exception.BizException;
import com.zhixue.course.dto.QuestionView;
import com.zhixue.course.entity.QuestionBank;
import com.zhixue.course.mapper.QuestionBankMapper;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 题库服务：按知识点推荐练习。
 * 排序规则：难度升序 + 题号升序，保证同一知识点多次调用结果稳定、可复现。
 */
@Service
public class QuestionBankService {

    private final QuestionBankMapper questionBankMapper;
    private final ObjectMapper objectMapper;

    public QuestionBankService(QuestionBankMapper questionBankMapper, ObjectMapper objectMapper) {
        this.questionBankMapper = questionBankMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 按知识点推荐题目。
     *
     * @param courseId   课程 ID
     * @param kpId       知识点编号
     * @param count      返回条数，1—10
     * @param difficulty 难度过滤；null 表示不限
     * @return 题目视图列表，答案与解析不下发
     */
    public List<QuestionView> recommend(Long courseId, String kpId, int count, Integer difficulty) {
        LambdaQueryWrapper<QuestionBank> query = new LambdaQueryWrapper<QuestionBank>()
                .eq(QuestionBank::getCourseId, courseId)
                .eq(QuestionBank::getPrimaryKpId, kpId)
                .eq(QuestionBank::getIsActive, 1)
                .eq(difficulty != null, QuestionBank::getDifficulty, difficulty)
                .orderByAsc(QuestionBank::getDifficulty)
                .orderByAsc(QuestionBank::getQId)
                .last("LIMIT " + Math.max(1, Math.min(count, 10)));
        return questionBankMapper.selectList(query).stream().map(this::toView).toList();
    }

    /**
     * 取某知识点下全部启用题目，按难度、题号升序。
     * 供 /api/recommendations 做三层选题（题库单点最多 6 题，无需分页）。
     *
     * @param courseId 课程 ID
     * @param kpId     知识点编号
     * @return 题目视图列表；无题时为空
     */
    public List<QuestionView> listByKp(Long courseId, String kpId) {
        LambdaQueryWrapper<QuestionBank> query = new LambdaQueryWrapper<QuestionBank>()
                .eq(QuestionBank::getCourseId, courseId)
                .eq(QuestionBank::getPrimaryKpId, kpId)
                .eq(QuestionBank::getIsActive, 1)
                .orderByAsc(QuestionBank::getDifficulty)
                .orderByAsc(QuestionBank::getQId);
        return questionBankMapper.selectList(query).stream().map(this::toView).toList();
    }

    /**
     * 按 question_kp 关联取题（含次要关联，05 §3.1 变式/拓展池语义）：
     * 主知识点匹配 + 多对多关联匹配的并集，难度、题号升序。
     *
     * @param courseId 课程 ID
     * @param kpId     知识点编号
     * @return 题目视图列表；无题时为空
     */
    public List<QuestionView> listByKpIncludeSecondary(Long courseId, String kpId) {
        return questionBankMapper.selectByKpIncludeSecondary(courseId, kpId)
                .stream().map(this::toView).toList();
    }

    /**
     * 取某课程全部启用题目，按难度、题号升序；供计划生成选题。
     */
    public List<QuestionView> listByCourse(Long courseId) {
        return questionBankMapper.selectList(new LambdaQueryWrapper<QuestionBank>()
                        .eq(QuestionBank::getCourseId, courseId)
                        .eq(QuestionBank::getIsActive, 1)
                        .orderByAsc(QuestionBank::getDifficulty)
                        .orderByAsc(QuestionBank::getQId))
                .stream().map(this::toView).toList();
    }

    /** 取某课程全部启用题目编号；供班级画像按课程过滤作答记录。 */
    public List<String> listQidsByCourse(Long courseId) {
        return questionBankMapper.selectList(new LambdaQueryWrapper<QuestionBank>()
                        .eq(QuestionBank::getCourseId, courseId)
                        .eq(QuestionBank::getIsActive, 1))
                .stream().map(QuestionBank::getQId).toList();
    }

    /**
     * 按 qId 取题目；不存在抛 40400。
     * 供 /api/practices 校验题目真实存在，避免脏 qId 落库。
     */
    public QuestionBank getById(String qId) {
        if (qId == null || qId.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "qId 不能为空");
        }
        QuestionBank question = questionBankMapper.selectById(qId);
        if (question == null || (question.getIsActive() != null && question.getIsActive() == 0)) {
            throw new BizException(ErrorCode.NOT_FOUND, "题目不存在: " + qId);
        }
        return question;
    }

    /**
     * 批量取题目难度（推荐难度基准用：practice_record 不冗余难度列）。
     *
     * @param qIds 题目编号列表
     * @return qId → difficulty；无难度信息的题不出现
     */
    public Map<String, Integer> difficultyByQids(List<String> qIds) {
        if (qIds == null || qIds.isEmpty()) {
            return Map.of();
        }
        return questionBankMapper.selectList(new LambdaQueryWrapper<QuestionBank>()
                        .in(QuestionBank::getQId, qIds))
                .stream()
                .filter(q -> q.getDifficulty() != null)
                .collect(java.util.stream.Collectors.toMap(
                        QuestionBank::getQId, QuestionBank::getDifficulty));
    }

    @SuppressWarnings("unchecked")
    private QuestionView toView(QuestionBank question) {
        List<QuestionView.Option> options = new ArrayList<>();
        if (question.getOptionsJson() != null && !question.getOptionsJson().isBlank()) {
            try {
                List<Map<String, Object>> raw = objectMapper.readValue(question.getOptionsJson(), List.class);
                for (Map<String, Object> item : raw) {
                    options.add(new QuestionView.Option(
                            String.valueOf(item.get("key")), String.valueOf(item.get("text"))));
                }
            } catch (Exception ignored) {
                // 选项 JSON 异常不阻断推荐：前端按无选项渲染，教师端可发现脏数据
            }
        }
        return new QuestionView(question.getQId(), question.getPrimaryKpId(), question.getQuestionType(),
                question.getQuestionText(), options, question.getDifficulty(), question.getSource());
    }
}
