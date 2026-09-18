package com.cineagent.common.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.StreamWriteFeature;

/**
 * Money must never round-trip through JSON as scientific notation (e.g. {@code 1.2E+3}) — every
 * {@link java.math.BigDecimal} field in a response serializes as a plain decimal string. See
 * AGENTS.md §4.2.
 *
 * <p>Spring Boot 4 / Jackson 3: the customizer type is {@link JsonMapperBuilderCustomizer}
 * (package {@code tools.jackson.*}, not the Jackson 2 {@code com.fasterxml.jackson.*}), and
 * {@code WRITE_BIGDECIMAL_AS_PLAIN} moved from the old databind {@code SerializationFeature} to
 * the core {@link StreamWriteFeature}.
 */
@Configuration
public class JacksonConfig {

  @Bean
  public JsonMapperBuilderCustomizer bigDecimalAsPlainCustomizer() {
    return builder -> builder.enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN);
  }
}
