package com.MoofIT.Minecraft.Cenotaph;

import org.bukkit.block.Block;
import org.bukkit.block.Sign;

public class TombBlock {
	private Block block;
	private Block lBlock;
	private Block sign;
	private Block locketteSign;
	private long time;
	private String owner;
	private int ownerLevel;
	private boolean lwcEnabled = false;

	TombBlock(Block block, Block lBlock, Block sign, String owner, int ownerLevel, long time) {
		this.block = block;
		this.lBlock = lBlock;
		this.sign = sign;
		this.owner = owner;
		this.ownerLevel = ownerLevel;
		this.time = time;
	}
	TombBlock(Block block, Block lBlock, Block sign, String owner, int ownerLevel, long time, boolean lwc, Block locketteSign) {
		this.block = block;
		this.lBlock = lBlock;
		this.sign = sign;
		this.owner = owner;
		this.ownerLevel = ownerLevel;
		this.time = time;
		this.lwcEnabled = lwc;
		this.locketteSign = locketteSign;
	}

	long getTime() {
		return time;
	}
	Block getBlock() {
		return block;
	}
	Block getLBlock() {
		return lBlock;
	}
	Block getSign() {
		return sign;
	}
	Block getLocketteSign() {
		return locketteSign;
	}
	String getOwner() {
		return owner;
	}
	int getOwnerLevel() {
		return ownerLevel;
	}
	boolean getLwcEnabled() {
		return lwcEnabled;
	}
	void setLwcEnabled(boolean val) {
		lwcEnabled = val;
	}
	void setLocketteSign(Block signBlock) {
		this.locketteSign = signBlock;
	}
	void removeLocketteSign() {
		this.locketteSign = null;
	}
}
