package com.allanvital.maestrao.view.individual;

import com.allanvital.maestrao.model.Host;
import com.allanvital.maestrao.service.hosthealth.HostHealthMetricsService;
import com.allanvital.maestrao.service.hosthealth.HostHealthWindow;
import com.allanvital.maestrao.service.hosthealth.SvgSparkline;
import com.allanvital.maestrao.view.MainLayout;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Route(value = "host-health", layout = MainLayout.class)
@PageTitle("Host Health | Maestrao")
@PermitAll
public class HostHealthView extends VerticalLayout {

    private final HostHealthMetricsService metricsService;

    private final ComboBox<Host> host = new ComboBox<>("Host");
    private final ComboBox<HostHealthWindow> window = new ComboBox<>("Window");

    private final Span current = new Span();

    private final Span cpuValue = new Span("-");
    private final Span memValue = new Span("-");
    private final Span diskValue = new Span("-");
    private final Span loadValue = new Span("-");

    private final Div cpuGraph = new Div();
    private final Div memGraph = new Div();
    private final Div diskGraph = new Div();
    private final Div loadGraph = new Div();

    private final DateTimeFormatter dtf = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private final DateTimeFormatter axisDtf = DateTimeFormatter
            .ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault());

    public HostHealthView(HostHealthMetricsService metricsService) {
        this.metricsService = metricsService;

        setWidthFull();
        setHeight(null);
        setPadding(true);
        setSpacing(true);

        add(buildHeader(), buildControls(), buildContent());

        UI ui = UI.getCurrent();
        int previousPoll = ui.getPollInterval();
        ui.setPollInterval(15_000);
        ui.addPollListener(event -> refresh());
        addDetachListener(event -> ui.setPollInterval(previousPoll));

        refresh();
    }

    private VerticalLayout buildHeader() {
        H1 title = new H1("Host Health");
        title.getStyle().set("margin", "0");
        Paragraph subtitle = new Paragraph("Real-time Linux host telemetry gathered over SSH.");
        subtitle.getStyle().set("margin", "0").set("color", "var(--lumo-secondary-text-color)");
        VerticalLayout header = new VerticalLayout(title, subtitle);
        header.setPadding(false);
        header.setSpacing(false);
        return header;
    }

    private HorizontalLayout buildControls() {
        host.setWidth("420px");
        List<Host> items = metricsService.findEnabledHostsForSelection();
        host.setItems(items);
        host.setItemLabelGenerator(h -> h.getName() + " (" + h.getIp() + ")");
        host.addValueChangeListener(e -> refresh());

        window.setWidth("220px");
        window.setItems(HostHealthWindow.values());
        window.setItemLabelGenerator(HostHealthWindow::label);
        window.setValue(HostHealthWindow.M15);
        window.addValueChangeListener(e -> refresh());

        HorizontalLayout bar = new HorizontalLayout(host, window);
        bar.setAlignItems(Alignment.END);
        bar.setWidthFull();
        return bar;
    }

    private VerticalLayout buildContent() {
        current.getStyle().set("color", "var(--lumo-secondary-text-color)");

        Div cards = new Div(
                metricCard("CPU", cpuValue, cpuGraph, "#0ea5e9", "rgba(14,165,233,0.2)"),
                metricCard("Memory", memValue, memGraph, "#22c55e", "rgba(34,197,94,0.2)"),
                metricCard("Disk /", diskValue, diskGraph, "#f59e0b", "rgba(245,158,11,0.2)"),
                metricCard("Load 1/5/15", loadValue, loadGraph, null, null)
        );
        cards.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "1fr")
                .set("gap", "var(--lumo-space-m)")
                .set("width", "100%")
                .set("max-width", "1100px")
                .set("margin", "0 auto")
                .set("min-width", "0");

        VerticalLayout box = new VerticalLayout(current, cards);
        box.setPadding(false);
        box.setSpacing(true);
        box.setWidthFull();
        return box;
    }

    private Div metricCard(String title, Span value, Div graph, String strokeColor, String fillColor) {
        Span titleSpan = new Span(title);
        titleSpan.getStyle().set("font-size", "0.85rem").set("font-weight", "600").set("color", "var(--lumo-secondary-text-color)");

        value.getStyle().set("font-size", "1.5rem").set("font-weight", "700");
        graph.getStyle()
                .set("line-height", "0")
                .set("width", "100%")
                .set("max-width", "100%")
                .set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "8px")
                .set("padding", "4px")
                .set("box-sizing", "border-box")
                .set("overflow", "hidden");

        Div card = new Div(titleSpan, value, graph);
        card.getElement().setAttribute("data-stroke", strokeColor == null ? "" : strokeColor);
        card.getElement().setAttribute("data-fill", fillColor == null ? "" : fillColor);
        card.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "var(--lumo-space-s)")
                .set("width", "100%")
                .set("min-width", "0")
                .set("padding", "var(--lumo-space-m)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "12px")
                .set("background", "linear-gradient(180deg, rgba(255,255,255,0.02), rgba(255,255,255,0.0))");
        return card;
    }

    private void refresh() {
        Host selected = host.getValue();
        if (selected == null) {
            current.setText("Select a host to view metrics");
            cpuValue.setText("-");
            memValue.setText("-");
            diskValue.setText("-");
            loadValue.setText("-");
            cpuGraph.getElement().setProperty("innerHTML", "");
            memGraph.getElement().setProperty("innerHTML", "");
            diskGraph.getElement().setProperty("innerHTML", "");
            loadGraph.getElement().setProperty("innerHTML", "");
            return;
        }

        HostHealthWindow w = window.getValue() == null ? HostHealthWindow.M15 : window.getValue();
        HostHealthMetricsService.HostHealthSeries series = metricsService.getSeries(selected.getId(), w);
        HostHealthMetricsService.Point latest = series.latest();

        if (latest == null) {
            current.setText("No samples yet for this host");
            cpuValue.setText("-");
            memValue.setText("-");
            diskValue.setText("-");
            loadValue.setText("-");
            cpuGraph.getElement().setProperty("innerHTML", "");
            memGraph.getElement().setProperty("innerHTML", "");
            diskGraph.getElement().setProperty("innerHTML", "");
            loadGraph.getElement().setProperty("innerHTML", "");
            return;
        }

        current.setText("Last sample: " + dtf.format(latest.at())
                + " | CPU: " + fmt(latest.cpuUsedPct()) + "%"
                + " | Mem: " + fmt(latest.memUsedPct()) + "%"
                + " | Disk(/): " + fmt(latest.diskUsedPct()) + "%"
                + " | Load: " + fmt(latest.load1()) + "/" + fmt(latest.load5()) + "/" + fmt(latest.load15()));

        cpuValue.setText(fmt(latest.cpuUsedPct()) + "%");
        memValue.setText(fmt(latest.memUsedPct()) + "%");
        diskValue.setText(fmt(latest.diskUsedPct()) + "%");
        loadValue.setText(fmt(latest.load1()) + " / " + fmt(latest.load5()) + " / " + fmt(latest.load15()));

        String xStart = axisDtf.format(series.points().get(0).at());
        String xEnd = axisDtf.format(series.points().get(series.points().size() - 1).at());
        String xMid = axisDtf.format(series.points().get(series.points().size() / 2).at());

        cpuGraph.getElement().setProperty("innerHTML", SvgSparkline.areaLine(
                series.points().stream().map(p -> p.cpuUsedPct()).toList(),
                1040,
                170,
                "#0ea5e9",
                "rgba(14,165,233,0.2)",
                0,
                100,
                true,
                xStart,
                xMid,
                xEnd,
                "%",
                true
        ));
        memGraph.getElement().setProperty("innerHTML", SvgSparkline.areaLine(
                series.points().stream().map(p -> p.memUsedPct()).toList(),
                1040,
                170,
                "#22c55e",
                "rgba(34,197,94,0.2)",
                0,
                100,
                true,
                xStart,
                xMid,
                xEnd,
                "%",
                true
        ));
        diskGraph.getElement().setProperty("innerHTML", SvgSparkline.areaLine(
                series.points().stream().map(p -> p.diskUsedPct()).toList(),
                1040,
                170,
                "#f59e0b",
                "rgba(245,158,11,0.2)",
                0,
                100,
                true,
                xStart,
                xMid,
                xEnd,
                "%",
                true
        ));
        loadGraph.getElement().setProperty("innerHTML", SvgSparkline.multiLine(
                series.points().stream().map(p -> p.load1()).toList(),
                series.points().stream().map(p -> p.load5()).toList(),
                series.points().stream().map(p -> p.load15()).toList(),
                1040,
                190,
                xStart,
                xMid,
                xEnd,
                true,
                true
        ));
    }

    private String fmt(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return "-";
        }
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }
}
