package com.mcart.email.service;

import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Sends email verification messages (HTML link to auth {@code /auth/verify-email}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationEmailSender {

	private static final String VERIFY_PATH = "/auth/verify-email";
	public static final String VERIFICATION_SUBJECT = "Verify your email";

	private final JavaMailSender mailSender;

	@Value("${email.verification.base-url}")
	private String baseUrl;

	/** Optional override; when blank, {@code spring.mail.username} is used (must be non-blank for SMTP). */
	@Value("${email.verification.from:}")
	private String fromOverride;

	@Value("${spring.mail.username:}")
	private String mailUsername;

	private String fromEmail;

	@PostConstruct
	void validateConfig() {
		if (baseUrl == null || baseUrl.isBlank()) {
			throw new IllegalStateException("email.verification.base-url must be set (EMAIL_VERIFICATION_BASE_URL).");
		}
		String from = fromOverride != null && !fromOverride.isBlank() ? fromOverride.trim() : mailUsername.trim();
		if (from.isBlank()) {
			throw new IllegalStateException(
					"Verification email From address is empty. Set SPRING_MAIL_USERNAME in email-secrets "
							+ "(SMTP login / sender for Gmail) and/or EMAIL_VERIFICATION_FROM.");
		}
		fromEmail = from;
	}

	public void sendVerificationEmail(String to, String token) throws MessagingException {
		String trimmedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		String verificationLink = trimmedBase + VERIFY_PATH + "?token=" + token;

		MimeMessage message = mailSender.createMimeMessage();
		MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
		helper.setFrom(fromEmail);
		helper.setTo(to);
		helper.setSubject(VERIFICATION_SUBJECT);
		helper.setText(buildEmailBody(verificationLink), true);
		mailSender.send(message);
		log.info("Verification email dispatched");
	}

	private String buildEmailBody(String verificationLink) {
		return """
				<html>
				<body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
				    <h2>Verify your email address</h2>
				    <p>Thanks for signing up! Please click the link below to verify your email address:</p>
				    <p><a href="%s" style="background-color: #4CAF50; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px;">Verify Email</a></p>
				    <p>Or copy and paste this link into your browser:</p>
				    <p style="word-break: break-all;">%s</p>
				    <p>This link will expire in 24 hours.</p>
				    <p>If you didn't create an account, you can safely ignore this email.</p>
				</body>
				</html>
				""".formatted(verificationLink, verificationLink);
	}
}
