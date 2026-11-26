package dev.yucktheslime.mcabodyshapes.client;

import dev.yucktheslime.mcabodyshapes.body.BodyShape;
import dev.yucktheslime.mcabodyshapes.body.BodyShapeLimits;
import dev.yucktheslime.mcabodyshapes.body.BodyShapePresets;
import java.util.List;
import dev.yucktheslime.mcabodyshapes.body.BodyShapeWrites;
import dev.yucktheslime.mcabodyshapes.body.PregnancyProgress;
import dev.yucktheslime.mcabodyshapes.body.PregnancyWrites;
import dev.yucktheslime.mcabodyshapes.mixin.ScreenInvoker;
import dev.yucktheslime.mcabodyshapes.network.SetBodyShapePayload;
import dev.yucktheslime.mcabodyshapes.network.SetPregnancyPayload;
import net.conczin.mca.client.gui.VillagerEditorScreen;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/**
 * Widgets for the Shape tab on {@link VillagerEditorScreen}. MCA already uses the
 * name "body" for its skin/size page, so this add-on adds a sibling tab.
 */
public final class BodyShapeEditorPage {
    private static final int SLIDER_HEIGHT = 18;
    private static final BodyShape.Channel[] LEFT = {
            BodyShape.Channel.HIPS,
            BodyShape.Channel.WAIST,
            BodyShape.Channel.THIGHS,
            BodyShape.Channel.CURVES,
            BodyShape.Channel.WEIGHT
    };
    private static final BodyShape.Channel[] RIGHT = {
            BodyShape.Channel.MUSCLE,
            BodyShape.Channel.SHOULDERS,
            BodyShape.Channel.CHEST,
            BodyShape.Channel.GUT,
            BodyShape.Channel.HEIGHT
    };

    /** Index of the last applied config preset; -1 before the first click. */
    private static int presetIndex = -1;

    private BodyShapeEditorPage() {
    }

    private static Component presetLabel() {
        List<String> ids = BodyShapePresets.configuredIds();
        if (presetIndex >= 0 && presetIndex < ids.size()) {
            return Component.translatable("gui.mcabodyshapes.preset_named", ids.get(presetIndex));
        }
        return Component.translatable("gui.mcabodyshapes.preset");
    }

    public static void populate(VillagerEditorScreen screen, VillagerEntityMCA preview, VillagerEntityMCA visualization, UUID targetId) {
        BodyShape shape = currentShape(targetId, preview);
        applyLocally(targetId, preview, visualization, shape);

        int dataWidth = 220;
        int x = screen.width / 2;
        int y = screen.height / 2 - 90;
        int col = (dataWidth - 4) / 2;
        ScreenInvoker widgets = (ScreenInvoker) screen;

        // Two headline controls prominently at the top (0 = normal MCA body).
        addSlider(widgets, x, y, dataWidth, BodyShape.Channel.BUST, shape, targetId, preview, visualization);
        y += SLIDER_HEIGHT + 4;
        addSlider(widgets, x, y, dataWidth, BodyShape.Channel.SEAT, shape, targetId, preview, visualization);
        y += SLIDER_HEIGHT + 4;

        int rows = Math.max(LEFT.length, RIGHT.length);
        for (int i = 0; i < rows; i++) {
            if (i < LEFT.length) {
                addSlider(widgets, x, y, col, LEFT[i], shape, targetId, preview, visualization);
            }
            if (i < RIGHT.length) {
                addSlider(widgets, x + col + 4, y, col, RIGHT[i], shape, targetId, preview, visualization);
            }
            y += SLIDER_HEIGHT + 2;
        }

        y += 4;
        float pregnancy = PregnancyProgress.of(worldOrPreview(targetId, preview));
        BodyShapeSlider pregnancySlider = new BodyShapeSlider(
                x, y, dataWidth, SLIDER_HEIGHT,
                Component.translatable("gui.mcabodyshapes.pregnancy"),
                pregnancy,
                1.0f,
                value -> {
                    applyPregnancyLocally(targetId, preview, visualization, value);
                    PacketDistributor.sendToServer(new SetPregnancyPayload(targetId, value));
                }
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Component.translatable(
                        "gui.mcabodyshapes.pregnancy_value",
                        String.format("%.0f", amount() * 100.0f)
                ));
            }
        };
        pregnancySlider.refreshMessage();
        widgets.mcabodyshapes$addWidget(pregnancySlider);
        y += SLIDER_HEIGHT + 8;

        y += 4;
        int thirdW = (dataWidth - 8) / 3;
        widgets.mcabodyshapes$addWidget(Button.builder(
                Component.translatable("gui.mcabodyshapes.randomize"),
                b -> applyAndRebuild(screen, preview, visualization, targetId, BodyShape.random(RandomSource.create()))
        ).bounds(x, y, thirdW, 20).build());
        widgets.mcabodyshapes$addWidget(Button.builder(
                presetLabel(),
                b -> {
                    List<String> ids = BodyShapePresets.configuredIds();
                    if (ids.isEmpty()) {
                        return;
                    }
                    presetIndex = (presetIndex + 1) % ids.size();
                    BodyShape preset = BodyShapePresets.get(ids.get(presetIndex));
                    if (preset != null) {
                        applyAndRebuild(screen, preview, visualization, targetId, preset);
                    }
                }
        ).bounds(x + thirdW + 4, y, thirdW, 20).build());
        widgets.mcabodyshapes$addWidget(Button.builder(
                Component.translatable("gui.mcabodyshapes.reset"),
                b -> applyAndRebuild(screen, preview, visualization, targetId, BodyShape.defaults())
        ).bounds(x + 2 * (thirdW + 4), y, thirdW, 20).build());
    }

    public static void copyWorldShapeToPreview(UUID targetId, VillagerEntityMCA preview, VillagerEntityMCA visualization) {
        LivingEntity source = worldOrPreview(targetId, preview);
        BodyShape shape = BodyShape.get(source);
        BodyShapeWrites.apply(preview, shape);
        BodyShapeWrites.apply(visualization, shape);
        float pregnancy = PregnancyProgress.of(source);
        PregnancyWrites.apply(preview, pregnancy);
        PregnancyWrites.apply(visualization, pregnancy);
    }

    private static void addSlider(
            ScreenInvoker widgets,
            int x,
            int y,
            int width,
            BodyShape.Channel channel,
            BodyShape initial,
            UUID targetId,
            VillagerEntityMCA preview,
            VillagerEntityMCA visualization
    ) {
        Component label = Component.translatable("gui.mcabodyshapes.slider." + channel.key());
        // Figure controls use the configured range from mcabodyshapes-common.toml
        // (never wider than the hard caps); other channels keep 0..max.
        float min = BodyShapeLimits.min(channel);
        float max = BodyShapeLimits.max(channel);
        BodyShapeSlider slider = new BodyShapeSlider(
                x, y, width, SLIDER_HEIGHT,
                label,
                initial.get(channel),
                min,
                max,
                value -> {
                    BodyShape updated = currentShape(targetId, preview).with(channel, value);
                    applyLocally(targetId, preview, visualization, updated);
                    PacketDistributor.sendToServer(new SetBodyShapePayload(targetId, updated));
                }
        ) {
            @Override
            protected void updateMessage() {
                if (channel == BodyShape.Channel.BUST) {
                    float amount = amount();
                    if (amount <= 0.04f) {
                        setMessage(Component.translatable("gui.mcabodyshapes.slider.bust.flat"));
                    } else if (amount >= max - 0.04f) {
                        setMessage(Component.translatable("gui.mcabodyshapes.slider.bust.max"));
                    } else {
                        setMessage(Component.translatable(
                                "gui.mcabodyshapes.slider.bust.value",
                                String.format("%.2f", amount)
                        ));
                    }
                } else if (channel == BodyShape.Channel.SEAT) {
                    float amount = amount();
                    if (amount <= 0.04f) {
                        setMessage(Component.translatable("gui.mcabodyshapes.slider.seat.flat"));
                    } else if (amount >= max - 0.04f) {
                        setMessage(Component.translatable("gui.mcabodyshapes.slider.seat.thick"));
                    } else {
                        setMessage(Component.translatable(
                                "gui.mcabodyshapes.slider.seat.value",
                                String.format("%.2f", amount)
                        ));
                    }
                } else if (channel == BodyShape.Channel.CURVES) {
                    float amount = amount();
                    if (amount <= 0.04f) {
                        setMessage(Component.translatable("gui.mcabodyshapes.slider.curves.straight"));
                    } else if (amount >= BodyShape.MAX - 0.04f) {
                        setMessage(Component.translatable("gui.mcabodyshapes.slider.curves.full"));
                    } else {
                        setMessage(Component.translatable(
                                "gui.mcabodyshapes.slider.curves.value",
                                String.format("%.2f", amount)
                        ));
                    }
                } else {
                    super.updateMessage();
                }
            }
        };
        slider.refreshMessage();
        widgets.mcabodyshapes$addWidget(slider);
    }

    private static void applyAndRebuild(
            VillagerEditorScreen screen,
            VillagerEntityMCA preview,
            VillagerEntityMCA visualization,
            UUID targetId,
            BodyShape shape
    ) {
        applyLocally(targetId, preview, visualization, shape);
        PacketDistributor.sendToServer(new SetBodyShapePayload(targetId, shape));
        ((VillagerEditorAccess) screen).mcabodyshapes$rebuildShapePage();
    }

    private static void applyPregnancyLocally(
            UUID targetId,
            VillagerEntityMCA preview,
            VillagerEntityMCA visualization,
            float progress
    ) {
        PregnancyWrites.apply(preview, progress);
        PregnancyWrites.apply(visualization, progress);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            Entity entity = findEntity(minecraft, targetId);
            if (entity instanceof LivingEntity living) {
                PregnancyWrites.apply(living, progress);
            }
        }
    }

    private static void applyLocally(UUID targetId, VillagerEntityMCA preview, VillagerEntityMCA visualization, BodyShape shape) {
        BodyShapeWrites.apply(preview, shape);
        BodyShapeWrites.apply(visualization, shape);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            Entity entity = findEntity(minecraft, targetId);
            if (entity instanceof LivingEntity living) {
                BodyShapeWrites.apply(living, shape);
            }
        }
    }

    private static BodyShape currentShape(UUID targetId, VillagerEntityMCA preview) {
        return BodyShape.get(worldOrPreview(targetId, preview));
    }

    /**
     * True when MCA's editor is open on an A Trade of Immersion companion (TOI lets MCA's editor edit them).
     * Companions are not MCA villagers, so body shapes cannot be saved on them and the Shape tab is hidden.
     */
    public static boolean isToiCompanion(UUID targetId) {
        return findEntity(Minecraft.getInstance(), targetId) instanceof dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
    }

    private static Entity findEntity(Minecraft minecraft, UUID targetId) {
        if (minecraft.level == null) {
            return null;
        }
        if (minecraft.player != null && targetId.equals(minecraft.player.getUUID())) {
            return minecraft.player;
        }
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (targetId.equals(entity.getUUID())) {
                return entity;
            }
        }
        return null;
    }

    private static LivingEntity worldOrPreview(UUID targetId, VillagerEntityMCA preview) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity entity = findEntity(minecraft, targetId);
        if (entity instanceof LivingEntity living) {
            return living;
        }
        return preview;
    }


}
