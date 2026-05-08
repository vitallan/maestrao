package com.allanvital.maestrao.view;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * @author Allan Vital (https://allanvital.com)
 */
@Route("login")
@PageTitle("Login | Maestrao")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

    private final LoginForm loginForm = new LoginForm();

    public LoginView() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        Image logo = new Image("/maestrao.svg", "Maestrao");
        logo.setWidth("160px");
        logo.getStyle().set("margin-bottom", "var(--lumo-space-m)");

        loginForm.setAction("login");
        loginForm.setForgotPasswordButtonVisible(false);

        add(
                logo,
                new H1("Maestrao"),
                loginForm
        );
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (event.getLocation()
                .getQueryParameters()
                .getParameters()
                .containsKey("error")) {
            loginForm.setError(true);
        }
    }

}
