package com.envisione.progressiveskills.server.transaction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase4SelfTestTest {
    @Test
    void executableLifecycleProofPasses() {
        var result = Phase4SelfTest.run();
        assertTrue(result.successful(), result.detail());
    }
}
