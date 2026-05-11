package com.iruburu.telemetry;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
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

    public IruburuTelemetryMod() {
        FMLJavaModLoadingContext context = FMLJavaModLoadingContext.get();
        IEventBus modEventBus = context.getModEventBus();
        context.registerConfig(ModConfig.Type.COMMON, TelemetryConfig.SPEC);
        modEventBus.addListener(this::commonSetup);

        TelemetryClient client = new TelemetryClient(TelemetryConfig::current);
        this.events = new TelemetryEvents(client);
        MinecraftForge.EVENT_BUS.register(events);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Iruburu telemetry loaded");
    }
}
