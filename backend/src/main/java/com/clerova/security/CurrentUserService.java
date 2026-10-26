package com.clerova.security;

import com.clerova.domain.AppUser;
import com.clerova.exception.ApiException;
import com.clerova.repository.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the signed-in user and their organization from the request's
 * Supabase access token.
 */
@Service
public class CurrentUserService {

    private final AppUserRepository appUserRepository;

    public CurrentUserService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    public Jwt currentToken() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }

        throw new ApiException(
                HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED",
                "Please sign in to continue."
        );
    }

    public UUID currentUserId() {
        return UUID.fromString(currentToken().getSubject());
    }

    public String currentEmail() {
        String email = currentToken().getClaimAsString("email");
        return email != null ? email : "";
    }

    public Optional<AppUser> findCurrentUser() {
        return appUserRepository.findById(currentUserId());
    }

    /** The current user, who must have completed onboarding. */
    public AppUser requireUser() {
        return findCurrentUser().orElseThrow(() -> new ApiException(
                HttpStatus.FORBIDDEN,
                "ONBOARDING_REQUIRED",
                "Finish setting up your workspace to continue."
        ));
    }

    public UUID requireOrganizationId() {
        return requireUser().getOrganization().getId();
    }
}
