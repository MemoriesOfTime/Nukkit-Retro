package cn.nukkit.test;

import cn.nukkit.network.protocol.ProtocolInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Protocol min/max range")
class ProtocolRangeTest {

    @DisplayName("Defaults: min=0 and max=CURRENT_PROTOCOL let everything through")
    @Test
    void testDefaultsAllowAll() {
        int max = ProtocolInfo.CURRENT_PROTOCOL; // normalized value of max-protocol: -1
        for (int protocol : ProtocolInfo.SUPPORTED_PROTOCOLS) {
            assertTrue(ProtocolInfo.isProtocolInRange(protocol, 0, max), "protocol " + protocol + " should pass");
        }
    }

    @DisplayName("Minimum protocol rejects older clients")
    @Test
    void testMinimumProtocol() {
        assertAll(
                () -> assertTrue(ProtocolInfo.isProtocolInRange(ProtocolInfo.v0_16_0, ProtocolInfo.v0_16_0, ProtocolInfo.CURRENT_PROTOCOL)),
                () -> assertTrue(ProtocolInfo.isProtocolInRange(ProtocolInfo.v1_1_3, ProtocolInfo.v0_16_0, ProtocolInfo.CURRENT_PROTOCOL)),
                () -> assertFalse(ProtocolInfo.isProtocolInRange(ProtocolInfo.v0_15_10, ProtocolInfo.v0_16_0, ProtocolInfo.CURRENT_PROTOCOL)),
                () -> assertFalse(ProtocolInfo.isProtocolInRange(ProtocolInfo.v0_12_0, ProtocolInfo.v1_0_0, ProtocolInfo.CURRENT_PROTOCOL))
        );
    }

    @DisplayName("Maximum protocol rejects newer clients")
    @Test
    void testMaximumProtocol() {
        assertAll(
                () -> assertTrue(ProtocolInfo.isProtocolInRange(ProtocolInfo.v0_15_0, 0, ProtocolInfo.v0_15_10)),
                () -> assertTrue(ProtocolInfo.isProtocolInRange(ProtocolInfo.v0_15_10, 0, ProtocolInfo.v0_15_10)),
                () -> assertFalse(ProtocolInfo.isProtocolInRange(ProtocolInfo.v0_16_0, 0, ProtocolInfo.v0_15_10)),
                () -> assertFalse(ProtocolInfo.isProtocolInRange(ProtocolInfo.v1_1_3, 0, ProtocolInfo.v1_0_0))
        );
    }

    @DisplayName("min > max (min > 0) keeps only the minimum check, matching Nukkit-MOT")
    @Test
    void testInvertedRangeIgnoresMaximum() {
        assertAll(
                () -> assertTrue(ProtocolInfo.isProtocolInRange(ProtocolInfo.v1_1_3, ProtocolInfo.v0_16_0, ProtocolInfo.v0_15_0)),
                () -> assertTrue(ProtocolInfo.isProtocolInRange(999, ProtocolInfo.v0_16_0, ProtocolInfo.v0_15_0)),
                () -> assertFalse(ProtocolInfo.isProtocolInRange(ProtocolInfo.v0_15_10, ProtocolInfo.v0_16_0, ProtocolInfo.v0_15_0))
        );
    }
}
