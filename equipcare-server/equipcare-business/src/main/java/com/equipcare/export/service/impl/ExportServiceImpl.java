package com.equipcare.export.service.impl;

import com.equipcare.common.exception.BizException;
import com.equipcare.common.result.ResultCode;
import com.equipcare.export.mapper.ExportMapper;
import com.equipcare.export.service.ExportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Excel 导出实现。
 *
 * <p>列定义表（表头文本 → 结果 Map 的 key）同时决定列顺序与取值路径——
 * MyBatis 返回的 Map 不保证顺序，顺序由这里的显式列表保证。
 * 导出走 SXSSF 流式写并自动 flush，单表 5000 行内存可控。
 */
@Service
@RequiredArgsConstructor
public class ExportServiceImpl implements ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportServiceImpl.class);

    /** 单文件行数上限：导出是给人看的报表，超量请缩小时间范围 */
    private static final int MAX_ROWS = 5000;
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 一张表的列定义：[0] 表头，[1] 结果 Map 的 key */
    private record Column(String header, String key) {
    }

    private static final List<Column> DEVICE_COLUMNS = List.of(
            new Column("资产编号", "assetNo"), new Column("设备名称", "equipmentName"),
            new Column("分类", "categoryName"), new Column("位置", "locationPath"),
            new Column("品牌", "brand"), new Column("型号", "model"),
            new Column("状态", "status"), new Column("责任人", "ownerName"),
            new Column("最近巡检", "lastInspectTime"), new Column("下次巡检", "nextInspectTime"));

    private static final List<Column> INSPECTION_COLUMNS = List.of(
            new Column("任务编号", "taskCode"), new Column("设备", "equipmentName"),
            new Column("执行人", "assigneeName"), new Column("计划日期", "planDate"),
            new Column("状态", "status"), new Column("结果", "result"),
            new Column("异常数", "abnormalCount"), new Column("提交时间", "submitTime"),
            new Column("是否逾期", "overdueFlag"));

    private static final List<Column> WORK_ORDER_COLUMNS = List.of(
            new Column("工单号", "orderNo"), new Column("标题", "title"),
            new Column("设备", "equipmentName"), new Column("等级", "level"),
            new Column("状态", "status"), new Column("报修人", "reporterName"),
            new Column("维修人", "assigneeName"), new Column("报修时间", "reportTime"),
            new Column("完成时间", "actualFinishTime"), new Column("是否超时", "overdueFlag"),
            new Column("备件成本(元)", "spareCost"));

    private static final List<Column> INVENTORY_COLUMNS = List.of(
            new Column("备件编码", "partNo"), new Column("备件名称", "partName"),
            new Column("分类", "categoryName"), new Column("规格型号", "spec"),
            new Column("单位", "unit"), new Column("当前库存", "currentStock"),
            new Column("安全库存", "safetyStock"), new Column("单价(元)", "unitPrice"),
            new Column("库位", "warehouseLocation"), new Column("供应商", "supplier"),
            new Column("状态", "status"));

    private static final List<Column> COST_COLUMNS = List.of(
            new Column("工单号", "orderNo"), new Column("设备", "equipmentName"),
            new Column("维修人", "assigneeName"), new Column("完成时间", "actualFinishTime"),
            new Column("工时(h)", "laborHours"), new Column("备件成本(元)", "spareCost"),
            new Column("质量评分", "qualityScore"));

    private final ExportMapper exportMapper;

    @Override
    public void devices(HttpServletResponse response) {
        write(response, "设备清单", DEVICE_COLUMNS, exportMapper.selectDevices(), null);
    }

    @Override
    public void inspections(HttpServletResponse response, LocalDate startDate, LocalDate endDate) {
        Range range = rangeOf(startDate, endDate);
        write(response, "巡检记录", INSPECTION_COLUMNS,
                exportMapper.selectInspections(range.start(), range.endExclusive()), range);
    }

    @Override
    public void workOrders(HttpServletResponse response, LocalDate startDate, LocalDate endDate) {
        Range range = rangeOf(startDate, endDate);
        write(response, "维修工单", WORK_ORDER_COLUMNS,
                exportMapper.selectWorkOrders(range.start(), range.endExclusive(),
                        range.startDateTime(), range.endDateTime()), range);
    }

    @Override
    public void inventory(HttpServletResponse response) {
        write(response, "备件库存", INVENTORY_COLUMNS, exportMapper.selectInventory(), null);
    }

    @Override
    public void costStats(HttpServletResponse response, LocalDate startDate, LocalDate endDate) {
        Range range = rangeOf(startDate, endDate);
        write(response, "维修成本统计", COST_COLUMNS,
                exportMapper.selectCostStats(range.start(), range.endExclusive(),
                        range.startDateTime(), range.endDateTime()), range);
    }

    // ==================================================================
    // 内部实现
    // ==================================================================

    /** 时间范围：为空则导出全部；跨度与单文件行数一样受 366 天约束 */
    private record Range(String start, String endExclusive,
                         String startDateTime, String endDateTime) {
    }

    private Range rangeOf(LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) {
            return new Range(null, null, null, null);
        }
        LocalDate start = startDate == null ? LocalDate.of(2000, 1, 1) : startDate;
        LocalDate end = endDate == null ? LocalDate.now() : endDate;
        if (start.isAfter(end)) {
            throw new BizException(ResultCode.BAD_REQUEST, "起始日期不能晚于结束日期");
        }
        if (end.toEpochDay() - start.toEpochDay() > 366) {
            throw new BizException(ResultCode.BAD_REQUEST, "导出时间跨度不能超过 366 天");
        }
        return new Range(start.toString(), end.plusDays(1).toString(),
                start.atStartOfDay().format(DATETIME), end.plusDays(1).atStartOfDay().format(DATETIME));
    }

    private void write(HttpServletResponse response, String sheetName, List<Column> columns,
                       List<Map<String, Object>> rows, Range range) {
        if (rows != null && rows.size() >= MAX_ROWS) {
            throw new BizException(ResultCode.BAD_REQUEST, "导出数据超过 " + MAX_ROWS + " 行上限，请缩小时间范围");
        }
        String fileName = URLEncoder.encode(sheetName + "_"
                + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx", StandardCharsets.UTF_8);
        response.reset();
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName
                + "\"; filename*=UTF-8''" + fileName);

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(200)) {
            Sheet sheet = workbook.createSheet(sheetName);
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns.get(i).header());
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 16 * 256);
            }

            if (rows != null) {
                int rowIdx = 1;
                for (Map<String, Object> row : rows) {
                    Row dataRow = sheet.createRow(rowIdx++);
                    for (int i = 0; i < columns.size(); i++) {
                        dataRow.createCell(i).setCellValue(displayValue(row.get(columns.get(i).key())));
                    }
                }
            }

            if (range != null) {
                Sheet note = workbook.createSheet("导出说明");
                Row noteRow = note.createRow(0);
                noteRow.createCell(0).setCellValue("统计范围：" + range.start() + " 至 "
                        + LocalDate.parse(range.endExclusive()).minusDays(1) + "（含）；行数上限 " + MAX_ROWS);
            }

            workbook.write(response.getOutputStream());
            workbook.dispose();
        } catch (IOException e) {
            log.error("Excel 导出失败 sheet={}", sheetName, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "导出失败，请稍后重试");
        }
    }

    /** 单元格显示值：null → 空串；时间 → 统一格式；其余转字符串 */
    private String displayValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDateTime dateTime) {
            return DATETIME.format(dateTime);
        }
        // java.sql.Date 不支持 toInstant，且无时分秒，须先于 java.util.Date 分支处理
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
        }
        if (value instanceof java.util.Date date) {
            return DATETIME.format(date.toInstant().atZone(java.time.ZoneId.systemDefault()));
        }
        return value.toString();
    }
}
