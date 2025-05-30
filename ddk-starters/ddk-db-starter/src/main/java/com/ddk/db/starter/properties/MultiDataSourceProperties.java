package com.ddk.db.starter.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "ddk.datasource")
public class MultiDataSourceProperties {

    private List<DataSourceProperty> sources = new ArrayList<>();
    private String primary; // Name of the primary datasource, matches one of the names in sources

    public List<DataSourceProperty> getSources() {
        return sources;
    }

    public void setSources(List<DataSourceProperty> sources) {
        this.sources = sources;
    }

    public String getPrimary() {
        return primary;
    }

    public void setPrimary(String primary) {
        this.primary = primary;
    }

    public static class DataSourceProperty {
        private String name;
        private String url;
        private String username;
        private String password;
        private String driverClassName;
        private Class<? extends javax.sql.DataSource> type; // e.g., com.zaxxer.hikari.HikariDataSource.class

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }

        public Class<? extends javax.sql.DataSource> getType() {
            return type;
        }

        public void setType(Class<? extends javax.sql.DataSource> type) {
            this.type = type;
        }
    }
}
