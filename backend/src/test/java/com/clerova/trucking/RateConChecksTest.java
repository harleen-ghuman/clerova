package com.clerova.trucking;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RateConChecksTest {

    @Test
    void readsAmountsHoursAndPercentsWrittenAsText() {
        assertThat(Money.cents("2,150.00")).isEqualTo(215000L);
        assertThat(Money.cents("$2150")).isEqualTo(215000L);
        assertThat(Money.cents("2150.005")).isEqualTo(215001L);
        assertThat(Money.cents("")).isNull();
        assertThat(Money.cents("TBD")).isNull();
        assertThat(Money.cents("0")).isNull();
        assertThat(Money.cents("-50")).isNull();
        assertThat(Money.minutesFromHours("2")).isEqualTo(120);
        assertThat(Money.minutesFromHours("1.5")).isEqualTo(90);
        assertThat(Money.minutesFromHours("two")).isNull();
        assertThat(Money.percent("5%")).isEqualByComparingTo("5");
        assertThat(Money.percent("150")).isNull();
        assertThat(Money.dollars(215000)).isEqualTo("$2,150.00");
    }

    @Test
    void mcNumbersAreDigitsOnly() {
        assertThat(TruckingIntakeService.mcNumber("MC-845210")).isEqualTo("845210");
        assertThat(TruckingIntakeService.mcNumber("MC# 001610032")).isEqualTo("1610032");
        assertThat(TruckingIntakeService.mcNumber("none")).isNull();
        assertThat(TruckingIntakeService.mcNumber(null)).isNull();
    }

    @Test
    void brokerNamesMatchFmcsaDespiteSuffixesAndCase() {
        BrokerCheck check = new BrokerCheck(FmcsaStatus.ACTIVE, "CRESTLINE FREIGHT LLC", "CRESTLINE");
        assertThat(RateConChecks.sameCompany("Crestline Freight, Inc.", check)).isTrue();
        assertThat(RateConChecks.sameCompany("Crestline", check)).isTrue();
        assertThat(RateConChecks.sameCompany("Northgate Logistics", check)).isFalse();
        assertThat(RateConChecks.sameCompany("Anything", new BrokerCheck(FmcsaStatus.ACTIVE, null, null))).isTrue();
    }

    @Test
    void aCleanRateConHasNoFlags() {
        Load load = load(215000L, null, 120, 5000L);
        RateConResult read = new RateConResult();

        assertThat(RateConChecks.flags(read, load, "845210",
                new BrokerCheck(FmcsaStatus.ACTIVE, "Crestline Freight LLC", null))).isEmpty();
    }

    @Test
    void flagsWhatToCheckBeforeSigning() {
        Load load = load(189000L, 189000L, null, null);
        RateConResult read = new RateConResult();
        read.quickPayFeePercent = "5";
        read.paymentTerms = "Net 45";

        List<String> codes = RateConChecks.flags(read, load, null, null).stream().map(LoadFlag::getCode).toList();
        assertThat(codes).containsExactlyInAnyOrder("NO_DETENTION", "QUICK_PAY_FEE", "MC_MISSING");

        LoadFlag quickPay = RateConChecks.flags(read, load, null, null).stream()
                .filter(flag -> flag.getCode().equals("QUICK_PAY_FEE")).findFirst().orElseThrow();
        assertThat(quickPay.getTitle()).isEqualTo("Quick pay costs 5%");
        assertThat(quickPay.getDetail()).isEqualTo("Payment terms: Net 45. Quick pay takes 5% ($94.50 on this load).");
    }

    @Test
    void anUnreadableRateConAndBadFmcsaResultsAreThingsToFix() {
        Load load = new Load();
        load.setDetentionFreeMinutes(120);

        assertThat(RateConChecks.flags(new RateConResult(), load, "1", new BrokerCheck(FmcsaStatus.NOT_FOUND, null, null))
                .stream().map(LoadFlag::getCode).toList())
                .containsExactlyInAnyOrder("UNREADABLE", "BROKER_NOT_FOUND");
        assertThat(RateConChecks.flags(new RateConResult(), load(100000L, null, 120, null), "2",
                new BrokerCheck(FmcsaStatus.NOT_AUTHORIZED, "A CARRIER ONLY", null))
                .getFirst().getDetail()).contains("A CARRIER ONLY");
    }

    @Test
    void aDetentionConditionWithoutARateIsOneFlagNotTwo() {
        Load load = load(105750L, null, null, null);
        RateConResult read = new RateConResult();
        read.detentionCondition = "Detention isn't paid unless the broker is told.";
        read.detentionQuote = "Detention pay is not applicable if the broker is not notified";

        List<LoadFlag> flags = RateConChecks.flags(read, load, null, null);
        assertThat(flags.stream().map(LoadFlag::getCode).toList())
                .contains("NO_DETENTION").doesNotContain("DETENTION_CONDITION");
        LoadFlag detention = flags.stream().filter(flag -> flag.getCode().equals("NO_DETENTION")).findFirst().orElseThrow();
        assertThat(detention.getTitle()).isEqualTo("Detention rate isn't on the rate con");
        assertThat(detention.getDetail()).contains("broker is told").contains("Ask the broker for the detention rate");
    }

    private static Load load(Long rate, Long agreed, Integer freeMinutes, Long detentionRate) {
        Load load = new Load();
        Broker broker = new Broker();
        broker.setName("Crestline Freight");
        load.setBroker(broker);
        load.setRateCents(rate);
        load.setAgreedRateCents(agreed);
        load.setDetentionFreeMinutes(freeMinutes);
        load.setDetentionRateCents(detentionRate);
        LoadStop stop = new LoadStop();
        stop.setKind(StopKind.PICKUP);
        stop.setPlace("Fresno, CA");
        load.replaceStops(List.of(stop));
        return load;
    }
}
