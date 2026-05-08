package com.allanvital.maestrao.repository;

import com.allanvital.maestrao.model.JobExecutionStatus;

/**
 * @author Allan Vital (https://allanvital.com)
 */
public record JobFailedExecutionRow(Long executionId,
                                    Long runId,
                                    String hostName,
                                    JobExecutionStatus status,
                                    Integer exitCode,
                                    String errorMessage) {
}
