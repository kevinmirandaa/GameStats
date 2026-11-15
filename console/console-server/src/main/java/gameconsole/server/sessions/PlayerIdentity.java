package gameconsole.server.sessions;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PlayerIdentity {

    private static final int MAXIMUM_NAME_LENGTH = 24;
    private static final int MAXIMUM_SLUG_LENGTH = 30;

    private PlayerIdentity() {
    }

    public static String cleanName(String rawName, int position) {
        String name = rawName == null ? "" : rawName.trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) {
            return "Jugador " + position;
        }
        return name.length() > MAXIMUM_NAME_LENGTH ? name.substring(0, MAXIMUM_NAME_LENGTH) : name;
    }

    public static String idFor(String displayName) {
        String decomposed = Normalizer.normalize(displayName, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = decomposed.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.isEmpty()) {
            slug = "jugador";
        }
        if (slug.length() > MAXIMUM_SLUG_LENGTH) {
            slug = slug.substring(0, MAXIMUM_SLUG_LENGTH);
        }
        return "player-" + slug;
    }

    public static List<String> distinctIds(List<String> displayNames) {
        List<String> ids = new ArrayList<>();
        for (String displayName : displayNames) {
            String base = idFor(displayName);
            String candidate = base;
            int suffix = 2;
            while (ids.contains(candidate)) {
                candidate = base + "-" + suffix++;
            }
            ids.add(candidate);
        }
        return ids;
    }
}
