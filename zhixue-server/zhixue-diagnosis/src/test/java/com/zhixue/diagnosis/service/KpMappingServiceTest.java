package com.zhixue.diagnosis.service;

import com.zhixue.course.dto.KpMatchProfile;
import com.zhixue.course.service.KnowledgePointService;
import com.zhixue.diagnosis.dto.MapCandidate;
import com.zhixue.diagnosis.dto.MapRequest;
import com.zhixue.diagnosis.dto.MapResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
 * 映射打分、语料开关、章过滤口径与决策边界测试（不依赖 DB / Spring 上下文）。
 * <p>
 * 只锁**行为**，不复刻实现：
 * <ul>
 *   <li>UT1/UT2 —— misconception 入语料带来的排序变化，以及开关能真正回滚；</li>
 *   <li>UT3/UT4 —— 章过滤只缩小候选集合、不改 IDF 统计范围（D2），空章回退；</li>
 *   <li>UT5 —— 排序键必须是原始余弦，不是饱和后的 score（D3）；</li>
 *   <li>UT7 —— 决策三段随配置生效（confirm-floor 0.35）。</li>
 * </ul>
 * 全部离线指标（含两个语料口径 N=20 / N=60）见 tools/eval_mapping_offline.py，
 * 单测不承担精度门禁。
 */
@ExtendWith(MockitoExtension.class)
class KpMappingServiceTest {

    private static final long COURSE_ID = 1L;

    /** 与 application.yml 同值：score-scale 0.3 / auto 0.60 / margin 0.10 / floor 0.35 */
    private static final double SCORE_SCALE = 0.3;
    private static final double AUTO_THRESHOLD = 0.60;
    private static final double MARGIN = 0.10;
    private static final double CONFIRM_FLOOR = 0.35;

    @Mock
    private KnowledgePointService knowledgePointService;

    private KpMappingService service;

    @BeforeEach
    void setUp() {
        service = newService(SCORE_SCALE, CONFIRM_FLOOR, true);
    }

    // ---------- 构造 helper ----------

    private KpMappingService newService(double scoreScale, double floor, boolean useMisconception) {
        return new KpMappingService(knowledgePointService, scoreScale,
                AUTO_THRESHOLD, MARGIN, floor, useMisconception);
    }

    private KpMatchProfile profile(String kpId, String name, Long chapterId, String concept) {
        return profile(kpId, name, chapterId, concept, "");
    }

    private KpMatchProfile profile(String kpId, String name, Long chapterId,
                                   String concept, String misconception) {
        return new KpMatchProfile(kpId, name, chapterId, concept, misconception, List.of());
    }

    private MapRequest request(String text, Long chapterId) {
        MapRequest request = new MapRequest();
        request.setText(text);
        request.setCourseId(COURSE_ID);
        request.setChapterId(chapterId);
        return request;
    }

    private double scoreOf(MapResult result, String kpId) {
        return result.candidates().stream()
                .filter(candidate -> kpId.equals(candidate.kpId()))
                .mapToDouble(MapCandidate::score)
                .findFirst()
                .orElseThrow(() -> new AssertionError("候选中缺少 " + kpId + "：" + result.candidates()));
    }

    // ---------- 既有行为（回归） ----------

    @Test
    void similarConceptScoresHighAndAutoWhenMarginIsClear() {
        when(knowledgePointService.listMatchProfiles(COURSE_ID)).thenReturn(List.of(
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
        when(knowledgePointService.listMatchProfiles(COURSE_ID)).thenReturn(List.of());
        MapResult result = service.match(request("候选码的真子集不能唯一标识元组", null));
        assertEquals("FAILED", result.decision());
        assertNull(result.top1());
    }

    @Test
    void chapterIdKeepsOnlyThatChapterCandidates() {
        when(knowledgePointService.listMatchProfiles(COURSE_ID)).thenReturn(List.of(
                profile("KP0203", "候选码", 2L, "候选码是能唯一标识元组的属性组。"),
                profile("KP0304", "连接查询", 3L, "候选码相关：连接查询把两个表组合成结果表。")));

        MapResult result = service.match(request("候选码的真子集不能唯一标识元组", 3L));

        assertFalse(result.candidates().isEmpty());
        assertTrue(result.candidates().stream().allMatch(item -> "KP0304".equals(item.kpId())));
        assertEquals("KP0304", result.top1().kpId());
    }

    @Test
    void unknownChapterFallsBackToAllCandidates() {
        when(knowledgePointService.listMatchProfiles(COURSE_ID)).thenReturn(List.of(
                profile("KP0203", "候选码", 2L, "候选码是能唯一标识元组的属性组。"),
                profile("KP0304", "连接查询", 3L, "连接查询把两个表组合成结果表。")));

        MapResult result = service.match(request("候选码的真子集不能唯一标识元组", 99L));

        assertEquals("KP0203", result.top1().kpId());
        assertFalse(result.candidates().isEmpty());
    }

    @Test
    void ambiguityBecomesConfirmNotAuto() {
        when(knowledgePointService.listMatchProfiles(COURSE_ID)).thenReturn(List.of(
                profile("KP0203", "候选码", 2L, "候选码是能唯一标识元组的属性组。"),
                profile("KP0204", "主属性", 2L, "候选码是能唯一标识元组的属性组。")));

        MapResult result = service.match(request("候选码是能唯一标识元组的属性组", null));

        assertEquals("CONFIRM", result.decision());
        assertEquals(2, result.candidates().size());
        assertEquals(result.candidates().get(0).score(), result.candidates().get(1).score(), 1e-9);
    }

    // ---------- UT1：误区入语料改变排序 ----------

    @Test
    @DisplayName("UT1 两个知识点概念相同、仅误区不同时，用误区措辞命题应由该知识点胜出")
    void misconceptionMakesErrorStyleStemWin() {
        // 名称与概念**完全相同**，唯一的差异是 misconception —— 否则无法把差异归因到语料改动
        String sharedName = "候选码";
        String sharedConcept = "候选码是能唯一标识元组且真子集不能唯一标识的属性组，"
                + "主码从多个候选码中选定，候选码包含的属性称为主属性。";
        when(knowledgePointService.listMatchProfiles(COURSE_ID)).thenReturn(List.of(
                // kpId 更小，但误区与题干无关；不带误区时会因打平按 kpId 胜出
                profile("KP0202", sharedName, 2L, sharedConcept,
                        "认为主属性只属于主码，忽略包含在任一候选码中的属性都算主属性"),
                profile("KP0203", sharedName, 2L, sharedConcept,
                        "把候选码与超码混为一谈，认为去掉任一属性后属性组仍能唯一标识元组")));

        // score-scale 取 1.0：本用例只锁语料构成的**行为**，避免 score 饱和掩盖差异
        KpMappingService withMisconception = newService(1.0, CONFIRM_FLOOR, true);
        MapResult result = withMisconception.match(
                request("去掉任一属性后属性组仍能唯一标识元组，说明把候选码与超码混为一谈了", null));

        assertEquals(2, result.candidates().size());
        assertEquals("KP0203", result.top1().kpId());
        assertTrue(result.top1().score() > scoreOf(result, "KP0202"),
                "误区措辞应让 KP0203 的余弦严格更高");
    }

    // ---------- UT2：开关能把行为回滚到概念口径 ----------

    @Test
    @DisplayName("UT2 关闭 misconception-in-corpus 后排序回到概念口径（两者打平，按 kpId 升序）")
    void misconceptionDisabledFallsBackToConceptOnly() {
        String sharedName = "候选码";
        String sharedConcept = "候选码是能唯一标识元组且真子集不能唯一标识的属性组，"
                + "主码从多个候选码中选定，候选码包含的属性称为主属性。";
        when(knowledgePointService.listMatchProfiles(COURSE_ID)).thenReturn(List.of(
                profile("KP0202", sharedName, 2L, sharedConcept,
                        "认为主属性只属于主码，忽略包含在任一候选码中的属性都算主属性"),
                profile("KP0203", sharedName, 2L, sharedConcept,
                        "把候选码与超码混为一谈，认为去掉任一属性后属性组仍能唯一标识元组")));

        String stem = "去掉任一属性后属性组仍能唯一标识元组，说明把候选码与超码混为一谈了";

        KpMappingService conceptOnly = newService(1.0, CONFIRM_FLOOR, false);
        MapResult disabled = conceptOnly.match(request(stem, null));

        assertEquals(2, disabled.candidates().size());
        assertEquals(scoreOf(disabled, "KP0202"), scoreOf(disabled, "KP0203"), 1e-9,
                "纯概念口径下两个知识点语料完全相同，余弦必须相等");
        assertEquals("KP0202", disabled.top1().kpId(), "打平后按 kpId 升序，与误区无关");

        // 同一题干、同一份画像：打开开关后冠军必须是持有该误区文本的 KP0203
        MapResult enabled = newService(1.0, CONFIRM_FLOOR, true).match(request(stem, null));
        assertEquals("KP0203", enabled.top1().kpId());
    }

    // ---------- UT3：章过滤不得改变 IDF 统计范围（D2） ----------

    @Test
    @DisplayName("UT3 传/不传 chapterId 时，同一 kpId 的分数必须逐位相等")
    void chapterScopeKeepsGlobalScoreScale() {
        when(knowledgePointService.listMatchProfiles(COURSE_ID)).thenReturn(List.of(
                profile("KP0201", "关系模型", 2L,
                        "关系模型用二维表描述实体与联系，关系操作与完整性约束是另外两部分。"),
                profile("KP0202", "关系代数", 2L,
                        "关系代数用选择、投影、连接等运算表达查询，是关系操作的形式化语言。"),
                profile("KP0401", "数据库安全性", 4L,
                        "数据库安全性是指保护数据库，防止不合法使用造成数据泄露、篡改或破坏。")));

        String stem = "数据库安全性是指保护数据库，防止不合法使用造成数据泄露";

        // score-scale 取 1.0：暴露原始余弦，任何 df/N 漂移都会直接改变数值
        KpMappingService rawService = newService(1.0, CONFIRM_FLOOR, true);
        MapResult global = rawService.match(request(stem, null));
        MapResult scoped = rawService.match(request(stem, 4L));

        // 章 4 只有 1 个知识点：若实现"先裁剪再建 df/N"，该分会被显著抬高
        assertEquals(scoreOf(global, "KP0401"), scoreOf(scoped, "KP0401"), 0.0,
                "章过滤只能缩小候选集合，不能改变统计范围");
        assertEquals("KP0401", global.top1().kpId());
        assertEquals("KP0401", scoped.top1().kpId());
        assertEquals(1, scoped.candidates().size());
    }

    // ---------- UT4：章内无知识点时回退全量（既有语义，防回归） ----------

    @Test
    @DisplayName("UT4 传入不存在的 chapterId 时候选集合回退为全量")
    void chapterScopeFallsBackWhenChapterHasNoKp() {
        when(knowledgePointService.listMatchProfiles(COURSE_ID)).thenReturn(List.of(
                profile("KP0201", "关系模型", 2L, "关系模型用二维表描述实体与联系。"),
                profile("KP0401", "数据库安全性", 4L,
                        "数据库安全性是指保护数据库，防止不合法使用造成数据泄露、篡改或破坏。")));

        String stem = "数据库安全性是指保护数据库，防止不合法使用造成数据泄露";
        KpMappingService rawService = newService(1.0, CONFIRM_FLOOR, true);

        MapResult global = rawService.match(request(stem, null));
        MapResult fallback = rawService.match(request(stem, 99L));

        assertEquals(global.candidates().size(), fallback.candidates().size());
        assertEquals("KP0401", fallback.top1().kpId());
        assertEquals(scoreOf(global, "KP0401"), scoreOf(fallback, "KP0401"), 0.0);
    }

    // ---------- UT5：排序键必须是原始余弦（D3） ----------

    @Test
    @DisplayName("UT5 两个候选分数都饱和到 1.0 时，top1 仍按原始余弦而非 kpId 决定")
    void saturatedCandidatesRankByCosineNotByScore() {
        when(knowledgePointService.listMatchProfiles(COURSE_ID)).thenReturn(List.of(
                // kpId 更小、语料更长（稀释更多）→ 余弦更低；
                // 若按饱和后的 score 排序并以其为次序键，它会错误地成为 top1
                profile("KP0201", "候选码", 2L,
                        "候选码是能唯一标识元组且真子集不能唯一标识的属性组，主码从候选码中选定。"),
                profile("KP0202", "候选码", 2L,
                        "候选码是能唯一标识元组且真子集不能唯一标识的属性组。")));

        String stem = "候选码的真子集不能唯一标识元组";

        // 1) 先读出原始余弦（score-scale=1.0 时 score 就是舍入到 4 位的余弦）
        MapResult raw = newService(1.0, CONFIRM_FLOOR, true).match(request(stem, null));
        double cosineA = scoreOf(raw, "KP0201");
        double cosineB = scoreOf(raw, "KP0202");
        assertTrue(cosineA > 0.0, "用例失效：KP0201 余弦必须为正");
        assertTrue(cosineB > cosineA, "用例失效：需要 KP0202 的余弦严格更高");

        // 2) 把 score-scale 压到最小余弦的一半 → 两个候选都饱和到 1.0
        double scale = Math.min(cosineA, cosineB) * 0.5;
        MapResult saturated = newService(scale, CONFIRM_FLOOR, true).match(request(stem, null));

        assertEquals(2, saturated.candidates().size());
        assertEquals(1.0, saturated.candidates().get(0).score(), 1e-9, "两个候选都应饱和");
        assertEquals(1.0, saturated.candidates().get(1).score(), 1e-9, "两个候选都应饱和");
        assertEquals("KP0202", saturated.top1().kpId(),
                "分数打平后必须回落到原始余弦，而不是按 kpId 字母序");
    }

    // ---------- UT7：决策边界随配置生效 ----------

    @Test
    @DisplayName("UT7 score 0.34/0.35/0.36 的 FAILED/CONFIRM 分界随 confirm-floor 生效")
    void decisionBoundariesUseFloor035() {
        when(knowledgePointService.listMatchProfiles(COURSE_ID)).thenReturn(List.of(
                profile("KP0203", "候选码", 2L,
                        "候选码是能唯一标识元组且真子集不能唯一标识的属性组。")));

        MapRequest request = request("候选码的真子集不能唯一标识元组", null);

        // 用 score-scale=1.0 读出原始余弦，再反解出让 score 恰好落在目标值的 score-scale
        double cosine = newService(1.0, CONFIRM_FLOOR, true).match(request).top1().score();
        assertTrue(cosine > 0.0 && cosine < 1.0, "用例失效：需要 0 < 余弦 < 1，实得 " + cosine);

        assertEquals("FAILED", newService(cosine / 0.34, CONFIRM_FLOOR, true).match(request).decision(),
                "0.34 < floor(0.35) 应为 FAILED");
        assertEquals("CONFIRM", newService(cosine / 0.35, CONFIRM_FLOOR, true).match(request).decision(),
                "0.35 不小于 floor(0.35) 且未达 auto(0.60) 应为 CONFIRM");
        assertEquals("CONFIRM", newService(cosine / 0.36, CONFIRM_FLOOR, true).match(request).decision(),
                "0.36 应为 CONFIRM");

        // 同一 0.36 分在旧下限 0.40 下必须 FAILED —— 证明分界读的是配置而不是硬编码
        assertEquals("FAILED", newService(cosine / 0.36, 0.40, true).match(request).decision(),
                "floor=0.40 时 0.36 应被判 FAILED");

        // 单候选时 top2 视为 0，领先差恒满足 margin
        assertEquals("AUTO", newService(cosine / 0.60, CONFIRM_FLOOR, true).match(request).decision(),
                "0.60 且单候选应为 AUTO");
    }

    // ---------- 边界守卫（防止空值把 500 带进接口） ----------

    @Test
    @DisplayName("UT8 text 为 null 时直接 FAILED，不抛 NPE（画像非空，隔离出空值守卫）")
    void nullTextReturnsFailed() {
        when(knowledgePointService.listMatchProfiles(COURSE_ID)).thenReturn(List.of(
                profile("KP0203", "候选码", 2L, "候选码是能唯一标识元组的属性组。")));

        MapResult result = service.match(request(null, null));

        assertEquals("FAILED", result.decision());
        assertNull(result.top1());
        assertTrue(result.candidates().isEmpty());
    }

    @Test
    @DisplayName("UT9 misconception 为 null 与空串等价（覆盖 nullToEmpty，不把字面量 \"null\" 写进语料）")
    void nullMisconceptionBehavesLikeEmpty() {
        String concept = "候选码是能唯一标识元组且真子集不能唯一标识的属性组。";
        when(knowledgePointService.listMatchProfiles(COURSE_ID)).thenReturn(List.of(
                profile("KP0201", "候选码", 2L, concept, null),
                profile("KP0202", "候选码", 2L, concept, "")));

        // score-scale 取 1.0：暴露原始余弦，语料若因 null 多出一个 "null" 词元会立刻显现
        MapResult result = newService(1.0, CONFIRM_FLOOR, true)
                .match(request("候选码的真子集不能唯一标识元组", null));

        assertEquals(2, result.candidates().size());
        assertEquals(scoreOf(result, "KP0201"), scoreOf(result, "KP0202"), 1e-9,
                "null 与空串必须产生完全相同的语料");
        assertEquals("KP0201", result.top1().kpId());
    }
}
