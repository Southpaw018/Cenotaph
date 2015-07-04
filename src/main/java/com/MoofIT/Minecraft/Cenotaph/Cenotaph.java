package com.MoofIT.Minecraft.Cenotaph;

/**
 * Cenotaph - A Dead Man's Chest plugin for Bukkit
 * By Jim Drey (Southpaw018) <moof@moofit.com>
 * Original Copyright (C) 2011 Steven "Drakia" Scott <Drakia@Gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.If not, see <http://www.gnu.org/licenses/>.
 */

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.DynmapAPI;

import com.griefcraft.lwc.LWC;
import com.griefcraft.lwc.LWCPlugin;
import com.griefcraft.model.Protection;

/*
TODO
	--GENERAL
	- dynmap: display break time and security info
	- option to force low priority for player death events
	- block replacement strategy: agressive vs. passive

	--ECONOMY
	- vault integration
	- life purchasing
	- cenotaph payments
	- keep compass option
	- keep chest and sign option

	--REGION PROTECTION
	- worldguard integration
	- towny integration
	- factions integration

	--PVP
	- allow ops to restrict # of items looted
	- allow options to add killer to lock, or everyone to lock, or killer group to lock
*/

public class Cenotaph extends JavaPlugin {
	public final CenotaphEntityListener entityListener = new CenotaphEntityListener(this);
	public final CenotaphBlockListener blockListener = new CenotaphBlockListener(this);
	public final CenotaphPlayerListener playerListener = new CenotaphPlayerListener(this);
	public final CenotaphCommand commandExec = new CenotaphCommand(this);
	public final DynmapThread dynThread = new DynmapThread(this);
	public static Logger log;
	PluginManager pm;

	public LWCPlugin lwcPlugin = null;
	public DynmapAPI dynmap = null;

	public static ConcurrentLinkedQueue<TombBlock> tombList = new ConcurrentLinkedQueue<TombBlock>();
	public static HashMap<Location, TombBlock> tombBlockList = new HashMap<Location, TombBlock>();
	public static HashMap<String, ArrayList<TombBlock>> playerTombList = new HashMap<String, ArrayList<TombBlock>>();
	public static HashMap<String, EntityDamageEvent> deathCause = new HashMap<String, EntityDamageEvent>();

	public FileConfiguration config;

	/**
	 * Configuration options - Defaults
	 */
	//Core
	public boolean cenotaphSign = true;
	public boolean noDestroy = false;
	public boolean saveCenotaphList = true;
	public boolean noInterfere = true;
	public boolean versionCheck = true;
	public boolean voidCheck = true;
	public boolean creeperProtection = false;
	public boolean tntProtection = false;
	public String signMessage[] = new String[] {
		"{name}",
		"RIP",
		"{date}",
		"{time}"
	};
	public String dateFormat = "MM/dd/yyyy";
	public String timeFormat = "hh:mm a";
	public List<String> disableInWorlds;
	public boolean dynmapEnable = true;

	//Removal
	public boolean destroyQuickLoot = false;
	public boolean cenotaphRemove = false;
	public int removeTime = 3600;
	public boolean removeWhenEmpty = false;
	public boolean keepUntilEmpty = false;
	public boolean levelBasedRemoval = false;
	public int levelBasedTime = 300;

	//Security
	public boolean LocketteEnable = true;
	public boolean lwcEnable = false;
	public boolean securityRemove = false;
	public int securityTimeout = 3600;
	public boolean lwcPublic = false;

	//DeathMessages
	public HashMap<String, Object> deathMessages = new HashMap<String, Object>();

	//Config versioning
	public int configVer = 0;
	public final int configCurrent = 12;

	@Override
	public void onEnable() {
		log = Logger.getLogger("Minecraft");

		log.info("Cenotaph " + getDescription().getVersion() + " is enabled.");

		pm = getServer().getPluginManager();

		pm.registerEvents(entityListener,this);
		pm.registerEvents(blockListener,this);
		pm.registerEvents(playerListener,this);

		lwcPlugin = (LWCPlugin)loadPlugin("LWC");
		dynmap = (DynmapAPI)loadPlugin("dynmap");

		initDeathMessagesDefaults();
		loadConfig();
		if (dynmapEnable && dynmap != null) dynThread.activate(dynmap);
		for (World w : getServer().getWorlds())
			loadTombList(w.getName());

		if (versionCheck) {
			versionCheck(true);
		}

		// Start removal timer. Run every 5 seconds (20 ticks per second)
		if (securityRemove || cenotaphRemove)
			getServer().getScheduler().scheduleSyncRepeatingTask(this, new TombThread(this), 0L, 100L);
	}

	public void loadConfig() {
		this.reloadConfig();
		config = this.getConfig();

		configVer = config.getInt("configVer", configVer);
		if (configVer == 0) {
			log.info("[Cenotaph] Configuration error or no config file found. Generating default config file.");
			saveDefaultConfig();
			this.reloadConfig();
			config = this.getConfig();
		}
		else if (configVer < configCurrent) {
			log.warning("[Cenotaph] Your config file is out of date! Delete your config and /cenadmin reload to see the new options. Proceeding using set options from config file and defaults for new options..." );
		}

		//Core
		cenotaphSign = config.getBoolean("Core.cenotaphSign", cenotaphSign);
		noDestroy = config.getBoolean("Core.noDestroy", noDestroy);
		saveCenotaphList = config.getBoolean("Core.saveCenotaphList", saveCenotaphList);
		noInterfere = config.getBoolean("Core.noInterfere", noInterfere);
		versionCheck = config.getBoolean("Core.versionCheck", versionCheck);
		voidCheck = config.getBoolean("Core.voidCheck", voidCheck);
		creeperProtection = config.getBoolean("Core.creeperProtection", creeperProtection);
		tntProtection = config.getBoolean("Core.tntProtection", tntProtection);
		signMessage = loadSign();
		dateFormat = config.getString("Core.Sign.dateFormat", dateFormat);
		timeFormat = config.getString("Core.Sign.timeFormat", timeFormat);
		dynmapEnable = config.getBoolean("Core.dynmapEnable", dynmapEnable);

		try {
			disableInWorlds = config.getStringList("Core.disableInWorlds");
		} catch (NullPointerException e) {
			log.warning("[Cenotaph] Configuration failure while loading disableInWorlds. Using defaults.");
		}

		//Removal
		destroyQuickLoot = config.getBoolean("Removal.destroyQuickLoot", destroyQuickLoot);
		cenotaphRemove = config.getBoolean("Removal.cenotaphRemove", cenotaphRemove);
		removeTime = config.getInt("Removal.removeTime", removeTime);
		removeWhenEmpty = config.getBoolean("Removal.removeWhenEmpty", removeWhenEmpty);
		keepUntilEmpty = config.getBoolean("Removal.keepUntilEmpty", keepUntilEmpty);
		levelBasedRemoval = config.getBoolean("Removal.levelBasedRemoval", levelBasedRemoval);
		levelBasedTime = config.getInt("Removal.levelBasedTime", levelBasedTime);

		//Security
		LocketteEnable = config.getBoolean("Security.LocketteEnable", LocketteEnable);
		lwcEnable = config.getBoolean("Security.lwcEnable", lwcEnable);
		securityRemove = config.getBoolean("Security.securityRemove", securityRemove);
		securityTimeout = config.getInt("Security.securityTimeout", securityTimeout);
		lwcPublic = config.getBoolean("Security.lwcPublic", lwcPublic);

		//DeathMessages
		try {
			deathMessages = (HashMap<String, Object>)config.getConfigurationSection("DeathMessages").getValues(true);
		} catch (NullPointerException e) {
			log.warning("[Cenotaph] Configuration failure while loading deathMessages. Using defaults.");
		}
	}

	private void initDeathMessagesDefaults() {
		deathMessages.put("Monster.Zombie", "a Zombie");
		deathMessages.put("Monster.Skeleton", "a Skeleton");
		deathMessages.put("Monster.Spider", "a Spider");
		deathMessages.put("Monster.Wolf", "a Wolf");
		deathMessages.put("Monster.Creeper", "a Creeper");
		deathMessages.put("Monster.Slime", "a Slime");
		deathMessages.put("Monster.Ghast", "a Ghast");
		deathMessages.put("Monster.PigZombie", "a Pig Zombie");
		deathMessages.put("Monster.Giant", "a Giant");
		deathMessages.put("Monster.Other", "a Monster");
		deathMessages.put("Monster.Blaze", "a Blaze");
		deathMessages.put("Monster.CaveSpider", "a Cave Spider");
		deathMessages.put("Monster.EnderDragon", "a Dragon");
		deathMessages.put("Monster.Enderman", "an Enderman");
		deathMessages.put("Monster.IronGolem", "an Iron Golem");
		deathMessages.put("Monster.MagmaCube", "a Magma Cube");
		deathMessages.put("Monster.Silverfish", "a Siverfish");

		deathMessages.put("World.Cactus", "a Cactus");
		deathMessages.put("World.Suffocation", "Suffocation");
		deathMessages.put("World.Fall", "a Fall");
		deathMessages.put("World.Fire", "a Fire");
		deathMessages.put("World.Burning", "Burning");
		deathMessages.put("World.Lava", "Lava");
		deathMessages.put("World.Drowning", "Drowning");
		deathMessages.put("World.Lightning", "Lightning");

		deathMessages.put("Explosion.Misc", "an Explosion");
		deathMessages.put("Explosion.TNT", "a TNT Explosion");

		deathMessages.put("Misc.Dispenser", "a Dispenser");
		deathMessages.put("Misc.Void", "the Void");
		deathMessages.put("Misc.Other", "Unknown");
	}
	

	public void loadTombList(String world) {
		if (!saveCenotaphList) return;
		try {
			File fh = new File(this.getDataFolder().getPath(), "tombList-" + world + ".db");
			if (!fh.exists()) return;
			Scanner scanner = new Scanner(fh);
			while (scanner.hasNextLine()) {
				String line = scanner.nextLine().trim();
				String[] split = line.split(":");
				//block:lblock:sign:owner:level:time:lwc:locketteSign
				Block block = readBlock(split[0]);
				Block lBlock = readBlock(split[1]);
				Block sign = readBlock(split[2]);
				String owner = split[3];
				int level = Integer.valueOf(split[4]);
				long time = Long.valueOf(split[5]);
				boolean lwc = Boolean.valueOf(split[6]);
				Block locketteSign;
				if (split.length == 7) {
					// hack to allow old db files to still be usable
					locketteSign = null;
					continue;
				} else {				
					locketteSign = readBlock(split[7]);
				}
				
				if (block == null || owner == null) {
					log.info("[Cenotaph] Invalid entry in database " + fh.getName());
					continue;
				}
				
				TombBlock tBlock = new TombBlock(block, lBlock, sign, owner, level, time, lwc, locketteSign);
				tombList.offer(tBlock);
				// Used for quick tombStone lookup
				tombBlockList.put(block.getLocation(), tBlock);
				if (lBlock != null) tombBlockList.put(lBlock.getLocation(), tBlock);
				if (sign != null) tombBlockList.put(sign.getLocation(), tBlock);
				if (locketteSign != null) tombBlockList.put(locketteSign.getLocation(), tBlock);
				ArrayList<TombBlock> pList = playerTombList.get(owner);
				if (pList == null) {
					pList = new ArrayList<TombBlock>();
					playerTombList.put(owner, pList);
				}
				pList.add(tBlock);
			}
			scanner.close();
		} catch (IOException e) {
			log.info("[Cenotaph] Error loading cenotaph list: " + e);
		}
	}

	public void saveCenotaphList(String world) {
		if (!saveCenotaphList) return;
		try {
			File fh = new File(this.getDataFolder().getPath(), "tombList-" + world + ".db");
			BufferedWriter bw = new BufferedWriter(new FileWriter(fh));
			for (Iterator<TombBlock> iter = tombList.iterator(); iter.hasNext();) {
				TombBlock tBlock = iter.next();
				// Skip not this world
				if (!tBlock.getBlock().getWorld().getName().equalsIgnoreCase(world)) continue;

				StringBuilder builder = new StringBuilder();

				bw.append(printBlock(tBlock.getBlock()));
				bw.append(":");
				bw.append(printBlock(tBlock.getLBlock()));
				bw.append(":");
				bw.append(printBlock(tBlock.getSign()));
				bw.append(":");
				bw.append(tBlock.getOwner());
				bw.append(":");
				bw.append(Integer.toString(tBlock.getOwnerLevel()));
				bw.append(":");
				bw.append(String.valueOf(tBlock.getTime()));
				bw.append(":");
				bw.append(String.valueOf(tBlock.getLwcEnabled()));
				bw.append(":");
				bw.append(printBlock(tBlock.getLocketteSign()));

				bw.append(builder.toString());
				bw.newLine();
			}
			bw.close();
		} catch (IOException e) {
			log.info("[Cenotaph] Error saving cenotaph list: " + e);
		}
	}

	private String printBlock(Block b) {
		if (b == null) return null;
		return b.getWorld().getName() + "," + b.getX() + "," + b.getY() + "," + b.getZ();
	}

	private Block readBlock(String b) {
		if (b.length() == 0) return null;
		String[] split = b.split(",");
		//world,x,y,z
		World world = getServer().getWorld(split[0]);
		if (world == null) return null;
		return world.getBlockAt(Integer.valueOf(split[1]), Integer.valueOf(split[2]), Integer.valueOf(split[3]));
	}

	
	@Override
	public void onDisable() {
		for (World w : getServer().getWorlds()) saveCenotaphList(w.getName());
		if (dynmapEnable && dynmap != null) dynThread.cenotaphLayer.cleanup();
		getServer().getScheduler().cancelTasks(this);
	}
	private String[] loadSign() {
		String[] msg = signMessage;
		msg[0] = config.getString("Core.Sign.Line1", signMessage[0]);
		msg[1] = config.getString("Core.Sign.Line2", signMessage[1]);
		msg[2] = config.getString("Core.Sign.Line3", signMessage[2]);
		msg[3] = config.getString("Core.Sign.Line4", signMessage[3]);
		return msg;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		return commandExec.onCommand(sender, command, label, args);
	}

	/*
	 * Check if a plugin is loaded/enabled. Returns the plugin and print message to console if so, returns null otherwise
	 */
	private Plugin loadPlugin(String p) {
		Plugin plugin = pm.getPlugin(p);
		if (plugin != null && plugin.isEnabled()) {
			log.info("[Cenotaph] Using " + plugin.getDescription().getName() + " (v" + plugin.getDescription().getVersion() + ")");
			return plugin;
		}
		return null;
	}

	public void deactivateLWC(TombBlock tBlock, boolean force) {
		if (!lwcEnable) return;
		if (lwcPlugin == null) return;

		LWC lwc = lwcPlugin.getLWC();

		// Remove the protection on the chest
		Block _block = tBlock.getBlock();
		Protection protection = lwc.findProtection(_block.getLocation());
		if (protection != null) {
			protection.remove();
			//Set to public instead of removing completely
			if (lwcPublic && !force)
				lwc.getPhysicalDatabase().registerProtection(_block.getTypeId(), Protection.Type.PUBLIC, _block.getWorld().getName(), tBlock.getOwner(), "", _block.getX(), _block.getY(), _block.getZ());
		}
		else
		{
			log.info("[Cenotaph] - LWC Protection not found for chest at " + tBlock.getBlock().getLocation().toString());
		}

		// Remove the protection on the sign
		_block = tBlock.getSign();
		if (_block != null) {
			protection = lwc.findProtection(_block);
			if (protection != null) {
				protection.remove();
				// Set to public instead of removing completely
				if (lwcPublic && !force)
					lwc.getPhysicalDatabase().registerProtection(_block.getTypeId(), Protection.Type.PUBLIC, _block.getWorld().getName(), tBlock.getOwner(), "", _block.getX(), _block.getY(), _block.getZ());
			}
		}
		tBlock.setLwcEnabled(false);
	}
	public void deactivateLockette(TombBlock tBlock) {
		if (tBlock.getLocketteSign() == null) return;
		tBlock.getLocketteSign().setType(Material.AIR);
		tBlock.removeLocketteSign();
	}

	public void removeTomb(TombBlock tBlock, boolean removeList) {
		if (tBlock == null) return;

		tombBlockList.remove(tBlock.getBlock().getLocation());
		if (tBlock.getLBlock() != null) tombBlockList.remove(tBlock.getLBlock().getLocation());
		if (tBlock.getSign() != null) tombBlockList.remove(tBlock.getSign().getLocation());

		// Remove just this tomb from tombList
		ArrayList<TombBlock> tList = playerTombList.get(tBlock.getOwner());
		if (tList != null) {
			tList.remove(tBlock);
			if (tList.size() == 0) {
				playerTombList.remove(tBlock.getOwner());
			}
		}

		if (removeList)
			tombList.remove(tBlock);

		if (tBlock.getBlock() != null)
			saveCenotaphList(tBlock.getBlock().getWorld().getName());
	}

	//Never updated, so I removed it... :)
	public String versionCheck(Boolean printToLog) {
		return getDescription().getVersion();
	}

	/**
	 * Gets the Yaw from one location to another in relation to North.
	 *
	 */
	public double getYawTo(Location from, Location to) {
			final int distX = to.getBlockX() - from.getBlockX();
			final int distZ = to.getBlockZ() - from.getBlockZ();
			double degrees = Math.toDegrees(Math.atan2(-distX, distZ));
			degrees += 180;
		return degrees;
	}

	/**
	 * Converts a rotation to a cardinal direction name.
	 * Author: sk89q - Original function from CommandBook plugin
	 * @param rot
	 * @return
	 */
	public static String getDirection(double rot) {
		if (0 <= rot && rot < 22.5) {
			return "North";
		} else if (22.5 <= rot && rot < 67.5) {
			return "Northeast";
		} else if (67.5 <= rot && rot < 112.5) {
			return "East";
		} else if (112.5 <= rot && rot < 157.5) {
			return "Southeast";
		} else if (157.5 <= rot && rot < 202.5) {
			return "South";
		} else if (202.5 <= rot && rot < 247.5) {
			return "Southwest";
		} else if (247.5 <= rot && rot < 292.5) {
			return "West";
		} else if (292.5 <= rot && rot < 337.5) {
			return "Northwest";
		} else if (337.5 <= rot && rot < 360.0) {
			return "North";
		} else {
			return null;
		}
	}

	public String convertTime(int s) {
		String formatted = Integer.toString(s);
		if (s >= 86400) {
			formatted = String.format("%dd %d:%02d:%02d", s/86400, (s%86400)/3600, (s%3600)/60, s%60);
		}
		else if (s >= 3600) {
			formatted = String.format("%d:%02d:%02d", s/3600, (s%3600)/60, (s%60));
		}
		else if (s > 60) {
			formatted = String.format("%02d:%02d", s/60, s%60);
		}
		return formatted;
	}

	public void sendMessage(Player player, String message) {
		player.sendMessage(ChatColor.GOLD + "[Cenotaph] " + ChatColor.WHITE + message);
	}

	public void destroyCenotaph(Location loc) {
		destroyCenotaph(tombBlockList.get(loc));
	}
	public void destroyCenotaph(TombBlock tBlock) {
		if (tBlock.getBlock().getChunk().load() == false) {
			log.severe("[Cenotaph] Error loading world chunk trying to remove cenotaph at " + tBlock.getBlock().getX() + "," + tBlock.getBlock().getY() + "," + tBlock.getBlock().getZ() + " owned by " + tBlock.getOwner() + ".");
			return;
		}
		if (tBlock.getSign() != null) tBlock.getSign().setType(Material.AIR);
		deactivateLockette(tBlock);
		deactivateLWC(tBlock, true);

		tBlock.getBlock().setType(Material.AIR);
		if (tBlock.getLBlock() != null) tBlock.getLBlock().setType(Material.AIR);

		removeTomb(tBlock, true);

		Player p = getServer().getPlayer(tBlock.getOwner());
		if (p != null) sendMessage(p, "Your cenotaph has broken.");
	}

	public HashMap<String, ArrayList<TombBlock>> getCenotaphList() {
		return playerTombList;
	}
}
