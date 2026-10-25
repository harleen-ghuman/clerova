package com.clerova.email;

import com.clerova.domain.Organization;
import com.clerova.repository.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;

/**
 * Assigns each organization a forwarding address token, such as
 * "maple-property-k3j9x2" for maple-property-k3j9x2@in.clerova-ai.com.
 */
@Service
public class InboundAddressService {

    private static final String ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrganizationRepository organizationRepository;

    public InboundAddressService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    @Transactional
    public String getOrCreateToken(Organization organization) {
        if (organization.getInboundEmailToken() != null) {
            return organization.getInboundEmailToken();
        }

        String token;

        do {
            token = slug(organization.getName()) + "-" + randomSuffix();
        } while (organizationRepository.existsByInboundEmailToken(token));

        organization.setInboundEmailToken(token);
        organizationRepository.save(organization);

        return token;
    }

    public Optional<Organization> findByToken(String token) {
        return organizationRepository.findByInboundEmailToken(token.toLowerCase(Locale.ROOT));
    }

    static String slug(String name) {
        String ascii = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");

        if (ascii.length() > 24) {
            ascii = ascii.substring(0, 24).replaceAll("-+$", "");
        }

        return ascii.isEmpty() ? "team" : ascii;
    }

    private static String randomSuffix() {
        StringBuilder suffix = new StringBuilder();

        for (int i = 0; i < 6; i++) {
            suffix.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }

        return suffix.toString();
    }
}
