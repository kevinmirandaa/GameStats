package gameconsole.server.plugins;

import gameconsole.api.GameModule;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class GameCatalog {

    private final Map<String, PluginLoader.LoadedPlugin> pluginsByGameId = new LinkedHashMap<>();
    private final List<PluginLoader.LoadFailure> failures = new ArrayList<>();

    public GameCatalog(PluginLoader.LoadReport report) {
        failures.addAll(report.failures());
        for (PluginLoader.LoadedPlugin plugin : report.plugins()) {
            String gameId = plugin.module().gameId();
            if (pluginsByGameId.containsKey(gameId)) {
                failures.add(new PluginLoader.LoadFailure(plugin.jarName(), "gameId duplicado: " + gameId));
            } else {
                pluginsByGameId.put(gameId, plugin);
            }
        }
    }

    public List<GameModule> modules() {
        return pluginsByGameId.values().stream().map(PluginLoader.LoadedPlugin::module).toList();
    }

    public Optional<GameModule> find(String gameId) {
        return Optional.ofNullable(pluginsByGameId.get(gameId)).map(PluginLoader.LoadedPlugin::module);
    }

    public List<PluginLoader.LoadedPlugin> plugins() {
        return List.copyOf(pluginsByGameId.values());
    }

    public List<PluginLoader.LoadFailure> failures() {
        return List.copyOf(failures);
    }
}
