package com.mcart.email.subscriber;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage;
import com.mcart.email.service.OrderPaidEmailSender;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "email.order-paid.enabled", havingValue = "true", matchIfMissing = false)
public class OrderPaidEmailSubscriber {

	private static final String DEFAULT_SUBSCRIPTION = "order-paid-email-sub";
	private static final String EVENT_ORDER_PAID = "ORDER_PAID";

	private final OrderPaidEmailSender orderPaidEmailSender;
	private final ObjectMapper objectMapper;
	private final PubSubTemplate pubSubTemplate;

	@Value("${email.order-paid.subscription:" + DEFAULT_SUBSCRIPTION + "}")
	private String subscriptionName;

	private final Cache<String, Boolean> sentOrderIds = Caffeine.newBuilder()
			.maximumSize(50_000)
			.expireAfterWrite(24, TimeUnit.HOURS)
			.build();

	private com.google.cloud.pubsub.v1.Subscriber subscriber;

	@PostConstruct
	public void subscribe() {
		subscriber = pubSubTemplate.subscribe(subscriptionName, this::handleMessage);
		log.info("Email subscribed to order-paid subscription: {}", subscriptionName);
	}

	@PreDestroy
	public void shutdown() {
		if (subscriber != null) {
			subscriber.stopAsync();
		}
	}

	private void handleMessage(BasicAcknowledgeablePubsubMessage message) {
		try {
			String raw = message.getPubsubMessage().getData().toStringUtf8();
			JsonNode root = objectMapper.readTree(raw);
			if (!EVENT_ORDER_PAID.equals(root.path("eventType").asText())) {
				log.warn("Ignoring unexpected eventType on order-paid subscription");
				message.ack();
				return;
			}
			String orderId = root.path("orderId").asText(null);
			if (orderId != null && !orderId.isBlank()) {
				if (sentOrderIds.getIfPresent(orderId) != null) {
					log.debug("Duplicate delivery for order {}; ack without resending", orderId);
					message.ack();
					return;
				}
			}
			orderPaidEmailSender.sendReceipt(root);
			if (orderId != null && !orderId.isBlank()) {
				sentOrderIds.put(orderId, Boolean.TRUE);
			}
			message.ack();
		} catch (Exception ex) {
			log.error("Failed to process order-paid message", ex);
			message.nack();
		}
	}
}
