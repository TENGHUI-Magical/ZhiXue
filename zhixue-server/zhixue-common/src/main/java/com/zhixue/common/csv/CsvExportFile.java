package com.zhixue.common.csv;

/**
 * 已生成的 CSV 下载内容。exportedAt/filterSummary 同时用于首行元信息与下载响应头。
 */
public record CsvExportFile(byte[] content, String filename, String exportedAt,
                            String filterSummary, int rowCount) {
}
