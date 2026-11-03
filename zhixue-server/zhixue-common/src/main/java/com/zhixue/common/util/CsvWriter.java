package com.zhixue.common.util;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * CSV 导出工具（教师端学期材料）：UTF-8 + BOM（Excel 打开中文不乱码），
 * RFC 4180 转义（字段含逗号/引号/换行时双引号包裹、内部引号翻倍），
 * 公式/DDE 注入中和（以 = + - @ TAB CR 开头的单元格前置 '），
 * 换行统一 CRLF（Excel/记事本兼容）。
 */
public final class CsvWriter {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /** 公式/DDE 注入触发前缀：Excel/WPS 把以这些字符开头的单元格按公式或 DDE 命令解析。 */
    private static final String FORMULA_PREFIXES = "=+-@\t\r";

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

    /**
     * RFC 4180：含逗号/引号/CR/LF 的字段用双引号包裹，内部双引号翻倍。
     *
     * <p>公式/DDE 注入中和（2026-11-03）：以 = + - @ TAB CR 开头的单元格前置 '，
     * 电子表格软件按纯文本处理。选 ' 而非 TAB：' 是 Excel 对公式样输入自带的
     * 文本前缀记号，WPS/各 CSV 解析器行为一致且不会被 trim/分列吞掉；
     * TAB 前缀在部分解析器（如按制表符分列、去前导空白）下行为不确定。
     * 数值列误伤核查：现有调用方唯一可能带符号的数值列是平均耗时，
     * 其来源 practice_record.cost_seconds 只有两类写入：服务端计时
     * （恒为非负，时钟回拨防御为 0）与历史 PLAN 打卡自报（写入时有 @Min(0) 校验；
     * V2.2.9 起新 PLAN 记录恒落 null），
     * 评分 1—5、计数、正确率/掌握度均非负，种子数据无负数，故 '-' 一刀切不误伤。</p>
     */
    static String escape(String value) {
        String cell = value == null ? "" : value;
        if (!cell.isEmpty() && FORMULA_PREFIXES.indexOf(cell.charAt(0)) >= 0) {
            cell = "'" + cell;
        }
        if (cell.indexOf(',') >= 0 || cell.indexOf('"') >= 0
                || cell.indexOf('\r') >= 0 || cell.indexOf('\n') >= 0) {
            return '"' + cell.replace("\"", "\"\"") + '"';
        }
        return cell;
    }
}
