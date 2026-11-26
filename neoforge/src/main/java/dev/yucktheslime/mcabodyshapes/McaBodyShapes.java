package dev.yucktheslime.mcabodyshapes;

import dev.yucktheslime.mcabodyshapes.body.BodyShapeAssignment;
import dev.yucktheslime.mcabodyshapes.config.BodyShapeClientConfig;
import dev.yucktheslime.mcabodyshapes.config.BodyShapeCommonConfig;
import dev.yucktheslime.mcabodyshapes.body.BodyShapeAttachments;
import dev.yucktheslime.mcabodyshapes.command.BodyShapeCommands;
import dev.yucktheslime.mcabodyshapes.compat.McaPhysicsCompat;
import dev.yucktheslime.mcabodyshapes.network.SetBodyShapePayload;
import dev.yucktheslime.mcabodyshapes.network.SetPregnancyPayload;
import dev.yucktheslime.mcabodyshapes.body.BodyShapeAge;
import dev.yucktheslime.tradeofimmersion.life.client.BellyVisual;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(McaBodyShapes.MOD_ID)
public final class McaBodyShapes {
    public static final String MOD_ID = "mcabodyshapes";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public McaBodyShapes(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, BodyShapeCommonConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, BodyShapeClientConfig.SPEC);
        BodyShapeAttachments.ATTACHMENT_TYPES.register(modBus);
        modBus.addListener(this::onCommonSetup);
        modBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.addListener(BodyShapeCommands::register);
        NeoForge.EVENT_BUS.addListener(BodyShapeAssignment::onJoinLevel);
        NeoForge.EVENT_BUS.addListener(BodyShapeAssignment::onTick);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            // A Trade of Immersion's own simple belly steps aside wherever this mod's belly mesh draws it.
            BellyVisual.drawnElsewhere = BodyShapeAge::isShapeableAdult;
        }

        // Dev screenshot runner: never shipped (excluded from every jar); only present in a local dev tree.
        if (Boolean.getBoolean("mcabodyshapes.devshot")) {
            try {
                Class.forName("dev.yucktheslime.mcabodyshapes.devshot.DevScreenshotController")
                        .getMethod("register")
                        .invoke(null);
            } catch (ClassNotFoundException e) {
                LOGGER.warn("devshot requested but controller is not on the classpath");
            } catch (Throwable t) {
                LOGGER.error("devshot hook failed", t);
            }
        }
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        McaPhysicsCompat.probe();
        LOGGER.info("MCA Body Shapes ready (physics compat: {})", McaPhysicsCompat.isLoaded());
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(SetBodyShapePayload.TYPE, SetBodyShapePayload.STREAM_CODEC, SetBodyShapePayload::handle);
        registrar.playToServer(SetPregnancyPayload.TYPE, SetPregnancyPayload.STREAM_CODEC, SetPregnancyPayload::handle);
    }
}
