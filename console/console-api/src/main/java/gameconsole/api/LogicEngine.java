package gameconsole.api;

import java.util.Map;

public interface LogicEngine {

    Map<String, Object> ask(String queryName, Map<String, Object> arguments) throws LogicUnavailableException;
}
