package com.zhixue.diagnosis.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prompt 版本单一来源校验：Java 缓存键使用的版本与 AI 服务生成讲解的版本必须一致。
 * 两侧曾各自硬编码 v2；Python 单方面升版时，Java 仍按旧版键读取缓存，旧讲解最长滞留 7 天（TTL）。
 */
class PromptVersionConsistencyTest {

    private static final Pattern PROMPT_VERSION_PATTERN =
            Pattern.compile("PROMPT_VERSION\\s*=\\s*\"([^\"]+)\"");

    @Test
    void javaPromptVersionEqualsAiServicePromptVersion() throws IOException {
        String python = Files.readString(locateAiRagService(), StandardCharsets.UTF_8);
        Matcher matcher = PROMPT_VERSION_PATTERN.matcher(python);
        assertThat(matcher.find())
                .as("zhixue-ai/app/services/rag_service.py 必须声明 PROMPT_VERSION")
                .isTrue();
        assertThat(matcher.group(1))
                .as("RagExplainService.PROMPT_VERSION 与 rag_service.PROMPT_VERSION 必须一致")
                .isEqualTo(RagExplainService.PROMPT_VERSION);
    }

    /** 从当前工作目录向上查找 monorepo 中的 AI 服务文件，兼容模块目录/仓库根两种执行位置。 */
    private Path locateAiRagService() {
        List<Path> searched = new ArrayList<>();
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve("zhixue-ai/app/services/rag_service.py");
            searched.add(candidate);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new AssertionError("找不到 zhixue-ai/app/services/rag_service.py，已查找：" + searched);
    }
}
