package com.citybattle;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import java.util.*;

public class CityBattlePlugin extends JavaPlugin implements Listener {

    private Location centerLocation = null;
    private final Set<UUID> alivePlayers = new HashSet<>();
    private final Set<UUID> spectators = new HashSet<>();
    private boolean gameRunning = false;
    private Scoreboard scoreboard;
    private Objective objective;
    private final List<Location> spawnLocations = new ArrayList<>();
    private final List<List<Location>> glassBlocks = new ArrayList<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        setupScoreboard();
        getLogger().info("CityBattle plugin enabled!");
    }

    private void setupScoreboard() {
        scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        objective = scoreboard.registerNewObjective("citybattle", Criteria.DUMMY, 
            ChatColor.GOLD + "" + ChatColor.BOLD + "CITY BATTLE");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    private void updateScoreboard() {
        // Clear old scores
        for (String entry : scoreboard.getEntries()) {
            scoreboard.resetScores(entry);
        }
        objective.getScore(ChatColor.YELLOW + "Players Hidup:").setScore(5);
        objective.getScore(ChatColor.WHITE + "" + alivePlayers.size() + " / " + 
            (alivePlayers.size() + spectators.size())).setScore(4);
        objective.getScore(" ").setScore(3);
        objective.getScore(ChatColor.AQUA + "Border:").setScore(2);
        double borderSize = getServer().getWorlds().get(0).getWorldBorder().getSize();
        objective.getScore(ChatColor.WHITE + "" + (int)borderSize + " blok").setScore(1);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(scoreboard);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase()) {
            case "setcenter": return cmdSetCenter(sender);
            case "startgame": return cmdStartGame(sender);
            case "shrink": return cmdShrink(sender, args);
            case "supplydrop": return cmdSupplyDrop(sender);
            case "alive": return cmdAlive(sender);
            case "resetgame": return cmdResetGame(sender);
        }
        return false;
    }

    private boolean cmdSetCenter(CommandSender sender) {
        if (!(sender instanceof Player)) { sender.sendMessage("Mesti player!"); return true; }
        Player p = (Player) sender;
        centerLocation = p.getLocation().clone();
        WorldBorder wb = p.getWorld().getWorldBorder();
        wb.setCenter(centerLocation);
        p.sendMessage(ChatColor.GREEN + "Pusat border ditetapkan kat lokasi kamu!");
        return true;
    }

    private boolean cmdStartGame(CommandSender sender) {
        if (centerLocation == null) {
            sender.sendMessage(ChatColor.RED + "Set pusat dulu dengan /setcenter!");
            return true;
        }
        if (gameRunning) {
            sender.sendMessage(ChatColor.RED + "Game dah running!");
            return true;
        }

        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Takde player online!");
            return true;
        }

        // Remove host from player list if sender is a player
        if (sender instanceof Player) {
            players.remove((Player) sender);
        }

        if (players.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Takde player untuk main (selain host)!");
            return true;
        }

        gameRunning = true;
        alivePlayers.clear();
        spectators.clear();
        spawnLocations.clear();
        glassBlocks.clear();

        // Set host to spectator
        if (sender instanceof Player) {
            Player host = (Player) sender;
            host.setGameMode(GameMode.SPECTATOR);
            host.sendMessage(ChatColor.GOLD + "Kamu jadi spectator sebagai host!");
        }

        // Calculate spawn positions in a circle
        double radius = centerLocation.getWorld().getWorldBorder().getSize() / 2 * 0.7;
        int count = players.size();

        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI / count) * i;
            double x = centerLocation.getX() + radius * Math.cos(angle);
            double z = centerLocation.getZ() + radius * Math.sin(angle);
            double y = centerLocation.getWorld().getHighestBlockYAt((int) x, (int) z) + 1;
            spawnLocations.add(new Location(centerLocation.getWorld(), x, y, z));
        }

        // Teleport players, build glass cages, give kit
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            Location spawnLoc = spawnLocations.get(i);
            alivePlayers.add(player.getUniqueId());

            player.teleport(spawnLoc);
            player.setGameMode(GameMode.SURVIVAL);
            player.getInventory().clear();
            player.setHealth(20);
            player.setFoodLevel(20);
            player.addPotionEffect(new PotionEffect(PotionEffectType.getByName("DAMAGE_RESISTANCE"), 999999, 255, false, false));

            buildGlassCage(spawnLoc, i);
            player.setScoreboard(scoreboard);
            player.sendMessage(ChatColor.GOLD + "=== CITY BATTLE ROYALE ===");
            player.sendMessage(ChatColor.YELLOW + "Tunggu countdown untuk mula!");
        }

        // Broadcast to all
        Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + 
            "=== CITY BATTLE ROYALE BERMULA! ===");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "" + players.size() + " player telah memasuki arena!");

        // Countdown 5,4,3,2,1 then remove glass
        startCountdown(players);
        updateScoreboard();
        return true;
    }

    private void buildGlassCage(Location center, int playerIndex) {
        List<Location> cage = new ArrayList<>();
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        // Build 3x3x3 glass cage around player
        for (int x = cx - 1; x <= cx + 1; x++) {
            for (int y = cy - 1; y <= cy + 2; y++) {
                for (int z = cz - 1; z <= cz + 1; z++) {
                    if (x == cx && z == cz && y >= cy && y <= cy + 1) continue; // space for player
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == Material.AIR) {
                        block.setType(Material.GLASS);
                        cage.add(block.getLocation());
                    }
                }
            }
        }
        glassBlocks.add(cage);
    }

    private void removeAllGlass() {
        for (List<Location> cage : glassBlocks) {
            for (Location loc : cage) {
                Block block = loc.getBlock();
                if (block.getType() == Material.GLASS) {
                    block.setType(Material.AIR);
                }
            }
        }
        glassBlocks.clear();
    }

    private void startCountdown(List<Player> players) {
        new BukkitRunnable() {
            int count = 5;

            @Override
            public void run() {
                if (count > 0) {
                    String color = count <= 3 ? ChatColor.RED.toString() : ChatColor.YELLOW.toString();
                    for (Player p : players) {
                        if (p.isOnline()) {
                            p.sendTitle(color + "" + ChatColor.BOLD + count, 
                                ChatColor.GRAY + "Bersedia...", 5, 15, 5);
                            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                        }
                    }
                    Bukkit.broadcastMessage(color + "" + ChatColor.BOLD + count + "...");
                    count--;
                } else {
                    // MULA!
                    removeAllGlass();
                    for (Player p : players) {
                        if (p.isOnline()) {
                            p.sendTitle(ChatColor.GREEN + "" + ChatColor.BOLD + "MULA!", 
                                ChatColor.YELLOW + "Good luck!", 5, 25, 10);
                            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
                            p.removePotionEffect(PotionEffectType.getByName("DAMAGE_RESISTANCE"));
                        }
                    }
                    Bukkit.broadcastMessage(ChatColor.GREEN + "" + ChatColor.BOLD + 
                        "=== GAME BERMULA! GOOD LUCK! ===");
                    cancel();
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private boolean cmdShrink(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Guna: /shrink <saiz> <saat>");
            return true;
        }
        try {
            double size = Double.parseDouble(args[0]);
            long seconds = Long.parseLong(args[1]);
            World world = (centerLocation != null) ? centerLocation.getWorld() : 
                Bukkit.getWorlds().get(0);
            WorldBorder wb = world.getWorldBorder();
            wb.setSize(size, seconds);
            Bukkit.broadcastMessage(ChatColor.RED + "" + ChatColor.BOLD + 
                "⚠ BORDER MENGECIL! ⚠");
            Bukkit.broadcastMessage(ChatColor.YELLOW + "Border akan jadi " + (int)size + 
                " blok dalam masa " + seconds + " saat!");
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.5f);
            }
            updateScoreboard();
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Nombor tidak valid!");
        }
        return true;
    }

    private boolean cmdSupplyDrop(CommandSender sender) {
        if (centerLocation == null) {
            sender.sendMessage(ChatColor.RED + "Set center dulu!");
            return true;
        }
        World world = centerLocation.getWorld();
        double borderSize = world.getWorldBorder().getSize() / 2 * 0.8;
        Random random = new Random();
        double angle = random.nextDouble() * 2 * Math.PI;
        double dist = random.nextDouble() * borderSize;
        double x = centerLocation.getX() + dist * Math.cos(angle);
        double z = centerLocation.getZ() + dist * Math.sin(angle);
        double y = world.getHighestBlockYAt((int) x, (int) z) + 1;
        Location dropLoc = new Location(world, x, y, z);

        // Place chest
        Block chest = world.getBlockAt(dropLoc);
        chest.setType(Material.CHEST);
        org.bukkit.block.Chest chestState = (org.bukkit.block.Chest) chest.getState();
        org.bukkit.inventory.Inventory inv = chestState.getInventory();

        // Fill with loot
        inv.setItem(0, new org.bukkit.inventory.ItemStack(Material.DIAMOND_SWORD));
        inv.setItem(1, new org.bukkit.inventory.ItemStack(Material.DIAMOND_CHESTPLATE));
        inv.setItem(2, new org.bukkit.inventory.ItemStack(Material.GOLDEN_APPLE, 3));
        inv.setItem(3, new org.bukkit.inventory.ItemStack(Material.BOW));
        inv.setItem(4, new org.bukkit.inventory.ItemStack(Material.ARROW, 32));
        inv.setItem(5, new org.bukkit.inventory.ItemStack(Material.COOKED_BEEF, 16));
        chestState.update();

        // Shoot fireworks above drop location
        for (int i = 0; i < 3; i++) {
            final int delay = i * 10;
            new BukkitRunnable() {
                @Override
                public void run() {
                    Firework fw = world.spawn(dropLoc.clone().add(0, 1, 0), Firework.class);
                    FireworkMeta meta = fw.getFireworkMeta();
                    meta.addEffect(FireworkEffect.builder()
                        .withColor(Color.YELLOW, Color.RED)
                        .with(FireworkEffect.Type.BALL_LARGE)
                        .withFlicker().withTrail().build());
                    meta.setPower(2);
                    fw.setFireworkMeta(meta);
                }
            }.runTaskLater(this, delay);
        }

        Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "★ SUPPLY DROP! ★");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "Supply drop jatuh kat koordinat: " + 
            ChatColor.WHITE + (int)x + ", " + (int)z);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.5f);
        }
        return true;
    }

    private boolean cmdAlive(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Player Hidup ===");
        sender.sendMessage(ChatColor.GREEN + "Hidup: " + ChatColor.WHITE + alivePlayers.size());
        sender.sendMessage(ChatColor.GRAY + "Eliminated: " + ChatColor.WHITE + spectators.size());
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) sender.sendMessage(ChatColor.GREEN + "  - " + p.getName());
        }
        return true;
    }

    private boolean cmdResetGame(CommandSender sender) {
        gameRunning = false;
        alivePlayers.clear();
        spectators.clear();
        spawnLocations.clear();
        removeAllGlass();

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setGameMode(GameMode.SURVIVAL);
            p.getInventory().clear();
            p.setHealth(20);
            p.setFoodLevel(20);
            p.removePotionEffect(PotionEffectType.getByName("DAMAGE_RESISTANCE"));
            p.sendMessage(ChatColor.YELLOW + "Game telah di-reset!");
        }

        // Reset scoreboard
        setupScoreboard();
        Bukkit.broadcastMessage(ChatColor.GOLD + "Game telah di-reset untuk round baru!");
        sender.sendMessage(ChatColor.GREEN + "Reset berjaya!");
        return true;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!gameRunning || !alivePlayers.contains(player.getUniqueId())) return;

        alivePlayers.remove(player.getUniqueId());
        spectators.add(player.getUniqueId());

        String killer = "border/environment";
        if (player.getKiller() != null) killer = player.getKiller().getName();

        Bukkit.broadcastMessage(ChatColor.RED + "" + ChatColor.BOLD + 
            player.getName() + ChatColor.RED + " telah dieliminate oleh " + 
            ChatColor.YELLOW + killer + ChatColor.RED + "!");
        Bukkit.broadcastMessage(ChatColor.GRAY + "Baki: " + ChatColor.WHITE + 
            alivePlayers.size() + " player hidup");

        updateScoreboard();
        checkWinCondition();
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!gameRunning || !spectators.contains(player.getUniqueId())) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    player.setGameMode(GameMode.SPECTATOR);
                    player.sendMessage(ChatColor.GRAY + "Kamu dah eliminated. Tengok je!");
                }
            }
        }.runTaskLater(this, 5L);
    }

    private void checkWinCondition() {
        if (alivePlayers.size() == 1) {
            UUID winnerUUID = alivePlayers.iterator().next();
            Player winner = Bukkit.getPlayer(winnerUUID);
            if (winner != null) {
                Bukkit.broadcastMessage("");
                Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + 
                    "🏆 PEMENANG: " + winner.getName() + " 🏆");
                Bukkit.broadcastMessage("");

                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendTitle(ChatColor.GOLD + "" + ChatColor.BOLD + "🏆 " + winner.getName() + " MENANG! 🏆",
                        ChatColor.YELLOW + "Tahniah!", 10, 80, 20);
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                }

                // Spawn fireworks on winner
                new BukkitRunnable() {
                    int times = 0;
                    @Override
                    public void run() {
                        if (times >= 10 || !winner.isOnline()) { cancel(); return; }
                        Firework fw = winner.getWorld().spawn(winner.getLocation(), Firework.class);
                        FireworkMeta meta = fw.getFireworkMeta();
                        meta.addEffect(FireworkEffect.builder()
                            .withColor(Color.YELLOW, Color.ORANGE, Color.WHITE)
                            .with(FireworkEffect.Type.STAR)
                            .withFlicker().withTrail().build());
                        meta.setPower(1);
                        fw.setFireworkMeta(meta);
                        times++;
                    }
                }.runTaskTimer(this, 0L, 15L);

                gameRunning = false;
            }
        } else if (alivePlayers.size() == 0) {
            Bukkit.broadcastMessage(ChatColor.RED + "Semua player eliminated! Tiada pemenang.");
            gameRunning = false;
        }
    }
}
