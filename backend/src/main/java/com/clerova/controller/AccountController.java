package com.clerova.controller;

import com.clerova.dto.CompanyInfo;
import com.clerova.dto.MeResponse;
import com.clerova.dto.OnboardingRequest;
import com.clerova.dto.UpdateAccountRequest;
import com.clerova.security.CurrentUserService;
import com.clerova.service.AccountService;
import com.clerova.service.CompanyInfoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AccountController {

    private final AccountService accountService;
    private final CompanyInfoService companyInfoService;
    private final CurrentUserService currentUserService;

    public AccountController(
            AccountService accountService,
            CompanyInfoService companyInfoService,
            CurrentUserService currentUserService
    ) {
        this.accountService = accountService;
        this.companyInfoService = companyInfoService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/me")
    public MeResponse getMe() {
        return accountService.getMe();
    }

    @PatchMapping("/me")
    public MeResponse updateAccount(@Valid @RequestBody UpdateAccountRequest request) {
        return accountService.updateAccount(request);
    }

    /** The company's standard answers that suggested replies can use. */
    @GetMapping("/company-info")
    public CompanyInfo getCompanyInfo() {
        return companyInfoService.get(currentUserService.requireOrganizationId());
    }

    @PutMapping("/company-info")
    public CompanyInfo saveCompanyInfo(@Valid @RequestBody CompanyInfo info) {
        return companyInfoService.save(currentUserService.requireOrganizationId(), info);
    }

    @PostMapping("/onboarding")
    @ResponseStatus(HttpStatus.CREATED)
    public MeResponse completeOnboarding(
            @Valid @RequestBody OnboardingRequest request
    ) {
        return accountService.completeOnboarding(request);
    }
}
