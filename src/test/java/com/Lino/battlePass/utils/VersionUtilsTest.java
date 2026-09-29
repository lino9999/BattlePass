package com.Lino.battlePass.utils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class VersionUtilsTest {
    @ParameterizedTest
    @CsvSource({"8.5, 8.2, true", "8.5, 8.5, false", "8.5, 8.6, false", "8.10, 8.9, true",
            "8.9, 8.10, false", "8.5.0, 8.5, false", "8.5.1, 8.5, true", "v8.6, 8.5, true"})
    void onlyNewerReleasesTriggerAnUpdate(String latest, String installed, boolean expected) {
        assertEquals(expected, VersionUtils.isNewer(latest, installed));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"<html>Unavailable</html>", "8..5", "rate limit exceeded"})
    void ignoresInvalidUpdateResponses(String response) {
        assertFalse(VersionUtils.isNewer(response, "8.6"));
    }
}
