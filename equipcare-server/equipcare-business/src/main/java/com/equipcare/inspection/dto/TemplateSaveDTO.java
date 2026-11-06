package com.equipcare.inspection.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 新增 / 编辑巡检模板。
 *
 * <p>检查项整组提交：服务端在一个事务里「删旧建新」，避免逐项 diff 造成排序错乱。
 * 已发布的模板被修改时版本号 +1，历史任务里的项名称与标准是快照，不会被追溯改写。
 */
@Data
@Schema(description = "巡检模板保存请求")
public class TemplateSaveDTO {

    @Schema(description = "模板编码；留空自动生成", example = "TPL-PUMP-DAILY")
    @Pattern(regexp = "^[A-Za-z0-9_-]{2,40}$", message = "模板编码只能是字母、数字、下划线或短横线，长度 2-40")
    private String templateCode;

    @Schema(description = "模板名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "工业水泵日常巡检")
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 100, message = "模板名称不能超过 100 字")
    private String templateName;

    @Schema(description = "适用设备分类；为空表示通用模板")
    private Long categoryId;

    @Schema(description = "巡检说明")
    @Size(max = 500, message = "巡检说明不能超过 500 字")
    private String description;

    @Schema(description = "检查项列表，按提交顺序排序")
    @Valid
    private List<TemplateItemDTO> items;
}
