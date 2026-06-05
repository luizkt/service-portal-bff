package com.serviceportal.bff.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "bff.manager")
public class ManagerProperties {
    private String baseUrl;
    private String username;
    private String password;
}
