package com.allanvital.maestrao.view.individual;

import com.allanvital.maestrao.view.MainLayout;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

/**
 * @author Allan Vital (https://allanvital.com)
 */
@Route(value = "configuration", layout = MainLayout.class)
@PageTitle("Configuration | Maestrao")
@PermitAll
public class ConfigurationView extends VerticalLayout {

    public ConfigurationView() {
        add(new H1("Configuration"));
    }

}
