package com.envisione.progressiveskills.common.hardening;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerformanceWorkloadTest {
    @TempDir
    Path temporary;

    @Test
    void consumesTheLockedContractAndGeneratesTheFullFixtureIndex() throws Exception {
        var contract = PerformanceWorkload.readContract(
                Path.of(System.getProperty("progressiveskills.projectDir"))
                        .resolve("docs/performance/fixtures/perf-001-v1.toml"));
        assertEquals(PerformanceWorkload.reference(), contract);

        Path fixture = PerformanceWorkload.generateFixtureIndex(
                contract, temporary.resolve("perf-001.index"));
        assertTrue(Files.size(fixture) > 400_000L);
    }

    @Test
    void runsDeterministicFortyAndOneHundredPlayerSmokeSchedules() {
        var contract = PerformanceWorkload.reference();
        PerformanceWorkload.run(contract, 100, 1_000);
        var forty = PerformanceWorkload.run(contract, 40, 2_000);
        var hundred = PerformanceWorkload.run(contract, 100, 2_000);

        assertEquals(forty.checksum(), PerformanceWorkload.run(contract, 40, 2_000).checksum());
        assertEquals(contract.routedRuleCount(), forty.compiledRoutes());
        assertTrue(forty.events() > 0);
        assertTrue(hundred.events() > forty.events());
        assertTrue(hundred.matchedRoutes() > forty.matchedRoutes());
        assertTrue(forty.withinReferenceBudget());
        assertTrue(hundred.withinReferenceBudget());
    }

    @Test
    void fullReferenceSchedulesRetainLockedWarmupAndCaptureLengths() {
        var contract = PerformanceWorkload.reference();

        var forty = PerformanceWorkload.runReference(contract, 40);
        var hundred = PerformanceWorkload.runReference(contract, 100);

        assertEquals(contract.captureTicks(), forty.ticks());
        assertEquals(contract.captureTicks(), hundred.ticks());
        assertTrue(forty.withinReferenceBudget());
        assertTrue(hundred.withinReferenceBudget());
    }
}
