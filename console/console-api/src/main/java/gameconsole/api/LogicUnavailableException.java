package gameconsole.api;

public class LogicUnavailableException extends Exception {

    public LogicUnavailableException(String message) {
        super(message);
    }

    public LogicUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
