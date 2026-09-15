package com.uap.control_tickets.apivalidacaion.Service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sigse")
@Getter
@Setter
public class SigseProperties {
    private String url;
    private String apiKey;
}
