package com.allanvital.maestrao.artifactproxy.view;

import com.allanvital.maestrao.artifactproxy.config.ArtifactProxyProperties;
import com.allanvital.maestrao.artifactproxy.model.ArtifactRemote;
import com.allanvital.maestrao.artifactproxy.model.ArtifactRemoteAuthType;
import com.allanvital.maestrao.artifactproxy.service.ArtifactCacheService;
import com.allanvital.maestrao.artifactproxy.service.ArtifactPathProbeService;
import com.allanvital.maestrao.artifactproxy.service.ArtifactProxyMetricsService;
import com.allanvital.maestrao.artifactproxy.service.ArtifactRemoteService;
import com.allanvital.maestrao.artifactproxy.service.storage.ArtifactTreeNode;
import com.allanvital.maestrao.model.Credential;
import com.allanvital.maestrao.repository.CredentialRepository;
import com.allanvital.maestrao.view.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.data.provider.hierarchy.TreeData;
import com.vaadin.flow.data.provider.hierarchy.TreeDataProvider;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.io.IOException;
import java.util.List;

@Route(value = "artifact-proxy", layout = MainLayout.class)
@PageTitle("Artifact Proxy | Maestrao")
@PermitAll
@ConditionalOnProperty(prefix = "maestrao.artifact-proxy", name = "enabled", havingValue = "true")
public class ArtifactProxyView extends VerticalLayout {

    private final ArtifactRemoteService remoteService;
    private final CredentialRepository credentialRepository;
    private final ArtifactProxyMetricsService metricsService;
    private final ArtifactCacheService cacheService;
    private final ArtifactPathProbeService pathProbeService;

    private final Grid<ArtifactRemote> remotesGrid = new Grid<>(ArtifactRemote.class, false);
    private final TreeGrid<ArtifactTreeNode> treeGrid = new TreeGrid<>();

    private final Span filesStat = new Span();
    private final Span sizeStat = new Span();
    private final Span hitStat = new Span();
    private final Span missStat = new Span();

    public ArtifactProxyView(ArtifactRemoteService remoteService,
                             CredentialRepository credentialRepository,
                             ArtifactProxyMetricsService metricsService,
                             ArtifactCacheService cacheService,
                             ArtifactPathProbeService pathProbeService,
                             ArtifactProxyProperties properties) {
        this.remoteService = remoteService;
        this.credentialRepository = credentialRepository;
        this.metricsService = metricsService;
        this.cacheService = cacheService;
        this.pathProbeService = pathProbeService;

        setWidthFull();
        setHeight(null);

        add(new H1("Artifact Proxy"));
        add(new Paragraph("Maven proxy cache endpoint: /maven/*  | cache root: " + properties.getCacheRoot()));
        add(statsRow());
        add(probeActionRow());
        add(remotesForm());
        add(remotesGrid());
        add(treeSection());
        refreshAll();
    }

    private HorizontalLayout probeActionRow() {
        Button probe = new Button("Test artifact path", e -> openProbeDialog());
        HorizontalLayout row = new HorizontalLayout(probe);
        row.setWidthFull();
        return row;
    }

    private HorizontalLayout statsRow() {
        HorizontalLayout row = new HorizontalLayout(filesStat, sizeStat, hitStat, missStat);
        row.setWidthFull();
        row.setSpacing(true);
        return row;
    }

    private VerticalLayout remotesForm() {
        TextField name = new TextField("Name");
        TextField baseUrl = new TextField("Base URL");
        ComboBox<ArtifactRemoteAuthType> auth = new ComboBox<>("Auth");
        auth.setItems(ArtifactRemoteAuthType.values());
        auth.setValue(ArtifactRemoteAuthType.NONE);
        ComboBox<Credential> credential = new ComboBox<>("Credential");
        List<Credential> credentials = credentialRepository.findAll();
        credential.setItems(credentials);
        credential.setItemLabelGenerator(c -> c.getName());
        IntegerField timeout = new IntegerField("Timeout (ms)");
        timeout.setValue(10000);
        Button add = new Button("Add Remote", e -> {
            try {
                ArtifactRemote remote = new ArtifactRemote();
                remote.setName(name.getValue());
                remote.setBaseUrl(baseUrl.getValue());
                remote.setAuthType(auth.getValue());
                remote.setCredential(credential.getValue());
                remote.setTimeoutMs(timeout.getValue());
                remote.setEnabled(true);
                remoteService.save(remote);
                name.clear();
                baseUrl.clear();
                credential.clear();
                timeout.setValue(10000);
                refreshAll();
            } catch (Exception ex) {
                Notification.show("Failed to save remote: " + ex.getMessage());
            }
        });
        HorizontalLayout line = new HorizontalLayout(name, baseUrl, auth, credential, timeout, add);
        line.setWidthFull();
        line.setDefaultVerticalComponentAlignment(Alignment.END);
        VerticalLayout layout = new VerticalLayout(line);
        layout.setPadding(false);
        layout.setSpacing(false);
        return layout;
    }

    private Grid<ArtifactRemote> remotesGrid() {
        remotesGrid.addColumn(ArtifactRemote::getId).setHeader("ID").setAutoWidth(true);
        remotesGrid.addColumn(ArtifactRemote::getName).setHeader("Name").setAutoWidth(true);
        remotesGrid.addColumn(ArtifactRemote::getBaseUrl).setHeader("Base URL").setFlexGrow(1);
        remotesGrid.addColumn(r -> r.getAuthType().name()).setHeader("Auth").setAutoWidth(true);
        remotesGrid.addColumn(r -> r.getCredential() == null ? "-" : r.getCredential().getName()).setHeader("Credential").setAutoWidth(true);
        remotesGrid.addColumn(r -> Boolean.TRUE.equals(r.getEnabled()) ? "Yes" : "No").setHeader("Enabled").setAutoWidth(true);
        remotesGrid.addComponentColumn(remote -> new Button("Delete", e -> {
            remoteService.delete(remote.getId());
            refreshAll();
        })).setAutoWidth(true);
        remotesGrid.setWidthFull();
        return remotesGrid;
    }

    private VerticalLayout treeSection() {
        treeGrid.addHierarchyColumn(ArtifactTreeNode::getPath).setHeader("Cached artifact tree").setFlexGrow(1);
        treeGrid.addColumn(node -> node.isDirectory() ? "dir" : "file").setHeader("Type").setAutoWidth(true);
        treeGrid.addColumn(ArtifactTreeNode::getSizeBytes).setHeader("Bytes").setAutoWidth(true);
        treeGrid.setHeight("400px");

        TextField purgePath = new TextField("Purge path");
        Button purge = new Button("Purge", e -> {
            try {
                cacheService.purgePath(purgePath.getValue());
                purgePath.clear();
                refreshAll();
            } catch (Exception ex) {
                Notification.show("Purge failed: " + ex.getMessage());
            }
        });
        Button refresh = new Button("Refresh", e -> refreshAll());

        HorizontalLayout actions = new HorizontalLayout(purgePath, purge, refresh);
        actions.setDefaultVerticalComponentAlignment(Alignment.END);
        VerticalLayout layout = new VerticalLayout(actions, treeGrid);
        layout.setWidthFull();
        return layout;
    }

    private void refreshAll() {
        remotesGrid.setItems(remoteService.findAll());
        ArtifactProxyMetricsService.Snapshot metrics = metricsService.snapshot();
        filesStat.setText("Files: " + metrics.fileCount());
        sizeStat.setText("Size: " + metrics.totalSizeBytes() + " bytes");
        hitStat.setText("Hits: " + metrics.hitCount() + " (" + String.format(java.util.Locale.ROOT, "%.1f", metrics.hitRatioPercent()) + "%)");
        missStat.setText("Negative cache: " + metrics.negativeCount());
        try {
            ArtifactTreeNode root = cacheService.listTree("", 6, 2000);
            TreeData<ArtifactTreeNode> data = new TreeData<>();
            buildTree(data, null, root);
            treeGrid.setDataProvider(new TreeDataProvider<>(data));
            treeGrid.expand(root);
        } catch (IOException e) {
            Notification.show("Failed to load tree: " + e.getMessage());
        }
    }

    private void openProbeDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Artifact path probe");
        dialog.setWidth("850px");

        TextField path = new TextField("Artifact path");
        path.setWidthFull();
        path.setPlaceholder("org/example/demo/1.0/demo-1.0.jar");

        TextArea report = new TextArea("Result");
        report.setReadOnly(true);
        report.setWidthFull();
        report.setHeight("320px");

        Button run = new Button("Run probe", e -> {
            try {
                ArtifactPathProbeService.ProbeReport result = pathProbeService.probe(path.getValue());
                report.setValue(formatProbe(result));
            } catch (Exception ex) {
                report.setValue("Probe failed: " + ex.getMessage());
            }
        });
        Button close = new Button("Close", e -> dialog.close());

        VerticalLayout body = new VerticalLayout(path, run, report);
        body.setPadding(false);
        body.setSpacing(true);
        dialog.add(body);
        dialog.getFooter().add(close);
        dialog.open();
    }

    private String formatProbe(ArtifactPathProbeService.ProbeReport result) {
        StringBuilder out = new StringBuilder();
        out.append("Requested: ").append(result.requestedPath()).append('\n');
        out.append("Normalized: ").append(result.normalizedPath()).append('\n');
        out.append("Final status: ").append(result.finalStatus()).append('\n');
        out.append("Cache hit: ").append(result.cacheHit()).append('\n');
        out.append("Negative cache active: ").append(result.negativeCacheActive()).append('\n');
        if (result.negativeCacheTtlUntil() != null) {
            out.append("Negative cache TTL until: ").append(result.negativeCacheTtlUntil()).append('\n');
        }
        out.append("Winner remote: ").append(result.winnerRemote() == null ? "-" : result.winnerRemote()).append('\n');
        if (!result.remoteResults().isEmpty()) {
            out.append('\n').append("Remote results:").append('\n');
            for (ArtifactPathProbeService.ProbeRemoteResult remote : result.remoteResults()) {
                out.append("- ")
                        .append(remote.remoteName())
                        .append(" status=")
                        .append(remote.statusCode())
                        .append(" latencyMs=")
                        .append(remote.latencyMs());
                if (remote.error() != null && !remote.error().isBlank()) {
                    out.append(" error=").append(remote.error());
                }
                out.append('\n');
            }
        }
        return out.toString();
    }

    private void buildTree(TreeData<ArtifactTreeNode> data, ArtifactTreeNode parent, ArtifactTreeNode node) {
        data.addItem(parent, node);
        for (ArtifactTreeNode child : node.getChildren()) {
            buildTree(data, node, child);
        }
    }
}
