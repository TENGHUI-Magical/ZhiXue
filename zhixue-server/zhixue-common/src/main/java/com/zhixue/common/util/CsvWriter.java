package com.zhixue.common.util;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * CSV 导出工具（教师端学期材料）：UTF-8 + BOM（Excel 打开中文不乱码），
 * RFC 4180 转义（字段含逗号/引号/换行时双引号包裹、内部引号翻倍），
 * 换行统一 CRLF（Excel/记事本兼容）。
 */
public final class CsvWriter {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private CsvWriter() {
    }

    /**
     * 生成完整 CSV 字节（含 BOM）。
     *
     * @param header 中文表头
     * @param rows   数据行；null 单元格输出空串
     */
    public static byte[] write(List<String> header, List<List<String>> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(BOM);
        writeRow(out, header);
        for (List<String> row : rows) {
            writeRow(out, row);
        }
        return out.toByteArray();
    }

    private static void writeRow(ByteArrayOutputStream out, List<String> row) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < row.size(); i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(escape(row.get(i)));
        }
        line.append("\r\n");
        out.writeBytes(line.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** RFC 4180：含逗号/引号/CR/LF 的字段用双引号包裹，内部双引号翻倍。 */
    static String escape(String value) {
        String cell = value == null ? "" : value;
        if (cell.indexOf(',') >= 0 || cell.indexOf('"') >= 0
                || cell.indexOf('\r') >= 0 || cell.indexOf('\n') >= 0) {
            return '"' + cell.replace("\"", "\"\"") + '"';
        }
        return cell;
    }
}
