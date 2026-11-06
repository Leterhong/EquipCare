package com.equipcare.common.query;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.io.Serializable;
import java.util.regex.Pattern;

/**
 * 分页查询基类。所有列表查询 DTO 继承本类。
 *
 * <p>排序列只允许字母、数字和下划线，防止 SQL 注入；未传时使用各模块默认排序。
 */
@Schema(description = "分页查询参数")
public class PageQuery implements Serializable {

    private static final Pattern SAFE_COLUMN = Pattern.compile("^[A-Za-z0-9_]{1,40}$");

    @Schema(description = "页码，从 1 开始", example = "1", defaultValue = "1")
    @Min(value = 1, message = "页码不能小于 1")
    private Integer pageNum = 1;

    @Schema(description = "每页条数，最大 100", example = "10", defaultValue = "10")
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 100, message = "每页条数不能超过 100")
    private Integer pageSize = 10;

    @Schema(description = "排序列（数据库字段名），可选", example = "create_time")
    private String orderBy;

    @Schema(description = "排序方向：asc / desc", example = "desc")
    private String orderDir;

    public Integer getPageNum() {
        return pageNum;
    }

    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public String getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(String orderBy) {
        this.orderBy = orderBy;
    }

    public String getOrderDir() {
        return orderDir;
    }

    public void setOrderDir(String orderDir) {
        this.orderDir = orderDir;
    }

    /** 安全排序字段，非法值返回 null 表示使用默认排序 */
    public String safeOrderBy() {
        if (orderBy == null || orderBy.isBlank() || !SAFE_COLUMN.matcher(orderBy).matches()) {
            return null;
        }
        return orderBy;
    }

    public boolean isAsc() {
        return !"desc".equalsIgnoreCase(orderDir);
    }

    public long offset() {
        return (long) (pageNum - 1) * pageSize;
    }
}
