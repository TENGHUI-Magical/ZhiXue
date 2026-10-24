<template>
  <div class="page">
    <h2>错题诊断工作台</h2>

    <!-- 需求规格说明书 6 节状态机：上传未解析→B 区灰占位；OCR 中→骨架屏；低置信度→黄色警示 -->
    <el-row :gutter="16">
      <el-col :span="10">
        <el-card header="① 上传错题图片">
          <el-upload
            drag
            name="image"
            :http-request="handleUpload"
            :show-file-list="false"
          >
            <div class="upload-tip">拖拽错题图片至此，或点击选择文件</div>
            <div class="upload-sub">建议单题截图，避免强光倒影与手写遮挡</div>
          </el-upload>
        </el-card>
      </el-col>

      <el-col :span="14">
        <el-card
          header="② 识别结果与人工校正"
          :class="{ 'warn-border': lowConfidence }"
        >
          <!-- TODO(P0 契约 2.2)：taskId 轮询 → SUCCESS 填充表单；FAILED → 手动录入降级弹窗 -->
          <!-- 上传后进入骨架屏（需求规格说明书硬约束：骨架屏过渡），轮询返回后关闭 -->
          <el-skeleton v-if="parsing" :rows="5" animated class="ocr-skeleton" />
          <el-form v-else label-width="100px" disabled>
            <el-form-item label="识别状态">待上传（PENDING）</el-form-item>
            <el-form-item label="题干">
              <el-input
                type="textarea"
                placeholder="OCR 解析后自动填充，可人工修正"
              />
            </el-form-item>
            <el-form-item label="题目类型"
              ><el-select placeholder="自动预判"
            /></el-form-item>
            <el-form-item label="关联知识点"
              ><el-select placeholder="置信度不足时黄色高亮，强制人工确认"
            /></el-form-item>
            <el-form-item label="归因错因">
              <el-select placeholder="5 类标准错因（数据标注规范 3 节）" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary">确认入库并生成启发式讲解</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>

    <el-card
      header="③ 启发式讲解（SSE 流式输出，契约 2.4 节）"
      class="explain-card"
    >
      <!-- TODO(P0)：fetch + ReadableStream 手动解析 SSE，禁止 EventSource（避坑清单 S1）；打字机渲染 + 引用来源底部展示 -->
      <el-empty
        description="确认错题后，讲解将以流式输出展示于此，并标注教材/讲义引用来源"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { ElMessage } from "element-plus";
import type { UploadRequestOptions } from "element-plus";
import { api } from "../api/request";

// 骨架期占位：接契约 2.2 节轮询后替换为真实状态机
const lowConfidence = ref(false);
const parsing = ref(false);

/** 契约 2.2 第一步响应 data */
interface ParseAccepted {
  taskId: string;
}

/** 走 axios 上传，自动带 satoken（避坑清单 A2）；不再用 el-upload 原生 action */
const handleUpload = async (options: UploadRequestOptions) => {
  const form = new FormData();
  form.append("image", options.file);
  try {
    const data = await api.post<ParseAccepted>("/api/diagnosis/ocr/parse", form);
    onUploaded(data);
  } catch {
    ElMessage.error("上传失败，请检查后端服务后重试");
  }
};

const onUploaded = (data: ParseAccepted) => {
  parsing.value = true;
  // TODO(P0)：setInterval 1.5s 轮询 GET /api/diagnosis/ocr/task/{taskId}
  //   SUCCESS → { confidence, fields } 填充表单并 parsing=false；confidence < 0.75 置 lowConfidence=true
  //   FAILED  → 弹"手动录入引导"降级表单（契约 2.2 状态机 / 需求规格说明书 6.2 节）
  console.log("upload success, taskId =", data.taskId);
};
</script>

<style scoped>
.page h2 {
  margin-top: 0;
}
.upload-tip {
  font-size: 14px;
  color: #606266;
  padding-top: 20px;
}
.upload-sub {
  font-size: 12px;
  color: #909399;
}
.explain-card {
  margin-top: 16px;
}
.warn-border {
  border-color: #e6a23c;
}
.ocr-skeleton {
  padding: 12px 0;
}
</style>