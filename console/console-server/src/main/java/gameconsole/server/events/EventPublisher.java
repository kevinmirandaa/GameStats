package gameconsole.server.events;

import java.util.Map;

public interface EventPublisher {

    void publish(Map<String, Object> event);
}
