package com.arkana;

import com.arkana.domain.BillingAccount;
import com.arkana.domain.BillingAccountStatus;
import com.arkana.domain.BillingCheckout;
import com.arkana.domain.BillingCheckoutStatus;
import com.arkana.domain.BillingPaymentMethod;
import com.arkana.domain.BillingPromotionCampaign;
import com.arkana.domain.BillingPromotionEligibility;
import com.arkana.domain.BillingPromotionEligibilityStatus;
import com.arkana.domain.BillingProvider;
import com.arkana.domain.BillingProviderPlanMapping;
import com.arkana.domain.BillingProviderSubscription;
import com.arkana.domain.Client;
import com.arkana.domain.Profile;
import com.arkana.domain.Reading;
import com.arkana.domain.ReadingComment;
import com.arkana.domain.ReadingDeckMode;
import com.arkana.domain.ReadingPosition;
import com.arkana.domain.ReadingShare;
import com.arkana.domain.ReadingShareStatus;
import com.arkana.domain.ReadingStatus;
import com.arkana.domain.SpreadPosition;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class TestDataGenerator {

    public static Profile.ProfileBuilder randomProfile() {
        UUID id = UUID.randomUUID();
        return Profile.builder()
            .id(id)
            .email(id + "@arkana.test")
            .locale(randomValue(List.of("pt-BR", "en")))
            .createdAt(randomDate())
            .updatedAt(randomDate());
    }

    public static Client.ClientBuilder randomClient(Profile owner) {
        UUID id = UUID.randomUUID();
        return Client.builder()
            .id(id)
            .ownerId(owner.getId())
            .name("Client " + id);
    }

    public static BillingAccount.BillingAccountBuilder randomBillingAccount(Profile owner) {
        return BillingAccount.builder()
            .id(UUID.randomUUID())
            .ownerId(owner.getId())
            .status(randomValue(List.of(BillingAccountStatus.values())))
            .trialStartedAt(randomDate())
            .trialEndsAt(randomDate())
            .currentPeriodStart(randomDate())
            .currentPeriodEnd(randomDate())
            .overrideEndsAt(randomDate());
    }

    public static BillingCheckout.BillingCheckoutBuilder randomBillingCheckout(
        BillingAccount account,
        UUID planPriceId,
        UUID idempotencyKey) {
        return BillingCheckout.builder()
            .id(UUID.randomUUID())
            .billingAccountId(account.getId())
            .planPriceId(planPriceId)
            .paymentMethod(randomValue(List.of(BillingPaymentMethod.values())))
            .status(randomValue(List.of(BillingCheckoutStatus.values())))
            .idempotencyKey(idempotencyKey)
            .provider(randomValue(List.of(BillingProvider.values())))
            .providerCheckoutId("checkout-" + UUID.randomUUID())
            .checkoutUrl("https://checkout.arkana.test/" + UUID.randomUUID())
            .expiresAt(randomDate());
    }

    public static BillingProviderSubscription.BillingProviderSubscriptionBuilder randomSubscription(
        BillingAccount account) {
        UUID id = UUID.randomUUID();
        return BillingProviderSubscription.builder()
            .id(id)
            .billingAccountId(account.getId())
            .provider(randomValue(List.of(BillingProvider.values())))
            .providerSubscriptionId("subscription-" + id);
    }

    public static BillingProviderPlanMapping.BillingProviderPlanMappingBuilder randomPlanMapping(UUID planPriceId) {
        UUID id = UUID.randomUUID();
        return BillingProviderPlanMapping.builder()
            .id(id)
            .planPriceId(planPriceId)
            .provider(randomValue(List.of(BillingProvider.values())))
            .providerProductId("product-" + id);
    }

    public static BillingPromotionCampaign.BillingPromotionCampaignBuilder randomCampaign() {
        UUID id = UUID.randomUUID();
        return BillingPromotionCampaign.builder()
            .id(id)
            .code("CAMPAIGN_" + id)
            .name("Campaign " + id)
            .status(randomValue(List.of("ACTIVE", "INACTIVE")))
            .startsAt(randomDate())
            .endsAt(randomDate())
            .retentionPolicy(randomValue(List.of(
                "WHILE_SUBSCRIPTION_ACTIVE",
                "FIRST_CHECKOUT_ONLY")));
    }

    public static BillingPromotionEligibility.BillingPromotionEligibilityBuilder randomEligibility(
        BillingAccount account,
        BillingPromotionCampaign campaign) {
        return BillingPromotionEligibility.builder()
            .id(UUID.randomUUID())
            .billingAccountId(account.getId())
            .campaignId(campaign.getId())
            .status(randomValue(List.of(BillingPromotionEligibilityStatus.values())))
            .grantedAt(randomDate())
            .firstCheckoutEndsAt(randomDate())
            .lockedAt(randomDate())
            .expiredAt(randomDate())
            .forfeitedAt(randomDate());
    }

    public static Reading.ReadingBuilder randomReading(Profile owner, Client client) {
        UUID id = UUID.randomUUID();
        return Reading.builder()
            .id(id)
            .ownerId(owner.getId())
            .clientId(client == null ? null : client.getId())
            .spreadId(randomValue(List.of(
                "mandala",
                "celtic-cross",
                "peladan",
                "three-balance",
                "three-time",
                "advice",
                "aphrodite",
                "goals")))
            .deckMode(randomValue(List.of(ReadingDeckMode.values())))
            .status(randomValue(List.of(ReadingStatus.values())))
            .title("Reading " + id)
            .startedAt(randomDate())
            .createdAt(randomDate())
            .updatedAt(randomDate());
    }

    public static ReadingPosition.ReadingPositionBuilder randomReadingPosition(
        Reading reading,
        SpreadPosition source) {
        return ReadingPosition.builder()
            .id(UUID.randomUUID())
            .readingId(reading.getId())
            .spreadPositionId(source.getId())
            .positionKey(randomText("position"))
            .positionOrder((short) ThreadLocalRandom.current().nextInt(1, 1000))
            .namePtBr(randomText("Posicao"))
            .nameEn(randomText("Position"))
            .meaningPtBr(randomText("Significado"))
            .meaningEn(randomText("Meaning"))
            .x(BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble(0, 100)))
            .y(BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble(0, 100)))
            .rotation((short) ThreadLocalRandom.current().nextInt(-180, 181))
            .createdAt(randomDate())
            .updatedAt(randomDate());
    }

    public static ReadingComment.ReadingCommentBuilder randomComment(Profile owner, Reading reading) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return ReadingComment.builder()
            .id(id)
            .ownerId(owner.getId())
            .readingId(reading.getId())
            .body("Comment " + id)
            .createdAt(now)
            .updatedAt(now);
    }

    public static ReadingShare.ReadingShareBuilder randomReadingShare(Reading reading) {
        return ReadingShare.builder()
            .id(UUID.randomUUID())
            .reading(reading)
            .status(randomValue(List.of(ReadingShareStatus.values())))
            .createdAt(randomPastDate())
            .expiresAt(randomFutureDate())
            .accessCount(ThreadLocalRandom.current().nextLong(0, 10_000));
    }

    private static OffsetDateTime randomDate() {
        long randomDays = ThreadLocalRandom.current().nextLong(-365, 366);
        return OffsetDateTime.now(ZoneOffset.UTC).plusDays(randomDays);
    }

    private static OffsetDateTime randomPastDate() {
        return OffsetDateTime.now(ZoneOffset.UTC)
            .minusDays(ThreadLocalRandom.current().nextLong(1, 366));
    }

    private static OffsetDateTime randomFutureDate() {
        return OffsetDateTime.now(ZoneOffset.UTC)
            .plusDays(ThreadLocalRandom.current().nextLong(1, 366));
    }

    private static String randomText(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static <T> T randomValue(List<T> values) {
        return values.get(ThreadLocalRandom.current().nextInt(values.size()));
    }
}
