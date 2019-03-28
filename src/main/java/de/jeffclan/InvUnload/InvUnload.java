package de.jeffclan.InvUnload;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;

public class InvUnload extends JavaPlugin implements CommandExecutor, Listener {

	private static final int currentConfigVersion = 2;

	public static int freeSlotsInChest(Block block) {

		int freeSlots = 0;

		if (block.getState() instanceof Chest) {
			Inventory inv = ((Chest) block.getState()).getInventory();

			for (ItemStack itemStack : inv.getContents()) {
				if (itemStack == null || itemStack.getAmount() == 0 || itemStack.getType().equals(Material.AIR)) {
					freeSlots++;
				}
			}

		}

		return freeSlots;
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

	boolean usingMatchingConfig = true;
	/*
	 * Whoever has to read this code: I know, it is not organized very well, but it
	 * runs extremely fast. We are using some magic to do stuff easier than I
	 * actually planned so if you want to organize it, feel free to do so
	 */

	UpdateChecker updateChecker;

	private int defaultChestRadius = 10;

	private int maxChestRadius = 20;

	private int particleCount = 100;
	private Particle particleType = Particle.SPELL_WITCH;

	private Particle particleTypeDump = Particle.SPELL;

	private Messages messages;

	private int updateCheckInterval = 86400;

	protected WorldGuard worldGuard;

	// Checks permission for chest usage
	protected boolean canUseChestHere(Player player, Location loc) {
		
		try {
			 Class.forName( "com.sk89q.worldguard.bukkit.WorldGuardPlugin" );
			 Class.forName( "com.sk89q.worldguard.WorldGuard" );
			} catch( ClassNotFoundException e ) {
				//getLogger().info("WorldGuard not found");
				return true;
			}
		
		if (WorldGuard.getInstance() == null || getWorldGuard() == null
				|| getConfig().getBoolean("use-worldguard") == false) {
			return true;
		}

		// getLogger().info("WorldGuard is active");
		
		
		// return false when use is prohibited
		if (WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery().queryState(
				BukkitAdapter.adapt(loc), getWorldGuard().wrapPlayer(player), Flags.USE) == StateFlag.State.DENY) {
			 //getLogger().info("No Use access here!");
			return false;
		}		

		if (WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery().queryState(
				BukkitAdapter.adapt(loc), getWorldGuard().wrapPlayer(player),
				Flags.CHEST_ACCESS) == StateFlag.State.DENY) {
			 //getLogger().info("No chest access here!");
			
			return false;
		}
		
	

		// getLogger().info("This chest is accessible.");
		return true;
	}

	private void dumpInventory(Player p, int radius) {
		ArrayList<ChestSpaceCombination> chestSpaceCombinations = new ArrayList<ChestSpaceCombination>();
		List<Block> whereToSpawnParticles = new ArrayList<Block>();

		if (getNearbyChests(p.getLocation(), radius, p).size() == 0) {
			p.sendMessage(messages.MSG_NO_CHESTS_NEARBY);
			return;
		}

		for (Block block : getNearbyChests(p.getLocation(), radius, p)) {
			InventoryHolder holder = ((Chest) block.getState()).getInventory().getHolder();
			if (holder instanceof DoubleChest) {
				DoubleChest doubleChest = ((DoubleChest) holder);
				Chest leftChest = (Chest) doubleChest.getLeftSide();
				// Chest rightChest = (Chest) doubleChest.getRightSide();
				int slotsFreeInThisChest = freeSlotsInChest(leftChest.getBlock());
				chestSpaceCombinations.add(new ChestSpaceCombination(slotsFreeInThisChest, leftChest.getBlock(),
						leftChest.getBlock().hashCode()));
			} else {
				chestSpaceCombinations.add(new ChestSpaceCombination(freeSlotsInChest(block), block, block.hashCode()));
			}
		}

		Collections.sort(chestSpaceCombinations, Collections.reverseOrder());

		ArrayList<ItemStack> playerStuffList = new ArrayList<ItemStack>();
		for (ItemStack is : p.getInventory().getStorageContents()) {
			playerStuffList.add(is);
		}
		for (int i = 0; i < 9; i++) { // iterate through hotbar
			playerStuffList.remove(p.getInventory().getItem(i));
		}
		ItemStack[] playerStuff = playerStuffList.toArray(new ItemStack[playerStuffList.size()]);

		for (ItemStack itemStack : playerStuff) {
			if (isItemStackEmpty(itemStack))
				continue;

			ArrayList<Block> chestInventories = new ArrayList<Block>();
			for (ChestSpaceCombination comb : chestSpaceCombinations) {
				chestInventories.add(comb.block);
			}

			p.getInventory().remove(itemStack);
			int amount = itemStack.getAmount();
			HashMap<Integer, ItemStack> unstorable = ((InventoryHolder) chestInventories.get(0).getState())
					.getInventory().addItem(itemStack);

			if (unstorable.size() == 0 || unstorable.get(0).getAmount() != amount) {

				InventoryHolder holder = ((Chest) chestInventories.get(0).getState()).getInventory().getHolder();
				if (holder instanceof DoubleChest) {
					DoubleChest doubleChest = ((DoubleChest) holder);
					Chest leftChest = (Chest) doubleChest.getLeftSide();
					Chest rightChest = (Chest) doubleChest.getRightSide();
					if (!whereToSpawnParticles.contains(rightChest.getBlock())) {
						whereToSpawnParticles.add(rightChest.getBlock());
					}
					if (!whereToSpawnParticles.contains(leftChest.getBlock())) {
						whereToSpawnParticles.add(leftChest.getBlock());
					}
				}

				if (!whereToSpawnParticles.contains(chestInventories.get(0))) {
					whereToSpawnParticles.add(chestInventories.get(0));
				}
			}

			chestInventories.remove(0);

			while (unstorable.size() > 0 && chestInventories.size() > 0) {
				int amount1 = unstorable.get(0).getAmount();
				unstorable = ((InventoryHolder) chestInventories.get(0).getState()).getInventory()
						.addItem(unstorable.get(0));
				if (unstorable.size() == 0 || unstorable.get(0).getAmount() != amount1) {

					InventoryHolder holder = ((Chest) chestInventories.get(0).getState()).getInventory().getHolder();
					if (holder instanceof DoubleChest) {
						DoubleChest doubleChest = ((DoubleChest) holder);
						Chest leftChest = (Chest) doubleChest.getLeftSide();
						Chest rightChest = (Chest) doubleChest.getRightSide();
						if (!whereToSpawnParticles.contains(rightChest.getBlock())) {
							whereToSpawnParticles.add(rightChest.getBlock());
						}
						if (!whereToSpawnParticles.contains(leftChest.getBlock())) {
							whereToSpawnParticles.add(leftChest.getBlock());
						}
					}

					if (!whereToSpawnParticles.contains(chestInventories.get(0))) {
						whereToSpawnParticles.add(chestInventories.get(0));
					}
				}
			}
			if (unstorable.size() > 0) {
				p.getInventory().addItem(unstorable.get(0));
			}

		}

		if (whereToSpawnParticles.size() > 0) {
			for (Block block : whereToSpawnParticles) {
				World world = block.getLocation().getWorld();
				world.spawnParticle(particleTypeDump, block.getLocation().add(0.5, 0.75, 0.5), particleCount);
				// System.out.println("Spawning particles for dump");
			}
		}

	}

	public List<Block> getNearbyChests(Location location, int radius, Player player) {
		List<Block> blocks = new ArrayList<Block>();
		for (int x = location.getBlockX() - radius; x <= location.getBlockX() + radius; x++) {
			for (int y = location.getBlockY() - radius; y <= location.getBlockY() + radius; y++) {
				for (int z = location.getBlockZ() - radius; z <= location.getBlockZ() + radius; z++) {
					if (location.getWorld().getBlockAt(x, y, z).getState() instanceof Chest) {
						if (canUseChestHere(player, location.getWorld().getBlockAt(x, y, z).getLocation())) {
							blocks.add(location.getWorld().getBlockAt(x, y, z));
						}
					}
				}
			}
		}
		return blocks;
	}

	protected WorldGuardPlugin getWorldGuard() {
		
		try {
			 Class.forName( "com.sk89q.worldguard.bukkit.WorldGuardPlugin" );
			} catch( ClassNotFoundException e ) {
			 return null;
			}
		
		WorldGuardPlugin WG = (WorldGuardPlugin) getServer().getPluginManager().getPlugin("WorldGuard");
		if (WG == null)
			return null;

		return WG;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command arg1, String arg2, String[] arg3) {
		if (!(sender instanceof Player)) {
			return true;
		}

		Player p = (Player) sender;

		if (arg1.getName().equalsIgnoreCase("unload")) {

			int radius = defaultChestRadius;
			if (arg3.length > 0) {
				if (!Utils.isInteger(arg3[0])) {
					p.sendMessage(String.format(messages.MSG_NOT_A_NUMBER, arg3[0]));
					return true;
				} else {
					radius = Integer.parseInt(arg3[0]);
					if (radius > maxChestRadius) {
						p.sendMessage(messages.MSG_RADIUS_TOO_HIGH);
						return true;
					}
				}
			}
			unloadInventory(p, radius, true);
			return true;
		} else if (arg1.getName().equalsIgnoreCase("dump")) {

			int radius = defaultChestRadius;
			if (arg3.length > 0) {
				if (!Utils.isInteger(arg3[0])) {
					p.sendMessage(String.format(messages.MSG_NOT_A_NUMBER, arg3[0]));
					return true;
				} else {
					radius = Integer.parseInt(arg3[0]);
					if (radius > maxChestRadius) {
						p.sendMessage(messages.MSG_RADIUS_TOO_HIGH);
						return true;
					}
				}
			}
			if (getConfig().getBoolean("unload-before-dumping") && p.hasPermission("invunload.unload")) {

				unloadInventory(p, radius, false);
			}
			dumpInventory(p, radius);
			return true;
		}

		return false;
	}

	@Override
	public void onEnable() {

		saveDefaultConfig();

		worldGuard = null;

		if (getConfig().getBoolean("use-worldguard")) {
			try {
				@SuppressWarnings({ "rawtypes", "unused" })
				Class cls = Class.forName("com.sk89q.worldguard.WorldGuard");
				worldGuard = WorldGuard.getInstance();

				if (!(worldGuard instanceof WorldGuard)) {
					worldGuard = null;
				}

			} catch (ClassNotFoundException e) {
				// Dop nothing
			}

			if (worldGuard != null) {
				getLogger().info("Hooked into WorldGuard succesfully.");
				getLogger().info("*** Please note that WorldGuard integration is currently experimental ***");
				getLogger().info("*** See the config.yml file for more information ***");
			} 
		}

		// Config version prior to 1? Then it must have been generated by InvUnload 0.x
		if (getConfig().getInt("config-version", 0) < 1) {
			getLogger().warning("========================================================");
			getLogger().warning("You are using a config file that has been generated");
			getLogger().warning("prior to InvUnload 1.0.0.");
			getLogger().warning("To allow everyone to use the new features, your config");
			getLogger().warning("has been renamed to config.old.yml and a new one has");
			getLogger().warning("been generated. Please examine the new config file to");
			getLogger().warning("see the new possibilities and adjust your settings.");
			getLogger().warning("========================================================");

			File configFile = new File(getDataFolder().getAbsolutePath() + File.separator + "config.yml");
			File oldConfigFile = new File(getDataFolder().getAbsolutePath() + File.separator + "config.old.yml");
			if (oldConfigFile.getAbsoluteFile().exists()) {
				oldConfigFile.getAbsoluteFile().delete();
			}
			configFile.getAbsoluteFile().renameTo(oldConfigFile.getAbsoluteFile());
			saveDefaultConfig();
			try {
				getConfig().load(configFile.getAbsoluteFile());
			} catch (IOException | InvalidConfigurationException e) {
				getLogger().warning("Could not load freshly generated config file!");
				e.printStackTrace();
			}
		} else if (getConfig().getInt("config-version", 0) != currentConfigVersion) {
			getLogger().warning("========================================================");
			getLogger().warning("YOU ARE USING AN OLD CONFIG FILE!");
			getLogger().warning("This is not a problem, as InvUnload will just use the");
			getLogger().warning("default settings for unset values. However, if you want");
			getLogger().warning("to configure the new options, please go to");
			getLogger().warning("https://www.spigotmc.org/resources/1-13-invunload-beta.60095/");
			getLogger().warning("and replace your config.yml with the new one. You can");
			getLogger().warning("then insert your old changes into the new file.");
			getLogger().warning("========================================================");
			usingMatchingConfig = false;
		}

		getCommand("unload").setExecutor(this);
		getCommand("dump").setExecutor(this);
		getServer().getPluginManager().registerEvents(this, this);
		messages = new Messages(this);

		@SuppressWarnings("unused")
		Metrics metrics = new Metrics(this);

		getConfig().addDefault("unload-before-dumping", true);
		getConfig().addDefault("default-chest-radius", 10);
		getConfig().addDefault("max-chest-radius", 20);
		getConfig().addDefault("config-version", 0);
		getConfig().addDefault("check-for-updates", "true");
		getConfig().addDefault("use-worldguard", false);

		updateChecker = new UpdateChecker(this);

		if (getConfig().getString("check-for-updates", "true").equalsIgnoreCase("true")) {
			Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
				public void run() {
					updateChecker.checkForUpdate();
				}
			}, 0L, updateCheckInterval * 20);
		} else if (getConfig().getString("check-for-updates", "true").equalsIgnoreCase("on-startup")) {
			updateChecker.checkForUpdate();
		}

	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		if (event.getPlayer().isOp()) {
			updateChecker.sendUpdateMessage(event.getPlayer());
		}
	}

	public boolean unloadInventory(Player p, int radius, boolean showMessageOnFail) {

		List<Block> nearbyBlocks = getNearbyChests(p.getLocation(), radius, p);

		if (nearbyBlocks.size() == 0) {
			if (showMessageOnFail)
				p.sendMessage(messages.MSG_NO_CHESTS_NEARBY);
			return false;
		}

		List<Block> whereToSpawnParticles = new ArrayList<Block>();
		Inventory playerInventory = p.getInventory();

		for (Block block : nearbyBlocks) {

			Inventory chestInventory = ((Chest) block.getState()).getInventory();

			// iterate through all items in the player's inventory

			for (ItemStack itemStack : playerInventory.getStorageContents()) {

				// discard empty stacks
				if (isItemStackEmpty(itemStack))
					continue;

				// only continue if the inventory already contains what we want to store
				if (getInventoryContents(chestInventory).contains(itemStack.getType())) {

					playerInventory.remove(itemStack);

					// try to add it to the chest
					HashMap<Integer, ItemStack> unstorableItemStacks = chestInventory.addItem(itemStack);

					if (unstorableItemStacks.size() == 0
							|| unstorableItemStacks.get(0).getAmount() != itemStack.getAmount()) {
						InventoryHolder holder = ((Chest) block.getState()).getInventory().getHolder();
						if (holder instanceof DoubleChest) {
							DoubleChest doubleChest = ((DoubleChest) holder);
							Chest leftChest = (Chest) doubleChest.getLeftSide();
							Chest rightChest = (Chest) doubleChest.getRightSide();
							if (!whereToSpawnParticles.contains(leftChest.getBlock())) {
								whereToSpawnParticles.add(leftChest.getBlock());
							}
							if (!whereToSpawnParticles.contains(rightChest.getBlock())) {
								whereToSpawnParticles.add(rightChest.getBlock());
							}
						} else if (!whereToSpawnParticles.contains(block)) {

							whereToSpawnParticles.add(block);

						}
					}

					// give the player all items back that could not be stored because chest is full
					for (ItemStack unstorableItemStack : unstorableItemStacks.values()) {
						// System.out.println(" could not store " + unstorableItemStack.getAmount() + "x
						// "
						// + unstorableItemStack.getType().name());
						playerInventory.addItem(unstorableItemStack);
					}
				}
			}
		}

		if (whereToSpawnParticles.size() > 0) {
			for (Block block : whereToSpawnParticles) {
				World world = block.getLocation().getWorld();
				world.spawnParticle(particleType, block.getLocation().add(0.5, 0.75, 0.5), particleCount);
			}
			return true;
		} else {
			if (showMessageOnFail)
				p.sendMessage(messages.MSG_COULD_NOT_UNLOAD);
			return false;
		}

	}
}
