package com.ticksim.monitor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ticksim.common.model.QuoteEvent;
import com.ticksim.common.model.TradeEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    @Bean
    public ConsumerFactory<String, TradeEvent> tradeConsumerFactory() {
        Map<String, Object> props = baseConsumerProps("consumer-monitor-trades");
        JsonDeserializer<TradeEvent> deserializer = new JsonDeserializer<>(TradeEvent.class, objectMapper());
        deserializer.addTrustedPackages("com.ticksim.common.model");
        deserializer.setUseTypeHeaders(false);
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TradeEvent> tradeKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, TradeEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(tradeConsumerFactory());
        factory.setConcurrency(1);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, QuoteEvent> quoteConsumerFactory() {
        Map<String, Object> props = baseConsumerProps("consumer-monitor-quotes");
        JsonDeserializer<QuoteEvent> deserializer = new JsonDeserializer<>(QuoteEvent.class, objectMapper());
        deserializer.addTrustedPackages("com.ticksim.common.model");
        deserializer.setUseTypeHeaders(false);
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, QuoteEvent> quoteKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, QuoteEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(quoteConsumerFactory());
        factory.setConcurrency(1);
        return factory;
    }

    private Map<String, Object> baseConsumerProps(String groupId) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return props;
    }
}
