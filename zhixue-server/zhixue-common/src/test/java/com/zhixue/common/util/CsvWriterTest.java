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
}
