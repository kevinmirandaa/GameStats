package gameconsole.server.plugins;

import gameconsole.api.GameModule;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

public final class PluginLoader {

    public record LoadedPlugin(String jarName, GameModule module) {
    }

    public record LoadFailure(String jarName, String reason) {
    }

    public record LoadReport(List<LoadedPlugin> plugins, List<LoadFailure> failures) {
    }

    private final Path directory;
    private final ClassLoader parent;
    private final List<URLClassLoader> openLoaders = new ArrayList<>();

    public PluginLoader(Path directory, ClassLoader parent) {
        this.directory = directory;
        this.parent = parent;
    }

    public synchronized LoadReport load() {
        List<LoadedPlugin> plugins = new ArrayList<>();
        List<LoadFailure> failures = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return new LoadReport(plugins, failures);
        }
        try (DirectoryStream<Path> jars = Files.newDirectoryStream(directory, "*.jar")) {
            List<Path> sorted = new ArrayList<>();
            jars.forEach(sorted::add);
            sorted.sort(Path::compareTo);
            for (Path jar : sorted) {
                loadJar(jar, plugins, failures);
            }
        } catch (IOException unreadable) {
            failures.add(new LoadFailure(directory.toString(), unreadable.getMessage()));
        }
        return new LoadReport(plugins, failures);
    }

    public synchronized void close() {
        for (URLClassLoader loader : openLoaders) {
            try {
                loader.close();
            } catch (IOException ignored) {
                continue;
            }
        }
        openLoaders.clear();
    }

    private void loadJar(Path jar, List<LoadedPlugin> plugins, List<LoadFailure> failures) {
        String jarName = jar.getFileName().toString();
        try {
            URL location = jar.toUri().toURL();
            URLClassLoader loader = new URLClassLoader(new URL[] {location}, parent);
            openLoaders.add(loader);
            int before = plugins.size();
            for (GameModule module : ServiceLoader.load(GameModule.class, loader)) {
                plugins.add(new LoadedPlugin(jarName, module));
            }
            if (plugins.size() == before) {
                failures.add(new LoadFailure(jarName, "El JAR no declara ningun GameModule"));
            }
        } catch (IOException | ServiceConfigurationError | RuntimeException failure) {
            failures.add(new LoadFailure(jarName, String.valueOf(failure.getMessage())));
        }
    }
}
