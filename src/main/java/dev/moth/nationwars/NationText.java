package dev.moth.nationwars;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Server-safe helpers for translated Nation Wars components. */
public final class NationText {
    private NationText() {
    }

    public static MutableComponent tr(String key, Object... arguments) {
        return Component.translatable(key, arguments);
    }

    public static MutableComponent message(String key, Object... arguments) {
        return Component.translatable("nationwars.message.prefix").append(Component.translatable(key, arguments));
    }

    public static Component doctrineName(Doctrine doctrine) {
        return doctrine.usesDefaultDisplayName()
            ? Component.translatable("nationwars.doctrine." + doctrine.translationStem() + ".name")
            : Component.literal(doctrine.displayName);
    }

    public static Component ideologyName(Ideology ideology) {
        return Component.translatable("nationwars.ideology." + ideology.name().toLowerCase(java.util.Locale.ROOT));
    }

    public static List<Component> doctrinePerks(Doctrine doctrine) {
        if (!doctrine.usesDefaultPerkLore()) {
            return doctrine.perkLore().stream().map(Component::literal).map(component -> (Component)component).toList();
        }
        return doctrine.defaultPerkTranslationKeys().stream()
            .map(Component::translatable).map(component -> (Component)component).toList();
    }

    public static MutableComponent join(List<? extends Component> parts) {
        MutableComponent joined = Component.empty();
        for (int index = 0; index < parts.size(); index++) {
            if (index > 0) {
                joined.append(Component.translatable("nationwars.common.list_separator"));
            }
            joined.append(parts.get(index));
        }
        return joined;
    }
}
