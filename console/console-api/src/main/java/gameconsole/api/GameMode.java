package gameconsole.api;

public record GameMode(String id, String label, int playerCount, boolean playersAreHuman) {
}
