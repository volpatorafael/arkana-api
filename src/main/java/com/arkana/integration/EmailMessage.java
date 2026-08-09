package com.arkana.integration;

public record EmailMessage(String recipient, String subject, String text) {
}
