package com.ticksim.writer.config;

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
        Map<String, Object> props = baseProps("consumer-writer-trades");
        JsonDeserializer<TradeEvent> deser = new JsonDeserializer<>(TradeEvent.class, objectMapper());
        deser.addTrustedPackages("com.ticksim.common.model");
        deser.setUseTypeHeaders(false);
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deser);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TradeEvent> tradeKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, TradeEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(tradeConsumerFactory());
        factory.setConcurrency(1);
        factory.setBatchListener(true);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, QuoteEvent> quoteConsumerFactory() {
        Map<String, Object> props = baseProps("consumer-writer-quotes");
        JsonDeserializer<QuoteEvent> deser = new JsonDeserializer<>(QuoteEvent.class, objectMapper());
        deser.addTrustedPackages("com.ticksim.common.model");
        deser.setUseTypeHeaders(false);
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deser);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, QuoteEvent> quoteKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, QuoteEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(quoteConsumerFactory());
        factory.setConcurrency(1);
        factory.setBatchListener(true);
        return factory;
    }

    private Map<String, Object> baseProps(String groupId) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1000);
        return props;
    }
}
