package com.clerova.trucking;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReferenceMatchTest {

    private static boolean same(String loadNumber, String reference) {
        return TruckingIntakeService.sameReference(
                TruckingIntakeService.reference(loadNumber), TruckingIntakeService.reference(reference));
    }

    @Test
    void matchesTheSameLoadWrittenDifferently() {
        assertThat(same("88213", "Load 88213")).isTrue();
        assertThat(same("88213", "Load #: 88213")).isTrue();
        assertThat(same("3466426-1", "#3466426")).isTrue();
        assertThat(same("3466426-1", "3466426-1")).isTrue();
        assertThat(same("LX-5521", "LX5521")).isTrue();
    }

    @Test
    void doesNotMatchOtherNumbers() {
        assertThat(same("88213", "882130")).isFalse();
        assertThat(same("88213", "PO 7781-A")).isFalse();
        assertThat(same("88213", "8821")).isFalse();
        assertThat(same("3466426-1", "34664")).isFalse();
    }

    @Test
    void readsTheTownFromAnAddress() {
        assertThat(TruckingIntakeService.town("Silver Pine Ranch, HC 33 Box 3389-4, Ely, NV 89301")).isEqualTo("Ely");
        assertThat(TruckingIntakeService.town("Macleff Ranch, 8749 Lsytuq Ave, Hanford, CA 93230")).isEqualTo("Hanford");
        assertThat(TruckingIntakeService.town("PRATT INDUSTRIES - STOCKTON, 2018 ZEPHYR ST, STOCKTON, CA 95202")).isEqualTo("STOCKTON");
        assertThat(TruckingIntakeService.town("no address")).isNull();
    }

    @Test
    void anEmailNamingBothTownsNamesTheLane() {
        Load load = new Load();
        LoadStop pickup = new LoadStop();
        pickup.setKind(StopKind.PICKUP);
        pickup.setPlace("Silver Pine Ranch, Ely, NV 89301");
        LoadStop delivery = new LoadStop();
        delivery.setKind(StopKind.DELIVERY);
        delivery.setPlace("Macleff Ranch, Hanford, CA 93230");
        load.replaceStops(java.util.List.of(pickup, delivery));

        assertThat(TruckingIntakeService.namesLane("Fwd: Ely to Hanford", load)).isTrue();
        assertThat(TruckingIntakeService.namesLane("Hanford paperwork", load)).isFalse();
        // "Ely" inside another word doesn't count
        assertThat(TruckingIntakeService.namesLane("Shelly sent the Hanford docs", load)).isFalse();
    }
}
