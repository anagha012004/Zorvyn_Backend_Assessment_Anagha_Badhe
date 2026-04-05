package com.financeapi.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username:}") String username,
            @Value("${spring.datasource.password:}") String password) {

        HikariDataSource ds = new HikariDataSource();

        // Render injects: postgresql://user:pass@host[:port]/db
        // JDBC requires:  jdbc:postgresql://host[:port]/db  + separate user/pass
        if (url.startsWith("postgresql://") || url.startsWith("postgres://")) {
            // Strip scheme
            String withoutScheme = url.replaceFirst("^postgres(ql)?://", "");

            // Split userinfo from hostpart: "user:pass@host/db"
            int atIdx = withoutScheme.lastIndexOf('@');
            String userInfo  = withoutScheme.substring(0, atIdx);
            String hostAndDb = withoutScheme.substring(atIdx + 1);

            // Split user:pass
            int colonIdx = userInfo.indexOf(':');
            String resolvedUser     = colonIdx >= 0 ? userInfo.substring(0, colonIdx) : userInfo;
            String resolvedPassword = colonIdx >= 0 ? userInfo.substring(colonIdx + 1) : password;

            ds.setJdbcUrl("jdbc:postgresql://" + hostAndDb);
            ds.setUsername(resolvedUser);
            ds.setPassword(resolvedPassword);
        } else {
            ds.setJdbcUrl(url);
            ds.setUsername(username);
            ds.setPassword(password);
        }

        ds.setDriverClassName("org.postgresql.Driver");
        return ds;
    }
}
