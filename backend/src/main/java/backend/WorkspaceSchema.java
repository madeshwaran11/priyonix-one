package backend;

import java.util.List;

/** A closed, server-owned contract for one business record type. */
public record WorkspaceSchema(String key, String label, String group, String description,
        List<Field> fields, List<String> columns, List<String> states, String initialState,
        List<Transition> transitions) {
    public record Field(String key, String label, String type, boolean required,
            List<String> options, String reference) {}
    public record Transition(String from, String to, String label, boolean adminOnly) {}
    public Transition transition(String from, String to) {
        return transitions.stream().filter(t -> t.from().equals(from) && t.to().equals(to)).findFirst().orElse(null);
    }
}
