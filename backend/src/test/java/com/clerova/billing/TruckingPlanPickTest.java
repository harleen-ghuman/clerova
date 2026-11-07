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
}
