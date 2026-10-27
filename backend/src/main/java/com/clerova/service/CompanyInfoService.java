package com.clerova.service;

import com.clerova.domain.Organization;
import com.clerova.dto.CompanyInfo;
import com.clerova.exception.ApiException;
import com.clerova.repository.OrganizationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** The company's standard answers, used in suggested replies and drafts. */
@Service
public class CompanyInfoService {

    private static final Logger log = LoggerFactory.getLogger(CompanyInfoService.class);

    private final OrganizationRepository organizationRepository;
    private final ObjectMapper objectMapper;

    public CompanyInfoService(OrganizationRepository organizationRepository, ObjectMapper objectMapper) {
        this.organizationRepository = organizationRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public CompanyInfo get(UUID organizationId) {
        return read(organizationRepository.findById(organizationId).orElseThrow());
    }

    @Transactional
    public CompanyInfo save(UUID organizationId, CompanyInfo info) {
        CompanyInfo cleaned = (info == null ? CompanyInfo.EMPTY : info).cleaned();
        int total = cleaned.entries().values().stream().mapToInt(String::length).sum();

        if (total > CompanyInfo.MAX_TOTAL) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COMPANY_INFO_TOO_LONG",
                    "That's more than Clerova can use. Keep all answers together under 8,000 characters.");
        }

        Organization organization = organizationRepository.findById(organizationId).orElseThrow();
        organization.setCompanyInfo(cleaned.entries().isEmpty() ? null : objectMapper.writeValueAsString(cleaned));
        organizationRepository.save(organization);

        return cleaned;
    }

    /** The answers as text for the AI, or null when there are none. */
    @Transactional(readOnly = true)
    public String forPrompt(UUID organizationId) {
        if (organizationId == null) {
            return null;
        }

        return organizationRepository.findById(organizationId)
                .map(this::read)
                .map(CompanyInfoService::asText)
                .orElse(null);
    }

    public static String asText(CompanyInfo info) {
        Map<String, String> entries = info == null ? Map.of() : info.entries();

        return entries.isEmpty()
                ? null
                : entries.entrySet().stream()
                        .map(entry -> entry.getKey() + ": " + entry.getValue())
                        .collect(Collectors.joining("\n"));
    }

    private CompanyInfo read(Organization organization) {
        String json = organization.getCompanyInfo();

        if (json == null || json.isBlank()) {
            return CompanyInfo.EMPTY;
        }

        try {
            return objectMapper.readValue(json, CompanyInfo.class);
        } catch (RuntimeException e) {
            log.warn("Could not read company info for organization {}", organization.getId(), e);
            return CompanyInfo.EMPTY;
        }
    }
}
