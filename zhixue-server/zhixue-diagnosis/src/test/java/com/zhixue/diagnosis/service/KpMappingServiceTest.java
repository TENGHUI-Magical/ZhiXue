package com.zhixue.diagnosis.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
 *   <li>UT13 —— CountDownLatch 并发交替画像 A/B，断言缓存内容不自洽（D-01）；</li>
 *   <li>UT14/UT15 —— 空白 chapterId（0/负数/JSON 空串）按未传章处理，不误报降级 warn；</li>
 *   <li>UT16 —— DEBUG 诊断分支开启前后 decision/top1/score 逐位一致。</li>
 *   <li>UT17/UT18 —— hubness 交换阈值 p95 与判定规则，对 Python 原型导出的
 *       81 个 hub / 233 条逐题中间量<b>逐条一致</b>（跨语言一致性，不靠肉眼）；</li>
 *   <li>UT19/UT20 —— 交换只动前两位、score 跟候选走（允许倒挂）、四条排除条件与严格边界；</li>
 *   <li>UT21/UT22/UT23 —— 用真实语料统计验证接线、开关可观测（默认不打 warn / 开启即 warn）、
 *       开启后 match 的 top3 集合 / decision / 「只可能换前两位」三条结构不变量。</li>
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
        return newService(scoreScale, floor, useMisconception, false);
    }

    private KpMappingService newService(double scoreScale, double floor, boolean useMisconception,
                                        boolean hubnessSwapEnabled) {
        return new KpMappingService(knowledgePointService, scoreScale,
                AUTO_THRESHOLD, MARGIN, floor, useMisconception, hubnessSwapEnabled);
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

    // ---------- 2026-11-05 增量：语料统计缓存与 hubness 诊断量 ----------

    @Test
    @DisplayName("UT10 画像内容不变时复用语料统计，内容变化按指纹自动重建")
    void corpusStatsReuseAndInvalidateByFingerprint() {
        List<KpMatchProfile> profiles = List.of(
                profile("KP0203", "候选码", 2L, "候选码是能唯一标识元组的属性组。"),
                profile("KP0401", "数据库安全性", 4L, "数据库安全性防止不合法使用造成数据泄露。"));

        KpMappingService.CorpusStats first = service.statsFor(profiles);
        KpMappingService.CorpusStats second = service.statsFor(profiles);
        assertSame(first, second, "相同画像必须复用同一份语料统计，避免逐请求重建 TF-IDF 向量");

        KpMappingService.CorpusStats changed = service.statsFor(List.of(
                profile("KP0203", "候选码", 2L, "候选码是能唯一标识元组的属性组。"),
                profile("KP0401", "数据库安全性", 4L, "改写后的概念切片。")));
        assertNotSame(first, changed, "画像内容变化必须让指纹失效并重建");

        KpMappingService.CorpusStats backToOld = service.statsFor(profiles);
        assertNotSame(changed, backToOld, "换回旧画像会再次重建；缓存只保留当前内容，行为仍正确");
    }

    @Test
    @DisplayName("UT11 hubness 诊断量：通用表述的知识点比专指术语更靠近语料质心")
    void hubnessIsHigherForGenericWording() {
        // 前两个知识点共享大量通用词，第三个只用专指术语；质心方向应更偏向通用词一侧。
        List<KpMatchProfile> profiles = List.of(
                profile("KP0001", "数据", 1L, "数据库系统中的数据关系查询与数据管理"),
                profile("KP0002", "数据", 1L, "数据库系统中的数据关系查询与数据处理"),
                profile("KP0003", "外码", 1L, "外码参照完整性违约处理级联 Restrict"));

        KpMappingService.CorpusStats stats = service.statsFor(profiles);
        double generic1 = stats.hubnessOf("KP0001");
        double generic2 = stats.hubnessOf("KP0002");
        double specific = stats.hubnessOf("KP0003");

        assertTrue(generic1 >= 0.0 && generic1 <= 1.0, "hubness 应落在 [0,1]");
        assertTrue(specific >= 0.0 && specific <= 1.0, "hubness 应落在 [0,1]");
        assertTrue(generic1 > specific && generic2 > specific,
                "共享通用词的知识点应比专指术语知识点更靠近语料质心（吸引子特征）");
        assertEquals(0.0, stats.hubnessOf("不存在的KP"), "未知知识点诊断量应为 0，不抛异常");
    }

    @Test
    @DisplayName("UT12 诊断量与缓存不改变任何对外行为（回归护栏）")
    void diagnosticsDoNotChangeMappingBehaviour() {
        List<KpMatchProfile> profiles = List.of(
                profile("KP0203", "候选码", 2L, "候选码是能唯一标识元组且真子集不能唯一标识的属性组。"),
                profile("KP0401", "数据库安全性", 4L,
                        "数据库安全性是指保护数据库，防止不合法使用造成数据泄露、篡改或破坏。"));
        when(knowledgePointService.listMatchProfiles(COURSE_ID)).thenReturn(profiles);

        MapRequest req = request("候选码的真子集不能唯一标识元组", null);
        MapResult first = service.match(req);
        MapResult second = service.match(req);

        assertEquals(first.decision(), second.decision());
        assertEquals(first.top1().kpId(), second.top1().kpId());
        assertEquals(first.candidates().size(), second.candidates().size());
        assertEquals(first.candidates().get(0).score(), second.candidates().get(0).score(), 1e-9);
    }

    // ---------- 2026-11-06 增量：D-01 并发护栏 / 空章归一化 / debug 分支 ----------

    @Test
    @DisplayName("UT13 并发交替画像 A/B 时缓存不撕裂：A 永远拿不到 B 的分数")
    void cachedStatsNeverTearUnderConcurrentProfileSwitch() throws Exception {
        // 两份画像的 kpId 集合完全相同、概念文本明显不同：缓存撕裂时不会抛异常，
        // 只会静默拿到另一份画像的 df/N/向量，正是 D-01 的故障形态。
        List<KpMatchProfile> profilesA = List.of(
                profile("KP0203", "候选码", 2L,
                        "候选码是能唯一标识元组且真子集不能唯一标识的属性组，主码从候选码中选定。"),
                profile("KP0401", "数据库安全性", 4L,
                        "数据库安全性是指保护数据库，防止不合法使用造成数据泄露、篡改或破坏。"));
        List<KpMatchProfile> profilesB = List.of(
                profile("KP0203", "连接查询", 2L,
                        "连接查询把两个表按连接条件组合成结果表，嵌套循环与排序合并是常见实现，与候选码无关。"),
                profile("KP0401", "事务", 4L,
                        "事务是用户定义的一个数据库操作序列，具有原子性一致性隔离性持久性，与数据库安全性无关。"));

        ThreadLocal<List<KpMatchProfile>> current = new ThreadLocal<>();
        when(knowledgePointService.listMatchProfiles(COURSE_ID))
                .thenAnswer(invocation -> current.get());

        String stem = "候选码的真子集不能唯一标识元组，主码从候选码中选定";
        current.set(profilesA);
        MapResult baselineA = service.match(request(stem, null));
        current.set(profilesB);
        MapResult baselineB = service.match(request(stem, null));
        current.remove();

        double scoreA = scoreOf(baselineA, "KP0203");
        double scoreB = scoreOf(baselineB, "KP0203");
        assertTrue(Math.abs(scoreA - scoreB) > 1e-6,
                "用例失效：两份画像对同一 kpId 的分数必须明显不同，实得 A=" + scoreA + " B=" + scoreB);

        int threads = 8;
        int rounds = 300;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int index = 0; index < threads; index++) {
            final boolean useA = (index % 2 == 0);
            final double expected = useA ? scoreA : scoreB;
            pool.execute(() -> {
                current.set(useA ? profilesA : profilesB);
                try {
                    start.await();
                    for (int round = 0; round < rounds; round++) {
                        MapResult result = service.match(request(stem, null));
                        double actual = scoreOf(result, "KP0203");
                        if (Math.abs(actual - expected) > 1e-9) {
                            throw new AssertionError(
                                    "缓存撕裂：" + (useA ? "画像A" : "画像B")
                                            + " 期望 KP0203 分数=" + expected
                                            + "，实得=" + actual
                                            + "（疑似复用了另一份画像的语料统计）");
                        }
                    }
                } catch (Throwable error) {
                    failure.compareAndSet(null, error);
                } finally {
                    current.remove();
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS), "并发用例超时（60s）");
        pool.shutdownNow();
        if (failure.get() != null) {
            throw new AssertionError("并发交替画像时缓存内容不自洽", failure.get());
        }
    }

    @Test
    @DisplayName("UT14 chapterId 为 0/负数时视为未传章：不过滤、不误报降级 warn")
    void blankChapterIdIsTreatedAsAbsent() {
        List<KpMatchProfile> profiles = List.of(
                profile("KP0203", "候选码", 2L, "候选码是能唯一标识元组的属性组。"),
                profile("KP0401", "数据库安全性", 4L, "数据库安全性防止不合法使用造成数据泄露。"));
        when(knowledgePointService.listMatchProfiles(COURSE_ID)).thenReturn(profiles);

        Logger logger = (Logger) LoggerFactory.getLogger(KpMappingService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            String stem = "候选码的真子集不能唯一标识元组";
            MapResult absent = service.match(request(stem, null));
            MapResult zero = service.match(request(stem, 0L));
            MapResult negative = service.match(request(stem, -1L));

            assertEquals(absent.candidates().size(), zero.candidates().size());
            assertEquals(absent.candidates().size(), negative.candidates().size());
            assertEquals(absent.top1().kpId(), zero.top1().kpId());
            assertEquals(absent.top1().kpId(), negative.top1().kpId());
            assertEquals(absent.top1().score(), zero.top1().score(), 1e-9);
            assertEquals(absent.top1().score(), negative.top1().score(), 1e-9);
            assertEquals(absent.decision(), zero.decision());
            assertEquals(absent.decision(), negative.decision());
            assertTrue(appender.list.stream().noneMatch(event -> event.getLevel() == Level.WARN),
                    "空白章不应触发「章过滤降级」warn");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    @DisplayName("UT15 空白章不误报 warn，但真实错章仍按既有语义降级并留痕")
    void blankChapterIdDoesNotSuppressRealFallbackWarning() {
        // 契约 MapRequest.chapterId 是 Long（不是 String），空白章在上层应
        // 归一化为 null/0；本模块 test classpath 无 jackson-databind（不新增依赖），
        // 反序列化层行为未实测，故只验证归一化后的 null/0/负数路径，
        // 以及「真实错章仍必须 warn」的对照面。
        List<KpMatchProfile> profiles = List.of(
                profile("KP0203", "候选码", 2L, "候选码是能唯一标识元组的属性组。"),
                profile("KP0401", "数据库安全性", 4L, "数据库安全性防止不合法使用造成数据泄露。"));
        when(knowledgePointService.listMatchProfiles(COURSE_ID)).thenReturn(profiles);

        Logger logger = (Logger) LoggerFactory.getLogger(KpMappingService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            String stem = "候选码的真子集不能唯一标识元组";
            MapResult absent = service.match(request(stem, null));
            appender.list.clear();
            MapResult blank = service.match(request(stem, 0L));
            assertTrue(appender.list.stream()
                            .noneMatch(event -> event.getLevel() == Level.WARN),
                    "空白章（0）不得触发章过滤降级 warn");

            MapResult wrongChapter = service.match(request(stem, 99L));
            assertEquals(absent.candidates().size(), blank.candidates().size());
            assertEquals(absent.candidates().size(), wrongChapter.candidates().size());
            assertEquals(absent.top1().kpId(), wrongChapter.top1().kpId());
            assertEquals(absent.top1().score(), wrongChapter.top1().score(), 1e-9);
            assertTrue(appender.list.stream().anyMatch(
                            event -> event.getLevel() == Level.WARN
                                    && event.getFormattedMessage().contains("章过滤降级")),
                    "真实错章仍必须保留降级 warn（AGENTS §3.10）");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    @DisplayName("UT16 开启 DEBUG 诊断日志后 decision/top1/score 与关闭时逐位一致")
    void debugDiagnosticsDoNotChangeMappingBehaviour() {
        List<KpMatchProfile> profiles = List.of(
                profile("KP0203", "候选码", 2L,
                        "候选码是能唯一标识元组且真子集不能唯一标识的属性组。"),
                profile("KP0401", "数据库安全性", 4L,
                        "数据库安全性是指保护数据库，防止不合法使用造成数据泄露、篡改或破坏。"));
        when(knowledgePointService.listMatchProfiles(COURSE_ID)).thenReturn(profiles);

        Logger logger = (Logger) LoggerFactory.getLogger(KpMappingService.class);
        Level original = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        MapRequest req = request("候选码的真子集不能唯一标识元组", null);
        try {
            MapResult debugOff = service.match(req);

            logger.setLevel(Level.DEBUG);
            assertTrue(logger.isDebugEnabled(), "用例失效：DEBUG 级别未生效");
            MapResult debugOn = service.match(req);

            assertEquals(debugOff.decision(), debugOn.decision());
            assertEquals(debugOff.top1().kpId(), debugOn.top1().kpId());
            assertEquals(debugOff.top1().score(), debugOn.top1().score(), 1e-9);
            assertEquals(debugOff.candidates().size(), debugOn.candidates().size());
            for (int index = 0; index < debugOff.candidates().size(); index++) {
                assertEquals(debugOff.candidates().get(index).score(),
                        debugOn.candidates().get(index).score(), 1e-9);
            }
            assertTrue(appender.list.stream().anyMatch(event -> event.getLevel() == Level.DEBUG),
                    "用例失效：debug 诊断分支未被执行，无法证明零覆盖已补齐");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(original);
        }
    }

    // ---------- hubness 交换实验开关（默认关闭） ----------

    /** Python 原型导出的逐题中间量：v1 TSV，见 mapping-swap-20261106/make_java_fixture.py */
    private record SwapCase(String qId, double hub, double score1, double score2,
                            boolean swapped, String scopes) {
    }

    private record SwapFixture(double threshold, List<Double> hubs, List<SwapCase> cases) {
    }

    private static SwapFixture loadSwapFixture() {
        InputStream in = KpMappingServiceTest.class
                .getResourceAsStream("/mapping/hubness_swap_fixture.tsv");
        assertNotNull(in, "测试资源缺失：/mapping/hubness_swap_fixture.tsv");
        double threshold = Double.NaN;
        List<Double> hubs = new ArrayList<>();
        List<SwapCase> cases = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\t");
                switch (parts[0]) {
                    case "THRESHOLD" -> threshold = Double.parseDouble(parts[1]);
                    case "HUB" -> hubs.add(Double.parseDouble(parts[1]));
                    case "CASE" -> cases.add(new SwapCase(parts[1],
                            Double.parseDouble(parts[2]), Double.parseDouble(parts[3]),
                            Double.parseDouble(parts[4]), "1".equals(parts[5]), parts[6]));
                    default -> {
                        // META 行只用于人读，测试不依赖
                    }
                }
            }
        } catch (IOException failure) {
            throw new AssertionError("读取交换 fixture 失败", failure);
        }
        assertTrue(threshold > 0 && !Double.isNaN(threshold), "fixture 缺少 THRESHOLD");
        return new SwapFixture(threshold, hubs, cases);
    }

    private static List<String> ids(List<MapCandidate> candidates) {
        return candidates.stream().map(MapCandidate::kpId).toList();
    }

    private boolean swapOf(double hubness, double score1, double score2, double threshold) {
        List<MapCandidate> candidates = new ArrayList<>(List.of(
                new MapCandidate("KP0001", "第一名", score1, "VECTOR"),
                new MapCandidate("KP0002", "第二名", score2, "VECTOR")));
        return KpMappingService.applyHubnessSwap(candidates, threshold, kpId -> hubness);
    }

    @Test
    @DisplayName("UT17 p95 阈值对 Python 导出的 81 个 hubness 复算逐位一致，且空/单元素边界正确")
    void percentileNinetyFiveMatchesPythonFixture() {
        SwapFixture fixture = loadSwapFixture();
        assertEquals(81, fixture.hubs().size(), "fixture 必须含全量 81 个 hubness");
        assertEquals(fixture.threshold(),
                KpMappingService.percentile95(fixture.hubs()), 0.0,
                "Java p95 必须与 Python sorted(hub)[int(0.95*(n-1))] 逐位相同");
        // 空语料 → 正无穷，等价于永不交换（不是抛异常、不是 0）
        assertEquals(Double.POSITIVE_INFINITY, KpMappingService.percentile95(List.of()));
        assertEquals(0.42, KpMappingService.percentile95(List.of(0.42)), 0.0);
        // 11 个点：index = (int)(0.95*10) = 9 → 取第 10 个，不等于最大值
        List<Double> eleven = new ArrayList<>();
        for (int index = 0; index < 11; index++) {
            eleven.add(0.10 + index * 0.01);
        }
        double p95 = KpMappingService.percentile95(eleven);
        assertEquals(0.10 + 9 * 0.01, p95, 1e-12);
        assertTrue(p95 < eleven.get(10), "p95 不应等于最大值，否则阈值退化为 max");
    }

    @Test
    @DisplayName("UT18 交换判定对 Python 原型 233 条逐题中间量逐条一致（233:8 / 66:5 命中）")
    void shouldSwapMatchesPythonFixtureCaseByCase() {
        SwapFixture fixture = loadSwapFixture();
        assertEquals(233, fixture.cases().size(), "fixture 必须覆盖 8 章 233 题");
        int hits = 0;
        int hits66 = 0;
        for (SwapCase item : fixture.cases()) {
            boolean actual = KpMappingService.shouldSwap(item.hub(), item.score1(),
                    item.score2(), fixture.threshold());
            assertEquals(item.swapped(), actual,
                    "与 Python 原型不一致：" + item.qId() + " hub=" + item.hub()
                            + " score1=" + item.score1() + " score2=" + item.score2());
            if (actual) {
                hits++;
                if (item.scopes().contains("66")) {
                    hits66++;
                }
            }
        }
        assertEquals(8, hits, "233 口径命中数须与 results.json 的 swap_n=8 一致");
        assertEquals(5, hits66, "66 口径命中数须与 results.json 的 swap_n=5 一致");
    }

    @Test
    @DisplayName("UT19 交换只动前两位：score 跟候选走并允许倒挂，第 3 位以后顺序不变")
    void hubnessSwapOnlySwapsTopTwo() {
        List<MapCandidate> candidates = new ArrayList<>(List.of(
                new MapCandidate("KP0001", "通用吸引子", 0.5000, "VECTOR"),
                new MapCandidate("KP0002", "正确答案", 0.4400, "VECTOR"),
                new MapCandidate("KP0003", "第三名", 0.2000, "VECTOR")));
        // hub 0.30 > 阈值 0.25，分差 0.06 < G 0.08 → 交换
        assertTrue(KpMappingService.applyHubnessSwap(candidates, 0.25, kpId -> 0.30));
        assertEquals(List.of("KP0002", "KP0001", "KP0003"), ids(candidates));
        // score 跟候选走：交换后 top1.score(0.44) < 第二位.score(0.50)，即分数倒挂
        assertEquals(0.44, candidates.get(0).score(), 0.0);
        assertEquals(0.50, candidates.get(1).score(), 0.0);
        assertTrue(candidates.get(0).score() < candidates.get(1).score(),
                "分数倒挂是该路径的已知代价，必须显式断言而不是掩盖");
    }

    @Test
    @DisplayName("UT20 四条排除条件：hub 不过阈值、分差等于/超过 G、候选不足两个")
    void hubnessSwapIsSkippedWhenConditionsFail() {
        // hub 恰好等于阈值：严格 > 才交换
        assertFalse(swapOf(0.25, 0.50, 0.44, 0.25));
        // hub 低于阈值
        assertFalse(swapOf(0.24, 0.50, 0.44, 0.25));
        // 分差恰好等于 G=0.08：严格 < 才交换（0.08 - 0.0 在 double 里精确等于 0.08）
        assertFalse(swapOf(0.30, 0.08, 0.0, 0.25));
        // 分差超过 G
        assertFalse(swapOf(0.30, 0.53, 0.44, 0.25));
        // 候选不足两个
        List<MapCandidate> single = new ArrayList<>(List.of(
                new MapCandidate("KP0001", "唯一", 0.5000, "VECTOR")));
        assertFalse(KpMappingService.applyHubnessSwap(single, 0.25, kpId -> 0.30));
        assertFalse(KpMappingService.applyHubnessSwap(new ArrayList<>(), 0.25, kpId -> 0.30));
        assertFalse(KpMappingService.applyHubnessSwap(null, 0.25, kpId -> 0.30));
    }

    @Test
    @DisplayName("UT21 用真实语料统计接线：hub 最高的 top1 + 小分差 → 换位")
    void hubnessSwapWiredWithRealCorpusStats() {
        List<KpMatchProfile> profiles = List.of(
                profile("KP0501", "数据库完整性概述", 5L,
                        "数据库完整性是指数据的正确性、相容性以及防止数据泄露与篡改"),
                profile("KP0502", "实体完整性", 5L, "实体完整性要求主码非空且唯一"),
                profile("KP0203", "候选码", 2L,
                        "候选码是能唯一标识元组且真子集不能唯一标识的属性组"));
        // 本用例只验证「交换调用点 + 真实语料统计」的接线，不经过 mock，故不 stub
        KpMappingService.CorpusStats stats = service.statsFor(profiles);

        String hubMax = profiles.stream()
                .map(KpMatchProfile::kpId)
                .max((left, right) -> Double.compare(stats.hubnessOf(left), stats.hubnessOf(right)))
                .orElseThrow();
        String other = profiles.stream()
                .map(KpMatchProfile::kpId)
                .filter(kpId -> !kpId.equals(hubMax))
                .findFirst()
                .orElseThrow();
        assertTrue(stats.hubnessOf(hubMax) > stats.hubnessThreshold(),
                "用例失效：小语料里最大 hubness 应严格超过 p95 阈值");

        List<MapCandidate> candidates = new ArrayList<>(List.of(
                new MapCandidate(hubMax, "hub 最高", 0.5000, "VECTOR"),
                new MapCandidate(other, "第二名", 0.4500, "VECTOR")));
        service.applyHubnessSwapWithLog(candidates, stats);
        assertEquals(other, candidates.get(0).kpId(), "生产调用点必须换位");
        assertEquals(hubMax, candidates.get(1).kpId());
    }

    @Test
    @DisplayName("UT22 开关可观测：默认不打 warn，显式开启时启动即 warn（误开有痕）")
    void hubnessSwapSwitchIsObservable() {
        Logger logger = (Logger) LoggerFactory.getLogger(KpMappingService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            newService(SCORE_SCALE, CONFIRM_FLOOR, true);
            assertTrue(appender.list.stream().noneMatch(event ->
                            event.getLevel() == Level.WARN
                                    && event.getFormattedMessage().contains("hubness-swap-enabled")),
                    "默认关闭时不得产生交换开关 warn");

            newService(SCORE_SCALE, CONFIRM_FLOOR, true, true);
            assertTrue(appender.list.stream().anyMatch(event ->
                            event.getLevel() == Level.WARN
                                    && event.getFormattedMessage().contains("hubness-swap-enabled")),
                    "显式开启时必须 warn（AGENTS §3.10 降级/实验留痕）");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    @DisplayName("UT23 开启后 match 的结构不变量：候选数、decision、top3 集合不变，只可能换前两位")
    void enabledSwitchKeepsStructuralInvariants() {
        List<KpMatchProfile> profiles = List.of(
                profile("KP0501", "数据库完整性概述", 5L,
                        "数据库完整性是指数据的正确性、相容性以及防止数据泄露与篡改"),
                profile("KP0502", "实体完整性", 5L, "实体完整性要求主码非空且唯一"),
                profile("KP0203", "候选码", 2L,
                        "候选码是能唯一标识元组且真子集不能唯一标识的属性组"));
        when(knowledgePointService.listMatchProfiles(COURSE_ID)).thenReturn(profiles);
        KpMappingService off = newService(SCORE_SCALE, CONFIRM_FLOOR, true, false);
        KpMappingService on = newService(SCORE_SCALE, CONFIRM_FLOOR, true, true);
        MapRequest req = request("数据库完整性是指数据的正确性、相容性", null);

        MapResult baseline = off.match(req);
        MapResult switched = on.match(req);

        assertEquals(baseline.candidates().size(), switched.candidates().size());
        assertEquals(baseline.decision(), switched.decision(),
                "G=0.08 ≤ margin=0.10 保证交换不改变 AUTO/CONFIRM 归属");
        int top = Math.min(3, baseline.candidates().size());
        assertEquals(new HashSet<>(ids(baseline.candidates()).subList(0, top)),
                new HashSet<>(ids(switched.candidates()).subList(0, top)),
                "交换只能在同一集合内换序，不得引入/移除 top3 成员");
        List<String> left = ids(baseline.candidates());
        List<String> right = ids(switched.candidates());
        if (!left.equals(right)) {
            List<String> expectedSwap = new ArrayList<>(left);
            expectedSwap.set(0, left.get(1));
            expectedSwap.set(1, left.get(0));
            assertEquals(expectedSwap, right, "差异只允许是前两位互换");
        }
    }
}
