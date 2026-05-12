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
    private final Div cpuGraph = new Div();
    private final Div memGraph = new Div();
    private final Div diskGraph = new Div();
    private final Div loadGraph = new Div();

    private final DateTimeFormatter dtf = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public HostHealthView(HostHealthMetricsService metricsService) {
        this.metricsService = metricsService;

        setSizeFull();
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
        return new VerticalLayout(title);
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
        return bar;
    }

    private VerticalLayout buildContent() {
        current.getStyle().set("color", "var(--lumo-secondary-text-color)");

        cpuGraph.getStyle().set("color", "var(--lumo-primary-text-color)");
        memGraph.getStyle().set("color", "var(--lumo-primary-text-color)");
        diskGraph.getStyle().set("color", "var(--lumo-primary-text-color)");
        loadGraph.getStyle().set("color", "var(--lumo-primary-text-color)");

        VerticalLayout box = new VerticalLayout(current,
                labeled("CPU %", cpuGraph),
                labeled("Memory %", memGraph),
                labeled("Disk / %", diskGraph),
                labeled("Load (1/5/15)", loadGraph)
        );
        box.setPadding(false);
        box.setSpacing(true);
        return box;
    }

    private HorizontalLayout labeled(String label, Div content) {
        Span l = new Span(label);
        l.getStyle().set("width", "120px");
        l.getStyle().set("font-weight", "600");
        content.getStyle().set("flex", "1");
        HorizontalLayout row = new HorizontalLayout(l, content);
        row.setWidthFull();
        row.setAlignItems(Alignment.CENTER);
        return row;
    }

    private void refresh() {
        Host selected = host.getValue();
        if (selected == null) {
            current.setText("Select a host to view metrics");
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
            return;
        }

        current.setText("Last sample: " + dtf.format(latest.at())
                + " | CPU: " + fmt(latest.cpuUsedPct()) + "%"
                + " | Mem: " + fmt(latest.memUsedPct()) + "%"
                + " | Disk(/): " + fmt(latest.diskUsedPct()) + "%"
                + " | Load: " + fmt(latest.load1()) + "/" + fmt(latest.load5()) + "/" + fmt(latest.load15()));

        cpuGraph.getElement().setProperty("innerHTML", SvgSparkline.polyline(series.points().stream().map(p -> p.cpuUsedPct()).toList(), 520, 36));
        memGraph.getElement().setProperty("innerHTML", SvgSparkline.polyline(series.points().stream().map(p -> p.memUsedPct()).toList(), 520, 36));
        diskGraph.getElement().setProperty("innerHTML", SvgSparkline.polyline(series.points().stream().map(p -> p.diskUsedPct()).toList(), 520, 36));
        // render load1 only to keep it readable; values are still in latest text
        loadGraph.getElement().setProperty("innerHTML", SvgSparkline.polyline(series.points().stream().map(p -> p.load1()).toList(), 520, 36));
    }

    private String fmt(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return "-";
        }
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }
}
