package com.MoofIT.Minecraft.Cenotaph;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Blaze;
import org.bukkit.entity.CaveSpider;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.Giant;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Player;
import org.bukkit.entity.Silverfish;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Spider;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Directional;
import org.bukkit.material.MaterialData;
import com.griefcraft.lwc.LWC;
import com.griefcraft.model.Protection;

public class CenotaphEntityListener implements Listener {
	private Cenotaph plugin;
	private HashSet<Material> blockNoReplaceList = new HashSet<Material>();

	public CenotaphEntityListener(Cenotaph instance) {
		this.plugin = instance;
		initNoReplaceList();
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onEntityDamage(EntityDamageEvent event) {
		if (event.isCancelled()) return;
		if (!(event.getEntity() instanceof Player))return;

		Player player = (Player)event.getEntity();
		// Add them to the list if they're about to die
		if (player.getHealth() - event.getDamage() <= 0) {
			Cenotaph.deathCause.put(player.getName(), event);
		}
	}

	@EventHandler
	public void onEntityExplode(EntityExplodeEvent event)
	{
		if (event.isCancelled()) return;
		if (!plugin.creeperProtection) return;
		for (Block block : event.blockList()) {
			TombBlock tBlock = Cenotaph.tombBlockList.get(block.getLocation());
			if (tBlock != null) {
				event.setCancelled(true);
			}
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onEntityDeath(EntityDeathEvent event)
	{
		if (!(event.getEntity() instanceof Player)) return;
		Player p = (Player)event.getEntity();

		if (!p.hasPermission("cenotaph.use")) return;

		if (event.getDrops().size() == 0) {
			plugin.sendMessage(p, "Inv empty.");
			return;
		}

		for (String world : plugin.disableInWorlds) {
			String curWorld = p.getWorld().getName();
			if (world.equalsIgnoreCase(curWorld)) {
				plugin.sendMessage(p,"Cenotaph disabled in " + curWorld + ". Inv dropped.");
				return;
			}
		}


		// Get the current player location.
		Location loc = p.getLocation();
		Block block = p.getWorld().getBlockAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

		// If we run into something we don't want to destroy, go one up.
		if (blockNoReplaceList.contains(block.getType())) {
			block = p.getWorld().getBlockAt(loc.getBlockX(), loc.getBlockY() + 1, loc.getBlockZ());
		}

		//Don't create the chest if it or its sign would be in the void
		if (plugin.voidCheck && ((plugin.cenotaphSign && block.getY() > p.getWorld().getMaxHeight() - 1) || (!plugin.cenotaphSign && block.getY() > p.getWorld().getMaxHeight()) || p.getLocation().getY() < 1)) {
			plugin.sendMessage(p, "Chest would be in the Void. Inv dropped.");
			return;
		}

		// Check if the player has a chest.
		int pChestCount = 0;
		int pSignCount = 0;
		for (ItemStack item : event.getDrops()) {
			if (item == null) continue;
			if (item.getType() == Material.CHEST) pChestCount += item.getAmount();
			if (item.getType() == Material.SIGN) pSignCount += item.getAmount();
		}

		if (pChestCount == 0 && !p.hasPermission("cenotaph.freechest")) {
			plugin.sendMessage(p, "No chest! Inv dropped.");
			return;
		}

		// Check if we can replace the block.
		block = findPlace(block,false);
		if ( block == null ) {
			plugin.sendMessage(p, "No room to place chest. Inv dropped.");
			return;
		}

		// Check if there is a nearby chest
		if (plugin.noInterfere && checkChest(block)) {
			plugin.sendMessage(p, "Existing chest interfering with chest placement. Inv dropped.");
			return;
		}

		int removeChestCount = 1;
		int removeSignCount = 0;

		// Do the check for a large chest block here so we can check for interference
		Block lBlock = findLarge(block);

		// Set the current block to a chest, init some variables for later use.
		block.setType(Material.CHEST);
		// We're running into issues with 1.3 where we can't cast to a Chest :(
		BlockState state = block.getState();
		if (!(state instanceof Chest)) {
			plugin.sendMessage(p, "Could not access chest. Inv dropped.");
			return;
		}
		Chest sChest = (Chest)state;
		Chest lChest = null;
		int slot = 0;
		int maxSlot = sChest.getInventory().getSize();

		// Check if they need a large chest.
		if (event.getDrops().size() > maxSlot) {
			// If they are allowed spawn a large chest to catch their entire inventory.
			if (lBlock != null && p.hasPermission("cenotaph.large")) {
				removeChestCount = 2;
				// Check if the player has enough chests
				if (pChestCount >= removeChestCount || p.hasPermission("cenotaph.freechest")) {
					lBlock.setType(Material.CHEST);
					lChest = (Chest)lBlock.getState();
					maxSlot = maxSlot * 2;
				} else {
					removeChestCount = 1;
				}
			}
		}

		// Don't remove any chests if they get a free one.
		if (p.hasPermission("cenotaph.freechest"))
			removeChestCount = 0;

		// Check if we have signs enabled, if the player can use signs, and if the player has a sign or gets a free sign
		Block sBlock = null;
		if (plugin.cenotaphSign && p.hasPermission("cenotaph.sign") &&
			(pSignCount > 0 || p.hasPermission("cenotaph.freesign"))) {
			// Find a place to put the sign, then place the sign.
			sBlock = sChest.getWorld().getBlockAt(sChest.getX(), sChest.getY() + 1, sChest.getZ());
			if (canReplace(sBlock.getType())) {
				createSign(sBlock, p);
				removeSignCount += 1;
			} else if (lChest != null) {
				sBlock = lChest.getWorld().getBlockAt(lChest.getX(), lChest.getY() + 1, lChest.getZ());
				if (canReplace(sBlock.getType())) {
					createSign(sBlock, p);
					removeSignCount += 1;
				}
			}
		}

		// Don't remove a sign if they get a free one
		if (p.hasPermission("cenotaph.freesign"))
			removeSignCount -= 1;

		// Create a TombBlock for this tombstone
		TombBlock tBlock = new TombBlock(sChest.getBlock(), (lChest != null) ? lChest.getBlock() : null, sBlock, p.getName(), p.getLevel() + 1, (System.currentTimeMillis() / 1000));

		// Protect the chest/sign if LWC is installed.
		Boolean prot = false;
		Boolean protLWC = false;
		if (p.hasPermission("cenotaph.lwc"))
			prot = activateLWC(p, tBlock);
		tBlock.setLwcEnabled(prot);
		if (prot) protLWC = true;

		// Protect the chest with Lockette if installed, enabled, and unprotected.
		if (p.hasPermission("cenotaph.lockette") ) {
			if (p.hasPermission("cenotaph.freelockettesign")) {
				prot = protectWithLockette(p, tBlock);
			} else if (pSignCount > removeSignCount) {
				removeSignCount += 1;
				prot = protectWithLockette(p, tBlock);
			}
		}
		// Add tombstone to list
		Cenotaph.tombList.offer(tBlock);

		// Add tombstone blocks to tombBlockList
		Cenotaph.tombBlockList.put(tBlock.getBlock().getLocation(), tBlock);
		if (tBlock.getLBlock() != null) Cenotaph.tombBlockList.put(tBlock.getLBlock().getLocation(), tBlock);
		if (tBlock.getSign() != null) Cenotaph.tombBlockList.put(tBlock.getSign().getLocation(), tBlock);

		// Add tombstone to player lookup list
		ArrayList<TombBlock> pList = Cenotaph.playerTombList.get(p.getName());
		if (pList == null) {
			pList = new ArrayList<TombBlock>();
			Cenotaph.playerTombList.put(p.getName(), pList);
		}
		pList.add(tBlock);

		plugin.saveCenotaphList(p.getWorld().getName());

		// Next get the players inventory using the getDrops() method.
		for (Iterator<ItemStack> iter = event.getDrops().listIterator(); iter.hasNext();) {
			ItemStack item = iter.next();
			if (item == null) continue;
			// Take the chest(s)
			if (removeChestCount > 0 && item.getType() == Material.CHEST) {
				if (item.getAmount() >= removeChestCount) {
					item.setAmount(item.getAmount() - removeChestCount);
					removeChestCount = 0;
				} else {
					removeChestCount -= item.getAmount();
					item.setAmount(0);
				}
				if (item.getAmount() == 0) {
					iter.remove();
					continue;
				}
			}

			// Take a sign
			if (removeSignCount > 0 && item.getType() == Material.SIGN){
				item.setAmount(item.getAmount() - 1);
				removeSignCount -= 1;
				if (item.getAmount() == 0) {
					iter.remove();
					continue;
				}
			}

			// Add items to chest if not full.
			if (slot < maxSlot) {
				if (slot >= sChest.getInventory().getSize()) {
					if (lChest == null) continue;
					lChest.getInventory().setItem(slot % sChest.getInventory().getSize(), item);
				} else {
					sChest.getInventory().setItem(slot, item);
				}
				iter.remove();
				slot++;
			} else if (removeChestCount == 0) break;
		}

		int breakTime = (plugin.levelBasedRemoval ? Math.min(p.getLevel() + 1 * plugin.levelBasedTime,plugin.removeTime) : plugin.removeTime);
		String msg = "Inv stored. ";
		if (event.getDrops().size() > 0) msg += ChatColor.YELLOW + "Overflow: " + ChatColor.WHITE + event.getDrops().size() + " ";
		msg += ChatColor.YELLOW + "Security: " + ChatColor.WHITE;
		if (prot) {
			msg += (protLWC ? "LWC" : "Lockette") + " ";
			if (plugin.securityRemove) msg += ChatColor.YELLOW + "SecTime: " + ChatColor.WHITE + plugin.convertTime(plugin.securityTimeout) + " ";
		}
		else msg += "None ";
		msg += ChatColor.YELLOW + "BreakTime: " + ChatColor.WHITE + (plugin.cenotaphRemove ? plugin.convertTime(breakTime) : "Inf") + " ";
		if (plugin.removeWhenEmpty || plugin.keepUntilEmpty) {
			msg += ChatColor.YELLOW + "BreakOverride: " + ChatColor.WHITE;
			if (plugin.removeWhenEmpty) msg += "Break on empty";
			if (plugin.removeWhenEmpty && plugin.keepUntilEmpty) msg += " & ";
			if (plugin.keepUntilEmpty) msg += "Keep until empty";
		}
		plugin.sendMessage(p, msg);
	}

	private void createSign(Block signBlock, Player p) {
		String date = new SimpleDateFormat(plugin.dateFormat).format(new Date());
		String time = new SimpleDateFormat(plugin.timeFormat).format(new Date());
		String name = p.getName();
		String reason = "Unknown";

		EntityDamageEvent dmg = Cenotaph.deathCause.get(name);
		if (dmg != null) {
			Cenotaph.deathCause.remove(name);
			reason = getCause(dmg);
		}

		signBlock.setType(Material.SIGN_POST);
		final Sign sign = (Sign)signBlock.getState();

		for (int x = 0; x < 4; x++) {
			String line = plugin.signMessage[x];
			line = line.replace("{name}", name);
			line = line.replace("{date}", date);
			line = line.replace("{time}", time);
			line = line.replace("{reason}", reason);

			if (line.length() > 15) line = line.substring(0, 15);
			sign.setLine(x, line);
		}

		plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
			public void run() {
				sign.update();
			}
		});
	}

	private Boolean protectWithLockette(Player player, TombBlock tBlock) {
		if (!plugin.LocketteEnable) return false;

       // plugin.getLogger().info("Protecting with lockette!");
		Block signBlock = null;

		signBlock = findPlace(tBlock.getBlock(),true);
		if (signBlock == null) {
			//plugin.sendMessage(player, "No room for Lockette sign! Chest unsecured!");
			return false;
		}

		signBlock.setType(Material.AIR); //hack to prevent oddness with signs popping out of the ground as of Bukkit 818
		signBlock.setType(Material.WALL_SIGN);

		BlockState signBlockState = null;
		signBlockState = signBlock.getState();

		MaterialData signFacingDirection = signBlockState.getData();
		//BlockFace facing = getLocketteSignDirection(plugin.getYawTo(tBlock.getBlock().getLocation(), signBlock.getLocation()));
        BlockFace facing = tBlock.getBlock().getFace(signBlock);
		((Directional)signFacingDirection).setFacingDirection(facing);
		signBlockState.setData(signFacingDirection);

		final Sign sign = (Sign)signBlockState;

		String name = player.getName();
		if (name.length() > 15) name = name.substring(0, 15);
		sign.setLine(0, "[Private]");
		sign.setLine(1, name);
		plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
			public void run() {
				sign.update();
			}
		});
		tBlock.setLocketteSign(signBlock);
		return true;
	}

	private void initNoReplaceList() {
		blockNoReplaceList.add(Material.STEP);
		blockNoReplaceList.add(Material.TORCH);
		blockNoReplaceList.add(Material.REDSTONE_WIRE);
		blockNoReplaceList.add(Material.RAILS);
		blockNoReplaceList.add(Material.STONE_PLATE);
		blockNoReplaceList.add(Material.WOOD_PLATE);
		blockNoReplaceList.add(Material.REDSTONE_TORCH_ON);
		blockNoReplaceList.add(Material.REDSTONE_TORCH_OFF);
		blockNoReplaceList.add(Material.CAKE_BLOCK);
	}

	private Boolean activateLWC(Player player, TombBlock tBlock) {
		if (!plugin.lwcEnable) return false;
		if (plugin.lwcPlugin == null) return false;
		LWC lwc = plugin.lwcPlugin.getLWC();

		// Register the chest + sign as private
		Block block = tBlock.getBlock();
		Block sign = tBlock.getSign();
		lwc.getPhysicalDatabase().registerProtection(block.getTypeId(), Protection.Type.PRIVATE, block.getWorld().getName(), player.getName(), "", block.getX(), block.getY(), block.getZ());
		if (sign != null)
			lwc.getPhysicalDatabase().registerProtection(sign.getTypeId(), Protection.Type.PRIVATE, block.getWorld().getName(), player.getName(), "", sign.getX(), sign.getY(), sign.getZ());

		tBlock.setLwcEnabled(true);
		return true;
	}

	private String getCause(EntityDamageEvent dmg) {
		try {
			switch (dmg.getCause()) {
				case ENTITY_ATTACK:
				{
					EntityDamageByEntityEvent event = (EntityDamageByEntityEvent)dmg;
					Entity e = event.getDamager();
					if (e == null) {
						return plugin.deathMessages.get("Misc.Dispenser").toString();
					} else if (e instanceof Player) {
						return ((Player) e).getDisplayName();
					} else if (e instanceof PigZombie) {
						return plugin.deathMessages.get("Monster.PigZombie").toString();
					} else if (e instanceof Giant) {
						return plugin.deathMessages.get("Monster.Giant").toString();
					} else if (e instanceof Zombie) {
						return plugin.deathMessages.get("Monster.Zombie").toString();
					} else if (e instanceof Skeleton) {
						return plugin.deathMessages.get("Monster.Skeleton").toString();
					} else if (e instanceof Spider) {
						return plugin.deathMessages.get("Monster.Spider").toString();
					} else if (e instanceof Creeper) {
						return plugin.deathMessages.get("Monster.Creeper").toString();
					} else if (e instanceof Ghast) {
						return plugin.deathMessages.get("Monster.Ghast").toString();
					} else if (e instanceof Slime) {
						return plugin.deathMessages.get("Monster.Slime").toString();
					} else if (e instanceof Wolf) {
						return plugin.deathMessages.get("Monster.Wolf").toString();
					} else if (e instanceof Blaze) {
						return plugin.deathMessages.get("Monster.Blaze").toString();
					} else if (e instanceof CaveSpider) {
						return plugin.deathMessages.get("Monster.CaveSpider").toString();
					} else if (e instanceof EnderDragon) {
						return plugin.deathMessages.get("Monster.EnderDragon").toString();
					} else if (e instanceof Enderman) {
						return plugin.deathMessages.get("Monster.Enderman").toString();
					} else if (e instanceof IronGolem) {
						return plugin.deathMessages.get("Monster.IronGolem").toString();
					} else if (e instanceof MagmaCube) {
						return plugin.deathMessages.get("Monster.MagmaCube").toString();
					} else if (e instanceof Silverfish) {
						return plugin.deathMessages.get("Monster.Silverfish").toString();
					} else {
						return plugin.deathMessages.get("Monster.Other").toString();
					}
				}
				case CONTACT:
					return plugin.deathMessages.get("World.Cactus").toString();
				case SUFFOCATION:
					return plugin.deathMessages.get("World.Suffocation").toString();
				case FALL:
					return plugin.deathMessages.get("World.Fall").toString();
				case FIRE:
					return plugin.deathMessages.get("World.Fire").toString();
				case FIRE_TICK:
					return plugin.deathMessages.get("World.Burning").toString();
				case LAVA:
					return plugin.deathMessages.get("World.Lava").toString();
				case DROWNING:
					return plugin.deathMessages.get("World.Drowning").toString();
				case BLOCK_EXPLOSION:
					return plugin.deathMessages.get("Explosion.Misc").toString();
				case ENTITY_EXPLOSION:
				{
					try {
						EntityDamageByEntityEvent event = (EntityDamageByEntityEvent)dmg;
						Entity e = event.getDamager();
						if (e instanceof TNTPrimed) return plugin.deathMessages.get("Explosion.TNT").toString();
						else if (e instanceof Fireball) return plugin.deathMessages.get("Monster.Ghast").toString();
						else return plugin.deathMessages.get("Monster.Creeper").toString();
					} catch (Exception e) {
						return plugin.deathMessages.get("Explosion.Misc").toString();
					}
				}
				case VOID:
					return plugin.deathMessages.get("Misc.Void").toString();
				case LIGHTNING:
					return plugin.deathMessages.get("World.Lightning").toString();
				default:
					return plugin.deathMessages.get("Misc.Other").toString();
			}
		} catch (NullPointerException e) {
			Cenotaph.log.severe("[Cenotaph] Error processing death cause: " + dmg.getCause().toString());
			e.printStackTrace();
			return ChatColor.RED + "ERROR" + ChatColor.BLACK;
		}
	}

	/**
	 * Find a block near the base block to place the tombstone
	 * @param base
	 * @return
	 */
	Block findPlace(Block base, Boolean CardinalSearch) {
		if (canReplace(base.getType())) return base;
		int baseX = base.getX();
		int baseY = base.getY();
		int baseZ = base.getZ();
		World w = base.getWorld();

		if (CardinalSearch) {
			Block b;
			b = w.getBlockAt(baseX - 1, baseY, baseZ);
			if (canReplace(b.getType())) return b;
			b = w.getBlockAt(baseX + 1, baseY, baseZ);
			if (canReplace(b.getType())) return b;
			b = w.getBlockAt(baseX, baseY, baseZ - 1);
			if (canReplace(b.getType())) return b;
			b = w.getBlockAt(baseX, baseY, baseZ + 1);
			if (canReplace(b.getType())) return b;
			b = w.getBlockAt(baseX, baseY, baseZ);
			if (canReplace(b.getType())) return b;

			return null;
		}

		for (int x = baseX - 1; x < baseX + 1; x++) {
			for (int z = baseZ - 1; z < baseZ + 1; z++) {
				Block b = w.getBlockAt(x, baseY, z);
				if (canReplace(b.getType())) return b;
			}
		}

		return null;
	}

	Boolean canReplace(Material mat) {
		return (mat == Material.AIR ||
				mat == Material.SAPLING ||
				mat == Material.WATER ||
				mat == Material.STATIONARY_WATER ||
				mat == Material.LAVA ||
				mat == Material.STATIONARY_LAVA ||
				mat == Material.YELLOW_FLOWER ||
				mat == Material.RED_ROSE ||
				mat == Material.BROWN_MUSHROOM ||
				mat == Material.RED_MUSHROOM ||
				mat == Material.FIRE ||
				mat == Material.CROPS ||
				mat == Material.SNOW ||
				mat == Material.SUGAR_CANE ||
				mat == Material.GRAVEL ||
				mat == Material.SAND);
	}

	Block findLarge(Block base) {
		// Check all 4 sides for air.
		Block exp;
		exp = base.getWorld().getBlockAt(base.getX() - 1, base.getY(), base.getZ());
		if (canReplace(exp.getType()) && (!plugin.noInterfere || !checkChest(exp))) return exp;
		exp = base.getWorld().getBlockAt(base.getX(), base.getY(), base.getZ() - 1);
		if (canReplace(exp.getType()) && (!plugin.noInterfere || !checkChest(exp))) return exp;
		exp = base.getWorld().getBlockAt(base.getX() + 1, base.getY(), base.getZ());
		if (canReplace(exp.getType()) && (!plugin.noInterfere || !checkChest(exp))) return exp;
		exp = base.getWorld().getBlockAt(base.getX(), base.getY(), base.getZ() + 1);
		if (canReplace(exp.getType()) && (!plugin.noInterfere || !checkChest(exp))) return exp;
		return null;
	}

	boolean checkChest(Block base) {
		// Check all 4 sides for a chest.
		Block exp;
		exp = base.getWorld().getBlockAt(base.getX() - 1, base.getY(), base.getZ());
		if (exp.getType() == Material.CHEST) return true;
		exp = base.getWorld().getBlockAt(base.getX(), base.getY(), base.getZ() - 1);
		if (exp.getType() == Material.CHEST) return true;
		exp = base.getWorld().getBlockAt(base.getX() + 1, base.getY(), base.getZ());
		if (exp.getType() == Material.CHEST) return true;
		exp = base.getWorld().getBlockAt(base.getX(), base.getY(), base.getZ() + 1);
		if (exp.getType() == Material.CHEST) return true;
		return false;
	}
	
	private BlockFace getLocketteSignDirection(double rot) {

		if (0 <= rot && rot < 45) {
			return BlockFace.NORTH;
		} else if (45 <= rot && rot < 135) {
			return BlockFace.EAST;
		} else if (135 <= rot && rot < 225) {
			return BlockFace.SOUTH;
		} else if (225 <= rot && rot < 315) {
			return BlockFace.WEST;
		} else if (315 <= rot && rot < 360) {
			return BlockFace.NORTH;
		} else {
			return null;
		}
	}

}
