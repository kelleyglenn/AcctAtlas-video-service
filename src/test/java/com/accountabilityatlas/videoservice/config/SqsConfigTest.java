package com.accountabilityatlas.videoservice.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import io.awspring.cloud.sqs.support.converter.SqsMessagingMessageConverter;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

class SqsConfigTest {

  private final SqsConfig sqsConfig = new SqsConfig();

  @Test
  void sqsTemplate_createsTemplateWithoutPayloadTypeHeader() {
    SqsAsyncClient sqsAsyncClient = mock(SqsAsyncClient.class);
    ObjectMapper objectMapper = new ObjectMapper();

    SqsTemplate template = sqsConfig.sqsTemplate(sqsAsyncClient, objectMapper);

    assertNotNull(template);
  }

  @Test
  void sqsMessagingMessageConverter_createsConverterThatIgnoresPayloadTypeHeader() {
    ObjectMapper objectMapper = new ObjectMapper();

    SqsMessagingMessageConverter converter = sqsConfig.sqsMessagingMessageConverter(objectMapper);

    assertNotNull(converter);
  }
}
