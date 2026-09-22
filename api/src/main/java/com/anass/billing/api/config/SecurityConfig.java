package com.anass.billing.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;



        @Configuration
        public class SecurityConfig {

            @Bean
            public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                http
                        .csrf(csrf -> csrf.disable()) // no forms/cookies-based clients yet; revisit if that changes
                        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

                return http.build();
            }
        }