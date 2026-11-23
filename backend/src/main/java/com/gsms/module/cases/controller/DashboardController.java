package com.gsms.module.cases.controller;

import com.gsms.common.R;
import com.gsms.module.cases.dto.DashboardVO;
import com.gsms.module.cases.service.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** 流程总览：统计卡 + 两条流程条 + 待办预警 */
    @GetMapping
    @PreAuthorize("hasAuthority('dashboard:view')")
    public R<DashboardVO> overview() {
        return R.ok(dashboardService.overview());
    }
}
