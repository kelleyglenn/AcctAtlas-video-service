package com.accountabilityatlas.videoservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import io.awspring.cloud.sqs.support.converter.SqsMessagingMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

/**
 * SQS configuration for cross-service message handling.
 *
 * <p>By default, Spring Cloud AWS SQS adds a {@code JavaType} message attribute containing the
 * fully-qualified class name of the payload. When producer and consumer services define the same
 * event record in different packages, the consumer cannot load the producer's class, causing
 * deserialization to fail with {@code ClassNotFoundException}.
 *
 * <p>This configuration disables the {@code JavaType} header on both the sending and receiving
 * sides:
 *
 * <ul>
 *   <li><b>Producer ({@link SqsTemplate})</b>: {@code setPayloadTypeHeaderValueFunction} returns
 *       null, suppressing the header.
 *   <li><b>Consumer ({@link SqsMessagingMessageConverter})</b>: {@code setPayloadTypeMapper}
 *       returns null, forcing Jackson to use the {@code @SqsListener} method parameter type
 *       instead.
 * </ul>
 */
@Configuration
public class SqsConfig {

  /**
   * Custom SqsTemplate that does not send the JavaType header. This prevents cross-service
   * deserialization issues when event classes are defined in different packages.
   */
  @Bean
  public SqsTemplate sqsTemplate(SqsAsyncClient sqsAsyncClient, ObjectMapper objectMapper) {
    return SqsTemplate.builder()
        .sqsAsyncClient(sqsAsyncClient)
        .configureDefaultConverter(
            converter -> {
              converter.setObjectMapper(objectMapper);
              converter.setPayloadTypeHeaderValueFunction(message -> null);
            })
        .build();
  }

  /**
   * Custom SqsMessagingMessageConverter that ignores the JavaType header from incoming messages.
   * This forces deserialization to use the @SqsListener method parameter type, which allows
   * cross-service events with matching field structures but different package names.
   */
  @Bean
  public SqsMessagingMessageConverter sqsMessagingMessageConverter(ObjectMapper objectMapper) {
    SqsMessagingMessageConverter converter = new SqsMessagingMessageConverter();
    converter.setObjectMapper(objectMapper);
    converter.setPayloadTypeMapper(message -> null);
    return converter;
  }
}
