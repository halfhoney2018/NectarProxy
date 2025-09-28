package com.achance.gateway.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.achance.gateway.agent.config.AgentProperties;

@SpringBootApplication
@EnableConfigurationProperties(AgentProperties.class)
public class AgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
