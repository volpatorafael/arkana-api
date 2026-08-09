package com.arkana;

import com.arkana.domain.BillingAccount;
import com.arkana.domain.BillingCheckout;
import com.arkana.domain.BillingPromotionCampaign;
import com.arkana.domain.BillingPromotionEligibility;
import com.arkana.domain.BillingProviderPlanMapping;
import com.arkana.domain.BillingProviderSubscription;
import com.arkana.domain.Client;
import com.arkana.domain.Profile;
import com.arkana.domain.Reading;
import com.arkana.domain.ReadingComment;
import com.arkana.domain.ReadingPosition;
import com.arkana.domain.ReadingShare;
import com.arkana.domain.SpreadPosition;
import com.arkana.repository.BillingAccountRepository;
import com.arkana.repository.BillingCheckoutRepository;
import com.arkana.repository.BillingPromotionCampaignRepository;
import com.arkana.repository.BillingPromotionEligibilityRepository;
import com.arkana.repository.BillingProviderPlanMappingRepository;
import com.arkana.repository.BillingProviderSubscriptionRepository;
import com.arkana.repository.ClientRepository;
import com.arkana.repository.ProfileRepository;
import com.arkana.repository.ReadingCommentRepository;
import com.arkana.repository.ReadingPositionRepository;
import com.arkana.repository.ReadingRepository;
import com.arkana.repository.ReadingShareRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EntityGeneratorService {

    private final ProfileRepository profileRepository;
    private final ClientRepository clientRepository;
    private final BillingAccountRepository billingAccountRepository;
    private final BillingCheckoutRepository billingCheckoutRepository;
    private final BillingProviderSubscriptionRepository subscriptionRepository;
    private final BillingProviderPlanMappingRepository planMappingRepository;
    private final BillingPromotionCampaignRepository campaignRepository;
    private final BillingPromotionEligibilityRepository eligibilityRepository;
    private final ReadingRepository readingRepository;
    private final ReadingPositionRepository positionRepository;
    private final ReadingCommentRepository commentRepository;
    private final ReadingShareRepository shareRepository;

    public Profile randomProfile() {
        return profileRepository.saveAndFlush(TestDataGenerator.randomProfile().build());
    }

    public Client randomClient(Profile owner) {
        return clientRepository.saveAndFlush(TestDataGenerator.randomClient(owner).build());
    }

    public BillingAccount randomBillingAccount(Profile owner) {
        return billingAccountRepository.saveAndFlush(
            TestDataGenerator.randomBillingAccount(owner).build());
    }

    public BillingCheckout randomBillingCheckout(
        BillingAccount account,
        UUID planPriceId,
        UUID idempotencyKey) {
        return billingCheckoutRepository.saveAndFlush(
            TestDataGenerator.randomBillingCheckout(account, planPriceId, idempotencyKey).build());
    }

    public BillingProviderSubscription randomSubscription(BillingAccount account) {
        return subscriptionRepository.saveAndFlush(TestDataGenerator.randomSubscription(account).build());
    }

    public BillingProviderPlanMapping randomPlanMapping(UUID planPriceId) {
        return planMappingRepository.saveAndFlush(TestDataGenerator.randomPlanMapping(planPriceId).build());
    }

    public BillingPromotionCampaign randomCampaign() {
        return campaignRepository.saveAndFlush(TestDataGenerator.randomCampaign().build());
    }

    public BillingPromotionEligibility randomEligibility(
        BillingAccount account,
        BillingPromotionCampaign campaign) {
        return eligibilityRepository.saveAndFlush(
            TestDataGenerator.randomEligibility(account, campaign).build());
    }

    public Reading randomReading(Profile owner) {
        return randomReading(owner, null);
    }

    public Reading randomReading(Profile owner, Client client) {
        return readingRepository.saveAndFlush(TestDataGenerator.randomReading(owner, client).build());
    }

    public ReadingPosition randomReadingPosition(Reading reading, SpreadPosition spreadPosition) {
        return positionRepository.saveAndFlush(
            TestDataGenerator.randomReadingPosition(reading, spreadPosition).build());
    }

    public ReadingComment randomComment(Profile owner, Reading reading) {
        return commentRepository.saveAndFlush(TestDataGenerator.randomComment(owner, reading).build());
    }

    public ReadingShare randomReadingShare(Reading reading) {
        return shareRepository.saveAndFlush(TestDataGenerator.randomReadingShare(reading).build());
    }
}
