package com.mcart.email.redis;

/**
 * Redis key prefixes for the email service. Shared Redis instances have no schemas; use a dedicated
 * namespace so keys never collide with other services (e.g. auth uses {@code email_verification:}).
 */
public final class EmailRedisKeys {

	private EmailRedisKeys() {
	}

	/**
	 * Order-paid receipt idempotency: one key per order UUID. Value is arbitrary; presence means
	 * "already processed" until TTL expires.
	 */
	public static final String ORDER_PAID_DEDUPE_PREFIX = "mcart:email:order-paid:dedupe:";
}
