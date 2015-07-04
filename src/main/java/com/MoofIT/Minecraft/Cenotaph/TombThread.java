package com.MoofIT.Minecraft.Cenotaph;

import java.util.Iterator;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class TombThread extends Thread {
	private Cenotaph plugin;

	public TombThread(Cenotaph instance) {
		this.plugin = instance;
	}

	public void run() {
		long cTime = System.currentTimeMillis() / 1000;
		for (Iterator<TombBlock> iter = Cenotaph.tombList.iterator(); iter.hasNext();) {
			TombBlock tBlock = iter.next();
			boolean bRemoved = false;

			//"empty" option checks
			if (plugin.keepUntilEmpty || plugin.removeWhenEmpty) {

				//if the Block is not there, remove it from the list.
				if(tBlock==null){
					iter.remove();
					continue;
				}
			//	if(tBlock.getBlock().getState() instanceof Chest)
				Location loc = tBlock.getBlock().getLocation();
				if (loc.getWorld().getBlockTypeIdAt((int)loc.getX(),(int)loc.getY(),(int)loc.getZ() ) == Material.CHEST.getId()) {

					//IF the Chunk isn't loaded, then no one is there, lets not do anything with it right now.
					if(!loc.getChunk().isLoaded())
						continue;

					boolean isEmpty = true;

					Chest sChest = (Chest)tBlock.getBlock().getState();
				    Chest lChest = (tBlock.getLBlock() != null) ? (Chest)tBlock.getLBlock().getState() : null;

					ItemStack[] items =sChest.getInventory().getContents();

					for (ItemStack item : items) {
						if (item != null) isEmpty = false;
						break;
					}
					if (lChest != null && !isEmpty) {
						for (ItemStack item : lChest.getInventory().getContents()) {
							if (item != null) isEmpty = false;
							break;
						}
					}
					if (plugin.keepUntilEmpty) {
						if (!isEmpty) continue;
					}
					if (plugin.removeWhenEmpty) {
						if (isEmpty) {
							plugin.destroyCenotaph(tBlock);
							bRemoved = true;
							iter.remove();
						}
					}
				}
			}

			//Security removal check
			if (plugin.securityRemove) {
				Player p = plugin.getServer().getPlayer(tBlock.getOwner());

				if (cTime >= (tBlock.getTime() + plugin.securityTimeout)) {
					if (tBlock.getLwcEnabled() && plugin.lwcPlugin != null) {
						plugin.deactivateLWC(tBlock, false);
						tBlock.setLwcEnabled(false);
						if (p != null)
							plugin.sendMessage(p, "LWC protection disabled on your cenotaph!");
					}
					if (tBlock.getLocketteSign() != null && plugin.LocketteEnable) {
						plugin.deactivateLockette(tBlock);
						if (p != null)
							plugin.sendMessage(p, "Lockette protection disabled on your cenotaph!");
					}
				}
			}
			//Block removal check
			if (plugin.cenotaphRemove) {
				if (plugin.levelBasedRemoval) {
					if (cTime > Math.min(tBlock.getTime() + tBlock.getOwnerLevel() * plugin.levelBasedTime, tBlock.getTime() + plugin.removeTime)) {
						plugin.destroyCenotaph(tBlock);
						if(!bRemoved)
							iter.remove();
					}
				}
				else {
					if (cTime > (tBlock.getTime() + plugin.removeTime)) {
						plugin.destroyCenotaph(tBlock);
						if(!bRemoved)
							iter.remove();
					}
				}
			}
		}
	}
}
