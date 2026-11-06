package com.equipcare.workorder.service;

import com.equipcare.common.result.PageResult;
import com.equipcare.common.query.PageQuery;
import com.equipcare.workorder.dto.SlaPolicyUpdateDTO;
import com.equipcare.workorder.vo.SlaActiveOrderVO;
import com.equipcare.workorder.vo.SlaAlertVO;
import com.equipcare.workorder.vo.SlaPolicyVO;

import java.util.List;

/**
 * SLA 服务：政策配置、活跃工单 SLA 重算、超时预警。
 */
public interface SlaService {

    /** 全部政策（按 P1→P4），含兜底标记 */
    List<SlaPolicyVO> policies();

    /** 更新政策（按等级）；时限立即对后续建单 / 派单 / 提交验收生效 */
    void updatePolicy(String level, SlaPolicyUpdateDTO dto);

    /**
     * 扫描一轮：重算全部活跃工单（4 个进行中状态）的 sla_status，
     * 对新超时的工单登记预警记录并通知升级角色。
     *
     * @return 本轮新发通知的工单数
     */
    int scan();

    /** 活跃工单当前 SLA 状态（只读，不落库、不发通知） */
    List<SlaActiveOrderVO> activeOrders();

    /** 预警记录分页 */
    PageResult<SlaAlertVO> pageAlerts(PageQuery query, String stage, String level);
}
