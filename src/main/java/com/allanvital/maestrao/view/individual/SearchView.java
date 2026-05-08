package com.allanvital.maestrao.view.individual;

import com.allanvital.maestrao.service.log.search.LogSearchRow;
import com.allanvital.maestrao.service.log.search.LogSearchService;
import com.allanvital.maestrao.view.MainLayout;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * @author Allan Vital (https://allanvital.com)
 */
@Route(value = "search", layout = MainLayout.class)
@PageTitle("Search | Maestrao")
@PermitAll
public class SearchView extends VerticalLayout {

    private static final int PAGE_SIZE = 25;
    private static final int TRUNCATE_CHARS = 200;

    private final LogSearchService logSearchService;
    private final TextField query = new TextField();
    private final Grid<LogSearchRow> grid = new Grid<>(LogSearchRow.class, false);

    private final Span pageInfo = new Span();
    private final Span resultsCount = new Span();
    private final Button prevPage = new Button("Prev");
    private final Button nextPage = new Button("Next");

    private int currentPage = 0;
    private long totalItems = 0;

    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public SearchView(LogSearchService logSearchService) {
        this.logSearchService = logSearchService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        add(buildHeader(), buildSearchBar(), buildResultsCount());
        configureGrid();
        configurePager();

        loadPage(0);
    }

    private VerticalLayout buildHeader() {
        H1 title = new H1("Search");
        title.getStyle().set("margin", "0");
        return new VerticalLayout(title);
    }

    private HorizontalLayout buildSearchBar() {
        query.setWidthFull();
        query.setPlaceholder("Search logs... (e.g. log:\"My Log\" level=error service=api)");
        query.setClearButtonVisible(true);
        query.setValueChangeMode(ValueChangeMode.EAGER);
        query.setValueChangeTimeout(500);

        query.addValueChangeListener(event -> {
            currentPage = 0;
            loadPage(0);
        });

        HorizontalLayout bar = new HorizontalLayout(query);
        bar.setWidthFull();
        bar.setAlignItems(Alignment.BASELINE);
        bar.expand(query);

        return bar;
    }

    private Span buildResultsCount() {
        resultsCount.getStyle().set("color", "var(--lumo-secondary-text-color)");
        resultsCount.getStyle().set("margin-bottom", "var(--lumo-space-s)");
        return resultsCount;
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.setPageSize(PAGE_SIZE);

        grid.addColumn(LogSearchRow::logName)
                .setHeader("Log")
                .setAutoWidth(true)
                .setFlexGrow(0)
                .setSortable(false);

        grid.addColumn(row -> dateTimeFormatter.format(row.ingestedAt()))
                .setHeader("Ingested at")
                .setAutoWidth(true)
                .setFlexGrow(0)
                .setSortable(false);

        grid.addComponentColumn(row -> {
                    String full = row.line() == null ? "" : row.line();
                    String preview = truncate(full);

                    Button open = new Button(preview);
                    open.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
                    open.getStyle().set("text-align", "left");
                    open.getStyle().set("white-space", "normal");
                    open.getStyle().set("padding", "0");

                    open.addClickListener(event -> openLineDialog(row));
                    return open;
                })
                .setHeader("Line")
                .setFlexGrow(1)
                .setSortable(false);

        add(grid);
        expand(grid);
    }

    private void configurePager() {
        prevPage.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        nextPage.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        prevPage.addClickListener(event -> {
            if (currentPage <= 0) {
                return;
            }
            loadPage(currentPage - 1);
        });

        nextPage.addClickListener(event -> {
            int maxPage = maxPageIndex();
            if (currentPage >= maxPage) {
                return;
            }
            loadPage(currentPage + 1);
        });

        pageInfo.getStyle().set("color", "var(--lumo-secondary-text-color)");

        HorizontalLayout pager = new HorizontalLayout(prevPage, pageInfo, nextPage);
        pager.setWidthFull();
        pager.setAlignItems(Alignment.CENTER);
        pager.expand(pageInfo);

        add(pager);
    }

    private void loadPage(int pageIndex) {
        if (pageIndex < 0) {
            pageIndex = 0;
        }

        Page<LogSearchRow> page = logSearchService.search(query.getValue(), PageRequest.of(pageIndex, PAGE_SIZE));
        totalItems = page.getTotalElements();
        currentPage = pageIndex;

        grid.setItems(page.getContent());

        updatePager();
    }

    private void updatePager() {
        int maxPage = maxPageIndex();
        int totalPages = Math.max(1, maxPage + 1);
        int pageNumber = Math.min(currentPage + 1, totalPages);

        pageInfo.setText("Page " + pageNumber + " of " + totalPages + " (" + totalItems + " results)");
        resultsCount.setText(totalItems + " results");

        prevPage.setEnabled(currentPage > 0);
        nextPage.setEnabled(currentPage < maxPage);
    }

    private int maxPageIndex() {
        if (totalItems <= 0) {
            return 0;
        }
        return (int) ((totalItems - 1) / PAGE_SIZE);
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        if (value.length() <= TRUNCATE_CHARS) {
            return value;
        }
        return value.substring(0, TRUNCATE_CHARS) + "...";
    }

    private void openLineDialog(LogSearchRow row) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Log line");
        dialog.setDraggable(true);
        dialog.setResizable(true);
        dialog.setWidth("860px");

        Span meta = new Span(row.logName() + " | " + row.hostName() + " (" + row.hostIp() + ") | " + dateTimeFormatter.format(row.ingestedAt()));
        meta.getStyle().set("color", "var(--lumo-secondary-text-color)");

        TextArea full = new TextArea();
        full.setReadOnly(true);
        full.setWidthFull();
        full.setMinHeight("220px");
        full.setValue(row.line() == null ? "" : row.line());

        Button close = new Button("Close", event -> dialog.close());
        close.addClickShortcut(Key.ESCAPE);

        VerticalLayout content = new VerticalLayout(meta, full);
        content.setPadding(false);
        content.setSpacing(true);

        dialog.add(content);
        dialog.getFooter().add(close);
        dialog.open();
    }

}
