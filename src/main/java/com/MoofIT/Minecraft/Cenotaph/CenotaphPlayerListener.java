package com.MoofIT.Minecraft.Cenotaph;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class CenotaphPlayerListener implements Listener {
	private Cenotaph plugin;

	public CenotaphPlayerListener(Cenotaph instance) {
		this.plugin = instance;
	}

	@SuppressWarnings("deprecation")
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerInteract(PlayerInteractEvent event) {
		if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
		Block b = event.getClickedBlock();
		if (b.getType() != Material.SIGN_POST && b.getType() != Material.CHEST) return;
		// We'll do quickloot on rightclick of chest if we're going to destroy it anyways
		if (b.getType() == Material.CHEST && (!plugin.destroyQuickLoot || !plugin.noDestroy)) return;
		if (!event.getPlayer().hasPermission("cenotaph.quickloot")) return;

		TombBlock tBlock = Cenotaph.tombBlockList.get(b.getLocation());
		if (tBlock == null || !(tBlock.getBlock().getState() instanceof Chest)) return;

		if (!tBlock.getOwner().equals(event.getPlayer().getName())) return;

		Chest sChest = (Chest)tBlock.getBlock().getState();
		Chest lChest = (tBlock.getLBlock() != null) ? (Chest)tBlock.getLBlock().getState() : null;

		ItemStack[] items = sChest.getInventory().getContents();
		boolean overflow = false;
		for (int cSlot = 0; cSlot < items.length; cSlot++) {
			ItemStack item = items[cSlot];
			if (item == null) continue;
			if (item.getType() == Material.AIR) continue;
			int slot = event.getPlayer().getInventory().firstEmpty();
			if (slot == -1) {
				overflow = true;
				break;
			}
			event.getPlayer().getInventory().setItem(slot, item);
			sChest.getInventory().clear(cSlot);
		}
		if (lChest != null) {
			items = lChest.getInventory().getContents();
			for (int cSlot = 0; cSlot < items.length; cSlot++) {
				ItemStack item = items[cSlot];
				if (item == null) continue;
				if (item.getType() == Material.AIR) continue;
				int slot = event.getPlayer().getInventory().firstEmpty();
				if (slot == -1) {
					overflow = true;
					break;
				}
				event.getPlayer().getInventory().setItem(slot, item);
				lChest.getInventory().clear(cSlot);
			}
		}

		if (!overflow) {
			// We're quicklooting, so no need to resume this interaction
			event.setUseInteractedBlock(Result.DENY);
			event.setUseItemInHand(Result.DENY); //TODO: Minor bug here - if you're holding a sign, it'll still pop up
			event.setCancelled(true);

			if (plugin.destroyQuickLoot) {
				plugin.destroyCenotaph(tBlock);
			}
		}

		// Manually update inventory for the time being.
		event.getPlayer().updateInventory();
	}
}
