package com.allanvital.maestrao.view.individual;

import com.allanvital.maestrao.model.LogSource;
import com.allanvital.maestrao.model.LogSourceStatus;
import com.allanvital.maestrao.repository.JobDefinitionListRow;
import com.allanvital.maestrao.service.job.JobDefinitionService;
import com.allanvital.maestrao.service.job.JobQueryService;
import com.allanvital.maestrao.service.job.schedule.JobScheduleService;
import com.allanvital.maestrao.service.log.LogSourceService;
import com.allanvital.maestrao.view.MainLayout;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Allan Vital (https://allanvital.com)
 */
@Route(value = "", layout = MainLayout.class)
@PageTitle("Dashboard | Maestrao")
@PermitAll
public class DashboardView extends VerticalLayout {

    private static final int LOGS_PAGE_SIZE = 20;
    private static final int JOBS_PAGE_SIZE = 20;

    private final LogSourceService logSourceService;
    private final JobDefinitionService jobDefinitionService;
    private final JobQueryService jobQueryService;
    private final JobScheduleService jobScheduleService;

    private final Grid<LogSource> logsGrid = new Grid<>(LogSource.class, false);
    private final Span logsPageInfo = new Span();
    private final Button logsPrev = new Button("Prev");
    private final Button logsNext = new Button("Next");
    private int logsPage = 0;
    private long logsTotal = 0;

    private final Grid<JobRow> jobsGrid = new Grid<>(JobRow.class, false);
    private final Span jobsPageInfo = new Span();
    private final Button jobsPrev = new Button("Prev");
    private final Button jobsNext = new Button("Next");
    private int jobsPage = 0;
    private long jobsTotal = 0;

    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public DashboardView(LogSourceService logSourceService,
                         JobDefinitionService jobDefinitionService,
                         JobQueryService jobQueryService,
                         JobScheduleService jobScheduleService) {
        this.logSourceService = logSourceService;
        this.jobDefinitionService = jobDefinitionService;
        this.jobQueryService = jobQueryService;
        this.jobScheduleService = jobScheduleService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        H1 title = new H1("Dashboard");
        title.getStyle().set("margin", "0");

        VerticalLayout logsPanel = buildLogsPanel();
        VerticalLayout jobsPanel = buildJobsPanel();

        HorizontalLayout panels = new HorizontalLayout(logsPanel, jobsPanel);
        panels.setWidthFull();
        panels.setPadding(false);
        panels.setSpacing(true);
        panels.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.STRETCH);
        panels.getStyle().set("flex-wrap", "wrap");
        panels.setFlexGrow(1, logsPanel, jobsPanel);

        add(title, panels);
        setFlexGrow(1, panels);

        loadLogsPage(0);
        loadJobsPage(0);
    }

    private VerticalLayout buildLogsPanel() {
        VerticalLayout panel = new VerticalLayout();
        panel.setPadding(true);
        panel.setSpacing(true);
        panel.getStyle()
                .set("box-sizing", "border-box")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                // Two columns on wide screens, wrap on narrow.
                .set("flex", "1 1 520px")
                .set("min-width", "360px")
                .set("max-width", "100%");

        H1 header = new H1("Logs");
        header.getStyle().set("margin", "0").set("font-size", "var(--lumo-font-size-l)");

        configureLogsGrid();
        HorizontalLayout pager = buildLogsPager();

        panel.add(header, logsGrid, pager);
        panel.expand(logsGrid);
        panel.setFlexGrow(1, logsGrid);
        return panel;
    }

    private void configureLogsGrid() {
        logsGrid.setWidthFull();
        logsGrid.setAllRowsVisible(false);
        logsGrid.setPageSize(LOGS_PAGE_SIZE);

        logsGrid.addItemClickListener(event -> {
            LogSource item = event.getItem();
            if (item == null || item.getId() == null) {
                return;
            }
            UI.getCurrent().navigate("logs/" + item.getId());
        });

        logsGrid.addColumn(LogSource::getName)
                .setHeader("Log")
                .setAutoWidth(true)
                .setSortable(false);

        logsGrid.addColumn(log -> log.getHost() == null ? "-" : log.getHost().getName())
                .setHeader("Host")
                .setAutoWidth(true)
                .setSortable(false);

        logsGrid.addComponentColumn(this::logStateBadge)
                .setHeader("State")
                .setAutoWidth(true)
                .setSortable(false);
    }

    private Span logStateBadge(LogSource log) {
        String text = stateText(log);
        Span badge = new Span(text);
        badge.getStyle()
                .set("font-weight", "600")
                .set("padding", "2px 8px")
                .set("border-radius", "999px")
                .set("border", "1px solid var(--lumo-contrast-10pct)");

        LogSourceStatus status = log == null ? null : log.getStatus();
        if (status == LogSourceStatus.RUNNING) {
            badge.getStyle().set("background", "var(--lumo-success-color-10pct)").set("color", "var(--lumo-success-text-color)");
        } else if (status == LogSourceStatus.ERROR) {
            badge.getStyle().set("background", "var(--lumo-error-color-10pct)").set("color", "var(--lumo-error-text-color)");
            String lastError = log == null ? null : log.getLastError();
            if (lastError != null && !lastError.isBlank()) {
                badge.getElement().setProperty("title", lastError);
            }
        } else {
            // STOPPED (and anything else) => yellow
            badge.getStyle().set("background", "var(--lumo-warning-color-10pct)").set("color", "var(--lumo-warning-text-color)");
        }
        return badge;
    }

    private HorizontalLayout buildLogsPager() {
        logsPrev.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        logsNext.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        logsPageInfo.getStyle().set("color", "var(--lumo-secondary-text-color)");

        logsPrev.addClickListener(e -> {
            if (logsPage <= 0) {
                return;
            }
            loadLogsPage(logsPage - 1);
        });
        logsNext.addClickListener(e -> {
            int max = logsMaxPage();
            if (logsPage >= max) {
                return;
            }
            loadLogsPage(logsPage + 1);
        });

        HorizontalLayout pager = new HorizontalLayout(logsPrev, logsPageInfo, logsNext);
        pager.setWidthFull();
        pager.setAlignItems(FlexComponent.Alignment.CENTER);
        pager.expand(logsPageInfo);
        return pager;
    }

    private void loadLogsPage(int pageIndex) {
        if (pageIndex < 0) {
            pageIndex = 0;
        }

        Page<LogSource> page = logSourceService.findAll(PageRequest.of(pageIndex, LOGS_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "id")));
        logsTotal = page.getTotalElements();
        logsPage = pageIndex;
        logsGrid.setItems(page.getContent());
        updateLogsPager();
    }

    private void updateLogsPager() {
        int max = logsMaxPage();
        int totalPages = Math.max(1, max + 1);
        int pageNumber = Math.min(logsPage + 1, totalPages);
        logsPageInfo.setText("Page " + pageNumber + " of " + totalPages + " (" + logsTotal + " logs)");
        logsPrev.setEnabled(logsPage > 0);
        logsNext.setEnabled(logsPage < max);
    }

    private int logsMaxPage() {
        if (logsTotal <= 0) {
            return 0;
        }
        return (int) ((logsTotal - 1) / LOGS_PAGE_SIZE);
    }

    private VerticalLayout buildJobsPanel() {
        VerticalLayout panel = new VerticalLayout();
        panel.setPadding(true);
        panel.setSpacing(true);
        panel.getStyle()
                .set("box-sizing", "border-box")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                // Two columns on wide screens, wrap on narrow.
                .set("flex", "1 1 520px")
                .set("min-width", "360px")
                .set("max-width", "100%");

        H1 header = new H1("Jobs");
        header.getStyle().set("margin", "0").set("font-size", "var(--lumo-font-size-l)");

        configureJobsGrid();
        HorizontalLayout pager = buildJobsPager();

        panel.add(header, jobsGrid, pager);
        panel.expand(jobsGrid);
        panel.setFlexGrow(1, jobsGrid);
        return panel;
    }

    private void configureJobsGrid() {
        jobsGrid.setWidthFull();
        jobsGrid.setAllRowsVisible(false);
        jobsGrid.setPageSize(JOBS_PAGE_SIZE);

        jobsGrid.addItemClickListener(event -> {
            JobRow item = event.getItem();
            if (item == null || item.id == null) {
                return;
            }
            UI.getCurrent().navigate("jobs/" + item.id);
        });

        jobsGrid.addColumn(JobRow::name)
                .setHeader("Job")
                .setAutoWidth(true)
                .setSortable(false);

        jobsGrid.addColumn(row -> Long.toString(row.hostCount))
                .setHeader("Hosts")
                .setAutoWidth(true)
                .setSortable(false);

        jobsGrid.addComponentColumn(row -> {
                    Span s = new Span(row.lastResult);
                    if (row.lastFailed) {
                        s.getStyle().set("color", "var(--lumo-error-text-color)").set("font-weight", "600");
                    }
                    return s;
                })
                .setHeader("Last result")
                .setFlexGrow(1)
                .setSortable(false);

        jobsGrid.addColumn(row -> row.nextExecution == null ? "-" : dateTimeFormatter.format(row.nextExecution))
                .setHeader("Next execution")
                .setAutoWidth(true)
                .setSortable(false);
    }

    private HorizontalLayout buildJobsPager() {
        jobsPrev.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        jobsNext.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        jobsPageInfo.getStyle().set("color", "var(--lumo-secondary-text-color)");

        jobsPrev.addClickListener(e -> {
            if (jobsPage <= 0) {
                return;
            }
            loadJobsPage(jobsPage - 1);
        });
        jobsNext.addClickListener(e -> {
            int max = jobsMaxPage();
            if (jobsPage >= max) {
                return;
            }
            loadJobsPage(jobsPage + 1);
        });

        HorizontalLayout pager = new HorizontalLayout(jobsPrev, jobsPageInfo, jobsNext);
        pager.setWidthFull();
        pager.setAlignItems(FlexComponent.Alignment.CENTER);
        pager.expand(jobsPageInfo);
        return pager;
    }

    private void loadJobsPage(int pageIndex) {
        if (pageIndex < 0) {
            pageIndex = 0;
        }

        Page<JobDefinitionListRow> page = jobDefinitionService.findAllListRows(PageRequest.of(pageIndex, JOBS_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "id")));
        jobsTotal = page.getTotalElements();
        jobsPage = pageIndex;

        List<JobDefinitionListRow> list = page.getContent();
        List<Long> ids = list.stream().map(JobDefinitionListRow::id).filter(Objects::nonNull).toList();
        Map<Long, JobQueryService.JobLastOutcome> outcomes = jobQueryService.getLastOutcomes(ids);

        List<JobRow> rows = list.stream().map(r -> {
            JobQueryService.JobLastOutcome outcome = outcomes.get(r.id());
            String lastResult = outcome == null ? "-" : outcome.summary();
            boolean failed = outcome != null && outcome.failed();
            Instant next = null;
            try {
                next = jobScheduleService.getNextFireTime(r.id());
            } catch (RuntimeException ignored) {
            }
            return new JobRow(r.id(), r.name(), r.hostCount(), lastResult, failed, next);
        }).collect(Collectors.toList());

        jobsGrid.setItems(rows);
        updateJobsPager();
    }

    private void updateJobsPager() {
        int max = jobsMaxPage();
        int totalPages = Math.max(1, max + 1);
        int pageNumber = Math.min(jobsPage + 1, totalPages);
        jobsPageInfo.setText("Page " + pageNumber + " of " + totalPages + " (" + jobsTotal + " jobs)");
        jobsPrev.setEnabled(jobsPage > 0);
        jobsNext.setEnabled(jobsPage < max);
    }

    private int jobsMaxPage() {
        if (jobsTotal <= 0) {
            return 0;
        }
        return (int) ((jobsTotal - 1) / JOBS_PAGE_SIZE);
    }

    private String stateText(LogSource log) {
        if (log == null) {
            return "-";
        }
        if (log.getStatus() == LogSourceStatus.ERROR) {
            return "ERROR";
        }
        if (log.getStatus() == LogSourceStatus.RUNNING) {
            return "RUNNING";
        }
        return "STOPPED";
    }

    private record JobRow(Long id,
                          String name,
                          long hostCount,
                          String lastResult,
                          boolean lastFailed,
                          Instant nextExecution) {
    }

}
