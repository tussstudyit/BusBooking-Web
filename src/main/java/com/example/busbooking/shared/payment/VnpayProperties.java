package com.example.busbooking.shared.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vnpay")
public record VnpayProperties(
        String payUrl,
        String tmnCode,
        String hashSecret,
        String returnUrl
) {
}
