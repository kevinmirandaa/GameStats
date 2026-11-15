package gameconsole.api;

import java.util.List;

public record GameOption(String id, String label, List<String> choices, String defaultChoice) {
}
