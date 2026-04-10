package com.mcart.email.subscriber;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage;
import com.mcart.email.dto.VerificationEmailEvent;
import com.mcart.email.service.VerificationEmailSender;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Consumes verification-email requests from Pub/Sub (published by auth outbox job).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "email.pubsub.enabled", havingValue = "true", matchIfMissing = false)
public class VerificationEmailSubscriber {

	private static final String DEFAULT_SUBSCRIPTION = "email-verification-events-sub";
	private static final String AGGREGATE = "EMAIL_VERIFICATION";
	private static final String EVENT_SEND = "SEND_VERIFICATION_EMAIL";

	private final VerificationEmailSender verificationEmailSender;
	private final ObjectMapper objectMapper;
	private final PubSubTemplate pubSubTemplate;

	@Value("${email.pubsub.subscription:" + DEFAULT_SUBSCRIPTION + "}")
	private String subscriptionName;

	private com.google.cloud.pubsub.v1.Subscriber subscriber;

	@PostConstruct
	public void subscribe() {
		subscriber = pubSubTemplate.subscribe(subscriptionName, this::handleMessage);
		log.info("Subscribed to Pub/Sub subscription: {}", subscriptionName);
	}

	@PreDestroy
	public void shutdown() {
		if (subscriber != null) {
			subscriber.stopAsync();
			log.info("Stopped Pub/Sub subscription: {}", subscriptionName);
		}
	}

	private void handleMessage(BasicAcknowledgeablePubsubMessage message) {
		try {
			String raw = message.getPubsubMessage().getData().toStringUtf8();
			VerificationEmailEvent event = objectMapper.readValue(raw, VerificationEmailEvent.class);
			if (!AGGREGATE.equals(event.aggregateType()) || !EVENT_SEND.equals(event.eventType())) {
				log.warn("Ignoring unexpected event aggregateType={} eventType={}", event.aggregateType(), event.eventType());
				message.ack();
				return;
			}
			if (event.payload() == null) {
				throw new IllegalArgumentException("Missing payload");
			}
			Object emailObj = event.payload().get("email");
			Object tokenObj = event.payload().get("token");
			if (emailObj == null || tokenObj == null) {
				throw new IllegalArgumentException("Payload must contain email and token");
			}
			verificationEmailSender.sendVerificationEmail(emailObj.toString(), tokenObj.toString());
			message.ack();
		} catch (Exception ex) {
			log.error("Failed to process verification email message", ex);
			message.nack();
		}
	}
}
