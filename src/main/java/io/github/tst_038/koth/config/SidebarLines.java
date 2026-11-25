package io.github.tst_038.koth.config;

import java.util.List;

/** The sidebar texts, in MiniMessage with {@code %placeholders}: one list for while KOTH is not running, one for
 * while it waits for players, and one for while it runs. The waiting lines also have the placeholders of the refusal, such as {@code %online%}. */
public record SidebarLines(String title, List<String> inactive, List<String> waiting, List<String> active) {

    public SidebarLines {
        inactive = List.copyOf(inactive);
        waiting = List.copyOf(waiting);
        active = List.copyOf(active);
    }
}
