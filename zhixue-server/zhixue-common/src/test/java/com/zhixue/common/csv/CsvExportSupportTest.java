package com.zhixue.common.csv;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvExportSupportTest {

    @Test
    void createsBomMetadataHeaderAndReadableMultilineCells() {
        CsvExportFile file = CsvExportSupport.create(
                "teacher-students", "courseId=1;sort=lastActiveDesc",
                List.of("studentKey", "teacherComment"),
                List.of(List.of("20230001", "第一行\n第二行,\"引用\"")));

        byte[] content = file.content();
        assertEquals((byte) 0xEF, content[0]);
        assertEquals((byte) 0xBB, content[1]);
        assertEquals((byte) 0xBF, content[2]);
        String csv = new String(content, StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFF导出时间="));
        assertTrue(csv.contains("; 筛选条件=courseId=1;sort=lastActiveDesc\r\n"));
        assertTrue(csv.contains("studentKey,teacherComment\r\n"));
        assertTrue(csv.contains("20230001,\"第一行\n第二行,\"\"引用\"\"\"\r\n"));
        assertEquals(1, file.rowCount());
        assertTrue(file.filename().matches(
                "teacher-students-\\d{8}-courseId-1-sort-lastActiveDesc\\.csv"), file.filename());
        assertTrue(CsvExportSupport.asciiHeader(file.filterSummary())
                .matches("[\\x20-\\x7E]+"));
    }

    @Test
    void prefixesFormulaLikeCellsIncludingLeadingWhitespace() {
        CsvExportFile file = CsvExportSupport.create(
                "teacher-reviews", "status=ALL", List.of("value"),
                List.of(List.of("=1+1"), List.of("+cmd"), List.of("-2+3"),
                        List.of("@SUM(A1:A2)"), List.of("  =HYPERLINK(\"x\")"),
                        List.of("普通文本")));
        String csv = new String(file.content(), StandardCharsets.UTF_8);

        assertTrue(csv.contains("'=1+1"));
        assertTrue(csv.contains("'+cmd"));
        assertTrue(csv.contains("'-2+3"));
        assertTrue(csv.contains("'@SUM(A1:A2)"));
        assertTrue(csv.contains("'  =HYPERLINK"));
        assertTrue(csv.contains("普通文本"));
        assertFalse(csv.contains("\r\n=1+1"));
    }

    @Test
    void responseCarriesAttachmentDateAndAsciiFilterHeaders() {
        CsvExportFile file = CsvExportSupport.create(
                "teacher-reviews", "status=ALL;kpId=知识点", List.of("logPk"), List.of());
        ResponseEntity<byte[]> response = CsvExportSupport.response(file);

        assertEquals("text/csv;charset=UTF-8", response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)
                .contains(file.filename()));
        assertEquals(file.exportedAt(), response.getHeaders().getFirst("X-Exported-At"));
        String filters = response.getHeaders().getFirst("X-Export-Filters");
        assertTrue(filters.matches("[\\x20-\\x7E]+"));
        assertTrue(filters.contains("%E7%9F%A5%E8%AF%86%E7%82%B9"));
        assertEquals(file.content(), response.getBody());
    }

    @Test
    void legalEmptyResultStillContainsMetadataAndHeader() {
        CsvExportFile file = CsvExportSupport.create(
                "teacher-reviews", "status=PENDING;kpId=all;from=all;to=all",
                List.of("logPk", "explainId"), List.of());
        String csv = new String(file.content(), StandardCharsets.UTF_8);

        assertEquals(0, file.rowCount());
        assertTrue(csv.endsWith("logPk,explainId\r\n"));
        assertEquals(2, csv.split("\\r\\n").length);
    }
}
