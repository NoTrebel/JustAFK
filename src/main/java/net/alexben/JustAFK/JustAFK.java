package net.alexben.JustAFK;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class JustAFK extends JavaPlugin implements CommandExecutor, Listener {
    public static ConfigAccessor language;

    @Override
    public void onEnable() {
        new JUtility(this);

        // Save the default config
        saveDefaultConfig();

        // Setup threads for checking player movement
        Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(this, JUtility::checkActivity, 0, getConfig().
                getInt("movementcheckfreq") * 20);

        // Check for CommandBook
        if (Bukkit.getPluginManager().getPlugin("CommandBook") != null) {
            getLogger().warning("CommandBook has been detected.");
            getLogger().warning("Please ensure that the CommandBook AFK component has been disabled.");
            getLogger().warning("If this hasn't been done, JustAFK will not work.");
        }

        // Register the listeners
        getServer().getPluginManager().registerEvents(this, this);

        // Register and load commands
        getCommand("afk").setExecutor(this);
        getCommand("justafk").setExecutor(this);
        getCommand("whosafk").setExecutor(this);
        getCommand("setafk").setExecutor(this);

        // Start metrics
        try {
            Metrics metrics = new Metrics(this);
            metrics.start();
        } catch (IOException e) {
            // Metrics failed to load, log it
            getLogger().warning("Plugins metrics failed to load.");
        }

        // Load the strings/localization
        language = new ConfigAccessor(this, "localization.yml");

        // Register all currently online players
        for (Player player : getServer().getOnlinePlayers()) {
            JUtility.saveData(player, "isafk", false);
            JUtility.saveData(player, "iscertain", true);
            JUtility.saveData(player, "lastactive", System.currentTimeMillis());

            if (getConfig().getBoolean("hideawayplayers")) {
                JUtility.getAwayPlayers(true).forEach(player::hidePlayer);
            }
        }

        // Log that JustAFK successfully loaded
        getLogger().info("JustAFK has been successfully enabled!");
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
        HandlerList.unregisterAll((Plugin) this);
        getLogger().info("JustAFK has been disabled!");
    }

    /**
     * Handle Commands
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = (Player) sender;

        if (JUtility.hasPermissionOrOP(player, "justafk.basic")) {
            if (command.getName().equalsIgnoreCase("afk")) {
                if (JUtility.isAway(player)) {
                    JUtility.setAway(player, false, true);
                    JUtility.sendMessage(player, ChatColor.AQUA + StringEscapeUtils.unescapeJava(JustAFK.language.
                            getConfig().getString("private_return")));

                    return true;
                }

                // If they included an away message then set it BEFORE setting away.
                if (args.length > 0) {
                    String msg = StringUtils.join(args, " ");

                    JUtility.setAwayMessage(player, msg);
                }

                // Now set away status
                JUtility.setAway(player, true, true);

                // Send the messages.
                JUtility.sendMessage(player, ChatColor.AQUA + StringEscapeUtils.unescapeJava(JustAFK.language.
                        getConfig().getString("private_away")));

                return true;
            } else if (command.getName().equalsIgnoreCase("whosafk")) {
                if (JUtility.getAwayPlayers(true).isEmpty()) {
                    JUtility.sendMessage(player, StringEscapeUtils.unescapeJava(JustAFK.language.getConfig().
                            getString("nobody_away")));
                    return true;
                }

                List<String> playerNames = JUtility.getAwayPlayers(true).stream().map(Player::getName).
                        collect(Collectors.toList());

                JUtility.sendMessage(player, ChatColor.AQUA + StringEscapeUtils.unescapeJava(JustAFK.language.
                        getConfig().getString("currently_away")) + " " + StringUtils.join(playerNames, ", "));

                return true;
            } else if (command.getName().equalsIgnoreCase("setafk") && JUtility.hasPermissionOrOP(player,
                    "justafk.admin")) {
                Player editing = Bukkit.getPlayer(args[0]);

                if (editing != null) {
                    if (!JUtility.isAway(editing)) {
                        JUtility.setAway(editing, true, true);
                        JUtility.sendMessage(editing, ChatColor.GRAY + "" + ChatColor.ITALIC +
                                StringEscapeUtils.unescapeJava(JustAFK.language.getConfig().
                                        getString("setafk_away_private").replace("{name}", player.getDisplayName())));
                    } else {
                        JUtility.setAway(editing, false, true);
                        JUtility.sendMessage(editing, ChatColor.GRAY + "" + ChatColor.ITALIC +
                                StringEscapeUtils.unescapeJava(JustAFK.language.getConfig().
                                        getString("setafk_return_private").replace("{name}", player.getDisplayName())));
                    }

                    return true;
                } else {
                    player.sendMessage(ChatColor.RED + "/setafk <player>");
                }
            } else if (command.getName().equalsIgnoreCase("justafk")) {
                player.sendMessage(ChatColor.AQUA + "JustAFK" + ChatColor.GRAY + " is a plugin created for Bukkit intended for the use");
                player.sendMessage(ChatColor.GRAY + "of simple - yet powerful - away messages and other features");
                player.sendMessage(ChatColor.GRAY + "within Minecraft survival multiplayer.");
                player.sendMessage("");
                player.sendMessage(ChatColor.GRAY + "Author: " + ChatColor.AQUA + "_Alex");
                player.sendMessage(ChatColor.GRAY + "Source: " + ChatColor.AQUA +
                        "http://github.com/alexbennett/Minecraft-JustAFK");

                return true;
            }
        }

        return false;
    }

    /**
     * Handle Listening
     */
    @EventHandler(priority = EventPriority.MONITOR)
    private void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        JUtility.saveData(player, "isafk", false);
        JUtility.saveData(player, "iscertain", true);
        JUtility.saveData(player, "lastactive", System.currentTimeMillis());

        if (getConfig().getBoolean("hideawayplayers")) {
            JUtility.getAwayPlayers(true).forEach(player::hidePlayer);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        boolean certain = Boolean.parseBoolean(JUtility.getData(player, "iscertain").toString());
        boolean yawChange = event.getFrom().getYaw() != event.getTo().getYaw();
        boolean pitchChange = event.getFrom().getPitch() != event.getTo().getPitch();

        if (JUtility.isAway(player)) {
            if (certain) {
                JUtility.setAway(player, false, true);
                JUtility.sendMessage(player, ChatColor.AQUA + StringEscapeUtils.unescapeJava(JustAFK.language.
                        getConfig().getString("private_return")));
            } else {
                if (!player.isInsideVehicle()) {
                    if (pitchChange) {
                        JUtility.setAway(player, false, true);
                        JUtility.sendMessage(player, ChatColor.AQUA + StringEscapeUtils.unescapeJava(JustAFK.language.
                                getConfig().getString("private_return")));
                        return;
                    }
                }

                if (yawChange || pitchChange) {
                    JUtility.setAway(player, false, false);
                    JUtility.sendMessage(player, ChatColor.AQUA + StringEscapeUtils.unescapeJava(JustAFK.language.
                            getConfig().getString("private_return")));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        JUtility.saveData(player, "lastactive", System.currentTimeMillis());

        if (JUtility.isAway(player)) {
            JUtility.setAway(player, false, Boolean.parseBoolean(JUtility.getData(player, "iscertain").toString()));
            JUtility.sendMessage(player, ChatColor.AQUA + StringEscapeUtils.unescapeJava(JustAFK.language.getConfig().getString("private_return")));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onInventoryClick(InventoryClickEvent event) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(event.getWhoClicked().getName());

        JUtility.saveData(player, "lastactive", System.currentTimeMillis());

        assert player.isOnline() : player.getName() + " is not online.";
        if (JUtility.isAway(player.getPlayer())) {
            JUtility.setAway(player.getPlayer(), false, Boolean.parseBoolean(JUtility.getData(player, "iscertain").toString()));
            JUtility.sendMessage(player.getPlayer(), ChatColor.AQUA + StringEscapeUtils.unescapeJava(JustAFK.language.getConfig().getString("private_return")));
        }
    }
}
