package com.zhixue.diagnosis.dto;

/**
 * 课程维度按错因类型聚合的计数（班级画像 errorDist）。
 * 仅统计标注了知识点且作答错误的记录，与掌握度事件口径一致。
 */
public class ErrorTypeAgg {

    private String errorType;
    private Integer cnt;

    public String getErrorType() {
        return errorType;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    public Integer getCnt() {
        return cnt;
    }

    public void setCnt(Integer cnt) {
        this.cnt = cnt;
    }
}
