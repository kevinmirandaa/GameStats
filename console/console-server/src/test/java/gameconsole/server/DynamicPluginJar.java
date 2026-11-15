package gameconsole.server;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

public final class DynamicPluginJar {

    private static final String SOURCE = """
            package fixture.echo;

            import gameconsole.api.*;
            import java.util.List;
            import java.util.Map;

            public final class EchoModule implements GameModule {
                public String gameId() { return "echo"; }
                public String gameVersion() { return "1.0"; }
                public String title() { return "Eco"; }
                public String description() { return "Juego de prueba cargado dinamicamente"; }
                public List<GameMode> modes() { return List.of(new GameMode("SOLO", "Un jugador", 1, true)); }
                public List<GameOption> options() { return List.of(); }
                public GameSession createSession(SessionRequest request, SessionServices services) {
                    return new EchoSession(request);
                }

                static final class EchoSession extends ObservableGameSession {
                    private final PlayerSeat seat;
                    private int touches;

                    EchoSession(SessionRequest request) {
                        super("echo", "1.0", request.matchId());
                        this.seat = request.players().get(0);
                    }

                    protected void onStart() {
                        beginMatch(Map.of("mode", "SOLO"), List.of(new Participant(seat.playerId(), seat.displayName(), Participant.HUMAN)));
                    }

                    protected GameView buildView() {
                        return new GameView(matchId(), gameId(), "Eco", 1, 1,
                                List.of(new GameView.CellView(String.valueOf(touches), "plain", !finished())),
                                List.of(new GameView.PlayerView(seat.playerId(), seat.displayName(), "HUMAN", touches + " toques")),
                                finished() ? "" : seat.playerId(), "Toques: " + touches, "", finished(),
                                null, List.of());
                    }

                    protected ActionResult handle(String playerId, Map<String, Object> action) {
                        if (!seat.playerId().equals(playerId)) {
                            return ActionResult.rejected("Jugador desconocido");
                        }
                        touches++;
                        announceAction(playerId, "TOUCH", Map.of("touches", touches));
                        if (Boolean.TRUE.equals(action.get("finish"))) {
                            announceAction(playerId, "PLAYER_WON", Map.of("touches", touches));
                            finishMatch(Map.of("result", "WIN", "winnerPlayerId", playerId));
                        }
                        return ActionResult.ok();
                    }
                }
            }
            """;

    private DynamicPluginJar() {
    }

    public static Path create(Path pluginsDirectory, String jarName) throws IOException {
        Files.createDirectories(pluginsDirectory);
        Path classes = Files.createTempDirectory("echo-classes");
        compile(classes);
        Path jar = pluginsDirectory.resolve(jarName);
        try (OutputStream output = Files.newOutputStream(jar); JarOutputStream archive = new JarOutputStream(output)) {
            archive.putNextEntry(new JarEntry("META-INF/services/gameconsole.api.GameModule"));
            archive.write("fixture.echo.EchoModule\n".getBytes(StandardCharsets.UTF_8));
            archive.closeEntry();
            try (Stream<Path> files = Files.walk(classes)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    archive.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
                    archive.write(Files.readAllBytes(file));
                    archive.closeEntry();
                }
            }
        }
        return jar;
    }

    public static Path createWithoutModule(Path pluginsDirectory, String jarName) throws IOException {
        Files.createDirectories(pluginsDirectory);
        Path jar = pluginsDirectory.resolve(jarName);
        try (OutputStream output = Files.newOutputStream(jar); JarOutputStream archive = new JarOutputStream(output)) {
            archive.putNextEntry(new JarEntry("readme.txt"));
            archive.write("sin modulo".getBytes(StandardCharsets.UTF_8));
            archive.closeEntry();
        }
        return jar;
    }

    private static void compile(Path outputDirectory) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        JavaFileObject source = new SimpleJavaFileObject(URI.create("string:///fixture/echo/EchoModule.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return SOURCE;
            }
        };
        List<String> options = List.of("-d", outputDirectory.toString(), "-classpath", System.getProperty("java.class.path"));
        Boolean compiled = compiler.getTask(null, null, null, options, null, List.of(source)).call();
        if (!Boolean.TRUE.equals(compiled)) {
            throw new IOException("No se pudo compilar el plugin de prueba");
        }
    }
}
