package io.github.tst_038.koth.config;

import io.github.tst_038.minigami.api.config.ConfigFiles;
import io.github.tst_038.minigami.api.config.ConfigProblem;
import io.github.tst_038.minigami.api.message.Messages;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Changes the plugin's files for commands, as MiniGami changes its own: the file is read, changed and written off the
 * server thread, keeping its comments, and the sender is told how it went on the server thread. Hand edits keep working,
 * since a change starts from the file as it is.
 */
public final class FileEdits {

    /** Thrown by a change to refuse it: the sender is told the message, and the file stays as it was. */
    public static final class Refusal extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final String key;
        private final transient TagResolver[] tags;

        /** @param key the message to send */
        public Refusal(String key, TagResolver... tags) {
            super(key, null, false, false);
            this.key = key;
            this.tags = tags;
        }
    }

    private final Path folder;
    private final Messages messages;
    private final Executor fileIo;
    private final Executor serverThread;
    private final Consumer<Audience> reload;
    private final Logger logger;

    /**
     * @param fileIo runs the file work, off the server thread
     * @param reload reloads the files and tells the audience how it went, as {@code /kotha reload} does
     */
    public FileEdits(Path folder, Messages messages, Executor fileIo, Executor serverThread, Consumer<Audience> reload, Logger logger) {
        this.folder = folder;
        this.messages = messages;
        this.fileIo = fileIo;
        this.serverThread = serverThread;
        this.reload = reload;
        this.logger = logger;
    }

    /**
     * Applies the change to the file and writes it, unless the change throws a {@link Refusal} or the check finds problems;
     * the sender is told either. Both run off the server thread, so they may only use what was read before.
     *
     * @param check the problems the changed file would have
     * @return completes on the server thread, with whether the file was written
     */
    public CompletableFuture<Boolean> edit(Audience sender, String file, Consumer<YamlConfiguration> change,
            Function<YamlConfiguration, List<ConfigProblem>> check) {
        var path = folder.resolve(file);
        var fileTag = Placeholder.unparsed("file", file);
        return CompletableFuture.supplyAsync(() -> {
            try {
                return ConfigFiles.update(path, change, check);
            } catch (IOException | InvalidConfigurationException e) {
                throw new CompletionException(e);
            }
        }, fileIo).handleAsync((problems, failure) -> {
            if (failure == null) {
                if (!problems.isEmpty()) {
                    messages.send(sender, "edit.not-saved", fileTag);
                    problems.forEach(problem -> messages.send(sender, "admin.reload-problem", Placeholder.unparsed("problem", problem.toString())));
                }
                return problems.isEmpty();
            }
            var cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
            if (cause instanceof Refusal refusal) {
                messages.send(sender, refusal.key, refusal.tags);
            } else {
                logger.log(Level.SEVERE, "Could not change " + file, cause);
                messages.send(sender, "edit.failed", fileTag);
            }
            return false;
        }, serverThread);
    }

    /** {@link #edit} without a check, then, once written, tells the sender {@code done} and reloads. */
    public void editAndReload(Audience sender, String file, Consumer<YamlConfiguration> change, String done, TagResolver... tags) {
        editAndReload(sender, file, change, config -> List.of(), done, tags);
    }

    /** {@link #edit}, then, once written, tells the sender {@code done} and reloads. */
    public void editAndReload(Audience sender, String file, Consumer<YamlConfiguration> change,
            Function<YamlConfiguration, List<ConfigProblem>> check, String done, TagResolver... tags) {
        edit(sender, file, change, check).thenAccept(written -> {
            if (written) {
                done(sender, done, tags);
            }
        });
    }

    /** Tells the sender {@code done} and reloads; on the server thread. */
    public void done(Audience sender, String done, TagResolver... tags) {
        messages.send(sender, done, tags);
        reload.accept(sender);
    }
}
