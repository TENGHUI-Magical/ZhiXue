package com.zhixue.common.util;

/**
 * 姓名脱敏（教师端导出与列表共用）：保留首字与末字，中间打码
 * （“欧阳锋”→“欧*锋”），降低可识别度；2 字保留首字。
 * <p>
 * 单一事实源：班级学情列表（TeacherStudentService）与评阅留痕导出共用同一规则，
 * 禁止各自实现造成两处口径漂移。
 */
public final class NameMasker {

    private NameMasker() {
    }

    public static String mask(String name) {
        if (name == null || name.isBlank()) {
            return "学生";
        }
        String value = name.trim();
        if (value.length() == 1) {
            return value;
        }
        if (value.length() == 2) {
            return value.charAt(0) + "*";
        }
        return value.charAt(0) + "*".repeat(value.length() - 2) + value.charAt(value.length() - 1);
    }
}
