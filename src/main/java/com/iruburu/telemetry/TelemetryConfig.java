package com.iruburu.telemetry;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = IruburuTelemetryMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TelemetryConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.ConfigValue<String> API_URL = BUILDER
            .comment("Base URL for the Iruburu API, for example https://platform.faputa.com")
            .define("apiUrl", "");
    private static final ForgeConfigSpec.ConfigValue<String> API_KEY = BUILDER
            .comment("Telemetry API key. If empty, no telemetry is sent.")
            .define("apiKey", "");
    private static final ForgeConfigSpec.ConfigValue<String> SERVER_ID = BUILDER.define("serverId", "");
    private static final ForgeConfigSpec.ConfigValue<String> SERVER_NAME = BUILDER.define("serverName", "");
    private static final ForgeConfigSpec.ConfigValue<String> PACK_ID = BUILDER.define("packId", "");
    private static final ForgeConfigSpec.ConfigValue<String> PACK_VERSION = BUILDER.define("packVersion", "");
    private static final ForgeConfigSpec.BooleanValue INVENTORY_SNAPSHOTS_ENABLED = BUILDER
            .comment("Send periodic inventory_snapshot events for online players.")
            .define("inventorySnapshotsEnabled", true);
    private static final ForgeConfigSpec.IntValue INVENTORY_SNAPSHOT_INTERVAL_SECONDS = BUILDER
            .comment("Interval for periodic inventory snapshots.")
            .defineInRange("inventorySnapshotIntervalSeconds", 60, 5, 3600);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    private static volatile Values values = Values.fromForge();

    private TelemetryConfig() {
    }

    static Values current() {
        return values;
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        values = Values.fromForge();
    }

    record Values(
            String apiUrl,
            String apiKey,
            String serverId,
            String serverName,
            String packId,
            String packVersion,
            boolean inventorySnapshotsEnabled,
            int inventorySnapshotIntervalSeconds
    ) {
        private static Values fromForge() {
            return new Values(
                    property("iruburu.telemetry.apiUrl", API_URL.get()),
                    property("iruburu.telemetry.apiKey", API_KEY.get()),
                    property("iruburu.telemetry.serverId", SERVER_ID.get()),
                    property("iruburu.telemetry.serverName", SERVER_NAME.get()),
                    property("iruburu.telemetry.packId", PACK_ID.get()),
                    property("iruburu.telemetry.packVersion", PACK_VERSION.get()),
                    booleanProperty("iruburu.telemetry.inventorySnapshotsEnabled", INVENTORY_SNAPSHOTS_ENABLED.get()),
                    intProperty("iruburu.telemetry.inventorySnapshotIntervalSeconds", INVENTORY_SNAPSHOT_INTERVAL_SECONDS.get())
            );
        }

        boolean enabled() {
            return !apiKey.isBlank() && !apiUrl.isBlank();
        }

        String endpoint() {
            String base = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
            return base + "/api/v1/telemetry/server/events";
        }
    }

    private static String property(String key, String fallback) {
        String value = System.getProperty(key);
        return value == null ? fallback : value.trim();
    }

    private static boolean booleanProperty(String key, boolean fallback) {
        String value = System.getProperty(key);
        return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value);
    }

    private static int intProperty(String key, int fallback) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
