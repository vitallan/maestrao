package com.allanvital.maestrao.view;

import com.allanvital.maestrao.artifactproxy.view.ArtifactProxyView;
import com.allanvital.maestrao.artifactproxy.config.ArtifactProxyProperties;
import com.allanvital.maestrao.service.AppVersionService;
import com.allanvital.maestrao.view.individual.*;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;

/**
 * @author Allan Vital (https://allanvital.com)
 */
@PermitAll
public class MainLayout extends AppLayout {

    private final ArtifactProxyProperties artifactProxyProperties;

    public MainLayout(AuthenticationContext authenticationContext,
                      AppVersionService appVersionService,
                      ArtifactProxyProperties artifactProxyProperties) {
        this.artifactProxyProperties = artifactProxyProperties;
        createHeader(authenticationContext, appVersionService);
        createDrawer();
    }

    private void createHeader(AuthenticationContext authenticationContext, AppVersionService appVersionService) {
        Image icon = new Image("/favicon.svg", "Maestrao");
        icon.setHeight("26px");

        H1 title = new H1("Maestrao");
        title.getStyle()
                .set("font-size", "var(--lumo-font-size-l)")
                .set("margin", "0");

        Span version = new Span(appVersionService.getDisplayVersion());
        version.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("white-space", "nowrap");

        Button logout = new Button("Logout", event -> authenticationContext.logout());

        HorizontalLayout brand = new HorizontalLayout(icon, title);
        brand.setAlignItems(HorizontalLayout.Alignment.CENTER);
        brand.setSpacing(true);
        brand.getStyle().set("gap", "var(--lumo-space-m)");

        HorizontalLayout header = new HorizontalLayout(brand, version, logout);
        header.setWidthFull();
        header.setAlignItems(HorizontalLayout.Alignment.CENTER);
        header.expand(brand);
        header.getStyle()
                .set("padding", "0 var(--lumo-space-m)")
                .set("border-bottom", "1px solid var(--lumo-contrast-10pct)");

        addToNavbar(header);
    }

    private void createDrawer() {
        Span appName = new Span("Menu");
        appName.getStyle()
                .set("font-weight", "600")
                .set("padding", "var(--lumo-space-m)");

        SideNav nav = new SideNav();
        nav.addItem(
                new SideNavItem("Dashboard", DashboardView.class, VaadinIcon.DASHBOARD.create()),
                new SideNavItem("Credentials", CredentialsView.class, VaadinIcon.USER.create()),
                new SideNavItem("Hosts", HostsView.class, VaadinIcon.SERVER.create()),
                new SideNavItem("Host Health", HostHealthView.class, VaadinIcon.LINE_CHART.create()),
                new SideNavItem("Logs", LogsView.class, VaadinIcon.FILE_TEXT.create()),
                new SideNavItem("Jobs", JobsView.class, VaadinIcon.TOOLS.create()),
                new SideNavItem("Search", SearchView.class, VaadinIcon.SEARCH.create()),
                new SideNavItem("Email", EmailView.class, VaadinIcon.ENVELOPE.create())
                //new SideNavItem("Configuration", ConfigurationView.class, VaadinIcon.COG.create())
        );

        if (artifactProxyProperties.isEnabled()) {
            nav.addItem(new SideNavItem("Artifact Proxy", ArtifactProxyView.class, VaadinIcon.DOWNLOAD_ALT.create()));
        }

        addToDrawer(appName, nav);
    }

}
