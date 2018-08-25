package de.jeffclan.InvUnload;

import org.bukkit.block.Block;

public class ChestSpaceCombination implements Comparable<ChestSpaceCombination> {

	int freeSlots = 0;
	Block block;
	int myHash = 0;
	
	public ChestSpaceCombination(int slots,Block block, int myHash) {
		this.freeSlots = slots;
		this.block
		= block;
		this.myHash = myHash;
	}

	@Override
	public int compareTo(ChestSpaceCombination candidate) {
		
		return freeSlots - candidate.freeSlots;
	}

}
