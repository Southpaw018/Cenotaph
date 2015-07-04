package com.MoofIT.Minecraft.Cenotaph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;
import org.dynmap.markers.Marker;

public class DynmapThread extends Thread {
	DynmapAPI api;
	MarkerAPI markerapi;

	private Cenotaph plugin;

	public DynmapThread(Cenotaph instance) {
		this.plugin = instance;
	}

	abstract class Layer {
		MarkerSet set;
		MarkerIcon deficon;
		String labelfmt;
		Map<String, Marker> markers = new HashMap<String, Marker>();

		public Layer(String id, FileConfiguration cfg, String deflabel, String deficon, String deflabelfmt) {
			set = markerapi.getMarkerSet("cenotaph." + id);
			if(set == null)
				set = markerapi.createMarkerSet("cenotaph."+id, cfg.getString("layer."+id+".name", deflabel), null, false);
			else
				set.setMarkerSetLabel(cfg.getString("layer."+id+".name", deflabel));
			if(set == null) {
				Cenotaph.log.severe("[Cenotaph] Dynmap integration: Error creating " + deflabel + " marker set");
				return;
			}
			set.setLayerPriority(cfg.getInt("layer."+id+".layerprio", 10));
			set.setHideByDefault(cfg.getBoolean("layer."+id+".hidebydefault", false));
			int minzoom = cfg.getInt("layer."+id+".minzoom", 0);
			if(minzoom > 0) /* Don't call if non-default - lets us work with pre-0.28 dynmap */
				set.setMinZoom(minzoom);
			String icon = cfg.getString("layer."+id+".deficon", deficon);
			this.deficon = markerapi.getMarkerIcon(icon);
			if(this.deficon == null) {
				Cenotaph.log.info("[Cenotaph] Dynmap integration: Unable to load default icon '" + icon + "' - using default '"+deficon+"'");
				this.deficon = markerapi.getMarkerIcon(deficon);
			}
			labelfmt = cfg.getString("layer."+id+".labelfmt", deflabelfmt);
		}

		public void cleanup() {
			if(set != null) {
				set.deleteMarkerSet();
				set = null;
			}
			markers.clear();
		}

		void updateMarkerSet() {
			Map<String, Marker> newmap = new HashMap<String, Marker>(); /* Build new map */

			Map<String,Location> marks = getMarkers();
			for(String name: marks.keySet()) {
				Location loc = marks.get(name);

				String wname = loc.getWorld().getName();
				/* Get location */
				String id = wname + "/" + name;

				String label = labelfmt.replace("%name%", name);

				/* See if we already have marker */
				Marker m = markers.remove(id);
				if(m == null) { /* Not found? Need new one */
					m = set.createMarker(id, label, wname, loc.getX(), loc.getY(), loc.getZ(), deficon, false);
				}
				else { /* Else, update position if needed */
					m.setLocation(wname, loc.getX(), loc.getY(), loc.getZ());
					m.setLabel(label);
					m.setMarkerIcon(deficon);
				}
				newmap.put(id, m);	/* Add to new map */
			}
			/* Now, review old map - anything left is gone */
			for(Marker oldm : markers.values()) {
				oldm.deleteMarker();
			}
			/* And replace with new map */
			markers.clear();
			markers = newmap;
		}
		/* Get current markers, by ID with location */
		public abstract Map<String,Location> getMarkers();
	}

	public class cenotaphLayer extends Layer {
		public cenotaphLayer(FileConfiguration cfg, String fmt) {
			super("cenotaphs", cfg, "Cenotaphs", "chest", fmt);
		}
		/* Get current markers, by ID with location */
		public Map<String,Location> getMarkers() {
			HashMap<String,Location> map = new HashMap<String,Location>();
			if(Cenotaph.tombBlockList != null) {
				for(Entry<String, ArrayList<TombBlock>> playerCenotaphs : plugin.getCenotaphList().entrySet()) {
					String owner = playerCenotaphs.getKey();
					for (TombBlock tBlock : playerCenotaphs.getValue()) {
						map.put(owner,tBlock.getBlock().getLocation());
					}
				}
			}
			return map;
		}
	}

	/* Warps layer settings */
	Layer cenotaphLayer;

	long updperiod;
	long playerupdperiod;
	boolean stop;

	private class MarkerUpdate implements Runnable {
		public void run() {
			if(!stop)
				updateMarkers();
		}
	}

	/* Update mob population and position */
	private void updateMarkers() {
		cenotaphLayer.updateMarkerSet();
		plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new MarkerUpdate(), updperiod);
	}

	void activate(DynmapAPI api) {
		/* Now, get markers API */
		this.api = api;
		markerapi = api.getMarkerAPI();
		if(markerapi == null) {
			Cenotaph.log.severe("[Cenotaph] Dynmap integration: Error loading Dynmap marker API!");
			return;
		}
		cenotaphLayer = new cenotaphLayer(plugin.config, "[%name%]");

		/* Set up update job - based on period */
		double per = plugin.config.getDouble("update.period", 5.0);
		if(per < 2.0) per = 2.0;
		updperiod = (long)(per*20.0);
		stop = false;
		plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new MarkerUpdate(), 5*20);
	}
}