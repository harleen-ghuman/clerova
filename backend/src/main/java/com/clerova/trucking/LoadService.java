package com.clerova.trucking;

import com.clerova.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Loads for the app, always within one organization. */
@Service
public class LoadService {

    private final LoadRepository loadRepository;

    public LoadService(LoadRepository loadRepository) {
        this.loadRepository = loadRepository;
    }

    @Transactional(readOnly = true)
    public List<LoadResponses.LoadSummary> list(UUID organizationId) {
        return loadRepository.findForOrganization(organizationId).stream()
                .map(LoadResponses::summary)
                .toList();
    }

    @Transactional(readOnly = true)
    public LoadResponses.LoadDetail get(UUID organizationId, UUID loadId) {
        return LoadResponses.detail(require(organizationId, loadId));
    }

    /** Marks one thing to check as done, like "It's fine" on a flag. */
    @Transactional
    public LoadResponses.LoadDetail resolveFlag(UUID organizationId, UUID loadId, UUID flagId) {
        Load load = require(organizationId, loadId);
        LoadFlag flag = load.getFlags().stream()
                .filter(candidate -> candidate.getId().equals(flagId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Flag not found"));

        if (flag.getResolvedAt() == null) {
            flag.setResolvedAt(Instant.now());
        }

        return LoadResponses.detail(load);
    }

    private Load require(UUID organizationId, UUID loadId) {
        return loadRepository.findByIdAndOrganizationId(loadId, organizationId)
                .orElseThrow(() -> new NotFoundException("Load not found"));
    }
}
