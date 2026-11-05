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
}
