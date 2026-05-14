package com.allanvital.maestrao.view.component;

import com.allanvital.maestrao.model.JobExecutionStatus;
import com.allanvital.maestrao.model.JobRunStatus;
import com.allanvital.maestrao.model.LogSourceStatus;
import com.vaadin.flow.component.html.Span;

public final class StatusBadge {

    private StatusBadge() {
    }

    public static Span runningOrDash(boolean running) {
        return of(running ? "RUNNING" : "-", running ? Variant.WARNING : Variant.NEUTRAL);
    }

    public static Span forLogStatus(LogSourceStatus status, String fallbackText) {
        if (status == LogSourceStatus.RUNNING) {
            return of("RUNNING", Variant.WARNING);
        }
        if (status == LogSourceStatus.ERROR) {
            return of("ERROR", Variant.ERROR);
        }
        if (status == LogSourceStatus.STOPPED) {
            return of("STOPPED", Variant.NEUTRAL);
        }
        return of(fallbackText == null || fallbackText.isBlank() ? "-" : fallbackText, Variant.NEUTRAL);
    }

    public static Span forJobRunStatus(JobRunStatus status) {
        if (status == null) {
            return of("-", Variant.NEUTRAL);
        }
        return switch (status) {
            case RUNNING -> of("RUNNING", Variant.WARNING);
            case COMPLETED -> of("COMPLETED", Variant.SUCCESS);
            case ABORTED -> of("ABORTED", Variant.ERROR);
        };
    }

    public static Span forJobExecutionStatus(JobExecutionStatus status) {
        if (status == null) {
            return of("-", Variant.NEUTRAL);
        }
        return switch (status) {
            case RUNNING -> of("RUNNING", Variant.WARNING);
            case SUCCESS -> of("SUCCESS", Variant.SUCCESS);
            case FAILED -> of("FAILED", Variant.ERROR);
            case TIMEOUT -> of("TIMEOUT", Variant.ERROR);
            case ABORTED -> of("ABORTED", Variant.ERROR);
            case PENDING -> of("PENDING", Variant.NEUTRAL);
        };
    }

    public static Span of(String text, Variant variant) {
        Span badge = new Span(text == null || text.isBlank() ? "-" : text);
        badge.getStyle()
                .set("font-weight", "600")
                .set("padding", "2px 8px")
                .set("border-radius", "999px")
                .set("border", "1px solid var(--lumo-contrast-10pct)");

        if (variant == Variant.SUCCESS) {
            badge.getStyle().set("background", "var(--lumo-success-color-10pct)").set("color", "var(--lumo-success-text-color)");
        } else if (variant == Variant.WARNING) {
            badge.getStyle().set("background", "var(--lumo-warning-color-10pct)").set("color", "var(--lumo-warning-text-color)");
        } else if (variant == Variant.ERROR) {
            badge.getStyle().set("background", "var(--lumo-error-color-10pct)").set("color", "var(--lumo-error-text-color)");
        }
        return badge;
    }

    public enum Variant {
        SUCCESS,
        WARNING,
        ERROR,
        NEUTRAL
    }
}
