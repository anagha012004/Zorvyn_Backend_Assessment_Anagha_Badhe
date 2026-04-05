package com.financeapi.config;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;

@Configuration
public class DataSourceConfig {

    @Value("${spring.datasource.url}")
    private String rawUrl;

    @Value("${spring.datasource.username:}")
    private String username;

    @Value("${spring.datasource.password:}")
    private String password;

    /** Parsed JDBC URL (no embedded credentials). */
    private String jdbcUrl;
    private String resolvedUser;
    private String resolvedPassword;

    private void resolve() {
        if (jdbcUrl != null) return;
        if (rawUrl.startsWith("postgresql://") || rawUrl.startsWith("postgres://")) {
            URI uri = URI.create(rawUrl.replaceFirst("^postgres(ql)?://", "postgresql://"));
            String[] info = uri.getUserInfo() != null ? uri.getUserInfo().split(":", 2) : new String[]{};
            jdbcUrl = "jdbc:postgresql://" + uri.getHost()
                    + (uri.getPort() != -1 ? ":" + uri.getPort() : "")
                    + uri.getPath();
            resolvedUser     = info.length > 0 ? info[0] : username;
            resolvedPassword = info.length > 1 ? info[1] : password;
        } else {
            jdbcUrl          = rawUrl;
            resolvedUser     = username;
            resolvedPassword = password;
        }
    }

    @Bean
    @Primary
    public DataSource dataSource() {
        resolve();
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(jdbcUrl);
        ds.setUsername(resolvedUser);
        ds.setPassword(resolvedPassword);
        ds.setDriverClassName("org.postgresql.Driver");
        return ds;
    }

    @Bean
    public FlywayConfigurationCustomizer flywayCustomizer() {
        return (FluentConfiguration config) -> {
            resolve();
            config.dataSource(jdbcUrl, resolvedUser, resolvedPassword);
        };
    }
}
