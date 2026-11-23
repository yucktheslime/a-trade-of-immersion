package dev.yucktheslime.tradeofimmersion.client.voice;

import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.world.entity.Entity;

import java.util.concurrent.CompletableFuture;

/**
 * One spoken line, played as a streamed sound that follows the speaking entity (OpenAL positional audio, linear
 * fall-off over {@code range} blocks). Uses the "Voice/Speech" volume slider.
 *
 * <p>The audio comes from memory, not a resource pack: NeoForge asks {@link #getStream} and Fabric asks
 * {@link #getAudioStream} for the stream of a streamed sound, so both are implemented.
 */
public final class NpcVoiceInstance extends AbstractTickableSoundInstance {
    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(TradeOfImmersion.MOD_ID, "npc_voice");
    private final Entity speaker;
    private final WavAudioStream stream;
    private final Sound voice;

    public NpcVoiceInstance(Entity speaker, WavAudioStream stream, float volume, int range) {
        super(net.minecraft.sounds.SoundEvent.createVariableRangeEvent(ID), SoundSource.VOICE, SoundInstance.createUnseededRandom());
        this.speaker = speaker;
        this.stream = stream;
        this.volume = Math.max(0f, Math.min(1f, volume));
        this.pitch = 1f;
        this.looping = false;
        this.delay = 0;
        this.relative = false;
        this.attenuation = Attenuation.LINEAR;
        this.voice = new Sound(ID, ConstantFloat.of(1f), ConstantFloat.of(1f), 1, Sound.Type.FILE, true, false, Math.max(4, range));
        this.sound = voice;
        follow();
    }

    private void follow() {
        this.x = speaker.getX();
        this.y = speaker.getEyeY();
        this.z = speaker.getZ();
    }

    public Entity speaker() {
        return speaker;
    }

    @Override
    public WeighedSoundEvents resolve(SoundManager manager) {
        this.sound = voice;
        WeighedSoundEvents events = new WeighedSoundEvents(ID, null);
        events.addSound(voice);
        return events;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        if (speaker.isRemoved()) {
            stop();
            return;
        }
        follow();
    }

    /** NeoForge: SoundInstance#getStream. */
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary library, Sound sound, boolean looping) {
        return CompletableFuture.completedFuture(stream);
    }

    /** Fabric: FabricSoundInstance#getAudioStream. */
    public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary library, ResourceLocation id, boolean looping) {
        return CompletableFuture.completedFuture(stream);
    }
}
