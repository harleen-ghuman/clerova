package com.clerova.trucking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CarrierProfileRepository extends JpaRepository<CarrierProfile, UUID> {
}
