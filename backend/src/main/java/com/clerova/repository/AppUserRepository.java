package com.clerova.repository;

import com.clerova.domain.AppUser;
import com.clerova.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AppUserRepository
        extends JpaRepository<AppUser, UUID> {

    List<AppUser> findByOrganization_IdAndRole(UUID organizationId, UserRole role);
}
