package com.zhixue.diagnosis.dto;

import java.util.List;

/**
 * 映射结果（契约 MapResult）。
 *
 * @param decision   AUTO（自动接受）/ CONFIRM（人工确认）/ FAILED（无匹配）
 * @param top1       最高分候选；FAILED 时为 null
 * @param candidates 按分数倒序的候选列表
 * @param degraded   是否发生降级
 */
public record MapResult(String decision, MapCandidate top1, List<MapCandidate> candidates,
                        boolean degraded) {
}
