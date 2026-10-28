package com.zhixue.diagnosis.dto;

import java.util.List;

/**
 * 教师评阅分页（契约 openapi ReviewPage）。
 */
public record ReviewPageView(int page, int size, long total, List<ReviewItemView> records) {
}
