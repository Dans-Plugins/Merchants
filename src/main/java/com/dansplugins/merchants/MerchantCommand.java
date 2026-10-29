package com.dansplugins.merchants;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@code /merchant create|delete|list}. Each subcommand has its own permission node.
 */
public final class MerchantCommand implements TabExecutor {

    static final String CREATE_PERMISSION = "merchants.create";
    static final String DELETE_PERMISSION = "merchants.delete";
    static final String LIST_PERMISSION = "merchants.list";

    static final String USAGE = "Usage: /merchant create <name>, /merchant delete <name>, /merchant list";

    private final MerchantService service;
    private final MerchantRegistry registry;
    private final Logger logger;

    public MerchantCommand(MerchantService service, MerchantRegistry registry, Logger logger) {
        this.service = service;
        this.registry = registry;
        this.logger = logger;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(USAGE);
            return true;
        }
        // Merchant names may contain spaces, so everything after the subcommand is the name.
        String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).strip();
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> create(sender, name);
            case "delete" -> delete(sender, name);
            case "list" -> list(sender);
            default -> sender.sendMessage("Unknown subcommand '" + args[0] + "'. " + USAGE);
        }
        return true;
    }

    private void create(CommandSender sender, String name) {
        if (!sender.hasPermission(CREATE_PERMISSION)) {
            sender.sendMessage("You do not have permission to create merchants (" + CREATE_PERMISSION + ").");
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only a player can create a merchant, because it is placed where the player stands.");
            return;
        }
        if (name.isEmpty()) {
            sender.sendMessage("Usage: /merchant create <name>");
            return;
        }
        try {
            Merchant merchant = service.create(player.getLocation(), name);
            sender.sendMessage("Created merchant '" + merchant.name() + "'.");
        } catch (IllegalArgumentException e) {
            sender.sendMessage(e.getMessage());
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save merchants after creating '" + name + "'", e);
            sender.sendMessage("Merchant '" + name + "' was created but could not be saved; see the server log.");
        }
    }

    private void delete(CommandSender sender, String name) {
        if (!sender.hasPermission(DELETE_PERMISSION)) {
            sender.sendMessage("You do not have permission to delete merchants (" + DELETE_PERMISSION + ").");
            return;
        }
        if (name.isEmpty()) {
            sender.sendMessage("Usage: /merchant delete <name>");
            return;
        }
        try {
            Merchant merchant = service.delete(name);
            sender.sendMessage("Deleted merchant '" + merchant.name() + "'.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            sender.sendMessage(e.getMessage());
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save merchants after deleting '" + name + "'", e);
            sender.sendMessage("Merchant '" + name + "' was deleted but the change could not be saved; see the server log.");
        }
    }

    private void list(CommandSender sender) {
        if (!sender.hasPermission(LIST_PERMISSION)) {
            sender.sendMessage("You do not have permission to list merchants (" + LIST_PERMISSION + ").");
            return;
        }
        List<Merchant> merchants = registry.all();
        if (merchants.isEmpty()) {
            sender.sendMessage("There are no merchants.");
            return;
        }
        sender.sendMessage("Merchants (" + merchants.size() + "):");
        for (Merchant merchant : merchants) {
            sender.sendMessage("- " + merchant.name() + " in " + merchant.worldName() + " at "
                    + (int) Math.floor(merchant.x()) + ", " + (int) Math.floor(merchant.y()) + ", "
                    + (int) Math.floor(merchant.z()));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            addIfAllowed(completions, sender, prefix, "create", CREATE_PERMISSION);
            addIfAllowed(completions, sender, prefix, "delete", DELETE_PERMISSION);
            addIfAllowed(completions, sender, prefix, "list", LIST_PERMISSION);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("delete") && sender.hasPermission(DELETE_PERMISSION)) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (Merchant merchant : registry.all()) {
                if (merchant.name().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    completions.add(merchant.name());
                }
            }
        }
        return completions;
    }

    private static void addIfAllowed(List<String> completions, CommandSender sender, String prefix,
                                     String subcommand, String permission) {
        if (subcommand.startsWith(prefix) && sender.hasPermission(permission)) {
            completions.add(subcommand);
        }
    }
}
