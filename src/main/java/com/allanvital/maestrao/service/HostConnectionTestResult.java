package com.allanvital.maestrao.service;

/**
 * @author Allan Vital (https://allanvital.com)
 */
public record HostConnectionTestResult(boolean success, String message) {

    public static HostConnectionTestResult success(String message) {
        return new HostConnectionTestResult(true, message);
    }

    public static HostConnectionTestResult failure(String message) {
        return new HostConnectionTestResult(false, message);
    }

}
