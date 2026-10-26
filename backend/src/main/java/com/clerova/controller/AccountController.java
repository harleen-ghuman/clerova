package com.clerova.controller;

import com.clerova.dto.MeResponse;
import com.clerova.dto.OnboardingRequest;
import com.clerova.dto.UpdateAccountRequest;
import com.clerova.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/me")
    public MeResponse getMe() {
        return accountService.getMe();
    }

    @PatchMapping("/me")
    public MeResponse updateAccount(@Valid @RequestBody UpdateAccountRequest request) {
        return accountService.updateAccount(request);
    }

    @PostMapping("/onboarding")
    @ResponseStatus(HttpStatus.CREATED)
    public MeResponse completeOnboarding(
            @Valid @RequestBody OnboardingRequest request
    ) {
        return accountService.completeOnboarding(request);
    }
}
