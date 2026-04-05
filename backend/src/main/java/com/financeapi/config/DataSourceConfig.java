package com.financeapi.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;

@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username:}") String username,
            @Value("${spring.datasource.password:}") String password) {

        HikariDataSource ds = new HikariDataSource();

        // Render injects: postgresql://user:pass@host/db
        // JDBC requires:  jdbc:postgresql://host/db  + separate user/pass
        if (url.startsWith("postgresql://") || url.startsWith("postgres://")) {
            URI uri = URI.create(url.replaceFirst("^postgres(ql)?://", "postgresql://"));
            String[] info = uri.getUserInfo() != null ? uri.getUserInfo().split(":", 2) : new String[]{};
            ds.setJdbcUrl("jdbc:postgresql://" + uri.getHost()
                    + (uri.getPort() != -1 ? ":" + uri.getPort() : "")
                    + uri.getPath());
            ds.setUsername(info.length > 0 ? info[0] : username);
            ds.setPassword(info.length > 1 ? info[1] : password);
        } else {
            ds.setJdbcUrl(url);
            ds.setUsername(username);
            ds.setPassword(password);
        }

        ds.setDriverClassName("org.postgresql.Driver");
        return ds;
    }
}
