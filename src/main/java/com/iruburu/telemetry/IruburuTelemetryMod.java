package com.iruburu.telemetry;

import com.mojang.logging.LogUtils;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(IruburuTelemetryMod.MODID)
public final class IruburuTelemetryMod {
    public static final String MODID = "iruburu_telemetry";
    static final Logger LOGGER = LogUtils.getLogger();

    private final TelemetryEvents events;

    public IruburuTelemetryMod(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.COMMON, TelemetryConfig.SPEC);
        FMLCommonSetupEvent.getBus(context.getModBusGroup()).addListener(this::commonSetup);

        TelemetryClient client = new TelemetryClient(TelemetryConfig::current);
        this.events = new TelemetryEvents(client);

        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(events::onPlayerLoggedIn);
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(events::onPlayerLoggedOut);
        PlayerEvent.PlayerChangedDimensionEvent.BUS.addListener(events::onPlayerChangedDimension);
        LivingDeathEvent.BUS.addListener(events::onLivingDeath);
        LivingDamageEvent.BUS.addListener(events::onLivingDamage);
        AdvancementEvent.AdvancementEarnEvent.BUS.addListener(events::onAdvancementEarned);
        TickEvent.ServerTickEvent.Post.BUS.addListener(events::onServerTick);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Iruburu telemetry loaded");
    }
}
