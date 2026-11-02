package com.zhixue.course.dto;

import com.zhixue.course.entity.KpKeyword;

import java.util.List;

/**
 * 知识点映射画像：知识点基本信息 + 概念切片 + 学生典型错因 + 关键词表。
 * 供 diagnosis 模块做相似度打分，避免跨模块直接查 course 的表。
 * <p>
 * 本类型是模块内部 DTO，不是 HTTP 契约；改签名只会影响两个构造点
 * （{@code KnowledgePointService#listMatchProfiles}、{@code KpMappingServiceTest}）。
 *
 * @param kpId        知识点编号
 * @param name        知识点名称
 * @param chapterId   所属章节 ID；映射传 chapterId 时只在该章候选内排序
 * @param conceptText 概念切片（300—500 字），映射的主语料
 * @param misconception 学生典型错因（2—4 条人工撰写文本）；错题映射的第二语料，
 *                      由 {@code mapping.misconception-in-corpus} 开关控制是否入语料
 * @param keywords    关键词（含权重），仅用于前端解释命中原因，不参与排序
 */
public record KpMatchProfile(String kpId, String name, Long chapterId,
                             String conceptText, String misconception,
                             List<KpKeyword> keywords) {
}
