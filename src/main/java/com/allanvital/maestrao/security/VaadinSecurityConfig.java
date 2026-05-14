package com.allanvital.maestrao.security;

import com.allanvital.maestrao.view.LoginView;
import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * @author Allan Vital (https://allanvital.com)
 */
@Configuration
@EnableWebSecurity
public class VaadinSecurityConfig {

    @Value("${maestrao.security.admin.username:admin}")
    private String adminUsername;

    @Value("${maestrao.security.admin.password:admin}")
    private String adminPassword;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.with(VaadinSecurityConfigurer.vaadin(), configurer -> {
            configurer.loginView(LoginView.class);
        });

        // Allow unauthenticated access to static assets (favicon/logo).
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/favicon.ico",
                        "/favicon.svg",
                        "/favicon-*.png",
                        "/apple-touch-icon.png",
                        "/site.webmanifest",
                        "/web-app-manifest-*.png",
                        "/maestrao*.png",
                        "/maestrao.svg",
                        "/maven/**"
                ).permitAll()
        );

        return http.build();
    }

    @Bean
    public InMemoryUserDetailsManager users() {
        UserDetails admin = User.withUsername(adminUsername)
                .password("{noop}" + adminPassword)
                .roles("ADMIN")
                .build();

        return new InMemoryUserDetailsManager(admin);
    }

}
