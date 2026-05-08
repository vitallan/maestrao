package com.allanvital.maestrao.service.job.schedule;

import java.util.Locale;

/**
 * Converts standard 5-field cron (min hour dom mon dow) to Quartz 6-field cron
 * by prepending seconds and using '?' for either dom or dow.
 *
 * Rejects expressions that restrict both dom and dow (Quartz limitation).
 *
 * @author Allan Vital (https://allanvital.com)
 */
public class Cron5ToQuartzCronConverter {

    public String toQuartzCron(String cron5) {
        if (cron5 == null || cron5.isBlank()) {
            throw new IllegalArgumentException("cron is required");
        }

        String[] parts = cron5.trim().split("\\s+");
        if (parts.length != 5) {
            throw new IllegalArgumentException("cron must have 5 fields: min hour day-of-month month day-of-week");
        }

        String min = parts[0];
        String hour = parts[1];
        String dom = parts[2];
        String mon = parts[3];
        String dow = normalizeDow(parts[4]);

        boolean domRestricted = !"*".equals(dom);
        boolean dowRestricted = !"*".equals(dow);
        if (domRestricted && dowRestricted) {
            throw new IllegalArgumentException("cron cannot restrict both day-of-month and day-of-week (Quartz limitation). Use one and set the other to *");
        }

        // Quartz requires either dom or dow be '?'
        String quartzDom = domRestricted ? dom : "*";
        String quartzDow;
        if (dowRestricted) {
            quartzDom = "?";
            quartzDow = dow;
        } else {
            quartzDow = "?";
        }

        return "0 " + min + " " + hour + " " + quartzDom + " " + mon + " " + quartzDow;
    }

    private String normalizeDow(String dow) {
        if (dow == null || dow.isBlank()) {
            return "*";
        }
        String d = dow.trim();
        if ("*".equals(d)) {
            return "*";
        }

        // Normalize Sunday values (common cron): 0 or 7.
        if ("0".equals(d) || "7".equals(d)) {
            return "SUN";
        }

        // Accept MON..SUN case-insensitive.
        String upper = d.toUpperCase(Locale.ROOT);
        return upper;
    }
}
