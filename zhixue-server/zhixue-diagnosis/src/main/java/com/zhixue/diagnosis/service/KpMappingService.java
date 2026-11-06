package com.zhixue.diagnosis.service;

import com.zhixue.course.dto.KpMatchProfile;
import com.zhixue.course.entity.KpKeyword;
import com.zhixue.course.service.KnowledgePointService;
import com.zhixue.diagnosis.dto.MapCandidate;
import com.zhixue.diagnosis.dto.MapRequest;
import com.zhixue.diagnosis.dto.MapResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * 知识点映射（契约 V2：POST /api/diagnosis/map）。
 * <p>
 * 当前实现的是向量通道的词法近似：概念切片 + 学生典型错因的字符二元组 TF-IDF 余弦。
 * 选型与全部指标见 docs/design/mapping-optimization-2026-11-03.md；离线评测脚本
 * {@code tools/eval_mapping_offline.py} 是算法回归门禁（两个语料口径必须标 N）。
 * 关键词只用于解释，不参与排序；bge 向量接入后替换本类内部实现，接口不变。
 * <p>
 * 三条硬规则（改动前必须读）：
 * <ol>
 *   <li><b>排序键 = 原始 cosine</b>，不是校准后的 score。score = min(1, cos/score-scale)，
 *       cos ≥ score-scale 时多个候选会饱和到 1.0，若按 score 排序就退化为按 kpId 字母序。</li>
 *   <li><b>IDF 的 df / N 在全量画像上统计</b>，章过滤只缩小"参与排序的候选集合"，
 *       不能缩小统计范围，否则阈值标尺随章漂移（同一个分在不同章含义不同）。</li>
 *   <li><b>阈值全部读 application.yml 的 mapping.*</b>，代码只给与 yml 同值的默认值。</li>
 * </ol>
 * <p>
 * 2026-11-05 增量（只改工程与可观测性，<b>排序键、决策标尺、契约响应一律不变</b>）：
 * <ul>
 *   <li>语料统计按内容指纹缓存，避免每次请求重建 81 个 TF-IDF 向量；</li>
 *   <li>输出 hubness（知识点与语料质心的余弦，度量“通用近邻/吸引子”程度）、
 *       候选分数差、查询二元组数三项<b>只读诊断指标</b>，仅写 debug 日志，不参与排序与决策；</li>
 *   <li>chapterId 指向空章时回退全量改为显式 warn，补齐降级原因记录（§3.10）。</li>
 * </ul>
 * 诊断指标用于解释“为什么是这个候选”与定位 hubness 型错分；离线实验见
 * {@code spark-output/prototypes/mapping-boost-20261105/}。
 * <p>
 * 2026-11-06 增量（<b>实验开关，默认关闭，关闭时行为与上一版逐位相同</b>）：
 * <ul>
 *   <li>新增 {@code mapping.hubness-swap-enabled}（默认 false，未写入 application.yml）；
 *       开启后仅当“top1 是语料质心高 hubness 吸引子”且“top1/top2 校准分差 &lt; 0.08”时，
 *       交换候选列表前两位，其余顺序、每个候选的 cos/score 原值、决策标尺都不动；</li>
 *   <li>该路径会改变 top1，并可能产生 {@code top1.score < top2.score}（分数倒挂），
 *       与 docs/05 §1.6 隐含的“分数随排序单调”不变量冲突，<b>须人裁决后方可开启</b>；</li>
 *   <li>阈值 H 由全量 hubness 的 p95 静态推导（81 点 → 升序第 77 个），不引入新的可调超参；
 *       G=0.08 ≤ margin=0.10 保证交换集与 AUTO 集不交（静态不变量，非运行时护栏）。</li>
 * </ul>
 * 依据与全部实跑数据见 {@code spark-output/prototypes/mapping-swap-20261106/}；
 * 该项目只在双口径上各 +3 题、统计不显著，<b>不得当作“指标已提高”的证据</b>。
 */
@Service
public class KpMappingService {

    private static final Logger log = LoggerFactory.getLogger(KpMappingService.class);

    /** 二元组 IDF 指数；1.5 比 1.0 更能压住“关系/查询/SQL”这类高频词 */
    private static final double IDF_POWER = 1.5;

    /** 知识点名称在语料中重复次数，提高标题词的区分度 */
    private static final int NAME_REPEAT = 3;

    /** 每项最多返回的命中关键词数（仅用于展示） */
    private static final int MAX_MATCHED_KEYWORDS = 8;

    /** 关键词展示的最小长度，单字噪声太大 */
    private static final int MIN_KEYWORD_LENGTH = 2;

    /**
     * hubness 交换的校准分差上限 G。
     * <p>
     * 取 0.08 而不是可配参数：G ≤ margin(0.10) 是「交换集 ∩ AUTO 集 = ∅」的
     * <b>静态保证</b>——分差小于 margin 的两名本来就不可能判 AUTO，所以交换不会
     * 改变任何一题的 AUTO/CONFIRM 归属。若把 G 调到 &gt; margin，该不变量立即失效。
     */
    static final double SWAP_GAP = 0.08;

    /**
     * 交换阈值分位：H = 全量知识点 hubness 升序的第 95 百分位。
     * 81 个点 → {@code (int)(0.95 * 80) = 76}，即第 77 个（从 0 计）。
     */
    static final double HUB_P95_QUANTILE = 0.95;

    private final KnowledgePointService knowledgePointService;
    private final double scoreScale;
    private final double autoThreshold;
    private final double margin;
    private final double confirmFloor;
    /** 是否把 misconception（学生典型错因）纳入打分语料；false 即回退到 V0 行为 */
    private final boolean useMisconception;
    /** 是否启用 hubness top1↔top2 交换实验开关；默认 false，见类注释 2026-11-06 增量 */
    private final boolean hubnessSwapEnabled;

    /**
     * 语料统计缓存：单个 volatile 引用承载「指纹 + 统计」的不可变对。
     * 指纹与统计必须原子地一起发布：拆成两个独立 volatile 字段时，
     * 「先写 stats、后写指纹」之间会撕裂成 stats=画像B + fingerprint=画像A，
     * 后续画像 A 的请求会静默复用 B 的 df/N/vectors（错误打分、无异常、无日志）。
     */
    private volatile CacheEntry cached;

    /** 指纹与统计的不可变配对；record 组件均为 final，一次写入即可安全发布。 */
    private record CacheEntry(int fingerprint, CorpusStats stats) {
    }

    public KpMappingService(KnowledgePointService knowledgePointService,
                            @Value("${mapping.score-scale:0.3}") double scoreScale,
                            @Value("${mapping.auto-threshold:0.60}") double autoThreshold,
                            @Value("${mapping.margin:0.10}") double margin,
                            @Value("${mapping.confirm-floor}") double confirmFloor,
                            @Value("${mapping.misconception-in-corpus:true}") boolean useMisconception,
                            @Value("${mapping.hubness-swap-enabled:false}") boolean hubnessSwapEnabled) {
        this.knowledgePointService = knowledgePointService;
        this.scoreScale = scoreScale;
        this.autoThreshold = autoThreshold;
        this.margin = margin;
        this.confirmFloor = confirmFloor;
        this.useMisconception = useMisconception;
        this.hubnessSwapEnabled = hubnessSwapEnabled;
        if (hubnessSwapEnabled) {
            // 实验开关必须留痕：它改变 top1 且允许分数倒挂，误开后人要能从日志看出来。
            log.warn("mapping.hubness-swap-enabled=true：已启用 hubness top1↔top2 交换实验路径；"
                    + "该路径会改变 top1 并可能使 top1.score < top2.score（分数倒挂），"
                    + "开启前须完成验收集归属裁决，见 spark-output/prototypes/mapping-swap-20261106/README.md");
        }
    }

    /**
     * 将题干映射到候选知识点，并给出 AUTO / CONFIRM / FAILED 决策。
     *
     * @param request 题干、课程、可选章节
     * @return 契约 MapResult；无候选时 decision=FAILED、top1=null
     */
    public MapResult match(MapRequest request) {
        List<KpMatchProfile> all = knowledgePointService.listMatchProfiles(request.getCourseId());
        if (all.isEmpty() || request.getText() == null || request.getText().trim().length() < 5) {
            return new MapResult("FAILED", null, List.of(), false);
        }

        // 1) 统计口径固定：df / N / 文档向量一律在【全量画像】上建一次，与是否传章无关
        CorpusStats stats = statsFor(all);
        Map<String, Integer> queryTerms = termCounts(request.getText());
        Map<String, Double> queryVector = stats.vectorize(queryTerms);

        // 2) 候选范围受 chapterId 影响；空值一律视为「不传章」，不误报降级 warn
        Long chapterId = normalizeChapterId(request.getChapterId());
        List<KpMatchProfile> scope = all;
        if (chapterId != null) {
            List<KpMatchProfile> scoped = all.stream()
                    .filter(profile -> chapterId.equals(profile.chapterId()))
                    .toList();
            if (!scoped.isEmpty()) {
                scope = scoped;
            } else {
                // 降级必须留痕：chapterId 是 course_chapter 主键而非章序号，配错时静默回退
                // 会让线上"看起来在跑、其实没生效"（AGENTS §3.10 降级记原因）。
                log.warn("知识点映射章过滤降级 courseId={} chapterId={} reason=该章无知识点，回退全量候选",
                        request.getCourseId(), chapterId);
            }
        }

        // 3) 打分与排序：余弦 > 0 的候选全部返回；排序键是原始余弦（见类注释规则 1）
        List<Scored> scored = new ArrayList<>(scope.size());
        for (KpMatchProfile profile : scope) {
            double cosine = stats.cosine(queryVector, profile.kpId());
            if (cosine > 0) {
                scored.add(new Scored(profile, cosine,
                        matchedKeywords(profile.keywords(), request.getText())));
            }
        }
        scored.sort(Comparator.comparingDouble(Scored::cosine).reversed()
                .thenComparing(item -> item.profile().kpId()));

        List<MapCandidate> candidates = new ArrayList<>(scored.size());
        for (Scored item : scored) {
            candidates.add(new MapCandidate(item.profile().kpId(), item.profile().name(),
                    calibrate(item.cosine()), "VECTOR"));
        }
        // 实验开关（默认关闭）：关闭时这里什么都不做，candidates 与上一版逐位相同。
        // 注意：开关开启且传了 chapterId 时，candidates 是章过滤后的子集，而阈值 H 仍按
        // 全量 81 点算——该组合未经 Python 原型验证（原型只跑全量候选），开启前须补测。
        if (hubnessSwapEnabled) {
            applyHubnessSwapWithLog(candidates, stats);
        }

        String decision = decide(candidates);
        MapCandidate top1 = candidates.isEmpty() ? null : candidates.get(0);
        // 只读诊断指标：不参与排序与决策、不进响应体，仅用于解释候选与定位 hubness 型错分。
        if (top1 != null && log.isDebugEnabled()) {
            double gap = candidates.size() > 1 ? top1.score() - candidates.get(1).score() : 1.0;
            log.debug("映射诊断 kpId={} decision={} hubness={} scoreGap={} queryGrams={} candidates={}",
                    top1.kpId(), decision, round4(stats.hubnessOf(top1.kpId())),
                    round4(gap), queryTerms.size(), candidates.size());
        }
        // List.copyOf 保持与上一版 .toList() 相同的不可变语义（调用方不可改候选列表）
        return new MapResult(decision, top1, List.copyOf(candidates), false);
    }

    /**
     * 把「语义上未传章」统一归一化为 null。
     * <p>
     * 契约 {@code MapRequest.chapterId} 是 {@code Long}，不存在字符串空串形态；
     * 上层若传来空白章，应归一化为 null 或 0（反序列化层行为本模块未实测）。
     * 0 / 负数都不是合法的 course_chapter 主键，与 null 一并视为未传章，
     * 避免走进章过滤分支后因「该章无知识点」误报降级 warn。
     */
    private static Long normalizeChapterId(Long raw) {
        return (raw == null || raw <= 0L) ? null : raw;
    }

    /**
     * 按画像内容指纹复用语料统计。知识点语料只随管理员改动变化，
     * 原实现每次请求都重建 N 个 TF-IDF 向量；指纹一致时直接复用，失效时自动重建。
     * 包级可见，便于单测断言复用与失效行为（不对外暴露）。
     */
    CorpusStats statsFor(List<KpMatchProfile> profiles) {
        int fingerprint = corpusFingerprint(profiles);
        // 局部快照：读到的 entry 自洽，指纹与统计必然来自同一次构建。
        CacheEntry entry = cached;
        if (entry != null && entry.fingerprint() == fingerprint) {
            return entry.stats();
        }
        CorpusStats rebuilt = buildStats(profiles);
        // 一次性发布：杜绝「新 stats + 旧指纹」的撕裂窗口（D-01）。
        cached = new CacheEntry(fingerprint, rebuilt);
        return rebuilt;
    }

    /**
     * 指纹只覆盖参与语料构造的字段；keywords 与 chapterId 不参与打分语料。
     * <p>
     * 隐式假设：**kpId 全局唯一且当前只服务单课程**，因此 courseId 不入指纹。
     * 若未来支持多课程且 kpId 可能跨课程重复，必须把 courseId 纳入指纹，
     * 否则不同课程的同名 kpId 会命中同一份缓存而静默错误打分。
     */
    private int corpusFingerprint(List<KpMatchProfile> profiles) {
        int hash = useMisconception ? 1 : 0;
        for (KpMatchProfile profile : profiles) {
            hash = 31 * hash + Objects.hash(profile.kpId(), profile.name(),
                    profile.conceptText(), useMisconception ? profile.misconception() : null);
        }
        return hash;
    }

    /**
     * 语料质心 hubness：知识点向量与「全体知识点向量均值方向」的余弦。
     * 值越高代表越靠近语料分布中心、越容易成为通用近邻（吸引子）。
     * 只读诊断量，<b>不参与排序</b>，避免改变决策标尺；理论依据为 score centering / CSLS。
     */
    private Map<String, Double> computeHubness(Map<String, Map<String, Double>> vectors) {
        Map<String, Double> centroid = new HashMap<>();
        for (Map<String, Double> vector : vectors.values()) {
            for (Map.Entry<String, Double> entry : vector.entrySet()) {
                centroid.merge(entry.getKey(), entry.getValue(), Double::sum);
            }
        }
        double norm = Math.sqrt(centroid.values().stream().mapToDouble(v -> v * v).sum());
        if (norm > 0) {
            centroid.replaceAll((gram, weight) -> weight / norm);
        }
        Map<String, Double> hubness = new HashMap<>(vectors.size() * 2);
        for (Map.Entry<String, Map<String, Double>> entry : vectors.entrySet()) {
            hubness.put(entry.getKey(), cosine(centroid, entry.getValue()));
        }
        return hubness;
    }

    /** 在全量画像上一次性建立 df / N 与每个知识点的 TF-IDF 向量 */
    private CorpusStats buildStats(List<KpMatchProfile> profiles) {
        List<String> kpIds = new ArrayList<>(profiles.size());
        List<Map<String, Integer>> docTermCounts = new ArrayList<>(profiles.size());
        Map<String, Integer> docFreq = new HashMap<>();
        for (KpMatchProfile profile : profiles) {
            Map<String, Integer> counts = termCounts(corpusOf(profile));
            kpIds.add(profile.kpId());
            docTermCounts.add(counts);
            for (String gram : counts.keySet()) {
                docFreq.merge(gram, 1, Integer::sum);
            }
        }
        int docCount = profiles.size();
        Map<String, Map<String, Double>> vectors = new HashMap<>(docCount * 2);
        for (int i = 0; i < docCount; i++) {
            vectors.put(kpIds.get(i), vectorize(docTermCounts.get(i), docFreq, docCount));
        }
        return new CorpusStats(docFreq, docCount, vectors, computeHubness(vectors));
    }

    /** 把词法余弦归一到契约标度（0—1） */
    private double calibrate(double cosine) {
        double scaled = scoreScale <= 0 ? cosine : cosine / scoreScale;
        return round4(Math.min(1.0, Math.max(0.0, scaled)));
    }

    /**
     * 交换判定的纯函数形式：<b>只回答“要不要交换”，不产生副作用</b>。
     * <p>
     * 与 Python 原型 {@code swap_eval.py:324} 的表达式逐字对应：
     * {@code hub > threshold && (score1 - score2) < SWAP_GAP}。写成纯函数是为了让
     * 单测能拿原型导出的 233 条中间量逐条比对，而不必构造 81 点语料。
     * <p>
     * 注意 {@code threshold} 为 {@link Double#POSITIVE_INFINITY}（空语料）时恒为 false。
     *
     * @param hubness    交换前 top1 知识点与语料质心的余弦（未 round 的原值）
     * @param top1Score  交换前 top1 的校准分（已 round4）
     * @param top2Score  交换前 top2 的校准分（已 round4）
     * @param threshold  全量 hubness 的 p95 阈值
     */
    static boolean shouldSwap(double hubness, double top1Score, double top2Score, double threshold) {
        return hubness > threshold && (top1Score - top2Score) < SWAP_GAP;
    }

    /**
     * 全量 hubness 的 p95 阈值；空集合返回正无穷（永不交换）。
     * 与 Python {@code int(0.95 * (len - 1))} 同式，避免分位定义漂移。
     */
    static double percentile95(Collection<Double> values) {
        if (values == null || values.isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compare);
        int index = (int) (HUB_P95_QUANTILE * (sorted.size() - 1));
        return sorted.get(Math.min(Math.max(index, 0), sorted.size() - 1));
    }

    /**
     * 实验路径：仅交换候选列表前两位，其余顺序不动。
     * <p>
     * <b>score 跟候选走</b>——每个候选的 {@code score} 仍是它自己的校准分，不重算；
     * 因此交换后可能出现 {@code top1.score < candidates[1].score}（分数倒挂）。
     * 这是该路径的已知代价，不是 bug；docs/05 §1.6 的隐含不变量会因此失效。
     * <p>
     * 写成「列表 + 阈值 + hubness 取值函数」的静态形式，是为了让单测能直接喂构造好的
     * 候选与 hubness，而不必搭 81 点语料；生产调用传 {@code stats::hubnessOf}。
     *
     * @return 是否发生了交换（供单测与 debug 日志使用）
     */
    static boolean applyHubnessSwap(List<MapCandidate> candidates, double threshold,
                                    ToDoubleFunction<String> hubnessOf) {
        if (candidates == null || candidates.size() < 2) {
            return false;
        }
        MapCandidate first = candidates.get(0);
        MapCandidate second = candidates.get(1);
        double hubness = hubnessOf.applyAsDouble(first.kpId());
        if (!shouldSwap(hubness, first.score(), second.score(), threshold)) {
            return false;
        }
        candidates.set(0, second);
        candidates.set(1, first);
        return true;
    }

    /**
     * 生产调用点：做交换并写 debug 诊断（记录倒挂事实，便于线上定位）。
     * 包级可见，便于单测用真实 {@link CorpusStats} 直接验证接线，不必构造命中语料。
     */
    void applyHubnessSwapWithLog(List<MapCandidate> candidates, CorpusStats stats) {
        MapCandidate first = candidates.isEmpty() ? null : candidates.get(0);
        if (!applyHubnessSwap(candidates, stats.hubnessThreshold(), stats::hubnessOf)) {
            return;
        }
        if (log.isDebugEnabled() && first != null) {
            MapCandidate swappedIn = candidates.get(0);
            log.debug("映射交换 top1<->top2 from={} to={} hubness={} gap={} "
                            + "score1={} score2={}（分数倒挂={}）",
                    first.kpId(), swappedIn.kpId(), round4(stats.hubnessOf(first.kpId())),
                    round4(first.score() - swappedIn.score()),
                    first.score(), swappedIn.score(), swappedIn.score() < first.score());
        }
    }

    /** 契约 05-算法 1.4 的决策规则；阈值全部来自构造注入（application.yml） */
    private String decide(List<MapCandidate> candidates) {
        if (candidates.isEmpty() || candidates.get(0).score() < confirmFloor) {
            return "FAILED";
        }
        double top1 = candidates.get(0).score();
        double top2 = candidates.size() > 1 ? candidates.get(1).score() : 0.0;
        if (top1 >= autoThreshold && top1 - top2 >= margin) {
            return "AUTO";
        }
        return "CONFIRM";
    }

    /**
     * 语料 = 概念切片 + 名称重复 3 次 +（可选）学生典型错因。
     * <p>
     * 错因只追加**一次**：重复会引入新的权重旋钮，而错题映射的输入正是学生写错的表述，
     * 用"错法"匹配"错法"本身就是语义正确的先验（见设计文档 §6.3）。
     */
    private String corpusOf(KpMatchProfile profile) {
        String concept = nullToEmpty(profile.conceptText());
        String name = nullToEmpty(profile.name());
        StringBuilder sb = new StringBuilder(concept.length() + name.length() * NAME_REPEAT + 64);
        sb.append(concept);
        for (int i = 0; i < NAME_REPEAT; i++) {
            sb.append(' ').append(name);
        }
        if (useMisconception) {
            sb.append(' ').append(nullToEmpty(profile.misconception()));
        }
        return sb.toString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** 小写、去标点/空白/符号后切成字符二元组，统计词频 */
    private Map<String, Integer> termCounts(String raw) {
        Map<String, Integer> counts = new HashMap<>();
        if (raw == null || raw.isBlank()) {
            return counts;
        }
        String text = raw.toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{Z}\\p{S}]", "");
        for (int i = 0; i + 2 <= text.length(); i++) {
            counts.merge(text.substring(i, i + 2), 1, Integer::sum);
        }
        return counts;
    }

    /** TF-IDF 向量：tf = 1 + ln(count)，idf = (ln((N+1)/(df+1)) + 1)^1.5，最后做 L2 归一化 */
    private Map<String, Double> vectorize(Map<String, Integer> counts,
                                          Map<String, Integer> docFreq, int docCount) {
        Map<String, Double> vector = new HashMap<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            Integer df = docFreq.get(entry.getKey());
            if (df == null) {
                continue;
            }
            double idf = Math.pow(Math.log((docCount + 1.0) / (df + 1.0)) + 1.0, IDF_POWER);
            vector.put(entry.getKey(), (1.0 + Math.log(entry.getValue())) * idf);
        }
        double norm = Math.sqrt(vector.values().stream().mapToDouble(v -> v * v).sum());
        if (norm == 0) {
            return Map.of();
        }
        Map<String, Double> normalized = new HashMap<>(vector.size());
        for (Map.Entry<String, Double> entry : vector.entrySet()) {
            normalized.put(entry.getKey(), entry.getValue() / norm);
        }
        return normalized;
    }

    private double cosine(Map<String, Double> a, Map<String, Double> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        Map<String, Double> smaller = a.size() <= b.size() ? a : b;
        Map<String, Double> larger = smaller == a ? b : a;
        double sum = 0.0;
        for (Map.Entry<String, Double> entry : smaller.entrySet()) {
            Double other = larger.get(entry.getKey());
            if (other != null) {
                sum += entry.getValue() * other;
            }
        }
        return sum;
    }

    /** 关键词只用于展示命中解释，不影响得分 */
    private List<String> matchedKeywords(List<KpKeyword> keywords, String rawText) {
        if (rawText == null || keywords == null || keywords.isEmpty()) {
            return List.of();
        }
        String text = rawText.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        List<String> hits = new LinkedList<>();
        Set<String> seen = new HashSet<>();
        for (KpKeyword kw : keywords) {
            String keyword = kw.getKeyword() == null ? "" : kw.getKeyword().toLowerCase(Locale.ROOT).trim();
            if (keyword.length() >= MIN_KEYWORD_LENGTH && text.contains(keyword) && seen.add(keyword)) {
                hits.add(kw.getKeyword());
                if (hits.size() >= MAX_MATCHED_KEYWORDS) {
                    break;
                }
            }
        }
        return hits;
    }

    private double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    /** 内部排序载体：保留关键词命中，便于后续扩展 FUSED 通道 */
    private record Scored(KpMatchProfile profile, double cosine, List<String> matchedKeywords) {
    }

    /**
     * 跨请求复用的语料统计：全量 df、全量文档数 N 与 kpId→向量，
     * 由 {@link #statsFor(List)} 按画像内容指纹缓存（见 {@code CacheEntry}）。
     * 章过滤只影响「用哪些候选参与排序」，不改变本对象里的统计范围。
     * 包级可见，便于单测断言缓存复用与 hubness 诊断量；不对外暴露。
     */
    final class CorpusStats {

        private final Map<String, Integer> docFreq;
        private final int docCount;
        private final Map<String, Map<String, Double>> vectors;
        private final Map<String, Double> hubness;
        /**
         * 全量 hubness 的 p95 阈值，构造时算一次。
         * 必须基于<b>全量</b>知识点而非章过滤后的子集——否则阈值随 chapterId 漂移，
         * 同一道题在传章/不传章时交换判定会不一致（与 IDF 的 df/N 口径同源）。
         */
        private final double hubnessThreshold;

        private CorpusStats(Map<String, Integer> docFreq, int docCount,
                            Map<String, Map<String, Double>> vectors,
                            Map<String, Double> hubness) {
            this.docFreq = docFreq;
            this.docCount = docCount;
            this.vectors = vectors;
            this.hubness = hubness;
            this.hubnessThreshold = percentile95(hubness.values());
        }

        /** 只读诊断：知识点与语料质心的余弦，值越高越可能是通用近邻（吸引子） */
        double hubnessOf(String kpId) {
            return hubness.getOrDefault(kpId, 0.0);
        }

        /** 交换阈值（全量 p95）；空语料为正无穷，等价于“永不交换” */
        double hubnessThreshold() {
            return hubnessThreshold;
        }

        private Map<String, Double> vectorize(Map<String, Integer> counts) {
            return KpMappingService.this.vectorize(counts, docFreq, docCount);
        }

        private double cosine(Map<String, Double> queryVector, String kpId) {
            Map<String, Double> documentVector = vectors.get(kpId);
            if (documentVector == null) {
                return 0.0;
            }
            return KpMappingService.this.cosine(queryVector, documentVector);
        }
    }
}
