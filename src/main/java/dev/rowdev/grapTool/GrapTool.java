package dev.rowdev.grapTool;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GrapTool extends JavaPlugin implements Listener, TabExecutor {

    private final Set<UUID> grapEnabled = ConcurrentHashMap.newKeySet();
    private final Map<UUID, GrappleSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> grappleTargets = new ConcurrentHashMap<>();
    private Team grappleTeam;

    private double defaultDistance;
    private double minDistance;
    private double maxDistance;
    private double distanceStep;
    private boolean grappleSoundEnabled;
    private boolean glowEffectEnabled;
    private boolean titleEnabled;
    private String titleText;
    private String subtitleText;
    private boolean godModeEnabled;
    private Sound grappleStartSound;
    private Sound grappleUpdateSound;
    private float soundVolume;
    private float soundPitch;

    @Override
    public void onEnable() {
        showBanner();

        saveDefaultConfig();
        loadConfig();

        Bukkit.getPluginManager().registerEvents(this, this);
        PluginCommand grapCmd = this.getCommand("grap");
        if (grapCmd != null) grapCmd.setExecutor(this);

        if (glowEffectEnabled) {
            setupGrappleTeam();
        }

        startGrappleUpdateTask();
        if (titleEnabled) {
            startTitleUpdateTask();
        }
    }

    private void showBanner() {
        String[] bannerLines = {
                "§d   ██████╗ ██╗   ██╗    ██████╗  ██████╗ ██╗    ██╗██████╗  █████╗ ██╗███╗   ██╗",
                "§d   ██╔══██╗╚██╗ ██╔╝    ██╔══██╗██╔═══██╗██║    ██║██╔══██╗██╔══██╗██║████╗  ██║",
                "§d   ██████╔╝ ╚████╔╝     ██████╔╝██║   ██║██║ █╗ ██║██████╔╝███████║██║██╔██╗ ██║",
                "§d   ██╔══██╗  ╚██╔╝      ██╔══██╗██║   ██║██║███╗██║██╔══██╗██╔══██║██║██║╚██╗██║",
                "§d   ██████╔╝   ██║       ██║  ██║╚██████╔╝╚███╔███╔╝██║  ██║██║  ██║██║██║ ╚████║",
                "§d   ╚═════╝    ╚═╝       ╚═╝  ╚═╝ ╚═════╝  ╚══╝╚══╝ ╚═╝  ╚═╝╚═╝  ╚═╝╚═╝╚═╝  ╚═══╝",
                "",
                "§dby rowrain - discord.gg/raincloud",
                "",
        };

        for (String line : bannerLines) {
            getLogger().info(ChatColor.stripColor(ChatColor.translateAlternateColorCodes('§', line)));
        }
    }

    private void loadConfig() {
        FileConfiguration config = getConfig();

        // Mesafe ayarları
        defaultDistance = config.getDouble("distance.default", 3.0);
        minDistance = config.getDouble("distance.minimum", 1.0);
        maxDistance = config.getDouble("distance.maximum", 10.0);
        distanceStep = config.getDouble("distance.step", 0.5);

        // Efekt ayarları
        grappleSoundEnabled = config.getBoolean("grapple-sound", true);
        glowEffectEnabled = config.getBoolean("glow-effect", true);

        // Title ayarları
        titleEnabled = config.getBoolean("title.enabled", true);
        titleText = translateColorCodes(config.getString("title.main-title", "&4&l{grapper} &r&4Tarafından Graplendin."));
        subtitleText = translateColorCodes(config.getString("title.subtitle", "&c&lHaraket edemezsin."));

        // God mode
        godModeEnabled = config.getBoolean("god-mode", true);

        // Ses ayarları
        try {
            grappleStartSound = Sound.valueOf(config.getString("sounds.grapple-start", "ENTITY_ENDER_DRAGON_FLAP"));
            grappleUpdateSound = Sound.valueOf(config.getString("sounds.grapple-update", "BLOCK_PISTON_EXTEND"));
        } catch (IllegalArgumentException e) {
            getLogger().warning("Geçersiz ses ismi config'te, varsayılan sesler kullanılacak.");
            grappleStartSound = Sound.ENTITY_ENDER_DRAGON_FLAP;
            grappleUpdateSound = Sound.BLOCK_PISTON_EXTEND;
        }

        soundVolume = (float) config.getDouble("sounds.volume", 0.3);
        soundPitch = (float) config.getDouble("sounds.pitch", 1.6);
    }

    private String translateColorCodes(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return false;

        if (args.length == 1) {
            String arg = args[0].toLowerCase();

            if ("on".equals(arg)) {
                if (!player.hasPermission("graptool-use")) {
                    sendActionBar(player, "§cGrapple yetkin yok.");
                    return true;
                }
                grapEnabled.add(player.getUniqueId());
                sendActionBar(player, "§aGrapple modu açıldı.");
                return true;

            } else if ("off".equals(arg)) {
                stopGrapple(player);
                grapEnabled.remove(player.getUniqueId());
                sendActionBar(player, "§cGrapple modu kapatıldı.");
                return true;

            } else if ("reload".equals(arg)) {
                if (!player.hasPermission("graptool.admin")) {
                    sendActionBar(player, "§cBu komutu kullanma yetkin yok.");
                    return true;
                }
                reloadConfig();
                loadConfig();
                sendActionBar(player, "§aConfig yenilendi.");
                return true;
            }
        }

        sendActionBar(player, "§cKullanım: /grap <on|off|reload>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String input = args[0].toLowerCase();

            if ("on".startsWith(input)) completions.add("on");
            if ("off".startsWith(input)) completions.add("off");
            if (sender.hasPermission("graptool.admin") && "reload".startsWith(input)) {
                completions.add("reload");
            }

            return completions;
        }
        return new ArrayList<>();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        grapEnabled.remove(playerId);

        GrappleSession session = activeSessions.remove(playerId);
        if (session != null) {
            cleanupSession(session);
            grappleTargets.remove(playerId);
        }

        UUID grapplerUUID = null;
        for (Map.Entry<UUID, UUID> entry : grappleTargets.entrySet()) {
            if (entry.getValue().equals(playerId)) {
                grapplerUUID = entry.getKey();
                break;
            }
        }

        if (grapplerUUID != null) {
            Player grappler = Bukkit.getPlayer(grapplerUUID);
            if (grappler != null) {
                stopGrapple(grappler);
                sendActionBar(grappler, "§cHedef oyuncu çıktı, grapple iptal edildi.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!godModeEnabled) return;

        if (event.getEntity() instanceof Player player) {
            for (GrappleSession session : activeSessions.values()) {
                if (session.getTarget().equals(player)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (target.equals(player)) return;

        if (!grapEnabled.contains(player.getUniqueId()) ||
                !player.hasPermission("graptool-use")) return;

        if (target instanceof Player targetPlayer) {
            if (player.isOp() && targetPlayer.isOp()) {
                sendActionBar(player, "§cOP oyuncuları birbirlerini grapleyemez!");
                event.setCancelled(true);
                return;
            }

        }

        UUID playerId = player.getUniqueId();
        GrappleSession currentSession = activeSessions.get(playerId);

        if (player.isSneaking()) {
            if (currentSession != null && currentSession.getTarget().equals(target)) {
                stopGrapple(player);
                sendActionBar(player, "§cShift + vurma ile grapple bırakıldı.");
                event.setCancelled(true);
            }
            return;
        }

        // Aynı hedefi tekrar vurma ile iptal
        if (currentSession != null && currentSession.getTarget().equals(target)) {
            stopGrapple(player);
            sendActionBar(player, "§cGrap bağlantısı kesildi.");
            event.setCancelled(true);
            return;
        }

        // Yeni grapple başlat
        startGrapple(player, target);
        sendActionBar(player, "§aGrap bağlantısı kuruldu.");
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        GrappleSession session = activeSessions.get(playerId);
        if (session == null) return;

        // Scroll yönünü tespiti
        int previousSlot = event.getPreviousSlot();
        int newSlot = event.getNewSlot();

        boolean scrollUp = isScrollUp(previousSlot, newSlot);
        boolean scrollDown = isScrollDown(previousSlot, newSlot);

        if (scrollUp || scrollDown) {
            double currentDistance = session.getDistance();

            if (scrollUp) {
                currentDistance = Math.min(currentDistance + distanceStep, maxDistance);
            } else {
                currentDistance = Math.max(currentDistance - distanceStep, minDistance);
            }

            session.setDistance(currentDistance);
            sendActionBar(player, "§eMesafe: " + String.format("%.1f", currentDistance));
        }
    }

    private boolean isScrollUp(int previousSlot, int newSlot) {
        return (newSlot == previousSlot + 1) || (previousSlot == 8 && newSlot == 0);
    }

    private boolean isScrollDown(int previousSlot, int newSlot) {
        return (newSlot == previousSlot - 1) || (previousSlot == 0 && newSlot == 8);
    }

    private void startGrapple(Player player, LivingEntity target) {
        UUID playerId = player.getUniqueId();

        GrappleSession oldSession = activeSessions.remove(playerId);
        if (oldSession != null) {
            cleanupSession(oldSession);
            grappleTargets.remove(playerId);
        }

        GrappleSession session = new GrappleSession(target, defaultDistance, player.getName());
        activeSessions.put(playerId, session);
        grappleTargets.put(playerId, target.getUniqueId());

        if (glowEffectEnabled) {
            target.setGlowing(true);
            if (grappleTeam != null) {
                if (target instanceof Player targetPlayer) {
                    grappleTeam.addEntry(targetPlayer.getName());
                } else {
                    grappleTeam.addEntry(target.getUniqueId().toString());
                }
            }
        }

        target.setAI(false);

        if (grappleSoundEnabled) {
            player.getWorld().playSound(player.getLocation(), grappleStartSound, soundVolume, soundPitch);
        }

        if (target instanceof Player targetPlayer) {
            if (titleEnabled) {
                showGrappleTitle(targetPlayer, player.getName());
            }
            session.setOriginalFlyState(targetPlayer.getAllowFlight());
            targetPlayer.setAllowFlight(true);
            lookAtGrappler(targetPlayer, player);
        }
    }

    private void stopGrapple(Player player) {
        UUID playerId = player.getUniqueId();
        GrappleSession session = activeSessions.remove(playerId);
        grappleTargets.remove(playerId);

        if (session != null) {
            cleanupSession(session);
        }
    }

    private void cleanupSession(GrappleSession session) {
        LivingEntity target = session.getTarget();
        if (target != null && !target.isDead()) {
            if (glowEffectEnabled) {
                target.setGlowing(false);
            }
            target.setAI(true);

            // Glow kaldırma muhabbeti fln
            if (glowEffectEnabled && grappleTeam != null) {
                if (target instanceof Player targetPlayer) {
                    grappleTeam.removeEntry(targetPlayer.getName());
                } else {
                    grappleTeam.removeEntry(target.getUniqueId().toString());
                }
            }

            if (target instanceof Player targetPlayer) {
                if (titleEnabled) {
                    clearGrappleTitle(targetPlayer);
                }
                targetPlayer.setAllowFlight(session.getOriginalFlyState());
                if (!session.getOriginalFlyState()) {
                    targetPlayer.setFlying(false);
                }
            }
        }
    }

    private void startGrappleUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                activeSessions.entrySet().removeIf(entry -> {
                    UUID playerId = entry.getKey();
                    GrappleSession session = entry.getValue();

                    Player player = Bukkit.getPlayer(playerId);
                    if (player == null || !player.isOnline()) {
                        cleanupSession(session);
                        grappleTargets.remove(playerId);
                        return true;
                    }

                    LivingEntity target = session.getTarget();
                    if (target == null || target.isDead() || !target.isValid()) {
                        cleanupSession(session);
                        grappleTargets.remove(playerId);
                        return true;
                    }

                    updateGrappleTarget(player, session);
                    return false;
                });
            }
        }.runTaskTimer(this, 0L, 1L); // Her tick çalışır (20 TPS)
    }

    private void startTitleUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                activeSessions.values().forEach(session -> {
                    LivingEntity target = session.getTarget();
                    if (target instanceof Player targetPlayer && !target.isDead()) {
                        showGrappleTitle(targetPlayer, session.getGraplerName());
                    }
                });
            }
        }.runTaskTimer(this, 0L, 40L); // Her 2 saniyede güncellem tick değeri: (40 tick)
    }

    private void updateGrappleTarget(Player player, GrappleSession session) {
        LivingEntity target = session.getTarget();

        Location playerLocation = player.getLocation();
        playerLocation.setY(playerLocation.getY() + 1.0);

        double distance = session.getDistance();

        Vector direction = player.getEyeLocation().getDirection().normalize();
        Location newTargetLoc = playerLocation.clone().add(direction.multiply(distance));

        Location currentLoc = target.getLocation();
        Vector moveVector = newTargetLoc.toVector().subtract(currentLoc.toVector());

        if (moveVector.lengthSquared() < 0.01) return;

        if (target instanceof Player targetPlayer) {
            targetPlayer.teleport(newTargetLoc);
            targetPlayer.setVelocity(new Vector(0, 0, 0));
            lookAtGrappler(targetPlayer, player);
        } else {
            target.teleport(newTargetLoc);
        }

        if (grappleSoundEnabled && session.shouldPlaySound()) {
            target.getWorld().playSound(newTargetLoc, grappleUpdateSound, soundVolume * 0.3f, soundPitch);
        }
    }

    private void showGrappleTitle(Player targetPlayer, String graplerName) {
        String title = titleText.replace("{grapper}", graplerName);
        String subtitle = subtitleText;

        targetPlayer.sendTitle(title, subtitle, 10, 60, 10);
    }

    private void clearGrappleTitle(Player targetPlayer) {
        targetPlayer.sendTitle("", "", 0, 1, 0);
    }

    private void setupGrappleTeam() {
        try {
            Scoreboard mainScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

            Team oldTeam = mainScoreboard.getTeam("GrappleTeam");
            if (oldTeam != null) {
                oldTeam.unregister();
            }

            grappleTeam = mainScoreboard.registerNewTeam("GrappleTeam");
            grappleTeam.setColor(ChatColor.RED);
            grappleTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        } catch (Exception e) {
            getLogger().warning("Grapple team oluşturulamadı: " + e.getMessage());
        }
    }

    private void lookAtGrappler(Player targetPlayer, Player grappler) {
        Location targetLoc = targetPlayer.getEyeLocation();
        Location grapplerLoc = grappler.getEyeLocation();

        Vector direction = grapplerLoc.toVector().subtract(targetLoc.toVector()).normalize();

        double yaw = Math.atan2(-direction.getX(), direction.getZ()) * 180.0 / Math.PI;
        double pitch = Math.asin(-direction.getY()) * 80.0 / Math.PI;

        Location newLoc = targetLoc.clone();
        newLoc.setYaw((float) yaw);
        newLoc.setPitch((float) pitch);

        targetPlayer.teleport(newLoc);
    }

    private void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
    }

    @Override
    public void onDisable() {
        activeSessions.values().forEach(this::cleanupSession);
        activeSessions.clear();
        grappleTargets.clear();
    }

    private static class GrappleSession {
        private final LivingEntity target;
        private final String graplerName;
        private double distance;
        private long lastSoundTime;
        private boolean originalFlyState;
        private static final long SOUND_COOLDOWN = 100; // 100ms ses cooldown

        public GrappleSession(LivingEntity target, double distance, String graplerName) {
            this.target = target;
            this.distance = distance;
            this.graplerName = graplerName;
            this.lastSoundTime = 0;
            this.originalFlyState = false;
        }

        public LivingEntity getTarget() {
            return target;
        }

        public String getGraplerName() {
            return graplerName;
        }

        public double getDistance() {
            return distance;
        }

        public void setDistance(double distance) {
            this.distance = distance;
        }

        public boolean getOriginalFlyState() {
            return originalFlyState;
        }

        public void setOriginalFlyState(boolean originalFlyState) {
            this.originalFlyState = originalFlyState;
        }

        public boolean shouldPlaySound() {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastSoundTime > SOUND_COOLDOWN) {
                lastSoundTime = currentTime;
                return true;
            }
            return false;
        }
    }
}