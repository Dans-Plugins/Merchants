package com.dansplugins.merchants;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers parsing, permissions and messages. Creating a merchant from a player is not covered: a
 * stand-in Player cannot be built without a running server (see MerchantServiceTest).
 */
class MerchantCommandTest {

    @TempDir
    Path dir;

    private final MerchantRegistry registry = new MerchantRegistry();
    private final List<String> messages = new ArrayList<>();

    /** A console-like sender that holds the given permissions and records what it is sent. */
    private CommandSender sender(String... permissions) {
        Set<String> held = Set.of(permissions);
        return (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("sendMessage") && args.length == 1 && args[0] instanceof String message) {
                        messages.add(message);
                        return null;
                    }
                    if (method.getName().equals("hasPermission") && args[0] instanceof String permission) {
                        return held.contains(permission);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private MerchantCommand command() {
        MerchantStorage storage = new MerchantStorage(dir.resolve("merchants.yml").toFile(), Logger.getAnonymousLogger());
        return command(storage, merchant -> Optional.empty(), Logger.getAnonymousLogger());
    }

    private MerchantCommand command(MerchantStorage storage, MerchantService.VillagerLocator locator, Logger logger) {
        MerchantService service = new MerchantService(registry, storage, locator);
        return new MerchantCommand(service, registry, logger);
    }

    /** A logger that records what it is given instead of printing it. */
    private static Logger recordingLogger(List<LogRecord> records) {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return logger;
    }

    private void run(CommandSender sender, String... args) {
        assertTrue(command().onCommand(sender, null, "merchant", args));
    }

    @Test
    void noArgumentsShowsUsage() {
        run(sender());
        assertEquals(List.of(MerchantCommand.USAGE), messages);
    }

    @Test
    void unknownSubcommandShowsUsage() {
        run(sender(), "sell");
        assertEquals(List.of("Unknown subcommand 'sell'. " + MerchantCommand.USAGE), messages);
    }

    @Test
    void listShowsEveryMerchantSortedByName() {
        registry.add(new Merchant(UUID.randomUUID(), "Smith", "world", 10.7, 64, -3.2));
        registry.add(new Merchant(UUID.randomUUID(), "Baker", "world_nether", 0.5, 70, 0.5));

        run(sender(MerchantCommand.LIST_PERMISSION), "LIST");

        assertEquals(List.of(
                "Merchants (2):",
                "- Baker in world_nether at 0, 70, 0",
                "- Smith in world at 10, 64, -4"), messages);
    }

    @Test
    void listWithNoMerchantsSaysSo() {
        run(sender(MerchantCommand.LIST_PERMISSION), "list");
        assertEquals(List.of("There are no merchants."), messages);
    }

    @Test
    void listNeedsPermission() {
        registry.add(new Merchant(UUID.randomUUID(), "Baker", "world", 0, 64, 0));
        run(sender(), "list");
        assertEquals(List.of("You do not have permission to list merchants (merchants.list)."), messages);
    }

    @Test
    void createNeedsPermission() {
        run(sender(), "create", "Baker");
        assertEquals(List.of("You do not have permission to create merchants (merchants.create)."), messages);
    }

    @Test
    void createFromTheConsoleIsRejected() {
        run(sender(MerchantCommand.CREATE_PERMISSION), "create", "Baker");
        assertEquals(List.of("Only a player can create a merchant, because it is placed where the player stands."),
                messages);
        assertTrue(registry.all().isEmpty());
    }

    @Test
    void deleteTakesANameWithSpaces() {
        registry.add(new Merchant(UUID.randomUUID(), "Old Baker", "world", 0, 64, 0));

        run(sender(MerchantCommand.DELETE_PERMISSION), "delete", "old", "baker");

        assertEquals(List.of("Deleted merchant 'Old Baker'."), messages);
        assertFalse(registry.isNameTaken("Old Baker"));
    }

    @Test
    void deleteOfAnUnknownNameSaysSo() {
        run(sender(MerchantCommand.DELETE_PERMISSION), "delete", "Baker");
        assertEquals(List.of("There is no merchant named 'Baker'."), messages);
    }

    @Test
    void deleteWithoutANameShowsUsage() {
        run(sender(MerchantCommand.DELETE_PERMISSION), "delete");
        assertEquals(List.of("Usage: /merchant delete <name>"), messages);
    }

    @Test
    void deleteNeedsPermission() {
        registry.add(new Merchant(UUID.randomUUID(), "Baker", "world", 0, 64, 0));

        run(sender(), "delete", "Baker");

        assertEquals(List.of("You do not have permission to delete merchants (merchants.delete)."), messages);
        assertTrue(registry.isNameTaken("Baker"));
    }

    @Test
    void deleteInAnUnloadedAreaRelaysTheReasonAndKeepsTheMerchant() {
        registry.add(new Merchant(UUID.randomUUID(), "Baker", "world", 0, 64, 0));
        MerchantStorage storage = new MerchantStorage(dir.resolve("merchants.yml").toFile(), Logger.getAnonymousLogger());
        MerchantCommand command = command(storage, merchant -> {
            throw new IllegalStateException("Merchant 'Baker' is in an unloaded area. Go there and try again.");
        }, Logger.getAnonymousLogger());

        assertTrue(command.onCommand(sender(MerchantCommand.DELETE_PERMISSION), null, "merchant",
                new String[]{"delete", "Baker"}));

        assertEquals(List.of("Merchant 'Baker' is in an unloaded area. Go there and try again."), messages);
        assertTrue(registry.isNameTaken("Baker"));
        assertFalse(dir.resolve("merchants.yml").toFile().exists(), "nothing should be saved");
    }

    @Test
    void deleteThatCannotBeSavedSaysSoAndLogs() {
        registry.add(new Merchant(UUID.randomUUID(), "Baker", "world", 0, 64, 0));
        // Saving onto a directory fails with an IOException.
        MerchantStorage storage = new MerchantStorage(dir.toFile(), Logger.getAnonymousLogger());
        List<LogRecord> records = new ArrayList<>();
        MerchantCommand command = command(storage, merchant -> Optional.empty(), recordingLogger(records));

        assertTrue(command.onCommand(sender(MerchantCommand.DELETE_PERMISSION), null, "merchant",
                new String[]{"delete", "baker"}));

        // The message echoes the name as typed, not the merchant's stored name.
        assertEquals(List.of("Merchant 'baker' was deleted but the change could not be saved; see the server log."),
                messages);
        assertFalse(registry.isNameTaken("Baker"), "the merchant is removed even though the save failed");
        assertEquals(1, records.size());
        assertEquals(Level.SEVERE, records.get(0).getLevel());
        assertEquals("Could not save merchants after deleting 'baker'", records.get(0).getMessage());
        assertInstanceOf(IOException.class, records.get(0).getThrown());
    }

    @Test
    void tabCompletesNothingPastTheName() {
        registry.add(new Merchant(UUID.randomUUID(), "Baker", "world", 0, 64, 0));
        CommandSender sender = sender(MerchantCommand.CREATE_PERMISSION, MerchantCommand.DELETE_PERMISSION);

        assertEquals(List.of(), command().onTabComplete(sender, null, "merchant", new String[]{"create", "b"}));
        assertEquals(List.of(), command().onTabComplete(sender, null, "merchant", new String[]{"delete", "Baker", ""}));
        assertEquals(List.of(), command().onTabComplete(sender, null, "merchant", new String[]{"x"}));
    }

    @Test
    void tabCompletesOnlyPermittedSubcommands() {
        assertEquals(List.of("create", "list"), command().onTabComplete(
                sender(MerchantCommand.CREATE_PERMISSION, MerchantCommand.LIST_PERMISSION), null, "merchant",
                new String[]{""}));
        assertEquals(List.of("delete"), command().onTabComplete(
                sender(MerchantCommand.DELETE_PERMISSION), null, "merchant", new String[]{"D"}));
    }

    @Test
    void tabCompletesMerchantNamesForDelete() {
        registry.add(new Merchant(UUID.randomUUID(), "Baker", "world", 0, 64, 0));
        registry.add(new Merchant(UUID.randomUUID(), "Butcher", "world", 0, 64, 0));
        registry.add(new Merchant(UUID.randomUUID(), "Smith", "world", 0, 64, 0));

        assertEquals(List.of("Baker", "Butcher"), command().onTabComplete(
                sender(MerchantCommand.DELETE_PERMISSION), null, "merchant", new String[]{"delete", "b"}));
        assertEquals(List.of(), command().onTabComplete(
                sender(), null, "merchant", new String[]{"delete", "b"}));
    }
}
