package com.clerova.billing;

import com.clerova.domain.Plan;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TruckingPlanPickTest {

    /** Starter is $25 a truck, Fleet $199 up to 20: from 8 trucks Fleet costs less (also at founding prices). */
    @Test
    void fleetStartsAtEightTrucks() {
        assertThat(BillingService.truckingPlan(1)).isEqualTo(Plan.STARTER);
        assertThat(BillingService.truckingPlan(7)).isEqualTo(Plan.STARTER);
        assertThat(BillingService.truckingPlan(8)).isEqualTo(Plan.FLEET);
        assertThat(BillingService.truckingPlan(49)).isEqualTo(Plan.FLEET);
    }

    /** 20 loads a truck; Fleet pays for 20 trucks, so 8 to 20 trucks get 400 loads. */
    @Test
    void loadsFollowTheTrucksPaidFor() {
        assertThat(BillingService.loadsAllowance(1)).isEqualTo(20);
        assertThat(BillingService.loadsAllowance(7)).isEqualTo(140);
        assertThat(BillingService.loadsAllowance(8)).isEqualTo(400);
        assertThat(BillingService.loadsAllowance(12)).isEqualTo(400);
        assertThat(BillingService.loadsAllowance(20)).isEqualTo(400);
        assertThat(BillingService.loadsAllowance(25)).isEqualTo(500);
    }

    /** Canadian carriers pay the same numbers in CAD, on their own Stripe prices. */
    @Test
    void canadianCarriersUseTheCadPrices() {
        assertThat(BillingService.lookupKey(Plan.STARTER, "month", false, true, true)).isEqualTo("trucking_starter_monthly_cad");
        assertThat(BillingService.lookupKey(Plan.FLEET, "year", true, true, true)).isEqualTo("founding_trucking_fleet_yearly_cad");
        assertThat(BillingService.lookupKey(Plan.FLEET, "month", false, true, false)).isEqualTo("trucking_fleet_monthly");
        assertThat(BillingService.lookupKey(Plan.GROWTH, "month", false, false, true)).isEqualTo("growth_monthly");
    }
}
