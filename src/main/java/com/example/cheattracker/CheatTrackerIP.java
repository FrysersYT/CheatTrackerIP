package com.example.cheattracker;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.InetSocketAddress;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;

public class CheatTrackerIP extends JavaPlugin implements Listener {

    private Logger log;
    private File ipLogFile;
    private File commandLogFile;
    private File chatLogFile;
    private File playersFolder;
    private File playtimeFile;

    private Map<UUID, Long> joinTimes = new HashMap<>();
    private Map<UUID, Long> totalPlaytime = new HashMap<>();

    @Override
    public void onEnable() {
        this.log = getLogger();

        File dataFolder = getDataFolder();
        if (!dataFolder.exists()) dataFolder.mkdirs();

        ipLogFile = new File(dataFolder, "ip-log.txt");
        commandLogFile = new File(dataFolder, "playercommands.txt");
        chatLogFile = new File(dataFolder, "chat.txt");
        playtimeFile = new File(dataFolder, "playtime.txt");

        createFile(ipLogFile);
        createFile(commandLogFile);
        createFile(chatLogFile);
        createFile(playtimeFile);

        playersFolder = new File(dataFolder, "players");
        if (!playersFolder.exists()) playersFolder.mkdirs();

        loadPlaytime();

        getServer().getPluginManager().registerEvents(this, this);
        log.info("CheatTrackerIP enabled (1.20.1)");
    }

    @Override
    public void onDisable() {
        savePlaytime();
        log.info("CheatTrackerIP disabled");
    }

    private void createFile(File file) {
        if (!file.exists()) {
            try { file.createNewFile(); }
            catch (IOException e) { log.severe("Could not create " + file.getName() + ": " + e.getMessage()); }
        }
    }

    private void writeToFile(File file, String text) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
            writer.write(text);
            writer.newLine();
        } catch (IOException e) {
            log.severe("Error writing to " + file.getName() + ": " + e.getMessage());
        }
    }

    private void writeToPlayerFile(Player p, String text) {
        File playerFile = new File(playersFolder, p.getName() + ".txt");
        if (!playerFile.exists()) {
            try { playerFile.createNewFile(); }
            catch (IOException e) { log.severe("Could not create " + playerFile.getName() + ": " + e.getMessage()); }
        }
        writeToFile(playerFile, text);
    }

    private String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        minutes %= 60;
        seconds %= 60;
        if (hours > 0) return hours + " ч " + minutes + " мин";
        else return minutes + " мин " + seconds + " сек";
    }

    // Лог входа
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        InetSocketAddress addr = p.getAddress();
        if (addr != null) {
            String ip = addr.getAddress().getHostAddress();
            String line = "[" + timestamp() + "] Player " + p.getName() + " зашёл | IP: " + ip;
            log.info(line);
            writeToFile(ipLogFile, line);
            writeToPlayerFile(p, line);
        }
        joinTimes.put(p.getUniqueId(), System.currentTimeMillis());
    }

    // Лог выхода и подсчёт времени
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        long joinTime = joinTimes.getOrDefault(p.getUniqueId(), System.currentTimeMillis());
        long session = System.currentTimeMillis() - joinTime;

        totalPlaytime.put(p.getUniqueId(), totalPlaytime.getOrDefault(p.getUniqueId(), 0L) + session);

        String line = "[" + timestamp() + "] Player " + p.getName() + " вышел. Время в игре: " + formatDuration(session);
        log.info(line);
        writeToPlayerFile(p, line);

        savePlaytime();
    }

    // Лог команд
    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        String line = "[" + timestamp() + "] " + p.getName() + " использовал команду: " + event.getMessage();
        log.info(line);
        writeToFile(commandLogFile, line);
        writeToPlayerFile(p, line);
    }

    // Лог чата
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player p = event.getPlayer();
        String line = "[" + timestamp() + "] " + p.getName() + ": " + event.getMessage();
        log.info(line);
        writeToFile(chatLogFile, line);
        writeToPlayerFile(p, line);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("playerip")) {
            if (args.length != 1) { sender.sendMessage("§cПожалуста, ведите вот так: /playerip <player>"); return true; }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { sender.sendMessage("§cИгрок не найден или отключен."); return true; }
            InetSocketAddress addr = target.getAddress();
            if (addr != null) {
                String ip = addr.getAddress().getHostAddress();
                if (sender.hasPermission("cheattracker.ip") || !(sender instanceof Player))
                    sender.sendMessage("§a" + target.getName() + " IP: " + ip);
                else sender.sendMessage("§cУ тебя нет разрешения.");
            } else sender.sendMessage("§cIP-адрес этого игрока недоступен.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("playtime")) {
            if (args.length == 1) {
                String name = args[0];
                Player onlinePlayer = Bukkit.getPlayerExact(name);
                UUID uuid;
                long sessionTime = 0;

                if (onlinePlayer != null) {
                    uuid = onlinePlayer.getUniqueId();
                    long joinTime = joinTimes.getOrDefault(uuid, System.currentTimeMillis());
                    sessionTime = System.currentTimeMillis() - joinTime;
                } else {
                    File playerFile = new File(playersFolder, name + ".txt");
                    if (!playerFile.exists()) { sender.sendMessage("§cPlayer not found or never joined."); return true; }
                    uuid = UUID.nameUUIDFromBytes(name.getBytes());
                }

                long total = totalPlaytime.getOrDefault(uuid, 0L) + sessionTime;
                sender.sendMessage("§aИгрок " + name + " провёл на сервере: " + formatDuration(total) +
                        (onlinePlayer != null ? " (текущая сессия: " + formatDuration(sessionTime) + ")" : " (сейчас оффлайн)"));

            } else if (args.length == 0) {
                sender.sendMessage("§6=== Топ игроков по времени на сервере ===");

                totalPlaytime.entrySet().stream()
                        .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                        .limit(10)
                        .forEach(entry -> {
                            UUID uuid = entry.getKey();
                            long time = entry.getValue();
                            String playerName = "Unknown";
                            for (File f : playersFolder.listFiles()) {
                                if (f.getName().endsWith(".txt")) {
                                    String name = f.getName().replace(".txt", "");
                                    UUID tempUUID = UUID.nameUUIDFromBytes(name.getBytes());
                                    if (tempUUID.equals(uuid)) { playerName = name; break; }
                                }
                            }
                            sender.sendMessage("§a" + playerName + ": " + formatDuration(time));
                        });
            } else sender.sendMessage("§cUsage: /playtime [player]");
            return true;
        }

        return false;
    }

    private void loadPlaytime() {
        if (!playtimeFile.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(playtimeFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(";");
                if (parts.length == 2) {
                    UUID uuid = UUID.fromString(parts[0]);
                    long time = Long.parseLong(parts[1]);
                    totalPlaytime.put(uuid, time);
                }
            }
        } catch (IOException e) { log.severe("Error loading playtime: " + e.getMessage()); }
    }

    private void savePlaytime() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(playtimeFile))) {
            for (Map.Entry<UUID, Long> entry : totalPlaytime.entrySet()) {
                writer.write(entry.getKey().toString() + ";" + entry.getValue());
                writer.newLine();
            }
        } catch (IOException e) { log.severe("Error saving playtime: " + e.getMessage()); }
    }
}
