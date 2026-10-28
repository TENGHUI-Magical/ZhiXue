package com.zhixue.course.dto;

/**
 * 章节视图（契约 openapi Chapter）。
 */
public record ChapterView(Long chapterId, Integer chapterNo, String title) {
}
