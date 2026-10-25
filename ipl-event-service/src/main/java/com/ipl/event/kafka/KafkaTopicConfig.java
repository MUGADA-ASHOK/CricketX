package com.ipl.event.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String MATCH_SCHEDULED_TOPIC = "ipl.match.scheduled";
    public static final String MATCH_APPROVED_TOPIC = "ipl.match.approved";
    public static final String MATCH_TICKETS_OPENED_TOPIC = "ipl.match.tickets-opened";

    @Bean
    public NewTopic matchScheduledTopic() {
        return TopicBuilder.name(MATCH_SCHEDULED_TOPIC).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic matchApprovedTopic() {
        return TopicBuilder.name(MATCH_APPROVED_TOPIC).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic matchTicketsOpenedTopic() {
        return TopicBuilder.name(MATCH_TICKETS_OPENED_TOPIC).partitions(1).replicas(1).build();
    }
}
