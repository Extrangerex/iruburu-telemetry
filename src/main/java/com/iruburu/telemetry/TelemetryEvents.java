package com.iruburu.telemetry;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class TelemetryEvents {
    private static final long DAMAGE_RATE_LIMIT_NANOS = 100_000_000L;
    private static final int TICKS_PER_SECOND = 20;

    private final TelemetryClient client;
    private final Map<UUID, Long> lastDamageSentAt = new ConcurrentHashMap<>();
    private int serverTicks;

    TelemetryEvents(TelemetryClient client) {
        this.client = client;
    }

    @SubscribeEvent
    void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Map<String, Object> payload = playerPayload(player);
            payload.put("ip", player.connection.getRemoteAddress().toString());
            addPlayerState(player, payload);
            client.send("player_connected", payload);
            sendInventorySnapshot(player);
        }
    }

    @SubscribeEvent
    void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Map<String, Object> payload = playerPayload(player);
            payload.put("reason", "");
            addPlayerState(player, payload);
            client.send("player_disconnected", payload);
            sendInventorySnapshot(player);
            lastDamageSentAt.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DamageSource source = event.getSource();
            Map<String, Object> payload = playerPayload(player);
            payload.put("deathMessage", player.getCombatTracker().getDeathMessage().getString());
            payload.put("damageType", damageType(source));
            Entity killer = source.getEntity();
            payload.put("killerName", killer == null ? "" : killer.getName().getString());
            payload.put("killerType", entityType(killer));
            payload.put("healthBeforeDeath", player.getHealth());
            payload.put("xpLevel", player.experienceLevel);
            payload.put("deathCountAfterEstimated", deathCount(player) + 1);
            client.send("player_death", payload);
            sendInventorySnapshot(player);
        }
    }

    @SubscribeEvent
    void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            long now = System.nanoTime();
            Long previous = lastDamageSentAt.get(player.getUUID());
            if (previous != null && now - previous < DAMAGE_RATE_LIMIT_NANOS) {
                return;
            }
            lastDamageSentAt.put(player.getUUID(), now);

            float amount = event.getAmount();
            DamageSource source = event.getSource();
            Entity attacker = source.getEntity();
            Map<String, Object> payload = playerPayload(player);
            payload.put("amount", amount);
            payload.put("damageType", damageType(source));
            payload.put("attackerName", attacker == null ? "" : attacker.getName().getString());
            payload.put("attackerType", entityType(attacker));
            payload.put("healthBefore", player.getHealth());
            payload.put("healthAfterEstimated", Math.max(0.0F, player.getHealth() - amount));
            payload.put("armor", player.getArmorValue());
            payload.put("absorption", player.getAbsorptionAmount());
            payload.put("deathCount", deathCount(player));
            client.send("player_damaged", payload);
        }
    }

    @SubscribeEvent
    void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Map<String, Object> payload = playerPayload(player);
            payload.put("from", dimension(event.getFrom()));
            payload.put("to", dimension(event.getTo()));
            client.send("dimension_changed", payload);
        }
    }

    @SubscribeEvent
    void onAdvancementEarned(AdvancementEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Advancement advancement = event.getAdvancement();
            Map<String, Object> payload = playerPayload(player);
            DisplayInfo display = advancement.getDisplay();
            payload.put("advancementId", advancement.getId().toString());
            payload.put("title", display == null ? "" : display.getTitle().getString());
            client.send("advancement_completed", payload);
        }
    }

    @SubscribeEvent
    void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        serverTicks++;
        TelemetryConfig.Values config = TelemetryConfig.current();
        int intervalTicks = Math.max(1, config.inventorySnapshotIntervalSeconds()) * TICKS_PER_SECOND;
        if (!config.inventorySnapshotsEnabled() || serverTicks % intervalTicks != 0) {
            return;
        }

        MinecraftServer server = event.getServer();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendInventorySnapshot(player);
        }
    }

    private void sendInventorySnapshot(ServerPlayer player) {
        Map<String, Object> payload = playerPayload(player, false);
        Inventory inventory = player.getInventory();
        List<Map<String, Object>> items = new ArrayList<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                items.add(itemSummary(slot, stack));
            }
        }
        payload.put("items", items);
        payload.put("itemCount", items.size());
        payload.put("selectedSlot", inventory.selected);
        payload.put("armor", equipmentSummary(player, List.of(
                EquipmentSlot.FEET,
                EquipmentSlot.LEGS,
                EquipmentSlot.CHEST,
                EquipmentSlot.HEAD
        )));
        ItemStack offhand = player.getOffhandItem();
        payload.put("offhand", offhand.isEmpty() ? List.of() : List.of(itemSummary(0, offhand)));
        client.send("inventory_snapshot", payload);
    }

    private static Map<String, Object> playerPayload(ServerPlayer player) {
        return playerPayload(player, true);
    }

    private static Map<String, Object> playerPayload(ServerPlayer player, boolean includePosition) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("playerUuid", player.getUUID().toString());
        payload.put("playerName", player.getGameProfile().getName());
        payload.put("dimension", player.level().dimension().location().toString());
        if (includePosition) {
            payload.put("position", Map.of(
                    "x", player.getX(),
                    "y", player.getY(),
                    "z", player.getZ()
            ));
        }
        payload.put("world", worldPayload(player));
        payload.put("occurredAt", Instant.now().toString());
        return payload;
    }

    private static void addPlayerState(ServerPlayer player, Map<String, Object> payload) {
        payload.put("gameMode", gameMode(player));
        payload.put("health", player.getHealth());
        payload.put("maxHealth", player.getMaxHealth());
        payload.put("food", player.getFoodData().getFoodLevel());
        payload.put("saturation", player.getFoodData().getSaturationLevel());
        payload.put("xpLevel", player.experienceLevel);
        payload.put("xpProgress", player.experienceProgress);
        payload.put("totalExperience", player.totalExperience);
        payload.put("xpNeededForNextLevel", player.getXpNeededForNextLevel());
        payload.put("deathCount", deathCount(player));
    }

    private static List<Map<String, Object>> equipmentSummary(ServerPlayer player, List<EquipmentSlot> slots) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (EquipmentSlot slot : slots) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                result.add(itemSummary(slot.getIndex(), stack));
            }
        }
        return result;
    }

    private static Map<String, Object> itemSummary(int slot, ItemStack stack) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("slot", slot);
        if (stack.isEmpty()) {
            item.put("item", "");
            item.put("count", 0);
            item.put("damage", 0);
            item.put("maxDamage", 0);
            item.put("enchantments", List.of());
            return item;
        }
        item.put("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        item.put("count", stack.getCount());
        item.put("damage", stack.getDamageValue());
        item.put("maxDamage", stack.getMaxDamage());
        item.put("maxStackSize", stack.getMaxStackSize());
        if (stack.hasCustomHoverName()) {
            item.put("customName", stack.getHoverName().getString());
        }
        item.put("enchantments", enchantments(stack));
        return item;
    }

    private static List<Map<String, Object>> enchantments(ItemStack stack) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> entry : EnchantmentHelper.getEnchantments(stack).entrySet()) {
            Map<String, Object> enchantment = new LinkedHashMap<>();
            enchantment.put("id", BuiltInRegistries.ENCHANTMENT.getKey(entry.getKey()).toString());
            enchantment.put("level", entry.getValue());
            result.add(enchantment);
        }
        return result;
    }

    private static Map<String, Object> worldPayload(ServerPlayer player) {
        Level level = player.level();
        LevelData levelData = level.getLevelData();
        long dayTime = level.getDayTime();
        Map<String, Object> world = new LinkedHashMap<>();
        world.put("gameTime", level.getGameTime());
        world.put("dayTime", dayTime);
        world.put("day", dayTime / 24_000L);
        world.put("timeOfDay", dayTime % 24_000L);
        world.put("raining", level.isRaining());
        world.put("thundering", level.isThundering());
        world.put("difficulty", levelData.getDifficulty().getSerializedName());
        world.put("hardcore", levelData.isHardcore());
        MinecraftServer server = level.getServer();
        if (server != null) {
            world.put("onlinePlayers", server.getPlayerCount());
            world.put("maxPlayers", server.getMaxPlayers());
        }
        return world;
    }

    private static int deathCount(ServerPlayer player) {
        return player.getStats().getValue(Stats.CUSTOM, Stats.DEATHS);
    }

    private static String gameMode(ServerPlayer player) {
        GameType gameType = player.gameMode.getGameModeForPlayer();
        return gameType.getName();
    }

    private static String damageType(DamageSource source) {
        return source.typeHolder().unwrapKey()
                .map(key -> key.location().toString())
                .orElse(source.getMsgId());
    }

    private static String entityType(Entity entity) {
        return entity == null ? "" : BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }

    private static String dimension(ResourceKey<Level> key) {
        return key.location().toString();
    }
}
