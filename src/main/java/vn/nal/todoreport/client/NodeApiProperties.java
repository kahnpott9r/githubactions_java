package vn.nal.todoreport.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình kết nối tới app Node.js (repo githubactions).
 */
@ConfigurationProperties(prefix = "node-api")
public record NodeApiProperties(String baseUrl) {

    public NodeApiProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:3000";
        }
    }
}
