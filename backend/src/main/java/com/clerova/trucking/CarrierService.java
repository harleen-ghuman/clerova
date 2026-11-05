package com.clerova.trucking;

import com.clerova.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The carrier's own details, used on every invoice. */
@Service
public class CarrierService {

    public record CarrierDetails(
            String legalName, String mcNumber, String dotNumber, String address, String phone, String email,
            String invoicePrefix, int nextInvoiceNumber,
            boolean usesFactoring, String factorName, String factorAddress, String factorEmail,
            List<String> missing
    ) {
        static CarrierDetails of(CarrierProfile profile) {
            return new CarrierDetails(profile.getLegalName(), profile.getMcNumber(), profile.getDotNumber(),
                    profile.getAddress(), profile.getPhone(), profile.getEmail(), profile.getInvoicePrefix(),
                    profile.getNextInvoiceNumber(), profile.isUsesFactoring(), profile.getFactorName(),
                    profile.getFactorAddress(), profile.getFactorEmail(), CarrierService.missing(profile));
        }
    }

    private final CarrierProfileRepository repository;

    public CarrierService(CarrierProfileRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public CarrierDetails get(UUID organizationId) {
        return CarrierDetails.of(repository.findById(organizationId).orElseGet(() -> blank(organizationId)));
    }

    @Transactional
    public CarrierDetails save(UUID organizationId, CarrierDetails request) {
        CarrierProfile profile = repository.findById(organizationId).orElseGet(() -> blank(organizationId));

        profile.setLegalName(text(request.legalName(), 255, "company name"));
        profile.setMcNumber(number(request.mcNumber(), "MC number"));
        profile.setDotNumber(number(request.dotNumber(), "DOT number"));
        profile.setAddress(text(request.address(), 1000, "address"));
        profile.setPhone(text(request.phone(), 50, "phone number"));
        profile.setEmail(text(request.email(), 320, "email"));

        String prefix = text(request.invoicePrefix(), 20, "invoice prefix");

        if (prefix != null && !prefix.matches("[A-Za-z0-9 ._/#-]+")) {
            throw invalid("Use letters, numbers and dashes in the invoice prefix.");
        }

        profile.setInvoicePrefix(prefix);

        if (request.nextInvoiceNumber() < 1 || request.nextInvoiceNumber() > 99_999_999) {
            throw invalid("Enter the next invoice number, like 1001.");
        }

        profile.setNextInvoiceNumber(request.nextInvoiceNumber());
        profile.setUsesFactoring(request.usesFactoring());
        profile.setFactorName(text(request.factorName(), 255, "factoring company name"));
        profile.setFactorAddress(text(request.factorAddress(), 1000, "factoring company address"));
        profile.setFactorEmail(text(request.factorEmail(), 320, "factoring company email"));

        for (String email : new String[] {profile.getEmail(), profile.getFactorEmail()}) {
            if (email != null && !InvoiceService.isEmail(email)) {
                throw invalid("Enter a valid email address, like billing@yourcompany.com.");
            }
        }

        return CarrierDetails.of(repository.save(profile));
    }

    /** What an invoice needs that isn't filled in yet, in plain words. */
    static List<String> missing(CarrierProfile profile) {
        List<String> missing = new ArrayList<>();

        if (profile.getLegalName() == null) {
            missing.add("your company name");
        }

        if (profile.getAddress() == null) {
            missing.add("your address");
        }

        if (profile.getMcNumber() == null && profile.getDotNumber() == null) {
            missing.add("your MC or DOT number");
        }

        if (profile.isUsesFactoring()) {
            if (profile.getFactorName() == null) {
                missing.add("the factoring company's name");
            }

            if (profile.getFactorAddress() == null) {
                missing.add("where the factoring company is paid");
            }
        }

        return missing;
    }

    static CarrierProfile blank(UUID organizationId) {
        CarrierProfile profile = new CarrierProfile();
        profile.setOrganizationId(organizationId);
        return profile;
    }

    private static String text(String value, int max, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String cleaned = value.strip().replace("\r\n", "\n");

        if (cleaned.length() > max) {
            throw invalid("The " + label + " is too long.");
        }

        return cleaned;
    }

    /** MC and DOT numbers: digits only, "MC-845210" becomes "845210". */
    private static String number(String value, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String digits = value.replaceAll("(?i)^\\s*(mc|dot|usdot)[\\s#:.-]*", "").strip();

        if (!digits.matches("\\d{1,10}")) {
            throw invalid("Enter the " + label + " as digits, like 845210.");
        }

        return digits;
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CARRIER_DETAILS", message);
    }
}
