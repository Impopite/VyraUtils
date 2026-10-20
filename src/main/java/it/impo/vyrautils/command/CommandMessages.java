package it.impo.vyrautils.command;

import it.impo.vyrautils.lang.MessageKey;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * The two language keys every command needs, passed once to the
 * {@link CommandService} instead of being repeated in each command class.
 *
 * @param noPermission  message sent when a permission check fails
 * @param consoleCantDo message sent when a player-only command is used by the console
 */
public record CommandMessages(@NotNull MessageKey noPermission, @NotNull MessageKey consoleCantDo) {

    public CommandMessages {
        Objects.requireNonNull(noPermission, "noPermission");
        Objects.requireNonNull(consoleCantDo, "consoleCantDo");
    }
}
