package com.arkana.dto.billing;

public record BillingCheckoutActionResponse(
    String type,
    String url,
    String copyPasteCode,
    String qrCodeImage) {
}
