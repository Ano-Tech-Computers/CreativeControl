package no.atc.floyd.bukkit.cc;


import multiworld.MultiWorldPlugin;
import multiworld.api.MultiWorldAPI;
import multiworld.api.MultiWorldWorldData;
import multiworld.api.flag.FlagName;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Horse;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.entity.ExpBottleEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.PluginManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.block.Block;
import org.bukkit.command.*;

/**
* Limited creative mode plugin for Bukkit/Spigot
*
* @author FloydATC
*/
public class CreativeControl extends JavaPlugin implements Listener {
    //public static Permissions Permissions = null;
    private static MultiWorldPlugin multiworld = null;
    private static ConcurrentHashMap<World, Boolean> is_creative = new ConcurrentHashMap<World, Boolean>();  
    private static ConcurrentHashMap<String, GameMode> player_inventory = new ConcurrentHashMap<String, GameMode>();  
    
    
    
    public void onDisable() {
    	// Save all inventories to disk -- important for server restarts etc.
    	for (Player p : Bukkit.getOnlinePlayers()) {
	    	save_inventory(p, p.getGameMode());
    	}
    	
    	multiworld = null;
    }

    public void onEnable() {
    	
    	// Connect with MultiWorld
    	// multiworld.MultiWorldPlugin cannot be cast to multiworld.api.MultiWorldAPI
    	multiworld = (MultiWorldPlugin) getServer().getPluginManager().getPlugin("MultiWorld");
    	if (multiworld == null) {
    		getLogger().warning("Integration with MultiWorld failed, some security features unavailable");
    	} else {
    		getLogger().warning("Integration with MultiWorld enabled");

    		// Collect creative flag for each world and store in hash 'is_creative'
    		MultiWorldAPI api = multiworld.getApi();
    		for (MultiWorldWorldData mwd : api.getWorlds()) {
    			World w = mwd.getBukkitWorld();
    			Boolean creative = mwd.getOptionValue(FlagName.CREATIVEWORLD);
    			is_creative.put(w, creative);
        		getLogger().warning("World '"+w.getName()+"' "+(creative?"is":"is not")+" creative");
    		}
    	}
    	multiworld = null;
    	
    	// Get current player states (we have to assume none of them are on the move)
    	for (Player p : Bukkit.getOnlinePlayers()) {
    		player_inventory.put(p.getName(), p.getGameMode());
    		// Save inventory to disk so we have most recent information
    		save_inventory(p, p.getGameMode());
    	}
    	
    	// Register event handlers
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(this, this);
    }

    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args ) {
    	String cmdname = cmd.getName().toLowerCase();
        Player player = null;
        if (sender instanceof Player) {
        	player = (Player) sender;
            //getLogger().info("player="+player+" cmd="+cmdname+" args="+Arrays.toString(args));
        }
        
        // See if any options were specified
        //Boolean force = false;
        Integer options = numOptions(args);
        if (options > 0) {
            String[] revised_args = new String[args.length - options];
            Integer index = 0;
            for (String s: args) {
            	if (s.startsWith("--")) {
            		// Supported options go here
            		//if (s.equalsIgnoreCase("--force")) {
            		//	force = true;
            		//}
            	} else {
            		revised_args[index] = s;
            		index++;
            	}
            }
        	//getLogger().info("done revising argument list");
            args = revised_args;
        }
        

        if (cmdname.equalsIgnoreCase("gm") && (player == null || player.hasPermission("creativecontrol.gm"))) {
        	if (args.length == 1) {
        		if (player == null) {
        			respond(player, "�7[�6CC�7] Must specify player");
        			return true;
        		}
        		
        		GameMode gm = GameMode.SURVIVAL;
        		if (args[0].equalsIgnoreCase("1")) gm = GameMode.CREATIVE;
        		if (args[0].equalsIgnoreCase("2")) gm = GameMode.SURVIVAL;
        		if (player.getGameMode() == gm) {
        			respond(player, "�7[�6CC�7] Already in "+gm.name()+" mode");
        		} else {
    		        save_inventory(player, player.getGameMode());
    		        load_inventory(player, gm, "");
        			player.setGameMode(gm);
        			respond(player, "�7[�6CC�7] Switched to "+gm.name()+" mode");
        			getLogger().info(player.getName()+" manually switched to "+gm.name()+" mode");
        		}
        		return true;
        	}
        	if (args.length == 2 && (player == null || player.hasPermission("creativecontrol.gm.other"))) {
        		Player p = getServer().getPlayer(args[1]);
        		if (p == null) {
        			respond(player, "�7[�6CC�7] Unknown player '"+args[1]+"'");
        			return true;
        		} else {
            		GameMode gm = GameMode.SURVIVAL;
            		if (args[0].equalsIgnoreCase("1")) gm = GameMode.CREATIVE;
            		if (args[0].equalsIgnoreCase("2")) gm = GameMode.SURVIVAL;
            		if (p.getGameMode() == gm) {
            			respond(player, "�7[�6CC�7] "+p.getName()+" is already in "+gm.name()+" mode");
            		} else {
        		        save_inventory(player, p.getGameMode());
        		        load_inventory(p, gm, "");
            			p.setGameMode(gm);
            			if (player.equals(p)) {
                			respond(player, "�7[�6CC�7] Switched to "+gm.name()+" mode");
            			} else {
            				respond(player, "�7[�6CC�7] "+p.getName()+" switched to "+gm.name()+" mode");
            				respond(player, "�7[�6CC�7] "+player.getName()+" switched you to "+gm.name()+" mode");
            			}
            		    getLogger().info(p.getName()+" switched to "+gm.name()+" mode by "+player.getName());
        		    }
            		return true;
        		}
        	}
        	return false;
        }

        if (cmdname.equalsIgnoreCase("saveinventory") && (player == null || player.hasPermission("creativecontrol.save"))) {
            if (args.length > 0) {
            	for (String pname : args) {
            		Player p = getServer().getPlayer(pname);
            		if (p != null) {
            			save_inventory(p, p.getGameMode());
                		respond(player, "�7[�6CC�7] Saved "+pname);
            		} else {
                		respond(player, "�7[�6CC�7] Unknown player '"+pname+"'");
            		}
            	}
        		return true;
            } else {
            	if (player != null) {
            	    save_inventory(player, player.getGameMode());
            		respond(player, "�7[�6CC�7] Saved");
            		return true;
            	} else {
            		respond(player, "�7[�6CC�7] Please specify player name");
            		return false;
            	}
            }
        }
        if (cmdname.equalsIgnoreCase("loadinventory") && (player == null || player.hasPermission("creativecontrol.load"))) {
    		if (args.length == 0) {
    			if (player == null) {
    				return false;
    			} else {
    				if (load_inventory(player, player.getGameMode(), "")) {
                		respond(player, "�7[�6CC�7] Loaded");
    				} else {
                		respond(player, "�7[�6CC�7] Error loading");
    				}
            		return true;
    			}
    		}
        	if (args.length == 1) {
    			String pname = args[0];
    			Player p = getServer().getPlayer(pname);
    			if (p != null) {
    				if (load_inventory(p, p.getGameMode(), "")) {
    					respond(player, "�7[�6CC�7] Loaded "+pname);
    				} else {
    					respond(player, "�7[�6CC�7] Error loading "+pname);
    				}
    				return true;
    			} else {
    				respond(player, "�7[�6CC�7] Unknown player '"+pname+"'");
    				return true;
    			}
    		}
    		if (args.length == 2) {
    			String pname = args[0];
    			Player p = getServer().getPlayer(pname);
    			if (!args[1].equalsIgnoreCase("CREATIVE") && !args[1].equalsIgnoreCase("SURVIVAL")) {
    				respond(player, "�7[�6CC�7] Must specify gamemode CREATIVE or SURVIVAL");
    				return true;
    			}
    			GameMode gm = GameMode.valueOf(args[1]);
    			if (p != null) {
    				if (load_inventory(p, gm, "")) {
    					respond(player, "�7[�6CC�7] Loaded "+pname);
    				} else {
    					respond(player, "�7[�6CC�7] Error loading "+pname);
    				}
    				return true;
    			} else {
    				respond(player, "�7[�6CC�7] Unknown player '"+pname+"'");
    				return true;
    			}
    		}
    		if (args.length == 3) {
    			String pname = args[0];
    			Player p = getServer().getPlayer(pname);
    			if (!args[1].equalsIgnoreCase("CREATIVE") && !args[1].equalsIgnoreCase("SURVIVAL")) {
    				respond(player, "�7[�6CC�7] Must specify gamemode CREATIVE or SURVIVAL");
    				return true;
    			}
    			GameMode gm = GameMode.valueOf(args[1]);
    			if (p != null) {
    				if (load_inventory(p, gm, args[2])) {
    					respond(player, "�7[�6CC�7] Loaded "+args[2]+" as "+pname);
    				} else {
    					respond(player, "�7[�6CC�7] Error loading "+args[2]+" as "+pname);
    				}
    				return true;
    			} else {
    				respond(player, "�7[�6CC�7] Unknown player '"+pname+"'");
    				return true;
    			}
    		}
        }
        if (cmdname.equalsIgnoreCase("listinventory") && player == null) {
        	if (args.length == 1) {
    			String pname = args[0];
    			Player p = getServer().getPlayer(pname);
    			if (p != null) {
    				if (!list_inventory(p, p.getGameMode())) {
    					respond(player, "�7[�6CC�7] Error listing inventory of "+pname);
    				}
    				return true;
    			} else {
    				respond(player, "�7[�6CC�7] Unknown player '"+pname+"'");
    				return true;
    			}
    		}
        }
        return false;
    }
    
    @EventHandler
    public void onPlayerChangedWorldEvent( PlayerChangedWorldEvent event ) {
    	Player p = event.getPlayer();
    	World w = p.getWorld();
    	getLogger().info("Player "+p.getName()+" changed to world '"+w.getName()+"'");
    	if (is_creative.get(w) != (p.getGameMode() == GameMode.CREATIVE)) {
    		getLogger().info("Player "+p.getName()+" has incorrect game mode");
    		update_gamemode(p, w);
    	}
    }
    
    @EventHandler
    public boolean onPlayerJoin( PlayerJoinEvent event ) {
        Player player = event.getPlayer();
        String to_world = event.getPlayer().getLocation().getWorld().getName().toLowerCase();
        getLogger().info("player "+player.getName()+" joined world '"+to_world+"'");
        
        // Override game mode as it was on logout
        if (is_creative.get(event.getPlayer().getLocation().getWorld())) {
        	load_inventory(player, GameMode.CREATIVE, "");
        	player.setGameMode(GameMode.CREATIVE);
        } else {
        	load_inventory(player, GameMode.SURVIVAL, "");
        	player.setGameMode(GameMode.SURVIVAL);
        }
        //update_gamemode(player, player.getWorld());
    	return true;
    }

    @EventHandler
    public boolean onQuit( PlayerQuitEvent event ) {
    	// Save and unload locations from memory
        save_inventory(event.getPlayer(), event.getPlayer().getGameMode());
    	return true;
    }

    @EventHandler
    public void onPlayerGameModeChange( PlayerGameModeChangeEvent event ) {
    	Player p = event.getPlayer();
    	GameMode old_mode = p.getGameMode();
        GameMode new_mode = event.getNewGameMode();
        if (allow_gm_change(p, old_mode, new_mode)) {
        	getLogger().info("Player '"+p.getName()+"' in world '"+p.getWorld().getName()+"' is changing game mode from "+old_mode.name()+" to "+new_mode.name());
        	respond(p, "�7[�6CC�7] Entering "+new_mode.name()+" mode");
        	return;
        } else {
        	getLogger().info("Player '"+p.getName()+"' in world '"+p.getWorld().getName()+"' tried to change game mode from "+old_mode.name()+" to "+new_mode.name());
        	event.setCancelled(true);
        }
        return;
    }

    @EventHandler(priority = EventPriority.MONITOR) // Must process after TelePlugin
    public void onTeleport( PlayerTeleportEvent event ) {
    	if (event.isCancelled()) return ;
        Player player = event.getPlayer();
    	String from_world = event.getFrom().getWorld().getName().toLowerCase();
    	World w = event.getTo().getWorld();
    	String to_world = event.getTo().getWorld().getName().toLowerCase();
    	if (!from_world.equals(to_world)) {
            getLogger().info("Player "+player.getName()+" is teleporting from world '"+from_world+"' to '"+to_world+"'");
        	respond(player, "�7[�6CC�7] Teleporting to world '"+w.getName()+"'");
            update_gamemode(player, w);
    	}
    }    

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
    	if (event.getEntity() instanceof Player) {
	    	Player p = (Player) event.getEntity();
	    	Location loc = p.getLocation();
	    	getLogger().info("Player "+p.getName()+" died ("+loc.toString()+")");
	    	delete_inventory(p);
    	}
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
    	if (event.getEntity() instanceof Player) {
	    	Player p = (Player) event.getEntity();
	    	if (p.getGameMode() == GameMode.CREATIVE) {
	    		event.setCancelled(true);
	    	}
    	}
    }
    
    @EventHandler
    public void onPlayerExpChange(PlayerExpChangeEvent event) {
    	Player p = event.getPlayer();
    	
    	if (p.getGameMode() != GameMode.CREATIVE) return;
    	event.setAmount(0);
    }
    
    @EventHandler
    public void onPlayerInteract( PlayerInteractEvent event	) {
    	Player p = event.getPlayer();
    	if (p.getGameMode() != GameMode.CREATIVE) return;
    	Block b = event.getClickedBlock();
    	if (b == null) return;
    	if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
    		if (b.getType() == Material.ENDER_CHEST) {
	    		getLogger().warning(p.getName()+" tried to use ENDER CHEST in CREATIVE mode");
	    		respond(p, "�7[�6CC�7] Not permitted in CREATIVE mode");
				event.setCancelled(true);
    		}
    		if (b.getType() == Material.ENCHANTMENT_TABLE) {
	    		getLogger().warning(p.getName()+" tried to use ENCHANTMENT TABLE in CREATIVE mode");
	    		respond(p, "�7[�6CC�7] Not permitted in CREATIVE mode");
				event.setCancelled(true);
    		}
    		if (b.getType() == Material.ANVIL) {
	    		getLogger().warning(p.getName()+" tried to use ANVIL in CREATIVE mode");
	    		respond(p, "�7[�6CC�7] Not permitted in CREATIVE mode");
				event.setCancelled(true);
    		}
    		if (b.getType() == Material.BREWING_STAND) {
	    		getLogger().warning(p.getName()+" tried to use BREWING STAND in CREATIVE mode");
	    		respond(p, "�7[�6CC�7] Not permitted in CREATIVE mode");
				event.setCancelled(true);
    		}
//    		if (b.getType() == Material.FURNACE) {
//	    		getLogger().warning(p.getName()+" tried to use FURNACE in CREATIVE mode");
//	    		respond(p, "�7[�6CC�7] Not permitted in CREATIVE mode");
//				event.setCancelled(true);
//    		}
//    		if (b.getType() == Material.WORKBENCH) {
//	    		getLogger().warning(p.getName()+" tried to use WORKBENCH in CREATIVE mode");
//	    		respond(p, "�7[�6CC�7] Not permitted in CREATIVE mode");
//				event.setCancelled(true);
//    		}
    	}
    }

    @EventHandler
    public void onPlayerEggThrowEvent(PlayerEggThrowEvent event) {
    	Player p = event.getPlayer();
    	Location loc = p.getLocation();
    	World w = loc.getWorld();
		if (is_creative.get(w)) {
		    getLogger().info("Player "+p.getName()+" tried to throw "+event.getEgg().toString()+" in CREATIVE world '"+w.getName()+"'");
		    event.setHatching(false);
		}
    }
    

    @EventHandler
    public void onExpBottleEvent(ExpBottleEvent event) {
    	World w = event.getEntity().getWorld();
		if (is_creative.get(w)) {
		    getLogger().info("Entity "+event.getEntityType().toString()+" broken in CREATIVE. Cancelling.");
		    event.getEntity().remove();
		}
    }

    @EventHandler
    public void onEntityPortalEnter(EntityPortalEnterEvent event) {
    	Location loc = event.getLocation();
    	World w = loc.getWorld();
    	if (event.getEntity() instanceof Player) return; // Ignore players
		if (is_creative.get(w)) {
		    getLogger().info("Entity "+event.getEntityType().toString()+" entering portal at x="+loc.getX()+" z="+loc.getBlockZ()+" in world '"+w.getName()+"' (CREATIVE)");
		    event.getEntity().remove();
		    getLogger().info("Entity "+event.getEntityType().toString()+" removed");
		}
    }
    
    @EventHandler
    public void onPlayerInteractEntity( PlayerInteractEntityEvent event	) {
    	Player p = event.getPlayer();
    	if (p.getGameMode() != GameMode.CREATIVE) return;
    	Entity entity = event.getRightClicked();
    	if (entity instanceof Horse) {
    		Horse h = (Horse) entity;
    		getLogger().info(p.getName()+" tried to interact with "+h.getVariant());
    		respond(p, "�7[�6CC�7] Not permitted in CREATIVE mode");
			event.setCancelled(true);
			return;
    	}
    	if (entity instanceof Egg) {
    		Egg e = (Egg) entity;
    		getLogger().info(p.getName()+" tried to interact with "+e.toString());
    		respond(p, "�7[�6CC�7] Not permitted in CREATIVE mode");
			event.setCancelled(true);
			return;
    	}
    	if (entity instanceof ExperienceOrb) {
    		ExperienceOrb e = (ExperienceOrb) entity;
    		getLogger().info(p.getName()+" tried to interact with "+e.toString());
    		respond(p, "�7[�6CC�7] Not permitted in CREATIVE mode");    		
			event.setCancelled(true);
			return;
    	}
    	if (entity instanceof ThrownPotion) {
    		ThrownPotion t = (ThrownPotion) entity;
    		getLogger().info(p.getName()+" tried to interact with "+t.toString());
    		respond(p, "�7[�6CC�7] Not permitted in CREATIVE mode");    		
			event.setCancelled(true);
			return;
    	}
		//getLogger().info(p.getName()+" interacting with "+entity.toString()+" in CREATIVE mode");
    }

    @EventHandler
    public void onPotionSplash( PotionSplashEvent event	) {
    	World w = event.getPotion().getLocation().getWorld();
		if (is_creative.get(w)) {
		    getLogger().info("Potion "+event.getPotion().getType().name()+" cancelled in world '"+w.getName()+"'");
			event.setCancelled(true);
		}    	
    }
    

    @EventHandler
    public void onPlayerItemConsume( PlayerItemConsumeEvent event	) {
    	Player p = event.getPlayer();
    	if (p.getGameMode() == GameMode.CREATIVE) {
    		getLogger().warning(p.getName()+" tried to consume "+event.getItem().getType().name()+" in CREATIVE mode");
    		respond(p, "�7[�6CC�7] Not permitted in CREATIVE mode");
			event.setCancelled(true);
    	}
    }
    
    
    @EventHandler
    public void onPlayerDropItem( PlayerDropItemEvent event	) {
    	Player p = event.getPlayer();
    	if (p.getGameMode() == GameMode.CREATIVE) {
    		getLogger().warning(p.getName()+" tried to throw "+event.getItemDrop().getItemStack().getType()+" in CREATIVE mode");
    		respond(p, "�7[�6CC�7] Not permitted in CREATIVE mode");
			event.setCancelled(true);
    	}
    }
    
    
    // Safeguard against CREATIVE mode inventory manipulation while SURVIVAL inventory is loaded
    // This SHOULD not be possible, perhaps it's caused by lag or something? Investigate. 
    @EventHandler
    public void onInventoryCreative(InventoryCreativeEvent event) {
    	HumanEntity human = event.getWhoClicked();
    	GameMode gm = null;
    	Player p = null;
    	if (human instanceof Player) {
    		p = (Player) human;
    		gm = player_inventory.get(p.getName());
    		if (gm != GameMode.CREATIVE) {
    			event.setCancelled(true);
        		respond(p, "�7[�6CC�7] Internal error: Wrong inventory loaded, please report this incident");
        		getLogger().warning(p.getName()+" is in CREATIVE mode but appears to have incorrect inventory loaded");
        		return;
    		}
    		
    		if (p.getGameMode() == GameMode.CREATIVE) {
    			// Ban certain items in creative mode
    			if (event.getAction() == InventoryAction.PLACE_ALL || event.getAction() == InventoryAction.PLACE_SOME || event.getAction() == InventoryAction.PLACE_ONE) {
    				if (event.getCursor() != null && isBanned(event.getCursor().getType())) {
    	        		respond(p, "�7[�6CC�7] Not permitted in CREATIVE mode");
    	        		getLogger().warning(p.getName()+" in CREATIVE mode tried to equip banned item "+event.getCursor().getType().name());
    	        		event.setCancelled(true);
    	        		return;
    				}
    			}
    		}
    	}
    	/*
        // Collect debug info
        ItemStack stack = event.getCurrentItem();
        String stack_name = "(null)";
        if (stack != null) stack_name = stack.getType().name();

        String action = event.getAction().name();

    	ItemStack cursor = event.getCursor();
    	String cursor_name = "(null)";
        if (cursor != null) cursor_name = cursor.getType().name();
        
        // Show debug info
		getLogger().info("InventoryCreative who="+event.getWhoClicked().getName()+" action="+action+" cursor="+cursor_name+" slot="+event.getRawSlot()+" slot contains="+stack_name);
		*/
    }

    /* Players trading, using crafting benches, chests etc. Hoppers moving entities around.
    @EventHandler
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
    	Inventory src = event.getSource();
    	Inventory dst = event.getDestination();
    	Inventory init = event.getInitiator();
    	InventoryHolder holder = init.getHolder();
    	ItemStack item = event.getItem();
    	if (holder instanceof Hopper) return;
		getLogger().info("InventoryClick src="+src.getName()+" dst="+dst.getName()+" init="+init+" holder="+holder.getClass()+" item="+item.getType().name());
    }
    */
    
    /* All kinds of inventory clicks.
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
    	String slot_contains = event.getCurrentItem().getType().name();
    	String action = event.getAction().name();
		getLogger().info("InventoryClick who="+event.getWhoClicked()+" action="+action+" slot="+event.getRawSlot()+" slot contains="+slot_contains+" event="+event.toString());
    }
    */

    private boolean list_inventory(Player player, GameMode gm) {
        String pn = player.getName();
    	save_inventory(player, player.getGameMode());
    	String fname = "plugins/CreativeControl/inventories/"+pn+"."+gm.name()+".txt";
    	try {
        	BufferedReader input =  new BufferedReader(new FileReader(fname));
    		String line = null;
    		while (( line = input.readLine()) != null) {
    			line = line.trim();
    			if (line.equalsIgnoreCase("[armor]")) {
    				getLogger().info(pn+" "+line);
    				continue;
    			}
    			if (line.equalsIgnoreCase("[inventory]")) {
    				getLogger().info(pn+" "+line);
    				continue;
    			}
    			if (!line.equalsIgnoreCase("empty")) {
        	    	ItemStack stack = string_to_itemstack(line);
        	    	getLogger().info(pn+": "+stack.getType().name()+" ("+stack.getDurability()+") "+stack.getAmount());
    			}
    		}
    		input.close();
    		return true;
    	}
    	catch (FileNotFoundException e) {
    		getLogger().warning("File not found: " + fname);
    	}
    	catch (Exception e) {
    		e.printStackTrace();
    	}
    	
    	return true;
    }

    private boolean delete_inventory(Player player) {
    	String gm = player.getGameMode().toString();
    	String pn = player.getName();
    	getLogger().info("Delete "+gm+" inventory for "+pn);
    	String fname = "plugins/CreativeControl/inventories/"+pn+"."+gm+".txt";
    	return (new File(fname)).delete();
    }
    
    private boolean save_inventory(Player player, GameMode gamemode) {
    	String gm = gamemode.toString();
    	String pn = player.getName();
    	PlayerInventory inv = player.getInventory();
    	player_inventory.put(player.getName(), player.getGameMode());
    	getLogger().info("Save "+gm+" inventory for "+pn);
    	String fname = "plugins/CreativeControl/inventories/"+pn+"."+gm+".txt";
    	BufferedWriter output;
   		String newline = System.getProperty("line.separator");
   	    try {
       		output = new BufferedWriter(new FileWriter(fname));
	    	output.write("[armor]"+newline);
       		for (ItemStack stack : inv.getArmorContents()) {
       		    if (stack == null) {
       		    	output.write( "empty"+newline );
       		    } else {
       		    	output.write(itemstack_to_string(stack)+newline);
       		    }
       		}
	    	output.write("[inventory]"+newline);
       		for (ItemStack stack : inv.getContents()) {
       		    if (stack == null) {
       		    	output.write( "empty"+newline );
       		    } else {
       		    	output.write(itemstack_to_string(stack)+newline);
       		    }
       		}
       		output.close();
       		return true;
   	    }
   	    catch (Exception e) {
    		e.printStackTrace();
   	    }
   	    return false;
    }
    
    private boolean load_inventory(Player player, GameMode gm, String altname) {
    	String pn = player.getName();
    	PlayerInventory inv = player.getInventory();
    	inv.clear();
    	ItemStack[] armor = inv.getArmorContents();
    	ItemStack[] inventory = inv.getContents();
    	for (Integer i=0; i<armor.length; i++) {
    		armor[i] = null;
    	}
    	for (Integer i=0; i<inventory.length; i++) {
    		inventory[i] = null;
    	}
    	getLogger().info("Load "+gm+" inventory for "+pn);
    	player_inventory.put(player.getName(), gm);
    	String fname = "plugins/CreativeControl/inventories/"+pn+"."+gm+".txt";
    	if (!altname.isEmpty()) { 
    		fname = "plugins/CreativeControl/inventories/"+altname+"."+gm+".txt";
    	}
    	try {
        	BufferedReader input =  new BufferedReader(new FileReader(fname));
    		String line = null;
    		Integer i = 0;
    		Boolean section_armor = false;
    		Boolean section_inventory = false;
    		ItemStack stack = null;
    		while (( line = input.readLine()) != null) {
    			line = line.trim();
    			if (line.equalsIgnoreCase("[armor]")) {
    				getLogger().fine("Entering armor section");
    				i = 0;
    				section_armor = true;
    				section_inventory = false;
    				continue;
    			}
    			if (line.equalsIgnoreCase("[inventory]")) {
    				getLogger().fine("Entering inventory section");
    				i = 0;
    				section_armor = false;
    				section_inventory = true;
    				continue;
    			}
    			if (line.equalsIgnoreCase("empty")) {
    				if (section_armor) armor[i] = null;
    				if (section_inventory) inventory[i] = null;
    				continue;
    			}
    			
    	    	getLogger().fine("Processing "+pn+" inventory: "+line);
    	    	stack = string_to_itemstack(line);
    	    	if (isBanned(stack.getType())) {
	    	    	getLogger().warning("Banned item "+stack.getType().name()+" not loaded into inventory");
    	    	} else {
	    	    	getLogger().fine("Place ItemStack "+stack+" into inventory");
					if (section_armor) armor[i] = stack;
					if (section_inventory) inventory[i] = stack;
    	    	}
    	    	
    			i++;
    		}
    		input.close();
    		
    		getLogger().fine("Commit inventory");
    		inv.setArmorContents(armor);
    		inv.setContents(inventory);
    		return true;
    	}
    	catch (FileNotFoundException e) {
    		getLogger().warning("File not found: " + fname);
    	}
    	catch (Exception e) {
    		e.printStackTrace();
    	}
    	return false;
    }
    
    private String itemstack_to_string(ItemStack stack) {
   	
    	Material material = stack.getType();
    	//MaterialData materialdata = stack.getData();
    	//Byte data = 0;
    	//if (materialdata != null) data = stack.getData().getData();
        Short durability = stack.getDurability();
        Integer amount = stack.getAmount();

//        String str = "type=>"+material+" data=>"+data+" durability=>"+durability+" amount=>"+amount;
        String str = "type=>"+material+" durability=>"+durability+" amount=>"+amount;
    	for (Entry<Enchantment, Integer> entry : stack.getEnchantments().entrySet()) {
    		Enchantment enchantment = entry.getKey();
    		Integer level = entry.getValue();
    		//str = str.concat(" enchantment=>"+enchantment.getId()+":"+level);
    		str = str.concat(" enchantmentname=>"+enchantment.getName()+":"+level);
    	}
    	if (stack.getItemMeta().hasDisplayName()) {
    	  str = str.concat(" name=>"+ascii2hex(stack.getItemMeta().getDisplayName()));
    	}
		getLogger().fine("Serialized stack "+stack+" as: "+str);
    	return str;
    }
    
    private ItemStack string_to_itemstack(String string) {
		Map<String, Object> map = new HashMap<String, Object>();
		String StackName = null;
		
		// Recreate basic ItemStack
		String[] pairs = string.split(" ");
		for (String pair : pairs) {
			String[] key_value = pair.split("=>");
			if (key_value[0].equalsIgnoreCase("type")) map.put("type", key_value[1]);
			//if (key_value[0].equalsIgnoreCase("data")) map.put("data", Byte.parseByte(key_value[1]));
			if (key_value[0].equalsIgnoreCase("durability")) map.put("durability", Short.parseShort(key_value[1]));
			if (key_value[0].equalsIgnoreCase("amount")) map.put("amount", Integer.parseInt(key_value[1]));
			if (key_value[0].equalsIgnoreCase("name")) StackName = hex2ascii(key_value[1]);
		}

		getLogger().fine("Recreate stack using map: "+map);
    	ItemStack stack = ItemStack.deserialize(map);
		//MaterialData md = stack.getData();
		//if (md != null) md.setData((Byte) map.get("data"));
		stack.setDurability((Short) map.get("durability"));
    	if (StackName != null) {
    		stack.getItemMeta().setDisplayName(StackName);
    	}
    	// Add enchantments
		for (String pair : pairs) {
			String[] key_value = pair.split("=>");
			if (key_value[0].equalsIgnoreCase("enchantmentname")) {
				String[] type_level = key_value[1].split(":");
//				Enchantment ench = Enchantment.getById(Integer.parseInt(type_level[0]));
				Enchantment ench = Enchantment.getByName(type_level[0]);
				Integer level = Integer.parseInt(type_level[1]);
				getLogger().fine("Recreate enchantment type "+type_level[0]+" level "+level);
				try {
					stack.addEnchantment(ench, level);
				}
		    	catch (Exception e) {
		    		getLogger().warning("Could not apply enchantment "+ench.getName()+" level "+level+" to "+stack.getType().name());
		    		e.printStackTrace();
		    	}
			}
		}
    	
    	return stack;
    }
    

	private Integer numOptions(String[] array) {
		Integer options = 0;
		for (String str : array) {
			if (str.startsWith("--")) { options++; }
		}
		return options;
	}
	
	private void respond(Player p, String msg) {
		if (p != null) {
			p.sendMessage(msg);
		} else {
	    	Server server = getServer();
	    	ConsoleCommandSender console = server.getConsoleSender();
			console.sendMessage(msg);
		}
	}
	
	private boolean allow_gm_change(Player p, GameMode from, GameMode to) {
    	if (!p.hasPermission("creativecontrol.leave."+from.name().toLowerCase())) {
        	getLogger().fine("Player '"+p.getName()+"' does not have permission to leave "+from.name()+" mode");
    		return false;
    	}
    	if (!p.hasPermission("creativecontrol.enter."+to.name().toLowerCase())) {
        	getLogger().fine("Player '"+p.getName()+"' does not have permission to enter "+to.name()+" mode");
    		return false;
    	}
		return true;
	}

	private void update_gamemode(Player player, World w) {
		GameMode enter = null;
		
        // Set game mode
		if (is_creative.get(w)) {
			getLogger().info("  This is a CREATIVE world");
			if (player.getGameMode() != GameMode.CREATIVE) {
				getLogger().info("  Player "+player.getName()+" is not in CREATIVE mode");
				if (allow_gm_change(player, player.getGameMode(), GameMode.CREATIVE)) {
    				getLogger().fine("  Player "+player.getName()+" switching to CREATIVE mode");
    				enter = GameMode.CREATIVE;
				} else {
    				getLogger().info("  Player "+player.getName()+" does not have permission to switch to CREATIVE mode");
    				enter = GameMode.SURVIVAL;
				}
			} else {
				// Already creative, probably set by another plugin helping us
				enter = GameMode.CREATIVE;
			}
		} else {
			getLogger().info("  This is a SURVIVAL world");
			if (player.getGameMode() != GameMode.SURVIVAL) {
				getLogger().info("  Player "+player.getName()+" is not in SURVIVAL mode");
				if (allow_gm_change(player, player.getGameMode(), GameMode.SURVIVAL)) {
    				getLogger().fine("  Player "+player.getName()+" switching to SURVIVAL mode");
    				enter = GameMode.SURVIVAL;
				} else {
    				getLogger().info("  Player "+player.getName()+" does not have permission to switch to SURVIVAL mode");
    				enter = GameMode.CREATIVE;
				}
			} else {
				// Already survival, probably set by another plugin helping us
				enter = GameMode.SURVIVAL;
			}
		}
		
		
		// Make sure correct inventory is loaded
		GameMode loaded = player_inventory.get(player.getName());
		if (loaded == null) {
			getLogger().warning("Unknown inventory state for player "+player.getName());
			player_inventory.put(player.getName(), player.getGameMode());
			loaded = player_inventory.get(player.getName());
		} else {
			getLogger().info("Player "+player.getName()+" currently has "+loaded.name()+" inventory loaded");
		}
		if (enter != loaded) {
			getLogger().info("Player "+player.getName()+" needs to switch inventories");
			save_inventory(player, loaded); 
			load_inventory(player, enter, "");
		} else {
			getLogger().info("Player "+player.getName()+" is all set");
		}

		
		if (player.getGameMode() != enter) player.setGameMode(enter);

	}
	
	private boolean isBanned(Material m) {
		if (m == null) return false;
		if (m == Material.POTION) return true;
		if (m == Material.EXP_BOTTLE) return true;
		if (m == Material.TNT) return true;
		if (m == Material.DRAGON_EGG) return true;
		if (m == Material.MONSTER_EGG) return true;
		if (m == Material.MONSTER_EGGS) return true;
		if (m == Material.MOB_SPAWNER) return true;
		if (m == Material.EXPLOSIVE_MINECART) return true;
		return false;
	}
	
	private static String ascii2hex(String ascii) {
		char[] chars = ascii.toCharArray();
		StringBuffer hex = new StringBuffer();
		for (int i = 0; i < chars.length; i++)
	    {
	        hex.append(Integer.toHexString((int) chars[i]));
	    }
	    return hex.toString();
	}

	private static String hex2ascii(String hex)
	{
	    StringBuilder output = new StringBuilder("");
	    for (int i = 0; i < hex.length(); i += 2)
	    {
	        String str = hex.substring(i, i + 2);
	        output.append((char) Integer.parseInt(str, 16));
	    }
	    return output.toString();
	}
}


