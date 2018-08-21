package de.jeffclan.InvUnloader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class InvUnloader extends JavaPlugin implements CommandExecutor {

	private int chestRadius = 10;
	private boolean debug = true;
	private int particleCount = 100;
	private Particle particleType = Particle.SPELL_WITCH;

	@Override
	public void onEnable() {
		getCommand("unload").setExecutor(this);
		
		@SuppressWarnings("unused")
		Metrics metrics = new Metrics(this);
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command arg1, String arg2, String[] arg3) {
    	if(!(sender instanceof Player)) {
    		return true;
    	}
    	
    	Player p = (Player) sender;
    	
    	if(arg1.getName().equalsIgnoreCase("unload")) {
    		unloadInventory(p);
    		return true;
    	}
    	
    	return false;
    }

	public static List<Block> getNearbyChests(Location location, int radius) {
		List<Block> blocks = new ArrayList<Block>();
		for (int x = location.getBlockX() - radius; x <= location.getBlockX() + radius; x++) {
			for (int y = location.getBlockY() - radius; y <= location.getBlockY() + radius; y++) {
				for (int z = location.getBlockZ() - radius; z <= location.getBlockZ() + radius; z++) {
					if(location.getWorld().getBlockAt(x, y, z).getState() instanceof Chest) {
						blocks.add(location.getWorld().getBlockAt(x, y, z));
					}
				}
			}
		}
		return blocks;
	}

	public static List<Material> getInventoryContents(Inventory inventory) {

		List<Material> contents = new ArrayList<Material>();
		for (ItemStack itemStack : inventory.getContents()) {
			if (isItemStackEmpty(itemStack))
				continue;
			if (!contents.contains(itemStack.getType())) {
				contents.add(itemStack.getType());
			}
		}

		return contents;
	}

	public static boolean isItemStackEmpty(ItemStack itemStack) {
		if (itemStack == null)
			return true;
		if (itemStack.getAmount() == 0)
			return true;
		if (itemStack.getType() == Material.AIR)
			return true;
		return false;
	}

	public void unloadInventory(Player p) {
		
		if(debug) System.out.println("\n\n");

		List<Block> nearbyBlocks = getNearbyChests(p.getLocation(), chestRadius);
		List<Block> whereToSpawnParticles = new ArrayList<Block>();
		Inventory playerInventory = p.getInventory();

		// iterate through all chests in radius
		for(Block block : nearbyBlocks) {
				
			Inventory chestInventory = ((Chest) block.getState()).getInventory();

			// iterate through all items in the player's inventory
			for (ItemStack itemStack : playerInventory.getContents()) {

				// discard empty stacks
				if (isItemStackEmpty(itemStack))
					continue;

				// only continue if the inventory already contains what we want to store
				if (getInventoryContents(chestInventory).contains(itemStack.getType())) {

					if(debug) System.out.println("Trying to store " + itemStack.getAmount() + "x " + itemStack.getType().name()
							+ " in a chest that already holds this item");

					// Remove the item from the players inventory
					playerInventory.remove(itemStack);

					// try to add it to the chest
					HashMap<Integer, ItemStack> unstorableItemStacks = chestInventory.addItem(itemStack);
							
					if(unstorableItemStacks.size() == 0 || unstorableItemStacks.get(0).getAmount() != itemStack.getAmount()) {
						if(!whereToSpawnParticles.contains(block)) {
							whereToSpawnParticles.add(block);
						}
					}

					// give the player all items back that could not be stored because chest is full
					for (ItemStack unstorableItemStack : unstorableItemStacks.values()) {
						System.out.println("   could not store " + unstorableItemStack.getAmount() + "x "
								+ unstorableItemStack.getType().name());
						playerInventory.addItem(unstorableItemStack);
					}
				}
			}
		}
		
		if(whereToSpawnParticles.size() > 0) {
			for(Block block : whereToSpawnParticles) {
				World world = block.getLocation().getWorld();
				world.spawnParticle(particleType, 
						block.getLocation().add(0.5,0.75,0.5), particleCount);
				System.out.println("Spawning particles");
			}
		} else {
			p.sendMessage("There was nothing to unload. Maybe you have no chests for the remaining items?");
		}

	}

}
