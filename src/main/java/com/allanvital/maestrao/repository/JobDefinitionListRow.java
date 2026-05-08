package com.allanvital.maestrao.repository;

import com.allanvital.maestrao.model.JobShell;

import java.time.Instant;

/**
 * @author Allan Vital (https://allanvital.com)
 */
public record JobDefinitionListRow(Long id,
                                   String name,
                                   JobShell shell,
                                   boolean useSudo,
                                   Instant updatedAt,
                                   long hostCount) {
}
