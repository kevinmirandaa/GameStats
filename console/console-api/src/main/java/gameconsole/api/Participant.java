package gameconsole.api;

public record Participant(String playerId, String displayName, String kind) {

    public static final String HUMAN = "HUMAN";

    public static final String COMPUTER = "COMPUTER";
}
