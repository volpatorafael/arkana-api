package com.arkana.integration.resend;

import com.arkana.integration.EmailMessage;
import com.arkana.integration.EmailNotifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Component
public class ResendEmailNotifier implements EmailNotifier {
  private final String apiKey;
  private final String from;
  private final HttpClient http = HttpClient.newHttpClient();

  public ResendEmailNotifier(
      @Value("${arkana.resend.api-key:}") String apiKey,
      @Value("${arkana.resend.from:Arkana <hello@getarkana.com>}") String from) {
    this.apiKey = apiKey;
    this.from = from;
  }

  @Override
  public void send(EmailMessage message) {
    if (apiKey.isBlank()) {
      throw new IllegalStateException("Resend is not configured.");
    }

    String payload = "{\"from\":\"" + escape(from) + "\",\"to\":[\""
        + escape(message.recipient()) + "\"],\"subject\":\""
        + escape(message.subject()) + "\",\"text\":\"" + escape(message.text()) + "\"}";
    HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.resend.com/emails"))
        .header("Authorization", "Bearer " + apiKey)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(payload))
        .build();

    try {
      HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
      if (response.statusCode() >= 300) {
        throw new IllegalStateException("Resend rejected the notification.");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Email notification was interrupted.", exception);
    } catch (Exception exception) {
      throw new IllegalStateException("Email notification failed.", exception);
    }
  }

  private String escape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
  }
}
