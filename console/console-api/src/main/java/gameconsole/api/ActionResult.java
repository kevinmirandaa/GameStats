package gameconsole.api;

public record ActionResult(boolean accepted, String message) {

    public static ActionResult ok() {
        return new ActionResult(true, "");
    }

    public static ActionResult rejected(String reason) {
        return new ActionResult(false, reason);
    }
}
