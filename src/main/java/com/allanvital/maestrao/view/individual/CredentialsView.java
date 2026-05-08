package com.allanvital.maestrao.view.individual;

import com.allanvital.maestrao.model.Credential;
import com.allanvital.maestrao.model.CredentialType;
import com.allanvital.maestrao.service.CredentialService;
import com.allanvital.maestrao.view.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.PasswordField;
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

/**
 * @author Allan Vital (https://allanvital.com)
 */
@Route(value = "credentials", layout = MainLayout.class)
@PageTitle("Credentials | Maestrao")
@PermitAll
public class CredentialsView extends VerticalLayout {

    private static final int PAGE_SIZE = 20;

    private final CredentialService credentialService;
    private final Grid<Credential> grid = new Grid<>(Credential.class, false);

    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public CredentialsView(CredentialService credentialService) {
        this.credentialService = credentialService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        configureHeader();
        configureGrid();
    }

    private void configureHeader() {
        H1 title = new H1("Credentials");
        title.getStyle().set("margin", "0");

        Paragraph subtitle = new Paragraph("Credentials used by Maestrao to reach remote hosts and services");
        subtitle.getStyle()
                .set("margin-top", "0")
                .set("color", "var(--lumo-secondary-text-color)");

        Button addButton = new Button("Add credential", event -> openCredentialDialog(null));
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

        grid.addColumn(Credential::getName)
                .setHeader("Name")
                .setAutoWidth(true)
                .setSortable(false);

        grid.addColumn(credential -> credential.getType().getLabel())
                .setHeader("Type")
                .setAutoWidth(true)
                .setSortable(false);

        grid.addColumn(credential -> valueOrDash(credential.getUsername()))
                .setHeader("Username")
                .setAutoWidth(true)
                .setSortable(false);

        grid.addColumn(credential -> valueOrDash(credential.getDescription()))
                .setHeader("Description")
                .setFlexGrow(1)
                .setSortable(false);

        grid.addColumn(credential -> dateTimeFormatter.format(credential.getUpdatedAt()))
                .setHeader("Updated at")
                .setAutoWidth(true)
                .setSortable(false);

        grid.addComponentColumn(credential -> {
                    Button edit = new Button("Edit", event -> openCredentialDialog(credential));
                    edit.addThemeVariants(ButtonVariant.LUMO_SMALL);

                    Button delete = new Button("Delete", event -> confirmDelete(credential));
                    delete.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);

                    HorizontalLayout actions = new HorizontalLayout(edit, delete);
                    actions.setSpacing(true);
                    return actions;
                })
                .setHeader("Actions")
                .setAutoWidth(true)
                .setFlexGrow(0);

        grid.setDataProvider(DataProvider.fromCallbacks(
                query -> credentialService.findAll(PageRequest.of(
                                query.getPage(),
                                query.getPageSize(),
                                Sort.by(Sort.Direction.DESC, "id")
                        ))
                        .stream(),
                query -> Math.toIntExact(credentialService.count())
        ));

        add(grid);
        expand(grid);
    }

    private void openCredentialDialog(Credential credential) {
        boolean editing = credential != null;

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(editing ? "Edit credential" : "Add credential");
        //dialog.setModal(true);
        dialog.setDraggable(true);
        dialog.setResizable(true);
        dialog.setWidth("620px");

        TextField name = new TextField("Name");
        name.setRequiredIndicatorVisible(true);
        name.setWidthFull();

        Select<CredentialType> type = new Select<>();
        type.setLabel("Type");
        type.setItems(CredentialType.values());
        type.setItemLabelGenerator(CredentialType::getLabel);
        type.setRequiredIndicatorVisible(true);
        type.setWidthFull();

        TextField username = new TextField("Username");
        username.setWidthFull();

        TextArea secret = new TextArea(editing ? "New secret" : "Secret");
        secret.setRequiredIndicatorVisible(!editing);
        secret.setWidthFull();
        secret.setMinHeight("180px");
        secret.setHelperText(editing
                ? "Leave blank to keep the current encrypted value. For SSH keys, paste the full private key including BEGIN/END lines."
                : "For SSH keys, paste the full private key including BEGIN/END lines.");

        secret.setRequiredIndicatorVisible(!editing);
        secret.setWidthFull();
        if (editing) {
            secret.setHelperText("Leave blank to keep the current encrypted value.");
        }

        TextArea description = new TextArea("Description");
        description.setWidthFull();
        description.setMaxLength(500);

        if (editing) {
            name.setValue(valueOrEmpty(credential.getName()));
            type.setValue(credential.getType());
            username.setValue(valueOrEmpty(credential.getUsername()));
            description.setValue(valueOrEmpty(credential.getDescription()));
        } else {
            type.setValue(CredentialType.PASSWORD);
        }

        FormLayout form = new FormLayout(name, type, username, secret, description);
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("520px", 2)
        );
        form.setColspan(description, 2);

        Button save = new Button("Save", event -> {
            try {
                if (name.isEmpty()) {
                    name.setInvalid(true);
                    name.setErrorMessage("Name is required");
                    return;
                }

                if (type.isEmpty()) {
                    type.setInvalid(true);
                    return;
                }

                if (!editing && secret.isEmpty()) {
                    secret.setInvalid(true);
                    secret.setErrorMessage("Secret is required");
                    return;
                }

                if (editing) {
                    credentialService.update(
                            credential.getId(),
                            name.getValue(),
                            type.getValue(),
                            username.getValue(),
                            secret.getValue(),
                            description.getValue()
                    );
                    showSuccess("Credential updated");
                } else {
                    credentialService.create(
                            name.getValue(),
                            type.getValue(),
                            username.getValue(),
                            secret.getValue(),
                            description.getValue()
                    );
                    showSuccess("Credential created");
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

    private void confirmDelete(Credential credential) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Delete credential");
        dialog.setText("Do you really want to delete credential '" + credential.getName() + "'?");
        dialog.setCancelable(true);
        dialog.setConfirmText("Delete");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(event -> {
            credentialService.delete(credential.getId());
            grid.getDataProvider().refreshAll();
            showSuccess("Credential deleted");
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
        Notification notification = Notification.show(message, 5000, Notification.Position.TOP_END);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

}
