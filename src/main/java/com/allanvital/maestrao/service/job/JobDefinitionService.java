package com.allanvital.maestrao.service.job;

import com.allanvital.maestrao.model.JobDefinition;
import com.allanvital.maestrao.model.JobShell;
import com.allanvital.maestrao.model.Host;
import com.allanvital.maestrao.repository.HostRepository;
import com.allanvital.maestrao.repository.JobDefinitionRepository;
import com.allanvital.maestrao.repository.JobDefinitionListRow;
import com.allanvital.maestrao.service.job.schedule.Cron5ToQuartzCronConverter;
import com.allanvital.maestrao.service.job.schedule.JobScheduleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Allan Vital (https://allanvital.com)
 */
@Service
public class JobDefinitionService {

    private static final Logger log = LoggerFactory.getLogger(JobDefinitionService.class);

    private final JobDefinitionRepository jobDefinitionRepository;
    private final HostRepository hostRepository;
    private final JobScheduleService jobScheduleService;
    private final Cron5ToQuartzCronConverter cronConverter = new Cron5ToQuartzCronConverter();

    public JobDefinitionService(JobDefinitionRepository jobDefinitionRepository,
                                HostRepository hostRepository,
                                JobScheduleService jobScheduleService) {
        this.jobDefinitionRepository = jobDefinitionRepository;
        this.hostRepository = hostRepository;
        this.jobScheduleService = jobScheduleService;
    }

    @Transactional(readOnly = true)
    public Page<JobDefinition> findAll(Pageable pageable) {
        return jobDefinitionRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<JobDefinitionListRow> findAllListRows(Pageable pageable) {
        return jobDefinitionRepository.findAllWithHostCount(pageable);
    }

    @Transactional(readOnly = true)
    public long count() {
        return jobDefinitionRepository.count();
    }

    @Transactional(readOnly = true)
    public JobDefinition find(Long id) {
        return jobDefinitionRepository.findWithHostsById(id)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + id));
    }

    @Transactional
    public JobDefinition create(String name,
                                JobShell shell,
                                boolean useSudo,
                                String content,
                                Set<Long> hostIds,
                                boolean scheduleEnabled,
                                String cron5) {
        JobDefinition job = new JobDefinition();
        setInternals(job, name, shell, useSudo, content, hostIds, scheduleEnabled, cron5);
        JobDefinition saved = jobDefinitionRepository.save(job);
        jobScheduleService.applySchedule(saved.getId());
        log.info("job.create id={} name={} scheduleEnabled={} hostCount={} cronSet={}",
                saved.getId(), saved.getName(), saved.isScheduleEnabled(),
                saved.getHosts() == null ? 0 : saved.getHosts().size(), saved.getCron5() != null);
        return saved;
    }

    @Transactional
    public JobDefinition update(Long id,
                                String name,
                                JobShell shell,
                                boolean useSudo,
                                String content,
                                Set<Long> hostIds,
                                boolean scheduleEnabled,
                                String cron5) {
        JobDefinition job = jobDefinitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + id));
        setInternals(job, name, shell, useSudo, content, hostIds, scheduleEnabled, cron5);
        JobDefinition saved = jobDefinitionRepository.save(job);
        jobScheduleService.applySchedule(saved.getId());
        log.info("job.update id={} name={} scheduleEnabled={} hostCount={} cronSet={}",
                saved.getId(), saved.getName(), saved.isScheduleEnabled(),
                saved.getHosts() == null ? 0 : saved.getHosts().size(), saved.getCron5() != null);
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        jobScheduleService.unschedule(id);
        jobDefinitionRepository.deleteById(id);
        log.info("job.delete id={}", id);
    }

    private void setInternals(JobDefinition job,
                              String name,
                              JobShell shell,
                              boolean useSudo,
                              String content,
                              Set<Long> hostIds,
                              boolean scheduleEnabled,
                              String cron5) {
        job.setName(normalizeRequired(name, "name"));
        if (shell == null) {
            throw new IllegalArgumentException("shell is required");
        }
        job.setShell(shell);
        job.setUseSudo(useSudo);
        job.setContent(normalizeRequiredContent(content));

        Set<Host> hosts = resolveHosts(hostIds);
        job.setHosts(hosts);

        job.setScheduleEnabled(scheduleEnabled);
        job.setCron5(normalizeOptional(cron5));

        if (scheduleEnabled) {
            if (job.getCron5() == null) {
                throw new IllegalArgumentException("cron is required when scheduling is enabled");
            }
            if (hosts.isEmpty()) {
                throw new IllegalArgumentException("at least one host is required when scheduling is enabled");
            }
            // Validate early so UI gets a direct error.
            cronConverter.toQuartzCron(job.getCron5());
        }
    }

    private Set<Host> resolveHosts(Set<Long> hostIds) {
        if (hostIds == null || hostIds.isEmpty()) {
            return new HashSet<>();
        }

        List<Host> hosts = hostRepository.findAllById(hostIds);
        return new HashSet<>(hosts);
    }

    private String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeRequiredContent(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("content is required");
        }
        // Preserve script formatting as entered.
        return value;
    }
}
