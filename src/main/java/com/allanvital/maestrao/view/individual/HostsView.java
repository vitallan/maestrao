package com.allanvital.maestrao.view.individual;

import com.allanvital.maestrao.model.Credential;
import com.allanvital.maestrao.model.Host;
import com.allanvital.maestrao.service.CredentialService;
import com.allanvital.maestrao.service.HostConnectionTestResult;
import com.allanvital.maestrao.service.HostService;
import com.allanvital.maestrao.view.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
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
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * @author Allan Vital (https://allanvital.com)
 */
@Route(value = "hosts", layout = MainLayout.class)
@PageTitle("Hosts | Maestrao")
@PermitAll
public class HostsView extends VerticalLayout {

    private static final int PAGE_SIZE = 20;

    private final HostService hostService;
    private final CredentialService credentialService;
    private final Grid<Host> grid = new Grid<>(Host.class, false);

    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public HostsView(HostService hostService, CredentialService credentialService) {
        this.hostService = hostService;
        this.credentialService = credentialService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        configureHeader();
        configureGrid();
    }

    private void configureHeader() {
        H1 title = new H1("Hosts");
        title.getStyle().set("margin", "0");

        Paragraph subtitle = new Paragraph("Remote hosts to be accessed by Maestrao to collect logs and metrics");
        subtitle.getStyle()
                .set("margin-top", "0")
                .set("color", "var(--lumo-secondary-text-color)");

        Button addButton = new Button("Add host", event -> openHostDialog(null));
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

        grid.addColumn(Host::getName)
                .setHeader("Name")
                .setAutoWidth(true)
                .setSortable(false);

        grid.addColumn(Host::getIp)
                .setHeader("IP / Hostname")
                .setAutoWidth(true)
                .setSortable(false);

        grid.addColumn(Host::getSshPort)
                .setHeader("SSH Port")
                .setAutoWidth(true)
                .setSortable(false);

        grid.addColumn(host -> host.getCredential().getName())
                .setHeader("Credential")
                .setAutoWidth(true)
                .setSortable(false);

        grid.addColumn(host -> valueOrDash(host.getDescription()))
                .setHeader("Description")
                .setFlexGrow(1)
                .setSortable(false);

        grid.addColumn(host -> dateTimeFormatter.format(host.getUpdatedAt()))
                .setHeader("Updated at")
                .setAutoWidth(true)
                .setSortable(false);

        grid.addComponentColumn(host -> {
                    Button test = new Button("Test Connection", event -> testSavedHost(host));
                    test.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

                    Button edit = new Button("Edit", event -> openHostDialog(host));
                    edit.addThemeVariants(ButtonVariant.LUMO_SMALL);

                    Button delete = new Button("Delete", event -> confirmDelete(host));
                    delete.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);

                    HorizontalLayout actions = new HorizontalLayout(test, edit, delete);
                    actions.setSpacing(true);
                    return actions;
                })
                .setHeader("Actions")
                .setAutoWidth(true)
                .setFlexGrow(0);

        grid.setDataProvider(DataProvider.fromCallbacks(
                query -> hostService.findAll(PageRequest.of(
                                query.getPage(),
                                query.getPageSize(),
                                Sort.by(Sort.Direction.DESC, "id")
                        ))
                        .stream(),
                query -> Math.toIntExact(hostService.count())
        ));

        add(grid);
        expand(grid);
    }

    private void openHostDialog(Host host) {
        boolean editing = host != null;

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(editing ? "Edit host" : "Add host");
        dialog.setDraggable(true);
        dialog.setResizable(true);
        dialog.setWidth("720px");

        TextField name = new TextField("Name");
        name.setRequiredIndicatorVisible(true);
        name.setWidthFull();

        TextField ip = new TextField("IP / Hostname");
        ip.setRequiredIndicatorVisible(true);
        ip.setWidthFull();

        IntegerField sshPort = new IntegerField("SSH Port");
        sshPort.setRequiredIndicatorVisible(true);
        sshPort.setMin(1);
        sshPort.setMax(65535);
        sshPort.setStepButtonsVisible(true);
        sshPort.setValue(22);
        sshPort.setWidthFull();

        ComboBox<Credential> credential = new ComboBox<>("Credential");
        credential.setRequiredIndicatorVisible(true);
        credential.setWidthFull();
        credential.setItemLabelGenerator(item -> item.getName() + " (" + item.getType().getLabel() + ")");
        List<Credential> credentials = loadCredentials();
        credential.setItems(credentials);

        TextArea description = new TextArea("Description");
        description.setWidthFull();
        description.setMaxLength(500);

        Checkbox gatherHealthMetrics = new Checkbox("Gather host health metrics?");
        gatherHealthMetrics.setHelperText("If enabled, Maestrao will poll this host every 15 seconds to collect basic health metrics.");

        if (editing) {
            name.setValue(valueOrEmpty(host.getName()));
            ip.setValue(valueOrEmpty(host.getIp()));
            sshPort.setValue(host.getSshPort() == null ? 22 : host.getSshPort());
            credentials.stream()
                    .filter(item -> item.getId().equals(host.getCredential().getId()))
                    .findFirst()
                    .ifPresent(credential::setValue);
            description.setValue(valueOrEmpty(host.getDescription()));
            gatherHealthMetrics.setValue(host.isGatherHealthMetrics());
        }

        FormLayout form = new FormLayout(name, ip, sshPort, credential, description, gatherHealthMetrics);
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("520px", 2)
        );
        form.setColspan(description, 2);
        form.setColspan(gatherHealthMetrics, 2);

        Span testConnectionResult = new Span();
        testConnectionResult.getStyle().set("font-weight", "600");

        Button testConnection = new Button("Test Connection", event -> {
            testConnectionResult.setText("");

            if (!validateForm(name, ip, sshPort, credential)) {
                return;
            }

            HostConnectionTestResult result = hostService.testConnection(
                    ip.getValue(),
                    sshPort.getValue(),
                    credential.getValue().getId()
            );

            if (result.success()) {
                testConnectionResult.setText(result.message());
                testConnectionResult.getStyle().set("color", "var(--lumo-success-text-color)");
            } else {
                testConnectionResult.setText(result.message());
                testConnectionResult.getStyle().set("color", "var(--lumo-error-text-color)");
            }

            showConnectionResult(result);
        });
        testConnection.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button save = new Button("Save", event -> {
            try {
                if (!validateForm(name, ip, sshPort, credential)) {
                    return;
                }

                if (editing) {
                    hostService.update(
                            host.getId(),
                            name.getValue(),
                            ip.getValue(),
                            sshPort.getValue(),
                            description.getValue(),
                            credential.getValue().getId(),
                            gatherHealthMetrics.getValue()
                    );
                    showSuccess("Host updated");
                } else {
                    hostService.create(
                            name.getValue(),
                            ip.getValue(),
                            sshPort.getValue(),
                            description.getValue(),
                            credential.getValue().getId(),
                            gatherHealthMetrics.getValue()
                    );
                    showSuccess("Host created");
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
        dialog.add(testConnectionResult);
        dialog.getFooter().add(testConnection, cancel, save);
        dialog.open();
    }

    private boolean validateForm(TextField name,
                                 TextField ip,
                                 IntegerField sshPort,
                                 ComboBox<Credential> credential) {
        boolean valid = true;

        if (name.isEmpty()) {
            name.setInvalid(true);
            name.setErrorMessage("Name is required");
            valid = false;
        } else {
            name.setInvalid(false);
        }

        if (ip.isEmpty()) {
            ip.setInvalid(true);
            ip.setErrorMessage("IP / Hostname is required");
            valid = false;
        } else {
            ip.setInvalid(false);
        }

        Integer port = sshPort.getValue();
        if (port == null || port < 1 || port > 65535) {
            sshPort.setInvalid(true);
            sshPort.setErrorMessage("SSH port must be between 1 and 65535");
            valid = false;
        } else {
            sshPort.setInvalid(false);
        }

        if (credential.isEmpty()) {
            credential.setInvalid(true);
            credential.setErrorMessage("Credential is required");
            valid = false;
        } else {
            credential.setInvalid(false);
        }

        return valid;
    }

    private List<Credential> loadCredentials() {
        return credentialService.findAll(PageRequest.of(0, 1_000, Sort.by(Sort.Direction.ASC, "name")))
                .getContent();
    }

    private void testSavedHost(Host host) {
        HostConnectionTestResult result = hostService.testConnection(host.getId());
        showConnectionResult(result);
    }

    private void showConnectionResult(HostConnectionTestResult result) {
        if (result.success()) {
            showSuccess(result.message());
        } else {
            showError(result.message());
        }
    }

    private void confirmDelete(Host host) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Delete host");
        dialog.setText("Do you really want to delete host '" + host.getName() + "'?");
        dialog.setCancelable(true);
        dialog.setConfirmText("Delete");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(event -> {
            hostService.delete(host.getId());
            grid.getDataProvider().refreshAll();
            showSuccess("Host deleted");
        });
        dialog.open();
    }

    private String valueOrDash(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value;
    }

    private String valueOrEmpty(String value) {
        if (value == null) {
            return "";
        }
        return value;
    }

    private void showSuccess(String message) {
        Notification notification = Notification.show(message, 3000, Notification.Position.TOP_END);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void showError(String message) {
        Notification notification = Notification.show(message, 6000, Notification.Position.TOP_END);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
