package com.zhixue.common.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CSV 导出工具单测：BOM、RFC 4180 转义、CRLF、null 归一。 */
class CsvWriterTest {

    @Test
    void writesBomAndChineseHeaderWithCrlf() {
        byte[] csv = CsvWriter.write(List.of("学生ID", "姓名（脱敏）"),
                List.of(List.of("9001", "集**试")));

        String text = new String(csv, StandardCharsets.UTF_8);
        // BOM：Excel 打开中文不乱码的硬要求
        assertEquals((byte) 0xEF, csv[0]);
        assertEquals((byte) 0xBB, csv[1]);
        assertEquals((byte) 0xBF, csv[2]);
        assertTrue(text.startsWith("\uFEFF学生ID,姓名（脱敏）\r\n"));
        assertTrue(text.contains("9001,集**试\r\n"));
    }

    @Test
    void escapesCommaQuoteAndNewlinePerRfc4180() {
        String cell = CsvWriter.escape("含,逗号\"引号\"\n换行");
        assertEquals("\"含,逗号\"\"引号\"\"\n换行\"", cell);

        assertEquals("普通文本", CsvWriter.escape("普通文本"));
        assertEquals("", CsvWriter.escape(null));
    }

    @Test
    void rowCellsAreWrappedNotConcatenated() {
        byte[] csv = CsvWriter.write(List.of("a", "b"),
                List.of(List.of("1", "2"), List.of("3", "4")));
        String text = new String(csv, StandardCharsets.UTF_8);
        assertEquals("\uFEFFa,b\r\n1,2\r\n3,4\r\n", text);
    }

    /**
     * 公式/DDE 注入中和（2026-11-03）：以 = + - @ TAB CR 开头的单元格前置 ' 中和，
     * Excel/WPS 按纯文本处理。数值列已核查不可能为负（costSeconds 服务端计时或
     * @Min(0) 校验；评分/计数/比率均非负），'-' 一刀切不会误伤数值列。
     */
    @Test
    void neutralizesFormulaAndDdePrefixes() {
        assertEquals("'=SUM(A1:A2)", CsvWriter.escape("=SUM(A1:A2)"));
        assertEquals("'+1+1", CsvWriter.escape("+1+1"));
        assertEquals("'@SUM(A1)", CsvWriter.escape("@SUM(A1)"));
        assertEquals("'-1", CsvWriter.escape("-1"));
        assertEquals("'\tcmd", CsvWriter.escape("\tcmd"));
        // CR 开头：先中和，再按 RFC 4180 包裹（CR 本身触发包裹）
        assertEquals("\"'\rcmd\"", CsvWriter.escape("\rcmd"));
        // 中和后 RFC 4180 行为不变：含引号仍翻倍并包裹
        assertEquals("\"'=HYPERLINK(\"\"A1\"\",\"\"B1\"\")\"",
                CsvWriter.escape("=HYPERLINK(\"A1\",\"B1\")"));
    }

    /** 正常单元格（中文/数字/日期/逗号包裹）不受中和影响；BOM 仍只在流头加一次。 */
    @Test
    void normalCellsUnchangedAndBomWrittenOnce() {
        assertEquals("普通文本", CsvWriter.escape("普通文本"));
        assertEquals("123", CsvWriter.escape("123"));
        assertEquals("2026-11-03 10:00:00", CsvWriter.escape("2026-11-03 10:00:00"));
        assertEquals("\"含,逗号\"", CsvWriter.escape("含,逗号"));

        byte[] csv = CsvWriter.write(List.of("表头"),
                List.of(List.of("=SUM(A1)"), List.of("正常值")));
        assertEquals((byte) 0xEF, csv[0]);
        assertEquals((byte) 0xBB, csv[1]);
        assertEquals((byte) 0xBF, csv[2]);
        String text = new String(csv, StandardCharsets.UTF_8);
        assertEquals(1, text.chars().filter(c -> c == '\uFEFF').count());
        assertTrue(text.contains("'=SUM(A1)\r\n"));
        assertTrue(text.contains("正常值\r\n"));
    }
}
