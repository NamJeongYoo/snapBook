package com.interstellar.snapbook.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Value("${app.kafka.topic.reservation}")
    private String reservationTopic;

    @Bean
    public NewTopic reservationTopic() {
        return TopicBuilder.name(reservationTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
