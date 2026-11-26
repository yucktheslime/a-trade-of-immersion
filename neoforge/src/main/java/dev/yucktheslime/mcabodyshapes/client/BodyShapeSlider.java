package dev.yucktheslime.mcabodyshapes.client;

import java.util.function.Consumer;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import dev.yucktheslime.mcabodyshapes.body.BodyShape;

/**
 * A labelled slider whose stored {@link AbstractSliderButton} value is 0–1
 * and whose displayed amount runs {@code min}–{@code max} (by default 0–2, or
 * 0–3 for rear depth; the figure controls use the configured range).
 *
 * <p>Mouse dragging stays continuous (the geometry lerps on every value).
 * Arrow keys move in {@link #STEP} increments (0.05) so the control is
 * fine-grained and even, without jumping a large fraction of the range.
 */
public class BodyShapeSlider extends AbstractSliderButton {
    /** Fine keyboard increment in channel units (0–2 or 0–3). */
    public static final float STEP = 0.05f;

    private final Component label;
    private final Consumer<Float> callback;
    private final float min;
    private final float max;

    public BodyShapeSlider(int x, int y, int width, int height, Component label, float amount, Consumer<Float> callback) {
        this(x, y, width, height, label, amount, BodyShape.MAX, callback);
    }

    public BodyShapeSlider(int x, int y, int width, int height, Component label, float amount, float max, Consumer<Float> callback) {
        this(x, y, width, height, label, amount, 0.0f, max, callback);
    }

    /** Slider over {@code min..max} (the configured range for a control). */
    public BodyShapeSlider(int x, int y, int width, int height, Component label, float amount, float min, float max, Consumer<Float> callback) {
        super(x, y, width, height, label, toUnit(amount, min, max));
        this.label = label;
        this.callback = callback;
        this.min = min;
        this.max = Math.max(min + 1.0e-3f, max);
        updateMessage();
    }

    private static double toUnit(float amount, float min, float max) {
        float span = Math.max(1.0e-3f, max - min);
        return Mth.clamp((amount - min) / span, 0.0f, 1.0f);
    }

    public float amount() {
        return (float) (min + this.value * (max - min));
    }

    public void setAmount(float amount) {
        this.value = toUnit(amount, min, max);
        refreshMessage();
    }

    public void refreshMessage() {
        updateMessage();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean left = keyCode == 263;
        boolean right = keyCode == 262;
        if (left || right) {
            float delta = left ? -STEP : STEP;
            float next = Mth.clamp(Math.round((amount() + delta) / STEP) * STEP, min, max);
            this.value = toUnit(next, min, max);
            applyValue();
            updateMessage();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void applyValue() {
        callback.accept(amount());
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.translatable("gui.mcabodyshapes.slider_value", label, String.format("%.2f", amount())));
    }
}
