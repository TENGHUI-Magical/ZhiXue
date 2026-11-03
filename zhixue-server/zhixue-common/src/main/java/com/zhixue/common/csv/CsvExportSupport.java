package com.zhixue.common.csv;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 无第三方依赖的 CSV 生成：BOM、公式注入防护、标准引号与换行转义。 */
public final class CsvExportSupport {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private CsvExportSupport() {
    }

    public static CsvExportFile create(String prefix, String filterSummary,
                                       List<String> headers, List<? extends List<?>> rows) {
        LocalDateTime now = LocalDateTime.now(ZONE);
        String exportedAt = DATE_TIME.format(now);
        StringBuilder csv = new StringBuilder();
        appendRow(csv, List.of("导出时间=" + exportedAt + "; 筛选条件=" + filterSummary));
        appendRow(csv, headers);
        for (List<?> row : rows) {
            appendRow(csv, row);
        }
        byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
        byte[] content = new byte[BOM.length + body.length];
        System.arraycopy(BOM, 0, content, 0, BOM.length);
        System.arraycopy(body, 0, content, BOM.length, body.length);
        String filename = prefix + "-" + FILE_DATE.format(now) + "-"
                + filenamePart(filterSummary) + ".csv";
        return new CsvExportFile(content, filename, exportedAt, filterSummary, rows.size());
    }

    /** 统一 CSV 下载响应；业务异常仍由全局异常处理器返回 JSON 信封。 */
    public static ResponseEntity<byte[]> response(CsvExportFile file) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .contentLength(file.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.filename() + "\"")
                .header("X-Exported-At", file.exportedAt())
                .header("X-Export-Filters", asciiHeader(file.filterSummary()))
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        "Content-Disposition, X-Exported-At, X-Export-Filters")
                .body(file.content());
    }

    /** 响应头只允许 ASCII；保留完整筛选值，非 ASCII 与控制字符均百分号编码。 */
    public static String asciiHeader(String value) {
        if (value == null || value.isBlank()) {
            return "all";
        }
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String filenamePart(String filterSummary) {
        String readable = filterSummary == null ? "all" : filterSummary.replaceAll("[=;,&]+", "-");
        String value = asciiHeader(readable)
                .replaceAll("[^A-Za-z0-9._-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-+|-+$", "");
        return value.isBlank() ? "all" : value;
    }

    private static void appendRow(StringBuilder csv, List<?> row) {
        for (int i = 0; i < row.size(); i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(cell(row.get(i)));
        }
        csv.append("\r\n");
    }

    private static String cell(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        if (formulaLike(text)) {
            text = "'" + text;
        }
        if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0
                || text.indexOf('\r') >= 0 || text.indexOf('\n') >= 0) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }

    private static boolean formulaLike(String text) {
        if (text.isEmpty()) {
            return false;
        }
        int index = 0;
        while (index < text.length()
                && (text.charAt(index) == ' ' || text.charAt(index) == '\t')) {
            index++;
        }
        if (index == text.length()) {
            return false;
        }
        char first = text.charAt(index);
        return first == '=' || first == '+' || first == '-' || first == '@'
                || first == '\r' || first == '\n';
    }
}
