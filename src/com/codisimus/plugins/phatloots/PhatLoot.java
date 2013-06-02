package com.codisimus.plugins.phatloots;

import java.io.*;
import java.util.*;
import net.milkbowl.vault.economy.EconomyResponse;
import org.apache.commons.lang.WordUtils;
import org.apache.commons.lang.time.DateUtils;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * A PhatLoot is a reward made up of money and items
 *
 * @author Codisimus
 */
@SerializableAs("PhatLoot")
public class PhatLoot implements ConfigurationSerializable {
    static String current;
    static String last;
    static boolean onlyDropOnPlayerKill;
    static boolean replaceMobLoot;
    static boolean displayTimeRemaining;
    static boolean displayMobTimeRemaining;
    static float chanceOfDrop;
    static double lootingBonusPerLvl;
    static boolean autoClose;
    static boolean decimals;

    public String name; //A unique name for the Warp
    public int moneyLower; //Range of money that may be given
    public int moneyUpper;
    public int expLower; //Range of experience gained when looting
    public int expUpper;
    public ArrayList<Loot> lootList; //List of Loot

    public int days = PhatLootsConfig.defaultDays; //Reset time (will never reset if any are negative)
    public int hours = PhatLootsConfig.defaultHours;
    public int minutes = PhatLootsConfig.defaultMinutes;
    public int seconds = PhatLootsConfig.defaultSeconds;
    public boolean global = PhatLootsConfig.defaultGlobal; //Reset Type
    public boolean round = PhatLootsConfig.defaultRound;
    public boolean autoLoot = PhatLootsConfig.defaultAutoLoot;
    private HashSet<PhatLootChest> chests = new HashSet<PhatLootChest>(); //List of Chests linked to this PhatLoot
    Properties lootTimes = new Properties(); //PhatLootChest'PlayerName=Year'Day'Hour'Minute'Second

    /* OLD */
    public static final int INDIVIDUAL = 0, COLLECTIVE1 = 1,
            COLLECTIVE2 = 2, COLLECTIVE3 = 3, COLLECTIVE4 = 4,
            COLLECTIVE5 = 5, COLLECTIVE6 = 6, COLLECTIVE7 = 7,
            COLLECTIVE8 = 8, COLLECTIVE9 = 9, COLLECTIVE10 = 10;
    public int numberCollectiveLoots = PhatLootsConfig.defaultLowerNumberOfLoots; //Amount of loots received from each collective loot
    private ArrayList<OldLoot>[] lootTables = (ArrayList[]) new ArrayList[11]; //List of items that may be given
    public ArrayList<String> commands = new ArrayList<String>(); //Commands that will be run upon looting the Chest

    /**
     * Constructs a new PhatLoot
     *
     * @param name The name of the PhatLoot which will be created
     */
    public PhatLoot(String name) {
        this.name = name;
        lootList = new ArrayList<Loot>();
    }

    public PhatLoot(Map<String, Object> map) {
        String currentLine = null;
        try {
            current = name = (String) map.get(currentLine = "Name");

            Map nestedMap = (Map) map.get(currentLine = "Reset");
            days = (Integer) nestedMap.get(currentLine = "Days");
            hours = (Integer) nestedMap.get(currentLine = "Hours");
            minutes = (Integer) nestedMap.get(currentLine = "Minutes");
            seconds = (Integer) nestedMap.get(currentLine = "Seconds");

            global = (Boolean) map.get(currentLine = "Global");
            round = (Boolean) map.get(currentLine = "RoundDownTime");
            autoLoot = (Boolean) map.get(currentLine = "AutoLoot");

            nestedMap = (Map) map.get(currentLine = "Money");
            moneyUpper = (Integer) nestedMap.get(currentLine = "Upper");
            moneyLower = (Integer) nestedMap.get(currentLine = "Lower");

            nestedMap = (Map) map.get(currentLine = "Exp");
            expUpper = (Integer) nestedMap.get(currentLine = "Upper");
            expLower = (Integer) nestedMap.get(currentLine = "Lower");

            //Check which version the file is
            if (map.containsKey(currentLine = "LootList")) { //3.1+
                lootList = (ArrayList) map.get(currentLine = "LootList");
            } else { //pre-3.1
                numberCollectiveLoots = (Integer) map.get(currentLine = "NumberCollectiveLoots");

                nestedMap = (Map) map.get(currentLine = "Loots");
                lootTables[0] = (ArrayList) nestedMap.get(currentLine = "Individual");
                for (int i = 1; i < 11; i++) {
                    lootTables[i] = (ArrayList) nestedMap.get(currentLine = "Coll" + i);
                    Collections.sort(lootTables[i]);
                }

                if (map.containsKey(currentLine = "Commands")) {
                    commands = (ArrayList) map.get(currentLine = "Commands");
                }

                convert();
                save();
            }

            loadChests();
            loadLootTimes();
        } catch (Exception ex) {
            PhatLoots.logger.severe("Failed to load line: " + currentLine);
            PhatLoots.logger.severe("of PhatLoot: " + (current == null ? "unknown" : current));
            if (current == null) {
                PhatLoots.logger.severe("Last successfull load was...");
                PhatLoots.logger.severe("PhatLoot: " + (last == null ? "unknown" : last));
            }
        }
        last = current;
        current = null;
    }

    public void rollForLoot(Player player, PhatLootChest chest, Inventory inventory) {
        String user = player.getName();
        if (this.global) {
            user = "global";
        }

        long time = getTime(chest, user);

        if (time > 0L) {
            String timeRemaining = getTimeRemaining(time);

            if (timeRemaining == null) {
                return;
            }

            if (!timeRemaining.equals("0")) {
                if (PhatLootsConfig.timeRemaining != null) {
                    player.sendMessage(PhatLootsConfig.timeRemaining.replace("<time>", timeRemaining));
                }
                return;
            }

        }

        inventory.clear();

        if (moneyUpper > 0) {
            double amount = PhatLoots.random.nextInt(moneyUpper + 1 - moneyLower);
            amount += moneyLower;
            if (decimals) {
                amount = amount / 100;
            }

            if (amount > 0) {
                if (PhatLoots.econ != null) {
                    EconomyResponse r = PhatLoots.econ.depositPlayer(player.getName(), amount);
                    if (r.transactionSuccess() && PhatLootsConfig.moneyLooted != null) {
                        String money = PhatLoots.econ.format(amount).replace(".00", "");
                        player.sendMessage(PhatLootsConfig.moneyLooted.replace("<amount>", money));
                    }
                } else {
                    player.sendMessage("§6Vault §4is not enabled, so no money can be processed.");
                }
            } else if (amount < 0) {
                if (PhatLoots.econ != null) {
                    EconomyResponse r = PhatLoots.econ.withdrawPlayer(player.getName(), amount);
                    String money = PhatLoots.econ.format(amount).replace(".00", "");
                    if (r.transactionSuccess()) {
                        if (PhatLootsConfig.moneyCharged != null) {
                            player.sendMessage(PhatLootsConfig.moneyCharged.replace("<amount>", money));
                        }
                    } else {
                        if (PhatLootsConfig.insufficientFunds != null) {
                            player.sendMessage(PhatLootsConfig.insufficientFunds.replace("<amount>", money));
                        }
                        if (autoClose) {
                            player.closeInventory();
                            PhatLoots.closeInventory(player, inventory, chest.getBlock().getLocation(), global);
                        }
                        return;
                    }
                } else {
                    player.sendMessage("§6Vault §4is not enabled, so no money can be processed.");
                }
            }

        }

        if (expUpper > 0) {
            int amount = PhatLoots.random.nextInt(expUpper + 1 - expLower);
            amount += expLower;

            if (amount > 0) {
                player.giveExp(amount);
                if (PhatLootsConfig.experienceLooted != null) {
                    player.sendMessage(PhatLootsConfig.experienceLooted.replace("<amount>", String.valueOf(amount)));
                }
            }
        }

        if (PhatLootsConfig.lootMessage != null) {
            player.sendMessage(PhatLootsConfig.lootMessage.replace("<phatloot>", name));
        }
        if (PhatLootsConfig.lootBroadcast != null) {
            PhatLoots.server.broadcastMessage(PhatLootsConfig.lootBroadcast.replace("<name>", player.getName()).replace("<phatloot>", name));
        }

        boolean itemsInChest = chest.addLoots(lootAll(player, 0), player, inventory, autoLoot);

        if (!chest.isDispenser) {
            player.updateInventory();
        }

        if (autoLoot && !itemsInChest) {
            player.closeInventory();
            switch (chest.getBlock().getType()) {
            case CHEST:
            case TRAPPED_CHEST:
            case ENDER_CHEST:
                PhatLoots.closeInventory(player, inventory, chest.getBlock().getLocation(), global);
            default: break;
            }
        }

        if (global && (days < 0 || hours < 0 || minutes < 0 || seconds < 0)) {
            chests.remove(chest);
            saveChests();
        } else {
            setTime(chest, user);
        }
    }

    public int rollForLoot(Player player, List<ItemStack> drops) {
        ItemStack weapon = player == null ? null : player.getItemInHand();
        double lootingBonus = weapon == null
                              ? 0
                              :lootingBonusPerLvl * weapon.getEnchantmentLevel(Enchantment.LOOT_BONUS_MOBS);

        if (onlyDropOnPlayerKill && player == null) {
            drops.clear();
            return 0;
        }
        if (replaceMobLoot) {
            Iterator itr = drops.iterator();
            while (itr.hasNext()) {
                ItemStack item = (ItemStack) itr.next();
                if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                    continue;
                }
                itr.remove();
            }
        }

        if (player != null) {
            long time = getMobLootTime(player.getName());

            if (time > 0) {
                String timeRemaining = getTimeRemaining(time);

                if (timeRemaining == null) {
                    return 0;
                }

                if (!timeRemaining.equals("0")) {
                    if (PhatLootsConfig.mobTimeRemaining != null) {
                        player.sendMessage(PhatLootsConfig.mobTimeRemaining.replace("<time>", timeRemaining));
                    }
                    return 0;
                }
            }
        }

        List<ItemStack> loot = lootAll(player, lootingBonus);
        if (player != null && PhatLootsConfig.mobDroppedItem != null) {
            for (ItemStack item : loot) {
                String msg = PhatLootsConfig.mobDroppedItem.replace("<item>", getItemName(item));
                int amount = item.getAmount();
                msg = amount > 1
                      ? msg.replace("<amount>", String.valueOf(amount))
                      : msg.replace("x<amount>", "").replace("<amount>", "");
                player.sendMessage(msg);
            }
        }
        drops.addAll(loot);

        if (moneyUpper > 0 && player != null) {
            double amount = PhatLoots.random.nextInt(moneyUpper + 1 - moneyLower);
            amount += moneyLower;
            if (decimals) {
                amount = amount / 100;
            }

            if (amount > 0 && !player.getGameMode().equals(GameMode.CREATIVE) && player.hasPermission("phatloots.moneyfrommobs")) {
                if (PhatLoots.econ != null) {
                    EconomyResponse r = PhatLoots.econ.depositPlayer(player.getName(), amount);
                    if (r.transactionSuccess() && PhatLootsConfig.mobDroppedMoney != null) {
                        String money = PhatLoots.econ.format(amount).replace(".00", "");
                        player.sendMessage(PhatLootsConfig.mobDroppedMoney.replace("<amount>", money));
                    }
                } else {
                    player.sendMessage("§6Vault §4is not enabled, so no money can be processed.");
                }
            }
        }

        if (expUpper > 0) {
            int amount = PhatLoots.random.nextInt(expUpper + 1 - expLower);
            amount += expLower;
            if (player != null && PhatLootsConfig.mobDroppedExperience != null) {
                player.sendMessage(PhatLootsConfig.mobDroppedExperience.replace("<amount>", String.valueOf(amount)));
            }
            return amount;
        }

        return 0;
    }

    public void rollForLoot(LivingEntity entity, double level) {
        LinkedList<ItemStack> loot = lootAll(null, level);
        if (loot.size() != 5) {
            PhatLoots.logger.warning("Cannot add loot to " + entity.getType().getName() + " because the amount of loot was not equal to 5");
        }

        EntityEquipment eqp = entity.getEquipment();
        eqp.setItemInHand(loot.removeFirst());
        eqp.setHelmet(loot.removeFirst());
        eqp.setChestplate(loot.removeFirst());
        eqp.setLeggings(loot.removeFirst());
        eqp.setBoots(loot.removeFirst());

        eqp.setItemInHandDropChance(chanceOfDrop);
        eqp.setHelmetDropChance(chanceOfDrop);
        eqp.setChestplateDropChance(chanceOfDrop);
        eqp.setLeggingsDropChance(chanceOfDrop);
        eqp.setBootsDropChance(chanceOfDrop);

//        EntityEquipment eqp = entity.getEquipment();
//        LinkedList<ItemStack> loot = new LinkedList();
//        LootCollection coll = getCollection("hand");
//        if (coll != null) {
//            coll.getLoot(null, level, loot);
//            eqp.setItemInHand(loot.removeLast());
//            eqp.setItemInHandDropChance(chanceOfDrop);
//        }
//        coll = getCollection("helmet");
//        if (coll != null) {
//            coll.getLoot(null, level, loot);
//            eqp.setHelmet(loot.removeLast());
//            eqp.setHelmetDropChance(chanceOfDrop);
//        }
//        coll = getCollection("chestplate");
//        if (coll != null) {
//            coll.getLoot(null, level, loot);
//            eqp.setChestplate(loot.removeLast());
//            eqp.setChestplateDropChance(chanceOfDrop);
//        }
//        coll = getCollection("leggings");
//        if (coll != null) {
//            coll.getLoot(null, level, loot);
//            eqp.setLeggings(loot.removeLast());
//            eqp.setLeggingsDropChance(chanceOfDrop);
//        }
//        coll = getCollection("boots");
//        if (coll != null) {
//            coll.getLoot(null, level, loot);
//            eqp.setBoots(loot.removeLast());
//            eqp.setBootsDropChance(chanceOfDrop);
//        }
    }

    /**
     * Returns the remaining time until the PhatLootChest resets
     * Returns null if the PhatLootChest never resets
     *
     * @param time The given time
     * @return the remaining time until the PhatLootChest resets
     */
    public String getTimeRemaining(long time) {
        //Return null if the reset time is set to never
        if (days < 0 || hours < 0 || minutes < 0 || seconds < 0) {
            return null;
        }

        //Calculate the time that the chest will reset
        time += days * DateUtils.MILLIS_PER_DAY
                + hours * DateUtils.MILLIS_PER_HOUR
                + minutes * DateUtils.MILLIS_PER_MINUTE
                + seconds * DateUtils.MILLIS_PER_SECOND;

        long timeRemaining = time - System.currentTimeMillis();

        if (timeRemaining > DateUtils.MILLIS_PER_DAY) {
            return (int) timeRemaining / DateUtils.MILLIS_PER_DAY + " day(s)";
        } else {
            if (timeRemaining > DateUtils.MILLIS_PER_HOUR) {
                return (int) timeRemaining / DateUtils.MILLIS_PER_HOUR + " hour(s)";
            } else {
                if (timeRemaining > DateUtils.MILLIS_PER_MINUTE) {
                    return (int) timeRemaining / DateUtils.MILLIS_PER_MINUTE + " minute(s)";
                } else {
                    if (timeRemaining > DateUtils.MILLIS_PER_SECOND) {
                        return (int) timeRemaining / DateUtils.MILLIS_PER_SECOND + " second(s)";
                    } else {
                        return "0";
                    }
                }
            }
        }
    }

    /**
     * Fills the Chest (Block) with loot
     * Each item is rolled for to determine if it will by added to the Chest
     * Money is rolled for to determine how much will be given within the range
     */
    public LinkedList<ItemStack> lootAll(Player player, double lootingBonus) {
        LinkedList<ItemStack> itemList = new LinkedList<ItemStack>();
        for (Loot loot : lootList) {
            if (loot.rollForLoot(lootingBonus)) {
                loot.getLoot(player, lootingBonus, itemList);
            }
        }
        return itemList;
    }

    /**
     * Updates the Player's time value in the Map with the current time
     *
     * @param chest The PhatLootChest to set the time for
     * @param player The Player whose time is to be updated
     */
    public void setTime(PhatLootChest chest, String player) {
        Calendar calendar = Calendar.getInstance();

        if (round) {
            if (seconds == 0) {
                calendar.clear(Calendar.SECOND);
                if (minutes == 0) {
                    calendar.clear(Calendar.MINUTE);
                    if (hours == 0) {
                        calendar.clear(Calendar.HOUR_OF_DAY);
                    }
                }
            }
        }

        lootTimes.setProperty(chest.toString() + "'" + player, String.valueOf(System.currentTimeMillis()));
    }

    /**
     * Retrieves the time for the given Player
     *
     * @param player The Player whose time is requested
     * @return The time as a long
     */
    public long getMobLootTime(String player) {
        String string = lootTimes.getProperty(player);
        long time = 0;

        if (string != null) {
            try {
                time = Long.parseLong(string);
            } catch (Exception corruptData) {
                PhatLoots.logger.severe("Fixed corrupted time value!");
            }
        }

        return time;
    }

    /**
     * Retrieves the time for the given Player
     *
     * @param chest The PhatLootChest to set the time for
     * @param player The Player whose time is requested
     * @return The time as an array of ints
     */
    public long getTime(PhatLootChest chest, String player) {
        String string = lootTimes.getProperty(chest.toString() + "'" + player);
        long time = 0;

        if (string != null) {
            try {
                time = Long.parseLong(string);
            } catch (Exception corruptData) {
                PhatLoots.logger.severe("Fixed corrupted time value!");
            }
        }

        return time;
    }

    public void addChest(String string) {
        chests.add(new PhatLootChest(string.split("'")));
    }

    /**
     * Creates a PhatLootChest for the given Block and links it to this PhatLoot
     *
     * @param block The given Block
     */
    public void addChest(Block block) {
        chests.add(new PhatLootChest(block));
    }

    /**
     * Removes the PhatLootChest for the given Block from this PhatLoot
     *
     * @param block The given Block
     */
    public void removeChest(Block block) {
        chests.remove(new PhatLootChest(block));
    }

    /**
     * Returns whether the given PhatLootChest is linked to this PhatLoot
     *
     * @param chest The given PhatLootChest
     * @return true if the PhatLoot chest is linked
     */
    public boolean containsChest(PhatLootChest chest) {
        return chests.contains(chest);
    }

    /**
     * Returns a Collection of PhatLootChests linked to this PhatLoot
     *
     * @return a Collection of linked chests
     */
    public Collection<PhatLootChest> getChests() {
        return chests;
    }

    public boolean addLoot(Loot target) {
        for (Loot loot : lootList) {
            if (loot.equals(target)) {
                return false;
            }
        }
        lootList.add(target);
        return true;
    }

    public boolean removeLoot(Loot target) {
        Iterator<Loot> itr = lootList.iterator();
        while (itr.hasNext()) {
            if (itr.next().equals(target)) {
                itr.remove();
                return true;
            }
        }
        return false;
    }

    public LootCollection findCollection(String target) {
        for (Loot loot : lootList) {
            if (loot instanceof LootCollection) {
                LootCollection coll = ((LootCollection) loot).findCollection(target);
                if (coll != null) {
                    return coll;
                }
            }
        }
        return null;
    }

    /**
     * Resets the user times for all PhatLootChests of this PhatLoot
     * If a Block is given then only reset that PhatLootChest
     *
     * @param block The given Block
     */
    public void reset(Block block) {
        if (block == null) {
            //Reset all PhatLootChests
            lootTimes.clear();
        } else {
            //Find the PhatLootChest of the given Block and reset it
            String chest = block.getWorld().getName() + "'" + block.getX() + "'" + block.getY() + "'" + block.getZ() + "'";
            for (String key : lootTimes.stringPropertyNames()) {
                if (key.startsWith(chest)) {
                    lootTimes.remove(key);
                }
            }
        }
    }

    public void clean(Block block) {
        if (days == 0 && hours == 0 && minutes == 0 && seconds == 0) {
            reset(block);
            return;
        }
        Set<String> keys;
        if (block == null) {
            keys = lootTimes.stringPropertyNames();
        } else {
            keys = new HashSet();
            String chest = block.getWorld().getName() + "'" + block.getX() + "'" + block.getY() + "'" + block.getZ() + "'";
            for (String key : lootTimes.stringPropertyNames()) {
                if (key.startsWith(chest)) {
                    keys.add(key);
                }
            }
        }

        long time = System.currentTimeMillis()
                    - days * DateUtils.MILLIS_PER_DAY
                    - hours * DateUtils.MILLIS_PER_HOUR
                    - minutes * DateUtils.MILLIS_PER_MINUTE
                    - seconds * DateUtils.MILLIS_PER_SECOND;

        for (String key : keys) {
            if (Long.parseLong(lootTimes.getProperty(key)) < time) {
                lootTimes.remove(key);
            }
        }
    }

    public void saveAll() {
        save();
        saveLootTimes();
        saveChests();
    }

    public void save() {
        try {
            YamlConfiguration config = new YamlConfiguration();
            File file = new File(PhatLoots.dataFolder + File.separator + "LootTables" + File.separator + name + ".yml");

            config.set(name, this);
            config.save(file);
        } catch (Exception saveFailed) {
            PhatLoots.logger.severe("Save Failed!");
            saveFailed.printStackTrace();
        }
    }

    public void saveLootTimes() {
        if (lootTimes.isEmpty()) {
            return;
        }

        FileOutputStream fos = null;
        try {
            File file = new File(PhatLoots.dataFolder + File.separator + "LootTimes" + File.separator + name + ".properties");

            fos = new FileOutputStream(file);
            lootTimes.store(fos, null);
        } catch (Exception saveFailed) {
            PhatLoots.logger.severe("Save Failed!");
            saveFailed.printStackTrace();
        } finally {
            try {
                fos.close();
            } catch (Exception e) {
            }
        }
    }

    public void loadLootTimes() {
        FileInputStream fis = null;
        try {
            File file = new File(PhatLoots.dataFolder + File.separator + "LootTimes" + File.separator + name + ".properties");

            if (!file.exists()) {
                return;
            }
            fis = new FileInputStream(file);
            lootTimes.load(fis);
        } catch (Exception loadFailed) {
            PhatLoots.logger.severe("Load Failed!");
            loadFailed.printStackTrace();
        } finally {
            try {
                fis.close();
            } catch (Exception e) {
            }
        }
    }

    public void saveChests() {
        if (chests.isEmpty()) {
            return;
        }

        FileWriter fWriter = null;
        PrintWriter pWriter = null;
        try {
            File file = new File(PhatLoots.dataFolder + File.separator + "Chests" + File.separator + name + ".txt");

            fWriter = new FileWriter(file);
            pWriter = new PrintWriter(fWriter);
            for (PhatLootChest chest : getChests()) {
                pWriter.println(chest.toString());
            }
        } catch (Exception saveFailed) {
            PhatLoots.logger.severe("Save Failed!");
            saveFailed.printStackTrace();
        } finally {
            try {
                fWriter.close();
                pWriter.close();
            } catch (Exception e) {
            }
        }
    }

    public void loadChests() {
        Scanner scanner = null;
        try {
            File file = new File(PhatLoots.dataFolder + File.separator + "Chests" + File.separator + name + ".txt");
            if (!file.exists()) {
                return;
            }

            //Delete empty files
            if (file.length() == 0) {
                file.delete();
            }

            scanner = new Scanner(file);
            while (scanner.hasNext()) {
                addChest(scanner.next());
            }
        } catch (Exception saveFailed) {
            PhatLoots.logger.severe("Load Failed!");
            saveFailed.printStackTrace();
        } finally {
            try {
                scanner.close();
            } catch (Exception e) {
            }
        }
    }

    public static String getItemName(ItemStack item) {
        if (item.hasItemMeta()) {
            String name = item.getItemMeta().getDisplayName();
            if (name != null && !name.isEmpty()) {
                return name;
            }
        }
        return WordUtils.capitalizeFully(item.getType().toString().replace("_", " "));
    }

    public void convertLootTimes() {
        Calendar cal = Calendar.getInstance();
        for (Object key : lootTimes.keySet()) {
            try {
                String s = key.toString();
                String[] fields = lootTimes.getProperty(s).split("'");
                cal.set(Calendar.YEAR, Integer.parseInt(fields[0]));
                cal.set(Calendar.DAY_OF_YEAR, Integer.parseInt(fields[1]));
                cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(fields[2]));
                cal.set(Calendar.MINUTE, Integer.parseInt(fields[3]));
                cal.set(Calendar.SECOND, Integer.parseInt(fields[4]));
                lootTimes.setProperty(s, String.valueOf(cal.getTimeInMillis()));
            } catch (Exception ex) {
                PhatLoots.logger.severe(name + ".loottimes has been corrupted");
            }
        }
        saveLootTimes();
    }

    public int phatLootChestHashCode(Block block) {
        int hash = 7;
        hash = 47 * hash + block.getWorld().getName().hashCode();
        hash = 47 * hash + block.getX();
        hash = 47 * hash + block.getY();
        hash = 47 * hash + block.getZ();
        return hash;
    }

    @Override
    public Map<String, Object> serialize() {
        Map map = new TreeMap();
        map.put("Name", name);

        Map nestedMap = new HashMap();
        nestedMap.put("Days", days);
        nestedMap.put("Hours", hours);
        nestedMap.put("Minutes", minutes);
        nestedMap.put("Seconds", seconds);
        map.put("Reset", nestedMap);

        map.put("Global", global);
        map.put("RoundDownTime", round);
        map.put("AutoLoot", autoLoot);

        nestedMap = new HashMap();
        nestedMap.put("Upper", moneyUpper);
        nestedMap.put("Lower", moneyLower);
        map.put("Money", nestedMap);

        nestedMap = new HashMap();
        nestedMap.put("Upper", expUpper);
        nestedMap.put("Lower", expLower);
        map.put("Exp", nestedMap);

        map.put("LootList", lootList);
        return map;
    }

    /* OLD */
    private void convert() {
        //Convert each command
        for (String cmd : commands) {
            double percent = 100;
            if (cmd.matches(".*%[0-9]*[.]?[0-9]+")) {
                int index = cmd.lastIndexOf('%');
                percent = Double.parseDouble(cmd.substring(index + 1));
                cmd = cmd.substring(0, index);
            }
            CommandLoot command = new CommandLoot(cmd);
            command.setProbability(percent);
            lootList.add(command);
        }

        //Convert each Loot
        for (OldLoot loot : lootTables[INDIVIDUAL]) {
            lootList.add(new Item(loot));
        }

        //Convert each Collection
        for (int i = 1; i <= 10; i++) {
            if (!lootTables[i].isEmpty()) {
                LootCollection coll = new LootCollection(String.valueOf(i));
                for (OldLoot loot : lootTables[i]) {
                    coll.lootList.add(new Item(loot));
                }
                coll.lowerNumberOfLoots = numberCollectiveLoots;
                coll.upperNumberOfLoots = numberCollectiveLoots;
                lootList.add(coll);
            }
        }
    }

    public void setLoots(int id, String lootsString) {
        if (lootsString.isEmpty()) {
            return;
        }

        while ((lootsString.endsWith(",")) || (lootsString.endsWith(" "))) {
            lootsString = lootsString.substring(0, lootsString.length() - 1);
        }

        for (String lootString : lootsString.split(", ")) {
            try {
                String[] lootData = lootString.split("'");

                String item = lootData[0];

                Color color = null;
                String skullOwner = null;
                if (item.startsWith("(")) {
                    int index = item.indexOf(41);
                    String string = item.substring(1, index);
                    if (string.matches("[0-9]+")) {
                        color = Color.fromRGB(Integer.parseInt(item.substring(1, index)));
                    } else {
                        skullOwner = item.substring(1, index);
                    }
                    item = item.substring(index + 1);
                }
                int itemID;
                if (item.contains("+")) {
                    int index = item.indexOf(43);
                    itemID = Integer.parseInt(item.substring(0, index));
                    item = item.substring(index + 1);
                } else {
                    itemID = Integer.parseInt(item);
                    item = "";
                }

                String data = lootData[1];
                Map enchantments = null;
                boolean autoEnchant = false;

                if (lootData[1].endsWith("auto")) {
                    autoEnchant = true;
                    lootData[1] = lootData[1].substring(0, lootData[1].length() - 5);
                }
                if (data.contains("+")) {
                    int index = data.indexOf(43);
                    enchantments = PhatLootsCommand.getEnchantments(data.substring(index + 1));

                    data = data.substring(0, index);
                }

                String amount = lootData[2];
                int lower = PhatLootsCommand.getLowerBound(amount);
                int upper = PhatLootsCommand.getUpperBound(amount);

                if ((lower == -1) || (upper == -1)) {
                    throw new RuntimeException();
                }

                OldLoot loot = new OldLoot(itemID, lower, upper);
                if (color != null) {
                    loot.setColor(color);
                } else {
                    if (skullOwner != null) {
                        loot.setSkullOwner(skullOwner);
                    }
                }
                loot.setProbability(Double.parseDouble(lootData[3]));
                try {
                    loot.setDurability(Short.parseShort(data));
                } catch (Exception notDurability) {
                    enchantments = PhatLootsCommand.getEnchantments(data);
                }
                loot.setEnchantments(enchantments);
                if (autoEnchant) {
                    loot.autoEnchant = true;
                }

                loot.name = item;
                loot.updateItemStack();
                this.lootTables[id].add(loot);
            } catch (Exception invalidLoot) {
                PhatLoots.logger.info("Error occured while loading PhatLoot \"" + this.name + '"' + ", " + '"' + lootString + '"' + " is not a valid Loot");

                invalidLoot.printStackTrace();
            }
        }
    }

    public void setChests(String data) {
        if (data.isEmpty()) {
            return;
        }

        for (String chest : data.split(", ")) {
            try {
                String[] chestData = chest.split("'");

                if (PhatLoots.server.getWorld(chestData[0]) == null) {
                    continue;
                }

                PhatLootChest phatLootChest = new PhatLootChest(chestData[0], Integer.parseInt(chestData[1]), Integer.parseInt(chestData[2]), Integer.parseInt(chestData[3]));

                this.chests.add(phatLootChest);
            } catch (Exception invalidChest) {
                PhatLoots.logger.info("Error occured while loading PhatLoot \"" + this.name + '"' + ", " + '"' + chest + '"' + " is not a valid PhatLootChest");

                invalidChest.printStackTrace();
            }
        }
    }
}
