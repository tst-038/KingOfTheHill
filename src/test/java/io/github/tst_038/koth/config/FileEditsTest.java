package io.github.tst_038.koth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.config.ConfigProblem;
import io.github.tst_038.minigami.api.message.Messages;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileEditsTest {

    private static final String FILE = "config.yml";
    private static final String TEXT = "# The hill\npoint:\n  # Seconds to take it\n  capture-seconds: 5\n";

    @TempDir
    Path folder;
    private final List<Audience> reloads = new ArrayList<>();
    private FileEdits edits;

    @BeforeEach
    void setUp() throws Exception {
        Files.writeString(folder.resolve(FILE), TEXT);
        var logger = Logger.getAnonymousLogger();
        edits = new FileEdits(folder, new Messages(new YamlConfiguration(), logger), Runnable::run, Runnable::run, reloads::add, logger);
    }

    @Test
    void aChangeKeepsTheCommentsAndReloads() throws Exception {
        edits.editAndReload(Audience.empty(), FILE, file -> file.set("point.capture-seconds", 9), "done");

        assertEquals(TEXT.replace("5", "9"), Files.readString(folder.resolve(FILE)));
        assertEquals(1, reloads.size());
    }

    @Test
    void aRefusedChangeLeavesTheFileAsItWas() throws Exception {
        boolean written = edits.edit(Audience.empty(), FILE, file -> {
            file.set("point.capture-seconds", 9);
            throw new FileEdits.Refusal("setup.wrong-world");
        }, file -> List.of()).join();

        assertFalse(written);
        assertEquals(TEXT, Files.readString(folder.resolve(FILE)));
        assertTrue(reloads.isEmpty());
    }

    @Test
    void aChangeWithProblemsIsNotWritten() throws Exception {
        boolean written = edits.edit(Audience.empty(), FILE, file -> file.set("point.capture-seconds", 0),
                file -> List.of(new ConfigProblem(FILE, "point.capture-seconds", "must be at least 1"))).join();

        assertFalse(written);
        assertEquals(TEXT, Files.readString(folder.resolve(FILE)));
    }
}
