package com.allanvital.maestrao.view.individual;

import com.allanvital.maestrao.model.*;
import com.allanvital.maestrao.repository.JobDefinitionListRow;
import com.allanvital.maestrao.service.HostService;
import com.allanvital.maestrao.service.job.JobDefinitionService;
import com.allanvital.maestrao.service.job.JobQueryService;
import com.allanvital.maestrao.service.job.JobRunnerService;
import com.allanvital.maestrao.service.job.schedule.JobScheduleService;
import com.allanvital.maestrao.view.component.LiveIndicator;
import com.allanvital.maestrao.view.component.StatusBadge;
import com.allanvital.maestrao.view.MainLayout;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.Registration;
import jakarta.annotation.security.PermitAll;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.ZoneId;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Allan Vital (https://allanvital.com)
 */
@Route(value = "jobs/:id?", layout = MainLayout.class)
@PageTitle("Jobs | Maestrao")
@PermitAll
public class JobsView extends VerticalLayout implements BeforeEnterObserver {

    private static final int PAGE_SIZE = 20;

    private final JobDefinitionService jobDefinitionService;
    private final JobRunnerService jobRunnerService;
    private final JobQueryService jobQueryService;
    private final JobScheduleService jobScheduleService;
    private final HostService hostService;

    private final Grid<JobTableRow> grid = new Grid<>(JobTableRow.class, false);

    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public JobsView(JobDefinitionService jobDefinitionService,
                    JobRunnerService jobRunnerService,
                    JobQueryService jobQueryService,
                    JobScheduleService jobScheduleService,
                    HostService hostService) {
        this.jobDefinitionService = jobDefinitionService;
        this.jobRunnerService = jobRunnerService;
        this.jobQueryService = jobQueryService;
        this.jobScheduleService = jobScheduleService;
        this.hostService = hostService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        configureHeader();
        configureGrid();
    }

    private void configureHeader() {
        H1 title = new H1("Jobs");
        title.getStyle().set("margin", "0");

        Paragraph subtitle = new Paragraph("Run scripts on one or more hosts");
        subtitle.getStyle()
                .set("margin-top", "0")
                .set("color", "var(--lumo-secondary-text-color)");

        Button add = new Button("Add job", event -> openJobDialog(null));
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout top = new HorizontalLayout(new VerticalLayout(title, subtitle), add);
        top.setWidthFull();
        top.setAlignItems(Alignment.START);
        top.expand(top.getComponentAt(0));

        add(top);
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.setPageSize(PAGE_SIZE);

        grid.addColumn(JobTableRow::name)
                .setHeader("Name")
                .setAutoWidth(true)
                .setSortable(false);

        grid.addColumn(row -> row.shell().getLabel())
                .setHeader("Shell")
                .setAutoWidth(true)
                .setSortable(false);

        grid.addColumn(row -> row.useSudo() ? "Yes" : "No")
                .setHeader("Sudo")
                .setAutoWidth(true)
                .setSortable(false);

        grid.addComponentColumn(row -> jobStateBadge(row.running()))
                .setHeader("State")
                .setAutoWidth(true)
                .setSortable(false);

        grid.addColumn(row -> dateTimeFormatter.format(row.updatedAt()))
                .setHeader("Updated at")
                .setAutoWidth(true)
                .setSortable(false);

        grid.addColumn(row -> formatNextExecutionIn(safeNextExecution(row.id())))
                .setHeader("Next execution in")
                .setAutoWidth(true)
                .setSortable(false);

        grid.addComponentColumn(row -> {
                    Button run = new Button("Run", event -> runJob(row));
                    run.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
                    boolean hasHosts = row.hostCount() > 0;
                    run.setEnabled(hasHosts);
                    Span runWrap = new Span(run);
                    if (!hasHosts) {
                        runWrap.getElement().setAttribute("title", "Assign at least one host to run");
                    }

                    Button executions = new Button("Executions", event -> openExecutionsDialog(row.id(), row.name()));
                    executions.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

                    Button edit = new Button("Edit", event -> openJobDialog(row.id()));
                    edit.addThemeVariants(ButtonVariant.LUMO_SMALL);

                    Button delete = new Button("Delete", event -> confirmDelete(row.id(), row.name()));
                    delete.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);

                    return new HorizontalLayout(runWrap, executions, edit, delete);
                })
                .setHeader("Actions")
                .setAutoWidth(true)
                .setFlexGrow(0);

        grid.setDataProvider(DataProvider.fromCallbacks(
                query -> {
                    List<JobDefinitionListRow> baseRows = jobDefinitionService.findAllListRows(PageRequest.of(
                            query.getPage(),
                            query.getPageSize(),
                            Sort.by(Sort.Direction.DESC, "id")
                    )).getContent();
                    List<Long> ids = baseRows.stream().map(JobDefinitionListRow::id).toList();
                    var runningStates = jobQueryService.getRunningStates(ids);
                    return baseRows.stream().map(r -> new JobTableRow(
                            r.id(),
                            r.name(),
                            r.shell(),
                            r.useSudo(),
                            r.updatedAt(),
                            r.hostCount(),
                            Boolean.TRUE.equals(runningStates.get(r.id()))
                    ));
                },
                query -> Math.toIntExact(jobDefinitionService.count())
        ));

        add(grid);
        expand(grid);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String idStr = event.getRouteParameters().get("id").orElse(null);
        if (idStr == null || idStr.isBlank()) {
            return;
        }
        try {
            Long id = Long.parseLong(idStr);
            JobDefinition job = jobDefinitionService.find(id);
            openExecutionsDialog(id, job.getName(), true);
        } catch (NumberFormatException ignored) {
        }
    }

    private void openJobDialog(Long jobId) {
        JobDefinition job = jobId == null ? null : jobDefinitionService.find(jobId);
        boolean editing = job != null;

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(editing ? "Edit job" : "Add job");
        dialog.setDraggable(true);
        dialog.setResizable(true);
        dialog.setWidth("820px");

        TextField name = new TextField("Name");
        name.setRequiredIndicatorVisible(true);
        name.setWidthFull();

        ComboBox<JobShell> shell = new ComboBox<>("Shell");
        shell.setItems(JobShell.values());
        shell.setItemLabelGenerator(JobShell::getLabel);
        shell.setRequiredIndicatorVisible(true);
        shell.setWidthFull();

        Checkbox sudo = new Checkbox("Use sudo (NOPASSWD)");

        Checkbox scheduleEnabled = new Checkbox("Enable schedule");

        TextField cron5 = new TextField("Cron (5-field)");
        cron5.setPlaceholder("min hour day-of-month month day-of-week");
        cron5.setWidthFull();

        cron5.setEnabled(false);
        scheduleEnabled.addValueChangeListener(event -> cron5.setEnabled(Boolean.TRUE.equals(event.getValue())));

        TextArea content = new TextArea("Script");
        content.setRequiredIndicatorVisible(true);
        content.setWidthFull();
        content.setHeight("260px");

        MultiSelectComboBox<Host> hosts = new MultiSelectComboBox<>("Hosts");
        hosts.setWidthFull();
        List<Host> hostItems = hostService.findAllForSelection();
        hosts.setItems(hostItems);
        hosts.setItemLabelGenerator(h -> h.getName() + " (" + h.getIp() + ")");

        if (editing) {
            name.setValue(valueOrEmpty(job.getName()));
            shell.setValue(job.getShell());
            sudo.setValue(job.isUseSudo());
            content.setValue(valueOrEmpty(job.getContent()));
            scheduleEnabled.setValue(job.isScheduleEnabled());
            cron5.setValue(valueOrEmpty(job.getCron5()));
            cron5.setEnabled(job.isScheduleEnabled());
            Set<Host> selectedHosts = job.getHosts();
            if (selectedHosts != null && !selectedHosts.isEmpty()) {
                Set<Host> match = hostItems.stream()
                        .filter(h -> selectedHosts.stream().anyMatch(sel -> Objects.equals(sel.getId(), h.getId())))
                        .collect(Collectors.toSet());
                hosts.select(match);
            }
        } else {
            shell.setValue(JobShell.BASH);
            sudo.setValue(false);
            scheduleEnabled.setValue(false);
            cron5.setEnabled(false);
        }

        FormLayout form = new FormLayout(name, shell, sudo, scheduleEnabled, cron5, hosts, content);
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("520px", 2)
        );
        form.setColspan(cron5, 2);
        form.setColspan(hosts, 2);
        form.setColspan(content, 2);

        Button save = new Button("Save", event -> {
            try {
                if (!validateJobForm(name, shell, content)) {
                    return;
                }
                if (scheduleEnabled.getValue() && cron5.isEmpty()) {
                    cron5.setInvalid(true);
                    cron5.setErrorMessage("Cron is required when schedule is enabled");
                    return;
                }

                Set<Long> hostIds = hosts.getSelectedItems().stream()
                        .map(Host::getId)
                        .collect(Collectors.toSet());

                if (editing) {
                    jobDefinitionService.update(job.getId(),
                            name.getValue(),
                            shell.getValue(),
                            sudo.getValue(),
                            content.getValue(),
                            hostIds,
                            scheduleEnabled.getValue(),
                            cron5.getValue());
                    showSuccess("Job updated");
                } else {
                    jobDefinitionService.create(
                            name.getValue(),
                            shell.getValue(),
                            sudo.getValue(),
                            content.getValue(),
                            hostIds,
                            scheduleEnabled.getValue(),
                            cron5.getValue());
                    showSuccess("Job created");
                }

                dialog.close();
                grid.getDataProvider().refreshAll();
            } catch (RuntimeException e) {
                showError(e.getMessage());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancel = new Button("Cancel", event -> dialog.close());

        dialog.add(form);
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    private void runJob(JobTableRow row) {
        try {
            Long runId = jobRunnerService.startRun(row.id());
            showSuccess("Run started (id: " + runId + ")");
            openRunDetailsDialog(runId);
        } catch (RuntimeException e) {
            showError(e.getMessage());
        }
    }

    private void openExecutionsDialog(Long jobId, String jobName) {
        openExecutionsDialog(jobId, jobName, false);
    }

    private void openExecutionsDialog(Long jobId, String jobName, boolean clearRouteOnClose) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Executions: " + jobName);
        dialog.setDraggable(true);
        dialog.setResizable(true);
        dialog.setWidth("980px");
        dialog.setHeight("720px");

        Grid<JobRun> runs = new Grid<>(JobRun.class, false);
        runs.addColumn(JobRun::getId).setHeader("Run ID").setAutoWidth(true).setSortable(false);
        runs.addComponentColumn(run -> StatusBadge.forJobRunStatus(run.getStatus())).setHeader("Status").setAutoWidth(true).setSortable(false);
        runs.addColumn(run -> dateTimeFormatter.format(run.getStartedAt())).setHeader("Started at").setAutoWidth(true).setSortable(false);
        runs.addColumn(run -> run.getFinishedAt() == null ? "-" : dateTimeFormatter.format(run.getFinishedAt()))
                .setHeader("Finished at").setAutoWidth(true).setSortable(false);

        runs.setDataProvider(DataProvider.fromCallbacks(
                query -> jobQueryService.findRuns(jobId, PageRequest.of(query.getPage(), query.getPageSize()))
                        .stream(),
                query -> Math.toIntExact(jobQueryService.countRuns(jobId))
        ));

        Grid<JobExecution> execs = new Grid<>(JobExecution.class, false);
        execs.addColumn(exec -> exec.getHost().getName()).setHeader("Host").setAutoWidth(true).setSortable(false);
        execs.addComponentColumn(exec -> StatusBadge.forJobExecutionStatus(exec.getStatus())).setHeader("Status").setAutoWidth(true).setSortable(false);
        execs.addColumn(exec -> exec.getExitCode() == null ? "-" : exec.getExitCode().toString())
                .setHeader("Exit").setAutoWidth(true).setSortable(false);
        execs.addColumn(exec -> exec.getStartedAt() == null ? "-" : dateTimeFormatter.format(exec.getStartedAt()))
                .setHeader("Started at").setAutoWidth(true).setSortable(false);
        execs.addColumn(exec -> exec.getFinishedAt() == null ? "-" : dateTimeFormatter.format(exec.getFinishedAt()))
                .setHeader("Finished at").setAutoWidth(true).setSortable(false);

        execs.addComponentColumn(exec -> {
                    Button out = new Button("Stdout", event -> openTextDialog("Stdout", exec.getStdout(), exec.isTruncatedStdout()));
                    out.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
                    Button err = new Button("Stderr", event -> openTextDialog("Stderr", exec.getStderr(), exec.isTruncatedStderr()));
                    err.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
                    return new HorizontalLayout(out, err);
                })
                .setHeader("Output")
                .setAutoWidth(true)
                .setFlexGrow(0);

        final Long[] selectedRunId = new Long[]{null};
        execs.setDataProvider(DataProvider.fromCallbacks(
                query -> {
                    Long runId = selectedRunId[0];
                    if (runId == null) {
                        return java.util.stream.Stream.<JobExecution>empty();
                    }
                    return jobQueryService.findExecutions(runId, PageRequest.of(query.getPage(), query.getPageSize())).stream();
                },
                query -> {
                    Long runId = selectedRunId[0];
                    return runId == null ? 0 : Math.toIntExact(jobQueryService.countExecutions(runId));
                }
        ));

        runs.addSelectionListener(event -> {
            selectedRunId[0] = event.getFirstSelectedItem().map(JobRun::getId).orElse(null);
            execs.getDataProvider().refreshAll();
        });

        Button refresh = new Button("Refresh", event -> {
            runs.getDataProvider().refreshAll();
            JobRun selected = runs.asSingleSelect().getValue();
            if (selected != null) {
                selectedRunId[0] = selected.getId();
                execs.getDataProvider().refreshAll();
            }
        });
        refresh.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button run = new Button("Run", event -> {
            try {
                Long runId = jobRunnerService.startRun(jobId);
                showSuccess("Run started (id: " + runId + ")");
                openRunDetailsDialog(runId);
            } catch (RuntimeException e) {
                showError(e.getMessage());
            }
        });
        run.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button abortSelectedRun = new Button("Abort Run", event -> {
            JobRun selected = runs.asSingleSelect().getValue();
            if (selected == null || selected.getId() == null) {
                showError("Select a run first");
                return;
            }
            try {
                jobRunnerService.requestAbortRun(selected.getId());
                showSuccess("Abort requested for run " + selected.getId());
                runs.getDataProvider().refreshAll();
                selectedRunId[0] = selected.getId();
                execs.getDataProvider().refreshAll();
            } catch (RuntimeException e) {
                showError(e.getMessage());
            }
        });
        abortSelectedRun.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);

        LiveIndicator liveIndicator = new LiveIndicator();
        UI ui = UI.getCurrent();
        int previousPoll = ui.getPollInterval();
        ui.setPollInterval(1500);
        Registration pollReg = ui.addPollListener(event -> {
            runs.getDataProvider().refreshAll();
            if (selectedRunId[0] != null) {
                execs.getDataProvider().refreshAll();
            }
            liveIndicator.markUpdatedNow();
        });

        VerticalLayout layout = new VerticalLayout(new HorizontalLayout(run, abortSelectedRun, refresh, liveIndicator), runs, execs);
        layout.setSizeFull();
        layout.expand(runs, execs);

        dialog.add(layout);

        Button close = new Button("Back", event -> dialog.close());
        dialog.getFooter().add(close);

        dialog.addDetachListener(event -> {
            pollReg.remove();
            ui.setPollInterval(previousPoll);
            if (clearRouteOnClose) {
                UI.getCurrent().navigate("jobs");
            }
        });

        dialog.open();
    }

    private void openRunDetailsDialog(Long runId) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Run details (id: " + runId + ")");
        dialog.setDraggable(true);
        dialog.setResizable(true);
        dialog.setWidth("980px");
        dialog.setHeight("720px");

        Grid<JobExecution> execs = new Grid<>(JobExecution.class, false);
        execs.addColumn(exec -> exec.getHost().getName()).setHeader("Host").setAutoWidth(true).setSortable(false);
        execs.addComponentColumn(exec -> StatusBadge.forJobExecutionStatus(exec.getStatus())).setHeader("Status").setAutoWidth(true).setSortable(false);
        execs.addColumn(exec -> exec.getExitCode() == null ? "-" : exec.getExitCode().toString()).setHeader("Exit").setAutoWidth(true);
        execs.addColumn(exec -> exec.getStartedAt() == null ? "-" : dateTimeFormatter.format(exec.getStartedAt()))
                .setHeader("Started at").setAutoWidth(true);
        execs.addColumn(exec -> exec.getFinishedAt() == null ? "-" : dateTimeFormatter.format(exec.getFinishedAt()))
                .setHeader("Finished at").setAutoWidth(true);

        execs.addComponentColumn(exec -> {
                    Button out = new Button("Stdout", event -> openTextDialog("Stdout", exec.getStdout(), exec.isTruncatedStdout()));
                    out.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
                    Button err = new Button("Stderr", event -> openTextDialog("Stderr", exec.getStderr(), exec.isTruncatedStderr()));
                    err.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
                    return new HorizontalLayout(out, err);
                })
                .setHeader("Output")
                .setAutoWidth(true)
                .setFlexGrow(0);

        execs.setDataProvider(DataProvider.fromCallbacks(
                query -> jobQueryService.findExecutions(runId, PageRequest.of(query.getPage(), query.getPageSize())).stream(),
                query -> Math.toIntExact(jobQueryService.countExecutions(runId))
        ));

        Button refresh = new Button("Refresh", event -> execs.getDataProvider().refreshAll());
        refresh.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button abortRun = new Button("Abort Run", event -> {
            try {
                jobRunnerService.requestAbortRun(runId);
                showSuccess("Abort requested for run " + runId);
                execs.getDataProvider().refreshAll();
            } catch (RuntimeException e) {
                showError(e.getMessage());
            }
        });
        abortRun.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        LiveIndicator liveIndicator = new LiveIndicator();

        UI ui = UI.getCurrent();
        int previousPoll = ui.getPollInterval();
        ui.setPollInterval(1500);
        Registration pollReg = ui.addPollListener(event -> {
            execs.getDataProvider().refreshAll();
            liveIndicator.markUpdatedNow();
        });

        dialog.addDetachListener(event -> {
            pollReg.remove();
            ui.setPollInterval(previousPoll);
        });

        VerticalLayout layout = new VerticalLayout(new HorizontalLayout(abortRun, refresh, liveIndicator), execs);
        layout.setSizeFull();
        layout.expand(execs);
        dialog.add(layout);

        Button close = new Button("Back", event -> dialog.close());
        dialog.getFooter().add(close);

        dialog.open();
    }

    private void confirmDelete(Long jobId, String jobName) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Delete job");
        dialog.setText("Are you sure you want to delete '" + jobName + "'? This will not delete past executions.");
        dialog.setCancelable(true);
        dialog.setConfirmText("Delete");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(event -> {
            try {
                jobDefinitionService.delete(jobId);
                showSuccess("Job deleted");
                grid.getDataProvider().refreshAll();
            } catch (RuntimeException e) {
                showError(e.getMessage());
            }
        });
        dialog.open();
    }

    private boolean validateJobForm(TextField name,
                                    ComboBox<JobShell> shell,
                                    TextArea content) {
        boolean ok = true;
        if (name.isEmpty()) {
            name.setInvalid(true);
            name.setErrorMessage("Name is required");
            ok = false;
        }
        if (shell.isEmpty()) {
            shell.setInvalid(true);
            ok = false;
        }
        if (content.isEmpty()) {
            content.setInvalid(true);
            content.setErrorMessage("Content is required");
            ok = false;
        }
        return ok;
    }

    private void openTextDialog(String title, String text, boolean truncated) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(title + (truncated ? " (truncated)" : ""));
        dialog.setWidth("980px");
        dialog.setHeight("720px");
        dialog.setDraggable(true);
        dialog.setResizable(true);

        TextArea area = new TextArea();
        area.setValue(text == null ? "" : text);
        area.setWidthFull();
        area.setHeightFull();
        area.setReadOnly(true);

        dialog.add(area);

        Button close = new Button("Back", event -> dialog.close());
        dialog.getFooter().add(close);

        dialog.open();
    }

    private void showSuccess(String message) {
        Notification notification = Notification.show(message, 3000, Notification.Position.TOP_END);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void showError(String message) {
        Notification notification = Notification.show(message == null ? "Unknown error" : message, 5000, Notification.Position.TOP_END);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private Instant safeNextExecution(Long jobId) {
        try {
            return jobScheduleService.getNextFireTime(jobId);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String formatNextExecutionIn(Instant nextExecution) {
        if (nextExecution == null) {
            return "-";
        }
        long seconds = Duration.between(Instant.now(), nextExecution).getSeconds();
        if (seconds <= 0) {
            return "due now";
        }
        if (seconds < 60) {
            return "<1 min";
        }
        return (seconds / 60) + " min";
    }

    private Span jobStateBadge(boolean running) {
        return StatusBadge.runningOrDash(running);
    }

    private record JobTableRow(Long id,
                               String name,
                               JobShell shell,
                               boolean useSudo,
                               Instant updatedAt,
                               long hostCount,
                               boolean running) {
    }
}
