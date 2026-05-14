package com.allanvital.maestrao.view.component;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class LiveIndicator extends HorizontalLayout {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Span dot = new Span(" ");
    private final Span label = new Span("Live");
    private final Span updatedAt = new Span("-");

    public LiveIndicator() {
        setSpacing(true);
        setPadding(false);
        setAlignItems(Alignment.CENTER);

        dot.getStyle()
                .set("display", "inline-block")
                .set("width", "9px")
                .set("height", "9px")
                .set("border-radius", "50%")
                .set("background", "var(--lumo-warning-color)")
                .set("box-shadow", "0 0 0 0 var(--lumo-warning-color-50pct)")
                .set("animation", "maestrao-pulse 1.6s infinite");

        label.getStyle().set("font-weight", "600");
        updatedAt.getStyle().set("color", "var(--lumo-secondary-text-color)");

        getStyle().set("font-size", "var(--lumo-font-size-s)");
        getStyle().set("align-items", "center");
        getElement().executeJs(
                "if(!document.getElementById('maestrao-live-style')){" +
                        "const s=document.createElement('style');" +
                        "s.id='maestrao-live-style';" +
                        "s.textContent='@keyframes maestrao-pulse{0%{box-shadow:0 0 0 0 var(--lumo-warning-color-50pct);}70%{box-shadow:0 0 0 8px transparent;}100%{box-shadow:0 0 0 0 transparent;}}';" +
                        "document.head.appendChild(s);}" );

        add(dot, label, updatedAt);
        markUpdatedNow();
    }

    public void markUpdatedNow() {
        updatedAt.setText("Updated " + TIME.format(Instant.now()));
    }
}
