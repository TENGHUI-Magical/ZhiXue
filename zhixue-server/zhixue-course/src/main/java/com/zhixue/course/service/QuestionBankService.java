package com.zhixue.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixue.common.api.ErrorCode;
import com.zhixue.common.exception.BizException;
import com.zhixue.course.dto.QuestionAnswerView;
import com.zhixue.course.dto.QuestionBriefView;
import com.zhixue.course.dto.QuestionDetailView;
import com.zhixue.course.dto.QuestionView;
import com.zhixue.course.entity.KnowledgePoint;
import com.zhixue.course.entity.QuestionBank;
import com.zhixue.course.mapper.QuestionBankMapper;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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
    private final KnowledgePointService knowledgePointService;

    public QuestionBankService(QuestionBankMapper questionBankMapper, ObjectMapper objectMapper,
                               KnowledgePointService knowledgePointService) {
        this.questionBankMapper = questionBankMapper;
        this.objectMapper = objectMapper;
        this.knowledgePointService = knowledgePointService;
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
        int limit = Math.max(1, Math.min(count, 10));
        LambdaQueryWrapper<QuestionBank> query = new LambdaQueryWrapper<QuestionBank>()
                .eq(QuestionBank::getCourseId, courseId)
                .eq(QuestionBank::getPrimaryKpId, kpId)
                .eq(QuestionBank::getIsActive, 1)
                .eq(difficulty != null, QuestionBank::getDifficulty, difficulty)
                .orderByAsc(QuestionBank::getDifficulty)
                .orderByAsc(QuestionBank::getQId);
        return questionBankMapper.selectByWrapperLimit(query, limit).stream().map(this::toView).toList();
    }

    /**
     * 按知识点列出启用题目（简要视图，不含答案与解析）。
     * 课程归属由知识点解析，避免前端传错 courseId；limit 收敛到 1—50。
     *
     * @param kpId 知识点编号
     * @param count 期望条数
     * @return 题目简要视图；知识点无题时为空
     */
    public List<QuestionBriefView> listBriefByKp(String kpId, int count) {
        if (kpId == null || kpId.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "kpId 不能为空");
        }
        KnowledgePoint kp = knowledgePointService.getById(kpId);
        if (kp.getCourseId() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "知识点缺少课程归属: " + kpId);
        }
        int limit = Math.max(1, Math.min(count, 50));
        LambdaQueryWrapper<QuestionBank> query = new LambdaQueryWrapper<QuestionBank>()
                .eq(QuestionBank::getCourseId, kp.getCourseId())
                .eq(QuestionBank::getPrimaryKpId, kpId)
                .eq(QuestionBank::getIsActive, 1)
                .orderByAsc(QuestionBank::getDifficulty)
                .orderByAsc(QuestionBank::getQId);
        return questionBankMapper.selectByWrapperLimit(query, limit).stream()
                .map(question -> new QuestionBriefView(question.getQId(), question.getPrimaryKpId(),
                        question.getQuestionType(), question.getQuestionText(), question.getDifficulty()))
                .toList();
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
     * 题目详情（不含答案与解析）：学生先独立作答。
     */
    public QuestionDetailView getDetail(String qId) {
        QuestionBank question = getById(qId);
        String kpName = null;
        if (question.getPrimaryKpId() != null && !question.getPrimaryKpId().isBlank()) {
            KnowledgePoint kp = knowledgePointService.findById(question.getPrimaryKpId());
            if (kp != null) {
                kpName = kp.getName();
            }
        }
        List<QuestionDetailView.QuestionOption> options = new ArrayList<>();
        for (Map<String, Object> item : parseRawOptions(question.getOptionsJson())) {
            Object key = item.get("key");
            Object text = item.get("text");
            if (key != null && text != null) {
                options.add(new QuestionDetailView.QuestionOption(
                        String.valueOf(key), String.valueOf(text)));
            }
        }
        return new QuestionDetailView(question.getQId(), question.getCourseId(),
                question.getPrimaryKpId(), kpName, question.getQuestionType(),
                question.getQuestionText(), options, question.getDifficulty());
    }

    /** 参考答案与解析：仅在学生显式点击“查看答案”后调用。 */
    public QuestionAnswerView getAnswer(String qId) {
        QuestionBank question = getById(qId);
        return new QuestionAnswerView(question.getQId(), question.getAnswerText(),
                question.getAnalysisText());
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

    private QuestionView toView(QuestionBank question) {
        List<QuestionView.Option> options = new ArrayList<>();
        for (Map<String, Object> item : parseRawOptions(question.getOptionsJson())) {
            Object key = item.get("key");
            Object text = item.get("text");
            if (key != null && text != null) {
                options.add(new QuestionView.Option(String.valueOf(key), String.valueOf(text)));
            }
        }
        return new QuestionView(question.getQId(), question.getPrimaryKpId(), question.getQuestionType(),
                question.getQuestionText(), options, question.getDifficulty(), question.getSource());
    }

    /** 解析 options_json；脏数据降级为空数组，不阻断推荐/详情。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseRawOptions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<?> raw = objectMapper.readValue(json, List.class);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : raw) {
                // 元素可能不是对象（如 ["A","B"]），逐项过滤，避免 toView 里 ClassCastException
                if (item instanceof Map<?, ?> map) {
                    result.add((Map<String, Object>) map);
                }
            }
            return result;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    /** 批量按题号取题（评阅证据用）；空集合返回空 Map。 */
    public Map<String, QuestionBank> findByIds(Collection<String> qIds) {
        if (qIds == null || qIds.isEmpty()) {
            return Map.of();
        }
        Map<String, QuestionBank> result = new HashMap<>();
        for (QuestionBank question : questionBankMapper.selectBatchIds(qIds)) {
            result.put(question.getQId(), question);
        }
        return result;
    }
}
