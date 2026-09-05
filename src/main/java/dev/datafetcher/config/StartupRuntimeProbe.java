package dev.datafetcher.config;

import java.sql.Connection;
import java.sql.SQLException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.Config;
import org.jboss.logging.Logger;

import io.agroal.api.AgroalDataSource;
import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class StartupRuntimeProbe {

    private static final Logger LOG = Logger.getLogger(StartupRuntimeProbe.class);

    @Inject
    Config config;

    @Inject
    Instance<AgroalDataSource> dataSource;

    void onStart(@Observes StartupEvent event) {
        boolean virtualThreads = config.getOptionalValue("quarkus.thread.virtual.enabled", Boolean.class).orElse(false);
        String datasourceUrl = config.getOptionalValue("quarkus.datasource.jdbc.url", String.class).orElse(null);
        String bronzeRoot = config.getOptionalValue("bronze.root", String.class).orElse(null);

        StringBuilder line = new StringBuilder("runtime defaults: virtual threads=").append(virtualThreads);
        if (datasourceUrl != null) {
            line.append(" | datasource=").append(datasourceUrl);
        }
        if (bronzeRoot != null) {
            line.append(" | bronze.root=").append(bronzeRoot);
        }
        LOG.infof("%s", line);

        if (dataSource.isResolvable()) {
            try (Connection connection = dataSource.get().getConnection()) {
                LOG.info("postgresql reachable: ok");
            } catch (SQLException e) {
                throw new IllegalStateException(
                        "PostgreSQL 18 unreachable at boot: no silent success when the datasource is configured", e);
            }
        } else {
            LOG.info("no datasource configured - probe skipped");
        }
    }
}