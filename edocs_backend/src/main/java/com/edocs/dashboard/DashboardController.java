package com.edocs.dashboard;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edocs.dashboard.DashboardService.Dashboard;
import com.edocs.security.CurrentUser;

import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Dashboard")
@RestController
public class DashboardController {

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/dashboard")
    public Dashboard get() {
        return dashboard.build(CurrentUser.get().orgId());
    }
}
