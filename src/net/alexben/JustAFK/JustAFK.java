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
import java.util.Optional;
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
            getLogger().warning("If this hasn't been done, " + this.getDescription().getName() + " will not work.");
        }

        // Register the listeners
        getServer().getPluginManager().registerEvents(this, this);

        // Register and load commands
        getCommand("afk").setExecutor(this);
        getCommand("justafk").setExecutor(this);
        getCommand("whosafk").setExecutor(this);
        getCommand("whoisafk").setExecutor(this);
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
        String langFile = ("localization.{lang}.yml").replace("{lang}", getConfig().getString("lang"));
        language = new ConfigAccessor(this, langFile);

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
        getLogger().info(this.getDescription().getName() + " " + this.getDescription().getVersion() + " Enabled!");
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
        HandlerList.unregisterAll((Plugin) this);
        getLogger().info(this.getDescription().getName() + " Disabled!");
    }

    protected boolean onAfkCommand(CommandSender sender, String[] args) {
    	
    	if (sender instanceof Player) {
			Player player = (Player) sender;
			if (!player.hasPermission("justafk.afk")) {
				sender.sendMessage(ChatColor.RED + JustAFK.language.
	                    getConfig().getString("no_permission"));
				return true;
			}
		
    	
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

    	}
    	else {
    		JUtility.sendMessage(sender, ChatColor.RED + "This command can only be used in-game!");
    	}
        return true;
    	
        

    }
    
    protected boolean onWhosafkCommand(CommandSender sender, String[] args) {
    	
    	if (sender instanceof Player) {
			Player player = (Player) sender;
			if (!player.hasPermission("justafk.whosafk") && !player.hasPermission("justafk.whoisafk")) {
				JUtility.sendMessage(sender, ChatColor.RED + JustAFK.language.
	                    getConfig().getString("no_permission"));
								
				return true;
			}
		}
    	
    	if (JUtility.getAwayPlayers(true).isEmpty()) {
            JUtility.sendMessage(sender, StringEscapeUtils.unescapeJava(JustAFK.language.getConfig().
                    getString("nobody_away")));
            return true;
        }

        List<String> playerNames = JUtility.getAwayPlayers(true).stream().map(Player::getName).
                collect(Collectors.toList());

        JUtility.sendMessage(sender, ChatColor.AQUA + StringEscapeUtils.unescapeJava(JustAFK.language.
                getConfig().getString("currently_away")) + " " + StringUtils.join(playerNames, ", "));

        return true;
    }
    
    protected boolean onSetafkCommand(CommandSender sender, String[] args) {
    	
    	String sourceName = "SERVER";
    	
    	if (sender instanceof Player) {
			Player player = (Player) sender;
			sourceName = player.getDisplayName();
			if (!player.hasPermission("justafk.setafk")) {
				JUtility.sendMessage(sender, ChatColor.RED + JustAFK.language.
	                    getConfig().getString("no_permission"));
				return true;
			}
		}
    	
    	if(args.length != 1) {
    		return false;
    	}
    	
    	Player editing = Bukkit.getPlayer(args[0]);

        if (editing != null) {
            if (!JUtility.isAway(editing)) {
                JUtility.setAway(editing, true, true);
                JUtility.sendMessage(editing, ChatColor.GRAY + "" + ChatColor.ITALIC +
                        StringEscapeUtils.unescapeJava(JustAFK.language.getConfig().
                                getString("setafk_away_private").replace("{name}", sourceName)));
            } else {
                JUtility.setAway(editing, false, true);
                JUtility.sendMessage(editing, ChatColor.GRAY + "" + ChatColor.ITALIC +
                        StringEscapeUtils.unescapeJava(JustAFK.language.getConfig().
                                getString("setafk_return_private").replace("{name}", sourceName)));
            }

            return true;
        } else {
        	JUtility.sendMessage(sender, ChatColor.RED + "Player not found");
        }
    	return false;
    }
    
    /**
     * Handle Commands
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
  
        if (command.getName().equalsIgnoreCase("afk")) {
            return onAfkCommand(sender, args);
        } else if (command.getName().equalsIgnoreCase("whosafk") || command.getName().equalsIgnoreCase("whoisafk")) {
        	return onWhosafkCommand(sender, args);
        } else if (command.getName().equalsIgnoreCase("setafk")) {
        	return onSetafkCommand(sender, args);
        } else if (command.getName().equalsIgnoreCase("justafk")) {
        	JUtility.sendMessage(sender, ChatColor.AQUA + this.getDescription().getName() + " " + ChatColor.GRAY + this.getDescription().getDescription());
        	JUtility.sendMessage(sender, ChatColor.GRAY + "Authors: " + ChatColor.AQUA + this.getDescription().getAuthors().toString().replace("[", "").replace("]", ""));
        	JUtility.sendMessage(sender, ChatColor.GRAY + "Website: " + ChatColor.AQUA + this.getDescription().getWebsite());

            return true;
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
        Optional<Object> isCertain = JUtility.getData(player, "iscertain");
        boolean certain = isCertain != null && Boolean.parseBoolean(isCertain.get().toString());
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
        	Optional<Object> isCertain = JUtility.getData(player, "iscertain");
            boolean certain = isCertain != null && Boolean.parseBoolean(isCertain.get().toString());
            
            JUtility.setAway(player, false, certain);
            JUtility.sendMessage(player, ChatColor.AQUA + StringEscapeUtils.unescapeJava(JustAFK.language.getConfig().getString("private_return")));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onInventoryClick(InventoryClickEvent event) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(event.getWhoClicked().getName());

        JUtility.saveData(player, "lastactive", System.currentTimeMillis());

        assert player.isOnline() : player.getName() + " is not online.";
        if (JUtility.isAway(player.getPlayer())) {
        	Optional<Object> isCertain = JUtility.getData(player, "iscertain");
            boolean certain = isCertain != null && Boolean.parseBoolean(isCertain.get().toString());
            JUtility.setAway(player.getPlayer(), false, certain);
            JUtility.sendMessage(player.getPlayer(), ChatColor.AQUA + StringEscapeUtils.unescapeJava(JustAFK.language.getConfig().getString("private_return")));
        }
    }
}
