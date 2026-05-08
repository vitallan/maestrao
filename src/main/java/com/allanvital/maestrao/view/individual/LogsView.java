package com.allanvital.maestrao.view.individual;

import com.allanvital.maestrao.model.Host;
import com.allanvital.maestrao.model.LogSource;
import com.allanvital.maestrao.model.LogSourceStatus;
import com.allanvital.maestrao.service.HostService;
import com.allanvital.maestrao.service.log.LogLineQueryService;
import com.allanvital.maestrao.service.log.LogSourceService;
import com.allanvital.maestrao.view.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.component.UI;
import jakarta.annotation.security.PermitAll;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * @author Allan Vital (https://allanvital.com)
 */
@Route(value = "logs/:id?", layout = MainLayout.class)
@PageTitle("Logs | Maestrao")
@PermitAll
public class LogsView extends VerticalLayout implements BeforeEnterObserver {

    private static final int PAGE_SIZE = 20;

    private final LogSourceService logSourceService;
    private final HostService hostService;
    private final LogLineQueryService logLineQueryService;
    private final Grid<LogSource> grid = new Grid<>(LogSource.class, false);

    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public LogsView(LogSourceService logSourceService, HostService hostService, LogLineQueryService logLineQueryService) {
        this.logSourceService = logSourceService;
        this.hostService = hostService;
        this.logLineQueryService = logLineQueryService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        configureHeader();
        configureGrid();
    }

    private void configureHeader() {
        H1 title = new H1("Logs");
        title.getStyle().set("margin", "0");

        Paragraph subtitle = new Paragraph("Log sources to be collected from remote hosts");
        subtitle.getStyle()
                .set("margin-top", "0")
                .set("color", "var(--lumo-secondary-text-color)");

        Button addButton = new Button("Add New Log", event -> openLogDialog());
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout top = new HorizontalLayout(new VerticalLayout(title, subtitle), addButton);
        top.setWidthFull();
        top.setAlignItems(Alignment.START);
        top.expand(top.getComponentAt(0));

        add(top);
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.setPageSize(PAGE_SIZE);

        grid.addColumn(LogSource::getName)
                .setHeader("Name")
                .setAutoWidth(true)
                .setSortable(false);

        grid.addColumn(log -> log.getHost().getName())
                .setHeader("Host")
                .setAutoWidth(true)
                .setSortable(false);

        grid.addColumn(log -> log.getType().name())
                .setHeader("Type")
                .setAutoWidth(true)
                .setSortable(false);

        grid.addColumn(LogSource::getFilePath)
                .setHeader("File path")
                .setFlexGrow(1)
                .setSortable(false);

        grid.addColumn(this::stateText)
                .setHeader("State")
                .setAutoWidth(true)
                .setSortable(false);

        grid.addColumn(log -> dateTimeFormatter.format(log.getUpdatedAt()))
                .setHeader("Updated at")
                .setAutoWidth(true)
                .setSortable(false);

        grid.addComponentColumn(log -> {
                    Button edit = new Button("Edit", event -> openEditDialog(log.getId(), false));
                    edit.addThemeVariants(ButtonVariant.LUMO_SMALL);

                    Button toggle = new Button(log.isEnabled() ? "Pause" : "Resume", event -> toggle(log));
                    toggle.addThemeVariants(ButtonVariant.LUMO_SMALL);

                    Button delete = new Button("Delete", event -> confirmDelete(log));
                    delete.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);

                    return new HorizontalLayout(edit, toggle, delete);
                })
                .setHeader("Actions")
                .setAutoWidth(true)
                .setFlexGrow(0);

        grid.setDataProvider(DataProvider.fromCallbacks(
                query -> logSourceService.findAll(PageRequest.of(
                                query.getPage(),
                                query.getPageSize(),
                                Sort.by(Sort.Direction.DESC, "id")
                        ))
                        .stream(),
                query -> Math.toIntExact(logSourceService.count())
        ));

        add(grid);
        expand(grid);
    }

    private void openLogDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Add New Log");
        dialog.setDraggable(true);
        dialog.setResizable(true);
        dialog.setWidth("720px");

        TextField name = new TextField("Name");
        name.setRequiredIndicatorVisible(true);
        name.setWidthFull();

        com.vaadin.flow.component.combobox.ComboBox<Host> host = new com.vaadin.flow.component.combobox.ComboBox<>("Host");
        host.setRequiredIndicatorVisible(true);
        host.setWidthFull();
        List<Host> hosts = hostService.findAllForSelection();
        host.setItems(hosts);
        host.setItemLabelGenerator(h -> h.getName() + " (" + h.getIp() + ")");

        TextField filePath = new TextField("Log file path");
        filePath.setRequiredIndicatorVisible(true);
        filePath.setWidthFull();

        FormLayout form = new FormLayout(name, host, filePath);
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("520px", 2)
        );
        form.setColspan(filePath, 2);

        Button save = new Button("Save", event -> {
            try {
                if (name.isEmpty()) {
                    name.setInvalid(true);
                    name.setErrorMessage("Name is required");
                    return;
                }
                if (host.isEmpty()) {
                    host.setInvalid(true);
                    return;
                }
                if (filePath.isEmpty()) {
                    filePath.setInvalid(true);
                    filePath.setErrorMessage("File path is required");
                    return;
                }

                logSourceService.createLogFile(name.getValue(), host.getValue().getId(), filePath.getValue(), true);
                showSuccess("Log source created");
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

    private void toggle(LogSource log) {
        try {
            if (log.isEnabled()) {
                logSourceService.disable(log.getId());
                showSuccess("Log paused");
            } else {
                logSourceService.enable(log.getId());
                showSuccess("Log resumed");
            }
            grid.getDataProvider().refreshAll();
        } catch (RuntimeException e) {
            showError(e.getMessage());
        }
    }

    private void confirmDelete(LogSource log) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Delete log source");
        dialog.setText("Do you really want to delete log source '" + log.getName() + "'? All stored lines will be deleted as well.");
        dialog.setCancelable(true);
        dialog.setConfirmText("Delete");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(event -> {
            try {
                logSourceService.delete(log.getId());
                showSuccess("Log source deleted");
                grid.getDataProvider().refreshAll();
            } catch (RuntimeException e) {
                showError(e.getMessage());
            }
        });
        dialog.open();
    }

    private void openEditDialog(Long logSourceId) {
        openEditDialog(logSourceId, false);
    }

    private void openEditDialog(Long logSourceId, boolean clearRouteOnClose) {
        LogSource log = logSourceService.find(logSourceId);

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Edit Log");
        dialog.setDraggable(true);
        dialog.setResizable(true);
        dialog.setWidth("760px");

        TextField name = new TextField("Name");
        name.setReadOnly(true);
        name.setWidthFull();
        name.setValue(valueOrEmpty(log.getName()));

        TextField host = new TextField("Host");
        host.setReadOnly(true);
        host.setWidthFull();
        host.setValue(log.getHost().getName() + " (" + log.getHost().getIp() + ")");

        TextField type = new TextField("Type");
        type.setReadOnly(true);
        type.setWidthFull();
        type.setValue(log.getType().name());

        TextField state = new TextField("State");
        state.setReadOnly(true);
        state.setWidthFull();
        state.setValue(stateText(log));

        TextField lastError = new TextField("Last error");
        lastError.setReadOnly(true);
        lastError.setWidthFull();
        lastError.setValue(valueOrEmpty(log.getLastError()));

        TextField filePath = new TextField("Log file path");
        filePath.setRequiredIndicatorVisible(true);
        filePath.setWidthFull();
        filePath.setValue(valueOrEmpty(log.getFilePath()));

        Span latestMeta = new Span();
        latestMeta.getStyle().set("color", "var(--lumo-secondary-text-color)");

        TextArea latestLine = new TextArea("Latest ingested line");
        latestLine.setReadOnly(true);
        latestLine.setWidthFull();
        latestLine.setMinHeight("140px");

        Runnable refreshLatest = () -> {
            LogLineQueryService.LatestLogLine latest = logLineQueryService.findLatest(logSourceId);
            if (latest == null) {
                latestMeta.setText("No lines ingested yet");
                latestLine.setValue("");
            } else {
                latestMeta.setText("Ingested at: " + dateTimeFormatter.format(latest.ingestedAt()));
                latestLine.setValue(latest.line());
            }

            LogSource refreshed = logSourceService.find(logSourceId);
            state.setValue(stateText(refreshed));
            lastError.setValue(valueOrEmpty(refreshed.getLastError()));
        };

        Button refreshNow = new Button("Refresh", event -> {
            try {
                refreshLatest.run();
            } catch (RuntimeException e) {
                showError(e.getMessage());
            }
        });

        FormLayout form = new FormLayout(name, host, type, state, filePath, lastError);
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("520px", 2)
        );
        form.setColspan(filePath, 2);
        form.setColspan(lastError, 2);

        VerticalLayout latestBox = new VerticalLayout(latestMeta, latestLine, refreshNow);
        latestBox.setPadding(false);
        latestBox.setSpacing(true);

        Button save = new Button("Save", event -> {
            try {
                if (filePath.isEmpty()) {
                    filePath.setInvalid(true);
                    filePath.setErrorMessage("File path is required");
                    return;
                }

                logSourceService.updateFilePath(logSourceId, filePath.getValue());
                showSuccess("Log updated");
                grid.getDataProvider().refreshAll();
                refreshLatest.run();
            } catch (RuntimeException e) {
                showError(e.getMessage());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button close = new Button("Close", event -> dialog.close());

        dialog.add(new VerticalLayout(form, latestBox));
        dialog.getFooter().add(close, save);

        UI ui = UI.getCurrent();
        int previousPoll = ui.getPollInterval();
        ui.setPollInterval(1000);
        Registration pollReg = ui.addPollListener(event -> {
            try {
                refreshLatest.run();
            } catch (RuntimeException ignored) {
            }
        });

        dialog.addDetachListener(event -> {
            pollReg.remove();
            ui.setPollInterval(previousPoll);

            if (clearRouteOnClose) {
                ui.navigate("logs");
            }
        });

        refreshLatest.run();
        dialog.open();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String idStr = event.getRouteParameters().get("id").orElse(null);
        if (idStr == null || idStr.isBlank()) {
            return;
        }
        try {
            Long id = Long.parseLong(idStr);
            // After closing, navigate back to /logs so refresh/back behave nicely.
            openEditDialog(id, true);
        } catch (NumberFormatException ignored) {
        }
    }

    private String stateText(LogSource log) {
        if (log == null) {
            return "-";
        }
        if (!log.isEnabled()) {
            return "PAUSED";
        }
        if (log.getStatus() == LogSourceStatus.ERROR) {
            return "ERROR";
        }
        return "RUNNING";
    }

    private String valueOrEmpty(String value) {
        if (value == null) {
            return "";
        }
        return value;
    }

    private void showSuccess(String message) {
        Notification notification = Notification.show(message, 2500, Notification.Position.TOP_END);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void showError(String message) {
        Notification notification = Notification.show(message, 4000, Notification.Position.TOP_END);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

}
