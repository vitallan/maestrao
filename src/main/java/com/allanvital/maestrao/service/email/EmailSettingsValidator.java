package com.allanvital.maestrao.service.email;

import com.allanvital.maestrao.model.EmailSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared validation for persisted email settings.
 */
public final class EmailSettingsValidator {

    private EmailSettingsValidator() {
    }

    public static ValidationResult validate(EmailSettings settings) {
        if (settings == null) {
            return new ValidationResult(false, "Settings not found");
        }
        if (normalizeOptional(settings.getSmtpHost()) == null) {
            return new ValidationResult(false, "SMTP host is required");
        }
        Integer port = settings.getSmtpPort();
        if (port == null) {
            return new ValidationResult(false, "SMTP port is required");
        }
        if (port <= 0 || port > 65535) {
            return new ValidationResult(false, "SMTP port must be between 1 and 65535");
        }
        if (normalizeOptional(settings.getFromAddress()) == null) {
            return new ValidationResult(false, "From address is required");
        }
        if (splitToAddresses(settings.getToAddresses()).isEmpty()) {
            return new ValidationResult(false, "To addresses are required");
        }
        if (settings.getSendTime() == null) {
            return new ValidationResult(false, "Send time is required");
        }
        return new ValidationResult(true, null);
    }

    public static List<String> splitToAddresses(String raw) {
        String v = normalizeOptional(raw);
        if (v == null) {
            return List.of();
        }
        String[] parts = v.split(",");
        ArrayList<String> out = new ArrayList<>();
        for (String p : parts) {
            String s = normalizeOptional(p);
            if (s != null) {
                out.add(s);
            }
        }
        return out;
    }

    public static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record ValidationResult(boolean ok, String message) {
    }
}
