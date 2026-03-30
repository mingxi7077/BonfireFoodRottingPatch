package com.puddingkc;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class BfrCommand implements CommandExecutor {

    private static final Pattern HEX_INLINE = Pattern.compile("(?i)&#([0-9A-F]{6})");
    private final BfrMain plugin;

    public BfrCommand(BfrMain plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("bonfire.food.reload")) {
            sender.sendMessage(colorize("&cYou don't have permission."));
            return true;
        }

        plugin.loadConfig();
        sender.sendMessage(colorize("&x&B&7&C&5&A&3[BonfireFoodRotting] &x&A&6&9&D&8&A配置已重载。"));
        return true;
    }

    private static String colorize(String input) {
        if (input == null) {
            return null;
        }
        String expanded = expandInlineHex(input);
        return ChatColor.translateAlternateColorCodes('&', expanded);
    }

    private static String expandInlineHex(String input) {
        Matcher matcher = HEX_INLINE.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1).toUpperCase(Locale.ROOT);
            String replacement = "&x"
                    + "&" + hex.charAt(0)
                    + "&" + hex.charAt(1)
                    + "&" + hex.charAt(2)
                    + "&" + hex.charAt(3)
                    + "&" + hex.charAt(4)
                    + "&" + hex.charAt(5);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
