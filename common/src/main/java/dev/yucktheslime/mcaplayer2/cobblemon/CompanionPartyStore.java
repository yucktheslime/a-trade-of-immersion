package dev.yucktheslime.mcaplayer2.cobblemon;

import com.cobblemon.mod.common.api.storage.party.PartyStore;

import java.util.UUID;

/**
 * A Cobblemon party owned by a Player2 companion, keyed by that companion's entity UUID.
 * Not a player party — catches and gifts go here, not into the owner's {@code PlayerPartyStore}.
 */
public final class CompanionPartyStore extends PartyStore {
    public CompanionPartyStore(UUID companionId) {
        super(companionId);
    }

    public UUID getCompanionId() {
        return getUuid();
    }
}
