package gameconsole.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gameconsole.api.GameModule;
import gameconsole.server.plugins.GameCatalog;
import gameconsole.server.plugins.PluginLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginLoaderTest {

    @TempDir
    Path plugins;

    @Test
    void aGameJarThatTheConsoleNeverSawIsLoadedFromTheFolder() throws IOException {
        DynamicPluginJar.create(plugins, "echo.jar");
        PluginLoader loader = new PluginLoader(plugins, getClass().getClassLoader());

        PluginLoader.LoadReport report = loader.load();

        assertEquals(1, report.plugins().size());
        GameModule module = report.plugins().get(0).module();
        assertEquals("echo", module.gameId());
        assertEquals("echo.jar", report.plugins().get(0).jarName());
        assertTrue(report.failures().isEmpty());
        assertFalse(module.getClass().getClassLoader() == getClass().getClassLoader());
        loader.close();
    }

    @Test
    void aMissingFolderProducesAnEmptyReportWithoutFailing() {
        PluginLoader loader = new PluginLoader(plugins.resolve("does-not-exist"), getClass().getClassLoader());

        PluginLoader.LoadReport report = loader.load();

        assertTrue(report.plugins().isEmpty());
        assertTrue(report.failures().isEmpty());
    }

    @Test
    void aJarWithoutAGameModuleIsReportedAndTheOthersStillLoad() throws IOException {
        DynamicPluginJar.createWithoutModule(plugins, "broken.jar");
        DynamicPluginJar.create(plugins, "echo.jar");
        PluginLoader loader = new PluginLoader(plugins, getClass().getClassLoader());

        PluginLoader.LoadReport report = loader.load();

        assertEquals(1, report.plugins().size());
        assertEquals(1, report.failures().size());
        assertEquals("broken.jar", report.failures().get(0).jarName());
        loader.close();
    }

    @Test
    void aCorruptJarIsReportedAsAFailure() throws IOException {
        Files.writeString(plugins.resolve("corrupt.jar"), "esto no es un jar");
        PluginLoader loader = new PluginLoader(plugins, getClass().getClassLoader());

        PluginLoader.LoadReport report = loader.load();

        assertTrue(report.plugins().isEmpty());
        assertEquals(1, report.failures().size());
        loader.close();
    }

    @Test
    void nonJarFilesAreIgnored() throws IOException {
        Files.writeString(plugins.resolve("notes.txt"), "nada");
        PluginLoader loader = new PluginLoader(plugins, getClass().getClassLoader());

        PluginLoader.LoadReport report = loader.load();

        assertTrue(report.plugins().isEmpty());
        assertTrue(report.failures().isEmpty());
    }

    @Test
    void theCatalogRejectsADuplicatedGameId() throws IOException {
        DynamicPluginJar.create(plugins, "echo-a.jar");
        DynamicPluginJar.create(plugins, "echo-b.jar");
        PluginLoader loader = new PluginLoader(plugins, getClass().getClassLoader());

        GameCatalog catalog = new GameCatalog(loader.load());

        assertEquals(1, catalog.modules().size());
        assertEquals(1, catalog.failures().size());
        assertTrue(catalog.find("echo").isPresent());
        assertTrue(catalog.find("other").isEmpty());
        loader.close();
    }
}
