package com.anass.billing.common;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan("com.anass.billing.common")
@EnableJpaRepositories("com.anass.billing.common")
@EnableJpaAuditing
class CommonTestApplication {
}