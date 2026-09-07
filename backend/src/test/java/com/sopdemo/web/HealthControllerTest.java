package com.sopdemo.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HealthControllerTest {

    @Test
    void healthReportsUp() {
        assertThat(new HealthController().health().status()).isEqualTo("UP");
    }
}
