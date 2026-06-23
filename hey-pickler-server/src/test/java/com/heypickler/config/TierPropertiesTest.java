package com.heypickler.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TierPropertiesTest {

    @Test
    void bindsThresholds_andTierForPoints() {
        var source = new MapConfigurationPropertySource(Map.ofEntries(
            Map.entry("hey-pickler.tier.keys[0]", "BRONZE"),
            Map.entry("hey-pickler.tier.keys[1]", "SILVER"),
            Map.entry("hey-pickler.tier.keys[2]", "GOLD"),
            Map.entry("hey-pickler.tier.keys[3]", "PLATINUM"),
            Map.entry("hey-pickler.tier.keys[4]", "DIAMOND"),
            Map.entry("hey-pickler.tier.keys[5]", "MASTER"),
            Map.entry("hey-pickler.tier.names[0]", "青铜"),
            Map.entry("hey-pickler.tier.names[1]", "白银"),
            Map.entry("hey-pickler.tier.names[2]", "黄金"),
            Map.entry("hey-pickler.tier.names[3]", "铂金"),
            Map.entry("hey-pickler.tier.names[4]", "钻石"),
            Map.entry("hey-pickler.tier.names[5]", "王者"),
            Map.entry("hey-pickler.tier.star.thresholds[0]", "0"),
            Map.entry("hey-pickler.tier.star.thresholds[1]", "500"),
            Map.entry("hey-pickler.tier.star.thresholds[2]", "1200"),
            Map.entry("hey-pickler.tier.star.thresholds[3]", "2500"),
            Map.entry("hey-pickler.tier.star.thresholds[4]", "5000"),
            Map.entry("hey-pickler.tier.star.thresholds[5]", "10000"),
            Map.entry("hey-pickler.tier.party.thresholds[0]", "0"),
            Map.entry("hey-pickler.tier.party.thresholds[1]", "200"),
            Map.entry("hey-pickler.tier.party.thresholds[2]", "500"),
            Map.entry("hey-pickler.tier.party.thresholds[3]", "1200"),
            Map.entry("hey-pickler.tier.party.thresholds[4]", "2500"),
            Map.entry("hey-pickler.tier.party.thresholds[5]", "5000")
        ));
        TierProperties props = new Binder(source).bind("hey-pickler.tier", TierProperties.class).get();

        assertEquals("BRONZE", props.keyFor(0, "STAR"));
        assertEquals("BRONZE", props.keyFor(499, "STAR"));
        assertEquals("SILVER", props.keyFor(500, "STAR"));
        assertEquals("GOLD", props.keyFor(1200, "STAR"));
        assertEquals("MASTER", props.keyFor(10000, "STAR"));
        assertEquals("BRONZE", props.keyFor(0, "PARTY"));
        assertEquals("GOLD", props.keyFor(500, "PARTY"));
        assertEquals(6, props.getKeys().size());
        assertEquals("青铜", props.nameFor("BRONZE"));
    }
}
