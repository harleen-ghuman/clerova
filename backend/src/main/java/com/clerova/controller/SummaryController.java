package com.clerova.controller;

import com.clerova.dto.WeekSummaryResponse;
import com.clerova.security.CurrentUserService;
import com.clerova.service.SummaryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SummaryController {

    private final SummaryService summaryService;
    private final CurrentUserService currentUserService;

    public SummaryController(SummaryService summaryService, CurrentUserService currentUserService) {
        this.summaryService = summaryService;
        this.currentUserService = currentUserService;
    }

    /** The last 7 days, for the Today page. */
    @GetMapping("/api/summary")
    public WeekSummaryResponse summary() {
        return summaryService.lastSevenDays(currentUserService.requireOrganizationId());
    }
}
