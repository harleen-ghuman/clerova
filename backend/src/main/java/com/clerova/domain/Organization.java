package com.clerova.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.UUID;

// Updates write only changed columns, so a save never overwrites a usage
// count or token another request just set
@DynamicUpdate
@Entity
@Table(name = "organizations")
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Industry industry;

    @Column(length = 50)
    private String companySize;

    private String referralSource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Plan plan;

    private Instant trialEndsAt;

    @Column(nullable = false)
    private Integer aiMonthlyLimit;

    /** Month the usage count applies to, formatted yyyy-MM. */
    @Column(length = 7)
    private String aiUsagePeriod;

    @Column(nullable = false)
    private Integer aiUsageCount = 0;

    /** Local part of the organization's forwarding address. */
    @Column(length = 40, unique = true)
    private String inboundEmailToken;

    @Column(length = 64, unique = true)
    private String stripeCustomerId;

    /** Gets a hidden copy of each reply sent from Clerova; null means off. */
    @Column(length = 320)
    private String replyCopyEmail;

    /** The company's standard answers as JSON (see CompanyInfo); null when empty. */
    @Column(columnDefinition = "TEXT")
    private String companyInfo;

    @Column(length = 64)
    private String stripeSubscriptionId;

    /** Stripe subscription status, e.g. trialing, active, past_due, canceled. */
    @Column(length = 30)
    private String subscriptionStatus;

    /** month or year */
    @Column(length = 10)
    private String billingInterval;

    private Instant currentPeriodEnd;

    @Column(nullable = false)
    private boolean cancelAtPeriodEnd;

    @Column(nullable = false)
    private boolean foundingCustomer;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isTrialExpired(Instant now) {
        return plan == Plan.TRIAL
                && trialEndsAt != null
                && now.isAfter(trialEndsAt);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Industry getIndustry() {
        return industry;
    }

    public void setIndustry(Industry industry) {
        this.industry = industry;
    }

    public String getCompanySize() {
        return companySize;
    }

    public void setCompanySize(String companySize) {
        this.companySize = companySize;
    }

    public String getReferralSource() {
        return referralSource;
    }

    public void setReferralSource(String referralSource) {
        this.referralSource = referralSource;
    }

    public Plan getPlan() {
        return plan;
    }

    public void setPlan(Plan plan) {
        this.plan = plan;
    }

    public Instant getTrialEndsAt() {
        return trialEndsAt;
    }

    public void setTrialEndsAt(Instant trialEndsAt) {
        this.trialEndsAt = trialEndsAt;
    }

    public Integer getAiMonthlyLimit() {
        return aiMonthlyLimit;
    }

    public void setAiMonthlyLimit(Integer aiMonthlyLimit) {
        this.aiMonthlyLimit = aiMonthlyLimit;
    }

    public String getAiUsagePeriod() {
        return aiUsagePeriod;
    }

    public Integer getAiUsageCount() {
        return aiUsageCount;
    }

    public String getInboundEmailToken() {
        return inboundEmailToken;
    }

    public void setInboundEmailToken(String inboundEmailToken) {
        this.inboundEmailToken = inboundEmailToken;
    }

    public String getCompanyInfo() {
        return companyInfo;
    }

    public void setCompanyInfo(String companyInfo) {
        this.companyInfo = companyInfo;
    }

    public String getReplyCopyEmail() {
        return replyCopyEmail;
    }

    public void setReplyCopyEmail(String replyCopyEmail) {
        this.replyCopyEmail = replyCopyEmail;
    }

    public String getStripeCustomerId() {
        return stripeCustomerId;
    }

    public void setStripeCustomerId(String stripeCustomerId) {
        this.stripeCustomerId = stripeCustomerId;
    }

    public String getStripeSubscriptionId() {
        return stripeSubscriptionId;
    }

    public void setStripeSubscriptionId(String stripeSubscriptionId) {
        this.stripeSubscriptionId = stripeSubscriptionId;
    }

    public String getSubscriptionStatus() {
        return subscriptionStatus;
    }

    public void setSubscriptionStatus(String subscriptionStatus) {
        this.subscriptionStatus = subscriptionStatus;
    }

    public String getBillingInterval() {
        return billingInterval;
    }

    public void setBillingInterval(String billingInterval) {
        this.billingInterval = billingInterval;
    }

    public Instant getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }

    public void setCurrentPeriodEnd(Instant currentPeriodEnd) {
        this.currentPeriodEnd = currentPeriodEnd;
    }

    public boolean isCancelAtPeriodEnd() {
        return cancelAtPeriodEnd;
    }

    public void setCancelAtPeriodEnd(boolean cancelAtPeriodEnd) {
        this.cancelAtPeriodEnd = cancelAtPeriodEnd;
    }

    public boolean isFoundingCustomer() {
        return foundingCustomer;
    }

    public void setFoundingCustomer(boolean foundingCustomer) {
        this.foundingCustomer = foundingCustomer;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
