package com.zhixue.diagnosis.service;

import com.zhixue.course.dto.KpMatchProfile;
import com.zhixue.course.service.KnowledgePointService;
import com.zhixue.diagnosis.dto.MapResult;
import com.zhixue.diagnosis.dto.MapRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 映射打分与章先知测试。
 * <p>
 * 打分公式的 60 题离线评测见 zhixue/tools/eval_mapping_offline.py；
 * 本类只锁行为：排序、决策三段、章过滤与空章回退。
 */
@ExtendWith(MockitoExtension.class)
class KpMappingServiceTest {

    @Mock
    private KnowledgePointService knowledgePointService;

    private KpMappingService service;

    @BeforeEach
    void setUp() {
        service = new KpMappingService(knowledgePointService, 0.3, 0.60, 0.10, 0.40);
    }

    private KpMatchProfile profile(String kpId, String name, Long chapterId, String concept) {
        return new KpMatchProfile(kpId, name, chapterId, concept, List.of());
    }

    private MapRequest request(String text, Long chapterId) {
        MapRequest request = new MapRequest();
        request.setText(text);
        request.setCourseId(1L);
        request.setChapterId(chapterId);
        return request;
    }

    @Test
    void similarConceptScoresHighAndAutoWhenMarginIsClear() {
        when(knowledgePointService.listMatchProfiles(1L)).thenReturn(List.of(
                profile("KP0203", "候选码", 2L,
                        "候选码是能唯一标识元组且真子集不能唯一标识的属性组。主码从候选码中选定。"),
                profile("KP0304", "连接查询", 3L,
                        "连接查询把两个表按连接条件组合成结果表，与候选码的概念不同。")));

        MapResult result = service.match(request("候选码的真子集不能唯一标识元组", null));

        assertEquals("KP0203", result.top1().kpId());
        assertEquals("AUTO", result.decision());
        assertTrue(result.top1().score() > result.candidates().get(1).score());
        assertEquals("VECTOR", result.candidates().get(0).channel());
    }

    @Test
    void shortTextReturnsFailed() {
        MapResult result = service.match(request("短", null));
        assertEquals("FAILED", result.decision());
        assertNull(result.top1());
        assertTrue(result.candidates().isEmpty());
    }

    @Test
    void noProfilesReturnsFailed() {
        when(knowledgePointService.listMatchProfiles(1L)).thenReturn(List.of());
        MapResult result = service.match(request("候选码的真子集不能唯一标识元组", null));
        assertEquals("FAILED", result.decision());
        assertNull(result.top1());
    }

    @Test
    void chapterIdKeepsOnlyThatChapterCandidates() {
        when(knowledgePointService.listMatchProfiles(1L)).thenReturn(List.of(
                profile("KP0203", "候选码", 2L, "候选码是能唯一标识元组的属性组。"),
                profile("KP0304", "连接查询", 3L, "候选码相关：连接查询把两个表组合成结果表。")));

        MapResult result = service.match(request("候选码的真子集不能唯一标识元组", 3L));

        assertFalse(result.candidates().isEmpty());
        assertTrue(result.candidates().stream().allMatch(item -> "KP0304".equals(item.kpId())));
        assertEquals("KP0304", result.top1().kpId());
    }

    @Test
    void unknownChapterFallsBackToAllCandidates() {
        when(knowledgePointService.listMatchProfiles(1L)).thenReturn(List.of(
                profile("KP0203", "候选码", 2L, "候选码是能唯一标识元组的属性组。"),
                profile("KP0304", "连接查询", 3L, "连接查询把两个表组合成结果表。")));

        MapResult result = service.match(request("候选码的真子集不能唯一标识元组", 99L));

        assertEquals("KP0203", result.top1().kpId());
        assertFalse(result.candidates().isEmpty());
    }

    @Test
    void ambiguityBecomesConfirmNotAuto() {
        when(knowledgePointService.listMatchProfiles(1L)).thenReturn(List.of(
                profile("KP0203", "候选码", 2L, "候选码是能唯一标识元组的属性组。"),
                profile("KP0204", "主属性", 2L, "候选码是能唯一标识元组的属性组。")));

        MapResult result = service.match(request("候选码是能唯一标识元组的属性组", null));

        assertEquals("CONFIRM", result.decision());
        assertEquals(2, result.candidates().size());
        assertEquals(result.candidates().get(0).score(), result.candidates().get(1).score(), 1e-9);
    }
}
