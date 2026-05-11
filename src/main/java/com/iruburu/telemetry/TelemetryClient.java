package com.iruburu.telemetry;

import net.minecraft.SharedConstants;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

final class TelemetryClient {
    private final Supplier<TelemetryConfig.Values> configSupplier;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private final AtomicBoolean missingConfigWarned = new AtomicBoolean();

    TelemetryClient(Supplier<TelemetryConfig.Values> configSupplier) {
        this.configSupplier = configSupplier;
    }

    void send(String eventType, Map<String, Object> payload) {
        TelemetryConfig.Values config = configSupplier.get();
        if (!config.enabled()) {
            if (missingConfigWarned.compareAndSet(false, true)) {
                IruburuTelemetryMod.LOGGER.warn("Iruburu telemetry disabled: iruburu.telemetry.apiUrl or iruburu.telemetry.apiKey is empty");
            }
            return;
        }

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventType", eventType);
        event.put("serverId", config.serverId());
        event.put("serverName", config.serverName());
        event.put("packId", config.packId());
        event.put("packVersion", config.packVersion());
        event.put("minecraftVersion", minecraftVersion());
        event.put("loader", "forge");
        event.putAll(payload);

        HttpRequest request = HttpRequest.newBuilder(URI.create(config.endpoint()))
                .timeout(Duration.ofSeconds(3))
                .header("X-Telemetry-API-Key", config.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(event)))
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenAccept(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        IruburuTelemetryMod.LOGGER.warn("Iruburu telemetry request failed with status {}", response.statusCode());
                    }
                })
                .exceptionally(error -> {
                    IruburuTelemetryMod.LOGGER.warn("Iruburu telemetry request failed", error);
                    return null;
                });
    }

    private static String minecraftVersion() {
        Object version = SharedConstants.getCurrentVersion();
        for (String methodName : new String[]{"getName", "name", "id", "getId"}) {
            try {
                Object value = version.getClass().getMethod(methodName).invoke(version);
                if (value != null) {
                    return value.toString();
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return "unknown";
    }
}
