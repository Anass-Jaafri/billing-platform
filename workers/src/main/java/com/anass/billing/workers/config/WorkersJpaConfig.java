package com.anass.billing.workers.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaAuditing
@EntityScan("com.anass.billing.common")
@EnableJpaRepositories("com.anass.billing.common")
public class WorkersJpaConfig {
}
