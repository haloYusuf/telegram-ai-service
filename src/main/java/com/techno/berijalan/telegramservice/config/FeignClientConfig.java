package com.techno.berijalan.telegramservice.config;

import feign.Feign;
import feign.Request;
import feign.Retryer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class  FeignClientConfig {

    @Bean
    public Feign.Builder feignBuilder() {
        return Feign.builder()
                .retryer(new Retryer.Default());
    }

    @Bean
    public Request.Options requestOptions() {
        return new Request.Options(10, TimeUnit.SECONDS, 30, TimeUnit.SECONDS, true);
    }
}
