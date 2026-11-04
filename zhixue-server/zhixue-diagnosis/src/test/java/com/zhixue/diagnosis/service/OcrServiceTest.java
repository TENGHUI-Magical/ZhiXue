package com.zhixue.diagnosis.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhixue.common.exception.BizException;
import com.zhixue.diagnosis.config.AiClientProperties;
import com.zhixue.diagnosis.entity.OcrTask;
import com.zhixue.diagnosis.mapper.OcrTaskMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OCR 状态机单测：回收器必须用条件更新，不能无条件覆盖并发完成的 SUCCESS。
 */
@ExtendWith(MockitoExtension.class)
class OcrServiceTest {

    @Mock
    private OcrTaskMapper ocrTaskMapper;

    private OcrService service;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                OcrTask.class);
    }

    @BeforeEach
    void setUp() {
        service = new OcrService(ocrTaskMapper,
                new AiClientProperties("http://127.0.0.1:1", 1), new ObjectMapper());
        // @Value 在纯单测下不注入，maxImageBytes 会保持 0；显式设为 5MB 才能测到后续校验分支
        org.springframework.test.util.ReflectionTestUtils.setField(service, "maxImageBytes", 5_242_880L);
    }

    @Test
    void reclaimUsesConditionalUpdateInsteadOfOverwritingById() {
        OcrTask stale = new OcrTask();
        stale.setTaskId("T1");
        stale.setStatus("PENDING");
        stale.setUpdatedAt(LocalDateTime.now().minusMinutes(5));
        when(ocrTaskMapper.selectList(any())).thenReturn(List.of(stale));
        when(ocrTaskMapper.update(isNull(), any())).thenReturn(1);

        service.reclaimStaleTasks();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<OcrTask>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(ocrTaskMapper).update(isNull(), wrapper.capture());
        String sql = wrapper.getValue().getSqlSegment();
        assertThat(sql).contains("status");
        assertThat(sql).contains("task_id");
        verify(ocrTaskMapper, never()).updateById(any(OcrTask.class));
    }

    /**
     * 回收 deadline 按上海时区计算：把 JVM 默认时区拨到 UTC 后，查询用的 updated_at 下界
     * 仍应与上海当前时间同窗。把 {@code now(ZONE)} 回退成裸 {@code now()} 即红（差 8 小时）。
     */
    @Test
    void reclaimDeadlineUsesShanghaiZoneRegardlessOfJvmDefault() {
        when(ocrTaskMapper.selectList(any())).thenReturn(List.of());

        LocalDateTime shanghaiNow;
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            shanghaiNow = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
            service.reclaimStaleTasks();
        } finally {
            TimeZone.setDefault(original);
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<OcrTask>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(ocrTaskMapper).selectList(captor.capture());
        AbstractWrapper<?, ?, ?> wrapper = (AbstractWrapper<?, ?, ?>) captor.getValue();
        // LambdaQueryWrapper 的参数在生成 SQL 片段时才写入 paramNameValuePairs
        wrapper.getSqlSegment();
        LocalDateTime deadline = wrapper.getParamNameValuePairs().values().stream()
                .filter(LocalDateTime.class::isInstance)
                .map(LocalDateTime.class::cast)
                .findFirst().orElseThrow();
        assertThat(Duration.between(shanghaiNow, deadline).abs().getSeconds()).isLessThan(5);
    }

    /**
     * 上传白名单不能被 Content-Type 绕过：.txt 只要把 part 的 Content-Type 伪造成 image/jpeg，
     * 旧实现会因“扩展名非法 → 回落到 Content-Type 推断 jpg”而放行任意字节。
     */
    @Test
    void disguisedNonImageFileIsRejectedEvenWithImageContentType() {
        MockMultipartFile txt = new MockMultipartFile("file", "note.txt", "image/jpeg",
                "hello".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.submit(txt, "ct-txt-1", 9001L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("JPG");
    }

    /** 契约 §4 周清理：只删过期文件，保留新文件；腾空的子目录一并移除，根目录保留。 */
    @Test
    void purgeExpiredImagesDeletesOnlyStaleFilesAndEmptyDirs(@TempDir Path root) throws IOException {
        Instant cutoff = Instant.now();
        Path staleDir = root.resolve("1").resolve("202501");
        Path freshDir = root.resolve("2").resolve("202602");
        Files.createDirectories(staleDir);
        Files.createDirectories(freshDir);
        Path staleImage = staleDir.resolve("old.jpg");
        Files.writeString(staleImage, "x");
        Files.setLastModifiedTime(staleImage, FileTime.from(cutoff.minusSeconds(3600)));
        Path freshImage = freshDir.resolve("new.jpg");
        Files.writeString(freshImage, "y");
        Files.setLastModifiedTime(freshImage, FileTime.from(cutoff.plusSeconds(3600)));

        int deleted = OcrService.purgeExpiredImages(root, cutoff);

        assertThat(deleted).isEqualTo(1);
        assertThat(staleImage).doesNotExist();
        assertThat(staleDir).doesNotExist();
        assertThat(freshImage).exists();
        assertThat(freshDir).exists();
        assertThat(root).exists();
    }
}
