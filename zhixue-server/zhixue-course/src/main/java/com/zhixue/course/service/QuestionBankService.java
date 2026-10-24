package com.zhixue.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
