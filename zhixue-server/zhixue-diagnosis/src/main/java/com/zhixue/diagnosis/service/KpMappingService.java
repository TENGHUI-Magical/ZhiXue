package com.zhixue.diagnosis.service;

import com.zhixue.course.dto.KpMatchProfile;
import com.zhixue.course.entity.KpKeyword;
import com.zhixue.course.service.KnowledgePointService;
import com.zhixue.diagnosis.dto.MapCandidate;
import com.zhixue.diagnosis.dto.MapRequest;
import com.zhixue.diagnosis.dto.MapResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 知识点映射（契约 V2：POST /api/diagnosis/map）。
 * <p>
 * 当前实现的是向量通道的词法近似：概念切片字符二元组 TF-IDF 余弦。
 * 选型依据见 技术学习/15-映射top1评测-V1.md（60 题 top-1 73.3%，高于关键词通道 48.3%）。
 * 关键词只用于前端命中解释，不参与排序；bge 向量接入后替换本类内部实现，接口不变。
 * <p>
 * 分数归一到契约的 0—1 标度：score = min(1, cos / mapping.score-scale)，
 * 决策阈值读 application.yml 的 mapping.*，代码不写死数字。
 */
@Service
public class KpMappingService {

    /** 二元组 IDF 指数；1.5 比 1.0 更能压住“关系/查询/SQL”这类高频词 */
    private static final double IDF_POWER = 1.5;

    /** 知识点名称在语料中重复次数，提高标题词的区分度 */
    private static final int NAME_REPEAT = 3;

    /** 每项最多返回的命中关键词数（仅用于展示） */
    private static final int MAX_MATCHED_KEYWORDS = 8;

    /** 关键词展示的最小长度，单字噪声太大 */
    private static final int MIN_KEYWORD_LENGTH = 2;

    private final KnowledgePointService knowledgePointService;
    private final double scoreScale;
    private final double autoThreshold;
    private final double margin;
    private final double confirmFloor;

    public KpMappingService(KnowledgePointService knowledgePointService,
                            @Value("${mapping.score-scale:0.4}") double scoreScale,
                            @Value("${mapping.auto-threshold:0.60}") double autoThreshold,
                            @Value("${mapping.margin:0.10}") double margin,
                            @Value("${mapping.confirm-floor:0.40}") double confirmFloor) {
        this.knowledgePointService = knowledgePointService;
        this.scoreScale = scoreScale;
        this.autoThreshold = autoThreshold;
        this.margin = margin;
        this.confirmFloor = confirmFloor;
    }

    /**
     * 将题干映射到候选知识点，并给出 AUTO / CONFIRM / FAILED 决策。
     *
     * @param request 题干、课程、可选章节
     * @return 契约 MapResult；无候选时 decision=FAILED、top1=null
     */
    public MapResult match(MapRequest request) {
        List<KpMatchProfile> profiles = knowledgePointService.listMatchProfiles(request.getCourseId());
        if (profiles.isEmpty() || request.getText().trim().length() < 5) {
            return new MapResult("FAILED", null, List.of(), false);
        }

        List<Map<String, Integer>> docTermCounts = new ArrayList<>(profiles.size());
        Map<String, Integer> docFreq = new HashMap<>();
        for (KpMatchProfile profile : profiles) {
            Map<String, Integer> counts = termCounts(corpusOf(profile));
            docTermCounts.add(counts);
            for (String gram : counts.keySet()) {
                docFreq.merge(gram, 1, Integer::sum);
            }
        }

        int docCount = profiles.size();
        List<Map<String, Double>> docVectors = new ArrayList<>(docCount);
        for (Map<String, Integer> counts : docTermCounts) {
            docVectors.add(vectorize(counts, docFreq, docCount));
        }
        Map<String, Double> queryVector = vectorize(termCounts(request.getText()), docFreq, docCount);

        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < docCount; i++) {
            KpMatchProfile profile = profiles.get(i);
            double cosine = cosine(queryVector, docVectors.get(i));
            if (cosine > 0) {
                scored.add(new Scored(profile, cosine,
                        matchedKeywords(profile.keywords(), request.getText())));
            }
        }
        scored.sort(Comparator.comparingDouble(Scored::cosine).reversed()
                .thenComparing(item -> item.profile().kpId()));

        List<MapCandidate> candidates = scored.stream()
                .map(item -> new MapCandidate(item.profile().kpId(), item.profile().name(),
                        calibrate(item.cosine()), "VECTOR"))
                .toList();

        String decision = decide(candidates);
        MapCandidate top1 = candidates.isEmpty() ? null : candidates.get(0);
        return new MapResult(decision, top1, candidates, false);
    }

    /** 把词法余弦归一到契约标度（0—1） */
    private double calibrate(double cosine) {
        double scaled = scoreScale <= 0 ? cosine : cosine / scoreScale;
        return round4(Math.min(1.0, Math.max(0.0, scaled)));
    }

    /** 契约 05-算法 1.4 的决策规则 */
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

    /** 语料 = 概念切片 + 名称重复 3 次；名称单独出现时权重更高 */
    private String corpusOf(KpMatchProfile profile) {
        String concept = profile.conceptText() == null ? "" : profile.conceptText();
        String name = profile.name() == null ? "" : profile.name();
        StringBuilder sb = new StringBuilder(concept.length() + name.length() * NAME_REPEAT + 8);
        sb.append(concept);
        for (int i = 0; i < NAME_REPEAT; i++) {
            sb.append(' ').append(name);
        }
        return sb.toString();
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
        if (rawText == null || keywords.isEmpty()) {
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
}
