package com.payflow.auth.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopics {

    public static final String USER_REGISTERED = "payflow.user.registered.v1";

    @Bean
    NewTopic userRegisteredTopic() {
        return TopicBuilder.name(USER_REGISTERED).partitions(3).replicas(1).build();
    }
}