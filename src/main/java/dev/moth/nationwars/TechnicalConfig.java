package dev.moth.nationwars;

import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.ModConfigSpec;

/** NeoForge-managed technical settings. Gameplay balance remains in the existing server JSON. */
public final class TechnicalConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue DEBUG_COMMANDS;
    private static final ModConfigSpec.EnumValue<JoinPolicy> DEFAULT_JOIN_POLICY;
    private static final ModConfigSpec.BooleanValue SYNCHRONIZE_OPAC_CONFIGURATION;
    private static final ModConfigSpec.BooleanValue EDIT_OPAC_CONFIGURATION;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("technical");
        DEBUG_COMMANDS = builder.comment("Enables destructive Nation Wars debug commands. Permission level 4 is still required.")
            .define("debugCommandsEnabled", !FMLEnvironment.production);
        DEFAULT_JOIN_POLICY = builder.comment("Default policy for newly created nations. Legacy nations without a saved policy use INVITE_ONLY.")
            .defineEnum("defaultJoinPolicy", JoinPolicy.INVITE_ONLY);
        SYNCHRONIZE_OPAC_CONFIGURATION = builder.comment("Synchronizes Nation Wars runtime settings with OPAC.")
            .define("synchronizeOpacConfiguration", true);
        EDIT_OPAC_CONFIGURATION = builder.comment("Allows Nation Wars to safely update the specific OPAC settings it owns.")
            .define("editOpacConfiguration", true);
        builder.pop();
        SPEC = builder.build();
    }

    private TechnicalConfig() {
    }

    public static boolean debugCommandsEnabled() {
        return DEBUG_COMMANDS.get();
    }

    public static JoinPolicy defaultJoinPolicy() {
        return DEFAULT_JOIN_POLICY.get();
    }

    public static boolean synchronizeOpacConfiguration() {
        return SYNCHRONIZE_OPAC_CONFIGURATION.get();
    }

    public static boolean mayEditOpacConfiguration() {
        return EDIT_OPAC_CONFIGURATION.get();
    }
}
