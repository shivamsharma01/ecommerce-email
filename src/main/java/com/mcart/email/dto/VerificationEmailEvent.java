package com.mcart.email.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;
import java.util.UUID;

/**
 * Envelope published by auth {@code OutboxPublisherJob} for verification emails.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VerificationEmailEvent(
		String eventType,
		String aggregateType,
		UUID userId,
		String authIdentityId,
		Map<String, Object> payload,
		String occurredAt,
		int version,
		String outboxEventId
) {
}
