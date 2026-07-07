package dev.moth.nationwars;

import net.minecraft.world.level.GameRules;

public final class NationWarsGameRules {
    public static final GameRules.Key<GameRules.BooleanValue> LIMITED_DOCTRINES =
        GameRules.register("LimitedDoctrines", GameRules.Category.MISC, GameRules.BooleanValue.create(true));

    private NationWarsGameRules() {
    }

    public static void bootstrap() {
        // Referencing this class registers the gamerule.
    }
}
