package com.mcart.email.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "email.order-paid.enabled", havingValue = "true", matchIfMissing = false)
public class OrderPaidReceiptDedupe {

	private final StringRedisTemplate redis;

	@Value("${email.order-paid.dedupe-ttl-hours:24}")
	private long dedupeTtlHours;

	public boolean tryAcquire(String orderId) {
		if (orderId == null || orderId.isBlank()) {
			return true;
		}
		String key = EmailRedisKeys.ORDER_PAID_DEDUPE_PREFIX + orderId.trim();
		Boolean created = redis.opsForValue()
				.setIfAbsent(key, "1", Duration.ofHours(Math.max(1, dedupeTtlHours)));
		return Boolean.TRUE.equals(created);
	}

	public void release(String orderId) {
		if (orderId == null || orderId.isBlank()) {
			return;
		}
		redis.delete(EmailRedisKeys.ORDER_PAID_DEDUPE_PREFIX + orderId.trim());
	}
}
