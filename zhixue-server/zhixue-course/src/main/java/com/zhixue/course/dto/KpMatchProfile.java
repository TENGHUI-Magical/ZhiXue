package com.zhixue.course.dto;

import com.zhixue.course.entity.KpKeyword;

import java.util.List;

/**
 * 知识点映射画像：知识点基本信息 + 概念切片 + 关键词表。
 * 供 diagnosis 模块做相似度打分，避免跨模块直接查 course 的表。
 *
 * @param kpId       知识点编号
 * @param name       知识点名称
 * @param conceptText 概念切片（300—500 字），映射的主语料
 * @param keywords   关键词（含权重），用于前端解释命中原因
 */
public record KpMatchProfile(String kpId, String name, String conceptText, List<KpKeyword> keywords) {
}
