package com.zhixue.diagnosis.dto;

import java.util.List;

/**
 * 错题列表分页视图（契约 V2 / openapi ErrorRecordPage）。
 * 不复用 MyBatis-Plus Page：其序列化字段是 current/size，与契约的 page 不一致。
 *
 * @param page    当前页码，从 1 起
 * @param size    每页条数（1—50）
 * @param total   过滤后的总条数
 * @param records 当前页记录，创建时间倒序
 */
public record ErrorRecordPageView(int page, int size, long total, List<ErrorRecordView> records) {
}
