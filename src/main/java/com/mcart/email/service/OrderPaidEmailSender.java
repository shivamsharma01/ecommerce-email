package com.mcart.email.service;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderPaidEmailSender {

	private final JavaMailSender mailSender;

	@Value("${spring.mail.username:}")
	private String fromAddress;

	@Value("${email.order.base-url:}")
	private String orderBaseUrl;

	public void sendReceipt(JsonNode event) throws Exception {
		String orderId = text(event, "orderId");
		String to = text(event, "customerEmail");
		if (to == null || to.isBlank()) {
			log.warn("ORDER_PAID event has no customerEmail; skipping send for order {}", orderId);
			return;
		}
		long totalMinor = event.path("totalAmount").asLong(0);
		String totalStr = formatInr(totalMinor);

		StringBuilder rows = new StringBuilder();
		JsonNode items = event.get("items");
		if (items != null && items.isArray()) {
			for (JsonNode line : items) {
				String pid = text(line, "productId");
				int qty = line.path("quantity").asInt(0);
				long lineTotal = line.path("lineTotalMinor").asLong(0);
				rows.append("<tr><td>")
						.append(escape(pid))
						.append("</td><td>")
						.append(qty)
						.append("</td><td align=\"right\">")
						.append(formatInr(lineTotal))
						.append("</td></tr>");
			}
		}

		String orderLink = "";
		if (orderBaseUrl != null && !orderBaseUrl.isBlank() && orderId != null) {
			String base = orderBaseUrl.endsWith("/") ? orderBaseUrl.substring(0, orderBaseUrl.length() - 1) : orderBaseUrl;
			orderLink = "<p><a href=\"" + escapeAttr(base + "/orders/" + orderId) + "\">View your order</a></p>";
		}

		String html = """
				<html><body>
				<h2>Thank you for your order</h2>
				<p>Order ID: %s</p>
				<p>Total: <strong>%s</strong></p>
				<table border="1" cellpadding="6" cellspacing="0">
				<thead><tr><th>Product</th><th>Qty</th><th>Line total</th></tr></thead>
				<tbody>%s</tbody>
				</table>
				%s
				</body></html>
				""".formatted(escape(orderId), escape(totalStr), rows, orderLink);

		MimeMessage message = mailSender.createMimeMessage();
		MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
		if (fromAddress != null && !fromAddress.isBlank()) {
			helper.setFrom(fromAddress);
		}
		helper.setTo(to);
		helper.setSubject("Your order receipt — " + (orderId != null ? orderId : "MCart"));
		helper.setText(html, true);
		mailSender.send(message);
		log.info("Sent order receipt email orderId={}", orderId);
	}

	private static String text(JsonNode n, String field) {
		JsonNode v = n.get(field);
		return v != null && v.isTextual() ? v.asText() : null;
	}

	private static String formatInr(long amountMinor) {
		BigDecimal rupees = BigDecimal.valueOf(amountMinor).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
		return "₹" + rupees.toPlainString();
	}

	private static String escape(String s) {
		if (s == null) {
			return "";
		}
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static String escapeAttr(String s) {
		return escape(s).replace("\"", "&quot;");
	}
}
