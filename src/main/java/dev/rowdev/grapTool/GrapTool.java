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
import org.bukkit.entity.Creature;
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
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GrapTool extends JavaPlugin implements Listener, TabExecutor {

    private static final String PERMISSION_USE   = "graptool.use";
    private static final String PERMISSION_ADMIN = "graptool.admin";

    private static final String TEAM_NAME = "GrappleTeam";

    private static final double PLAYER_HEIGHT_OFFSET = 1.0;
    private static final double MIN_MOVE_DISTANCE_SQ = 0.04;
    private static final double PLAYER_VELOCITY_FACTOR = 0.35;
    private static final double PLAYER_MAX_VELOCITY = 1.5;
    private static final double PLAYER_MAX_VERTICAL_VELOCITY = 0.6;

    private static final double ENTITY_VELOCITY_FACTOR = 0.25;
    private static final double ENTITY_MAX_VELOCITY = 1.2;
    private static final double ENTITY_MAX_VERTICAL_VELOCITY = 0.4;

    private static final float SOUND_VOLUME_MULTIPLIER = 0.3f;
    private static final long  SOUND_COOLDOWN_MS       = 100;

    private static final int UPDATE_TASK_INTERVAL = 2;
    private static final int TITLE_UPDATE_INTERVAL = 40;
    private static final int TITLE_FADE_IN  = 10;
    private static final int TITLE_STAY     = 60;
    private static final int TITLE_FADE_OUT = 10;

    private final Set<UUID> grapEnabled                 = ConcurrentHashMap.newKeySet();
    private final Map<UUID, GrappleSession> activeMap   = new ConcurrentHashMap<>();
    private final Map<UUID, UUID>          targetLookup = new ConcurrentHashMap<>();
    private Team grappleTeam;

    private double defaultDistance, minDistance, maxDistance, distanceStep;
    private boolean grappleSoundEnabled, glowEffectEnabled;
    private boolean titleEnabled, godModeEnabled;
    private String  titleText, subtitleText;
    private Sound   grappleStartSound, grappleUpdateSound;
    private float   soundVolume, soundPitch;

    private int updateTaskId = -1, titleTaskId = -1;

    @Override public void onEnable() {
        showBanner();
        saveDefaultConfig();
        loadConfig();

        Bukkit.getPluginManager().registerEvents(this, this);
        PluginCommand cmd = getCommand("grap");
        if (cmd != null) cmd.setExecutor(this);

        if (glowEffectEnabled) setupGrappleTeam();
        startGrappleUpdateTask();
        if (titleEnabled) startTitleUpdateTask();

        getLogger().info("GrapTool enabled ✓");
    }

    @Override public void onDisable() {
        if (updateTaskId != -1) Bukkit.getScheduler().cancelTask(updateTaskId);
        if (titleTaskId  != -1) Bukkit.getScheduler().cancelTask(titleTaskId);
        cleanupAllSessions();
        getLogger().info("GrapTool disabled ✓");
    }


    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage("§cSadece oyuncu."); return true; }

        if (a.length == 1) {
            String arg = a[0].toLowerCase();
            switch (arg) {
                case "on" -> {
                    if (!p.hasPermission(PERMISSION_USE)) return noPerm(p);
                    grapEnabled.add(p.getUniqueId());
                    bar(p, "§aGrapple modu açıldı.");
                }
                case "off" -> {
                    if (!p.hasPermission(PERMISSION_USE)) return noPerm(p);
                    stopGrapple(p);
                    grapEnabled.remove(p.getUniqueId());
                    bar(p, "§cGrapple modu kapatıldı.");
                }
                case "reload" -> {
                    if (!p.hasPermission(PERMISSION_ADMIN)) return noPerm(p);
                    reloadConfig(); loadConfig();
                    bar(p, "§aConfig yenilendi.");
                }
                default -> bar(p, "§cKullanım: /grap <on|off|reload>");
            }
            return true;
        }

        bar((Player) s, "§cKullanım: /grap <on|off|reload>");
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        if (args.length != 1) return List.of();
        String in = args[0].toLowerCase();
        List<String> list = new ArrayList<>();
        if ("on".startsWith(in))  list.add("on");
        if ("off".startsWith(in)) list.add("off");
        if (s.hasPermission(PERMISSION_ADMIN) && "reload".startsWith(in)) list.add("reload");
        return list;
    }


    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        cleanupPlayerSession(id);
        cleanupPlayerAsTarget(id);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent e) {
        if (!godModeEnabled || !(e.getEntity() instanceof Player p)) return;
        activeMap.values().stream()
                .filter(ses -> ses.target.equals(p))
                .findFirst().ifPresent(ses -> e.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageBy(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (!(e.getEntity() instanceof LivingEntity tgt)) return;
        if (tgt.equals(p)) return;

        if (!grapEnabled.contains(p.getUniqueId()) || !p.hasPermission(PERMISSION_USE)) return;
        if (tgt instanceof Player tp && p.isOp() && tp.isOp()) {
            bar(p, "§cOP oyuncuları birbirini grapleyemez!");
            e.setCancelled(true);
            return;
        }

        UUID pid = p.getUniqueId();
        GrappleSession ses = activeMap.get(pid);

        if (ses != null && ses.target.equals(tgt)) {
            stopGrapple(p);
            bar(p, "§cGrap bağlantısı kesildi.");
            e.setCancelled(true);
            return;
        }

        if (startGrapple(p, tgt)) {
            String entityName = tgt instanceof Player ? ((Player) tgt).getName() : tgt.getType().name();
            bar(p, "§aGrap bağlantısı kuruldu: " + entityName);
        } else {
            bar(p, "§cGrapple başlatılamadı.");
        }
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onScroll(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        if (!p.hasPermission(PERMISSION_USE)) return;
        GrappleSession ses = activeMap.get(p.getUniqueId());
        if (ses == null) return;

        int prev = e.getPreviousSlot(), now = e.getNewSlot();
        boolean up   = (now == prev + 1) || (prev == 8 && now == 0);
        boolean down = (now == prev - 1) || (prev == 0 && now == 8);

        if (up || down) {
            double d = ses.distance;
            d = up ? Math.min(d + distanceStep, maxDistance)
                    : Math.max(d - distanceStep, minDistance);
            ses.distance = d;
            bar(p, "§eMesafe: " + String.format("%.1f", d));
        }
    }


    private boolean startGrapple(Player p, LivingEntity tgt) {
        if (tgt == null || tgt.isDead() || !tgt.isValid()) {
            getLogger().info("Hedef geçersiz: " + (tgt == null ? "null" : tgt.getType()));
            return false;
        }

        double distance = p.getLocation().distance(tgt.getLocation());
        if (distance > maxDistance * 2) {
            bar(p, "§cHedef çok uzak!");
            return false;
        }

        UUID pid = p.getUniqueId();
        GrappleSession old = activeMap.remove(pid);
        if (old != null) cleanupSession(old);

        try {
            GrappleSession ses = new GrappleSession(tgt, defaultDistance, p.getName());
            activeMap.put(pid, ses);
            targetLookup.put(pid, tgt.getUniqueId());

            if (glowEffectEnabled) applyGlow(tgt);

            if (tgt instanceof Player tp) {
                setupTargetPlayer(tp, ses, p);
            } else {
                setupTargetEntity(tgt, ses);
            }

            if (grappleSoundEnabled)
                p.getWorld().playSound(p.getLocation(), grappleStartSound, soundVolume, soundPitch);

            getLogger().info("Grapple başarılı: " + p.getName() + " -> " + tgt.getType());
            return true;
        } catch (Exception ex) {
            getLogger().warning("Grapple başlatılamadı: " + ex.getMessage());
            ex.printStackTrace();
            return false;
        }
    }

    private void stopGrapple(Player p) {
        GrappleSession ses = activeMap.remove(p.getUniqueId());
        targetLookup.remove(p.getUniqueId());
        if (ses != null) cleanupSession(ses);
    }

    private void startGrappleUpdateTask() {
        updateTaskId = new BukkitRunnable() {
            @Override public void run() {
                for (Iterator<Map.Entry<UUID,GrappleSession>> it = activeMap.entrySet().iterator(); it.hasNext();) {
                    Map.Entry<UUID,GrappleSession> ent = it.next();
                    Player p = Bukkit.getPlayer(ent.getKey());
                    GrappleSession ses = ent.getValue();
                    LivingEntity tgt = ses.target;

                    if (p == null || !p.isOnline() || tgt == null || tgt.isDead() || !tgt.isValid()) {
                        cleanupSession(ses); it.remove(); continue;
                    }
                    updateGrappleTarget(p, ses);
                }
            }
        }.runTaskTimer(this, 0L, UPDATE_TASK_INTERVAL).getTaskId();
    }

    private void startTitleUpdateTask() {
        titleTaskId = new BukkitRunnable() {
            @Override public void run() {
                activeMap.values().forEach(ses -> {
                    if (ses.target instanceof Player tp && tp.isOnline())
                        showTitle(tp, ses.graplerName);
                });
            }
        }.runTaskTimer(this, 0L, TITLE_UPDATE_INTERVAL).getTaskId();
    }

    private void updateGrappleTarget(Player p, GrappleSession ses) {
        LivingEntity tgt = ses.target;

        Location base = p.getLocation().clone();
        base.setY(base.getY() + PLAYER_HEIGHT_OFFSET);

        Vector dir = p.getEyeLocation().getDirection().normalize();
        Location desired = base.add(dir.multiply(ses.distance));

        if (tgt instanceof Player) {
            moveTargetPlayer((Player) tgt, desired);
        } else {
            moveTargetEntity(tgt, desired);
        }

        playUpdateSound(tgt, ses);
    }

    private void moveTargetPlayer(Player tgt, Location desired) {
        Vector delta = desired.toVector().subtract(tgt.getLocation().toVector());
        if (delta.lengthSquared() < MIN_MOVE_DISTANCE_SQ) return;

        Vector vel = delta.multiply(PLAYER_VELOCITY_FACTOR);
        if (vel.length() > PLAYER_MAX_VELOCITY) {
            vel = vel.normalize().multiply(PLAYER_MAX_VELOCITY);
        }

        vel.setY(Math.max(Math.min(vel.getY(), PLAYER_MAX_VERTICAL_VELOCITY), -PLAYER_MAX_VERTICAL_VELOCITY));

        tgt.setFallDistance(0);

        if (delta.lengthSquared() > 9.0) {
            tgt.teleport(desired);
        } else {
            tgt.setVelocity(vel);
        }

        tgt.setFallDistance(0);
    }

    private void moveTargetEntity(LivingEntity tgt, Location desired) {
        Vector delta = desired.toVector().subtract(tgt.getLocation().toVector());

        if (delta.lengthSquared() < MIN_MOVE_DISTANCE_SQ) return;
        Vector vel = delta.multiply(ENTITY_VELOCITY_FACTOR * 1.5);

        if (vel.length() > ENTITY_MAX_VELOCITY) {
            vel = vel.normalize().multiply(ENTITY_MAX_VELOCITY);
        }

        vel.setY(Math.max(Math.min(vel.getY(), ENTITY_MAX_VERTICAL_VELOCITY * 1.5), -ENTITY_MAX_VERTICAL_VELOCITY * 1.5));

        tgt.setFallDistance(0);
        if (delta.lengthSquared() > 6.0) {
            Location teleportLoc = desired.clone();
            teleportLoc.setYaw(tgt.getLocation().getYaw());
            teleportLoc.setPitch(tgt.getLocation().getPitch());
            tgt.teleport(teleportLoc);
        } else {
            tgt.setVelocity(vel);

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (tgt.isValid() && !tgt.isDead()) {
                        double currentDistance = tgt.getLocation().distance(desired);
                        if (currentDistance > 1.0) {
                            Location teleportLoc = desired.clone();
                            teleportLoc.setYaw(tgt.getLocation().getYaw());
                            teleportLoc.setPitch(tgt.getLocation().getPitch());
                            tgt.teleport(teleportLoc);
                        }
                    }
                }
            }.runTaskLater(this, 5L);
        }
    }

    private void playUpdateSound(Entity e, GrappleSession ses) {
        if (!grappleSoundEnabled || !ses.shouldPlaySound()) return;
        e.getWorld().playSound(e.getLocation(), grappleUpdateSound,
                soundVolume * SOUND_VOLUME_MULTIPLIER, soundPitch);
    }

    private void showTitle(Player tgt, String grapler) {
        String title = titleText.replace("{grapper}", grapler);
        tgt.sendTitle(title, subtitleText, TITLE_FADE_IN, TITLE_STAY, TITLE_FADE_OUT);
    }

    private void clearTitle(Player tgt) { tgt.sendTitle("", "", 0, 1, 0); }

    private void applyGlow(LivingEntity e) {
        e.setGlowing(true);
        if (grappleTeam != null) grappleTeam.addEntry(getEntry(e));
    }

    private void removeGlow(LivingEntity e) {
        e.setGlowing(false);
        if (grappleTeam != null) grappleTeam.removeEntry(getEntry(e));
    }

    private String getEntry(LivingEntity e) {
        return e instanceof Player ? e.getName() : e.getUniqueId().toString();
    }

    private void setupTargetPlayer(Player tp, GrappleSession ses, Player grapler) {
        if (titleEnabled) showTitle(tp, grapler.getName());
        ses.originalFly = tp.getAllowFlight();
        tp.setAllowFlight(true);
        tp.setFlying(true);
        lookAt(tp, grapler);
    }

    private void setupTargetEntity(LivingEntity entity, GrappleSession ses) {
        if (entity instanceof Creature creature) {
            ses.originalAI = creature.hasAI();
            creature.setTarget(null);
        }

    }

    private void cleanupTargetPlayer(Player tp, GrappleSession ses) {
        if (titleEnabled) clearTitle(tp);
        tp.setAllowFlight(ses.originalFly);
        if (!ses.originalFly) tp.setFlying(false);
    }

    private void cleanupTargetEntity(LivingEntity entity, GrappleSession ses) {
        if (entity instanceof Creature creature) {
            creature.setAI(ses.originalAI);
        }
    }

    private void cleanupSession(GrappleSession ses) {
        LivingEntity t = ses.target;
        if (t == null || t.isDead() || !t.isValid()) return;

        if (glowEffectEnabled) removeGlow(t);

        if (t instanceof Player tp) {
            cleanupTargetPlayer(tp, ses);
        } else {
            cleanupTargetEntity(t, ses);
        }
    }

    private void cleanupPlayerSession(UUID id) {
        grapEnabled.remove(id);
        GrappleSession ses = activeMap.remove(id);
        targetLookup.remove(id);
        if (ses != null) cleanupSession(ses);
    }

    private void cleanupPlayerAsTarget(UUID tgtId) {
        UUID grappler = targetLookup.entrySet().stream()
                .filter(e -> e.getValue().equals(tgtId))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
        if (grappler != null) {
            Player p = Bukkit.getPlayer(grappler);
            if (p != null && p.isOnline()) {
                stopGrapple(p);
                bar(p, "§cHedef çıktı, grapple iptal.");
            }
        }
    }

    private void cleanupAllSessions() {
        activeMap.values().forEach(this::cleanupSession);
        activeMap.clear(); targetLookup.clear(); grapEnabled.clear();
    }

    private void bar(Player p, String msg) {
        try { p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg)); }
        catch (Exception ex) { p.sendMessage(msg); }
    }

    private boolean noPerm(Player p) { bar(p, "§cYetkin yok."); return true; }

    private void lookAt(Player tgt, Player src) {
        Location tLoc = tgt.getEyeLocation(), sLoc = src.getEyeLocation();
        Vector dir = sLoc.toVector().subtract(tLoc.toVector()).normalize();
        tLoc.setYaw((float) (Math.atan2(-dir.getX(), dir.getZ()) * 180.0 / Math.PI));
        tLoc.setPitch((float) (Math.asin(-dir.getY()) * 80.0 / Math.PI));
        tgt.teleport(tLoc);
    }

    private void setupGrappleTeam() {
        try {
            Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
            Team old = sb.getTeam(TEAM_NAME); if (old != null) old.unregister();
            grappleTeam = sb.registerNewTeam(TEAM_NAME);
            grappleTeam.setColor(ChatColor.RED);
            grappleTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.FOR_OTHER_TEAMS);
        } catch (Exception ex) {
            getLogger().warning("Glow takımı kurulamadı: " + ex.getMessage());
            glowEffectEnabled = false;
        }
    }

    private void loadConfig() {
        FileConfiguration cfg = getConfig();

        defaultDistance  = cfg.getDouble("distance.default", 3.0);
        minDistance      = cfg.getDouble("distance.minimum", 1.0);
        maxDistance      = cfg.getDouble("distance.maximum", 10.0);
        distanceStep     = cfg.getDouble("distance.step", 0.5);

        grappleSoundEnabled = cfg.getBoolean("grapple-sound", true);
        glowEffectEnabled   = cfg.getBoolean("glow-effect",   true);

        titleEnabled  = cfg.getBoolean("title.enabled", true);
        titleText     = cc(cfg.getString("title.main-title",
                "&4&l{grapper} &r&4Tarafından Graplendin."));
        subtitleText  = cc(cfg.getString("title.subtitle",
                "&c&lHareket edemezsin."));

        godModeEnabled = cfg.getBoolean("god-mode", true);

        try {
            grappleStartSound  = Sound.valueOf(cfg.getString("sounds.grapple-start",
                    "ENTITY_ENDER_DRAGON_FLAP"));
            grappleUpdateSound = Sound.valueOf(cfg.getString("sounds.grapple-update",
                    "BLOCK_PISTON_EXTEND"));
        } catch (IllegalArgumentException ex) {
            getLogger().warning("Ses ismi hatalı, varsayılanlar kullanılıyor.");
            grappleStartSound  = Sound.ENTITY_ENDER_DRAGON_FLAP;
            grappleUpdateSound = Sound.BLOCK_PISTON_EXTEND;
        }

        soundVolume = (float) cfg.getDouble("sounds.volume", 0.3);
        soundPitch  = (float) cfg.getDouble("sounds.pitch",  1.6);
    }

    private String cc(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }

    private void showBanner() {
        String[] lines = {
                "§d   ██████╗ ██╗   ██╗    ██████╗  ██████╗ ██╗    ██╗██████╗  █████╗ ██╗███╗   ██╗",
                "§d   ██╔══██╗╚██╗ ██╔╝    ██╔══██╗██╔═══██╗██║    ██║██╔══██╗██╔══██╗██║████╗  ██║",
                "§d   ██████╔╝ ╚████╔╝     ██████╔╝██║   ██║██║ █╗ ██║██████╔╝███████║██║██╔██╗ ██║",
                "§d   ██╔══██╗  ╚██╔╝      ██╔══██╗██║   ██║██║███╗██║██╔══██╗██╔══██║██║██║╚██╗██║",
                "§d   ██████╔╝   ██║       ██║  ██║╚██████╔╝╚███╔███╔╝██║  ██║██║  ██║██║██║ ╚████║",
                "§d   ╚═════╝    ╚═╝       ╚═╝  ╚═╝ ╚═════╝  ╚══╝╚══╝ ╚═╝  ╚═╝╚═╝  ╚═╝╚═╝╚═╝  ╚═══╝",
                "",
                "§dby rowrain - discord.gg/raincloud",
                ""
        };
        Arrays.stream(lines).forEach(l -> getLogger().info(ChatColor.stripColor(l)));
    }

    private static class GrappleSession {
        final LivingEntity target;
        final String graplerName;
        double distance;
        long lastSound;
        boolean originalFly;
        boolean originalAI;

        GrappleSession(LivingEntity tgt, double dist, String name) {
            target = tgt;
            distance = dist;
            graplerName = name;
            originalFly = false;
            originalAI = true;
        }

        boolean shouldPlaySound() {
            long now = System.currentTimeMillis();
            if (now - lastSound > SOUND_COOLDOWN_MS) {
                lastSound = now;
                return true;
            }
            return false;
        }
    }
}
