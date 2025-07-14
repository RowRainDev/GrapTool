package dev.rowdev.graptool;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GrapTool extends JavaPlugin implements Listener, TabExecutor {

    private GrappleConfig config;
    private GrappleManager grappleManager;
    private CommandHandler commandHandler;
    private EventController eventController;
    private TaskManager taskManager;
    private PlayerStateManager playerStateManager;

    @Override
    public void onEnable() {
        initializeComponents();
        registerEvents();
        registerCommands();
        startTasks();
    }

    @Override
    public void onDisable() {
        if (taskManager != null) {
            taskManager.shutdown();
        }
        if (grappleManager != null) {
            grappleManager.cleanup();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return commandHandler.handleCommand(sender, command, label, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return commandHandler.handleTabComplete(sender, command, alias, args);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        eventController.handlePlayerQuit(event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageEvent event) {
        eventController.handleEntityDamage(event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        eventController.handleEntityDamageByEntity(event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        eventController.handlePlayerItemHeld(event);
    }

    private void initializeComponents() {
        saveDefaultConfig();
        config = new GrappleConfig(this);
        grappleManager = new GrappleManager(this, config);
        commandHandler = new CommandHandler(this, grappleManager);
        eventController = new EventController(this, grappleManager, config);
        taskManager = new TaskManager(this, grappleManager, config);
        playerStateManager = new PlayerStateManager();
    }

    private void registerEvents() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    private void registerCommands() {
        PluginCommand command = getCommand("grap");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
    }

    private void startTasks() {
        taskManager.startUpdateTask();
        if (config.isTitleEnabled()) {
            taskManager.startTitleTask();
        }
    }

    public GrappleConfig getGrappleConfig() {
        return config;
    }

    public GrappleManager getGrappleManager() {
        return grappleManager;
    }

    public PlayerStateManager getPlayerStateManager() {
        return playerStateManager;
    }
}

class GrappleConfig {
    
    private final JavaPlugin plugin;
    private double defaultDistance;
    private double minDistance;
    private double maxDistance;
    private double distanceStep;
    private boolean grappleSoundEnabled;
    private boolean glowEffectEnabled;
    private boolean titleEnabled;
    private boolean godModeEnabled;
    private String titleText;
    private String subtitleText;
    private Sound grappleStartSound;
    private Sound grappleUpdateSound;
    private float soundVolume;
    private float soundPitch;
    private String targetDisconnectedMessage;
    private String targetTooFarMessage;
    private String opCannotGrappleMessage;
    private String invalidCommandMessage;
    private String playerOnlyMessage;
    private String noPermissionMessage;
    private String grappleEnabledMessage;
    private String grappleDisabledMessage;
    private String configReloadedMessage;
    private String grappleStartedMessage;
    private String grappleStoppedMessage;
    private String grappleSneakStoppedMessage;
    private String grappleFailedMessage;
    private String distanceMessage;

    public GrappleConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfiguration();
    }

    private void loadConfiguration() {
        var config = plugin.getConfig();
        
        defaultDistance = config.getDouble("distance.default", 3.0);
        minDistance = config.getDouble("distance.minimum", 1.0);
        maxDistance = config.getDouble("distance.maximum", 10.0);
        distanceStep = config.getDouble("distance.step", 0.5);
        
        grappleSoundEnabled = config.getBoolean("grapple-sound", true);
        glowEffectEnabled = config.getBoolean("glow-effect", true);
        titleEnabled = config.getBoolean("title.enabled", true);
        godModeEnabled = config.getBoolean("god-mode", true);
        
        titleText = translateColors(config.getString("title.main-title", "&4&l{grapper} &r&4Tarafından Graplendin."));
        subtitleText = translateColors(config.getString("title.subtitle", "&c&lHareket edemezsin."));
        
        loadSounds(config);
        
        soundVolume = (float) config.getDouble("sounds.volume", 0.3);
        soundPitch = (float) config.getDouble("sounds.pitch", 1.6);
        
        targetDisconnectedMessage = translateColors(config.getString("messages.target-disconnected", "&cHedef çıktı, grapple iptal."));
        targetTooFarMessage = translateColors(config.getString("messages.target-too-far", "&cHedef çok uzak!"));
        opCannotGrappleMessage = translateColors(config.getString("messages.op-cannot-grapple", "&cOP oyuncuları birbirini grapleyemez!"));
        invalidCommandMessage = translateColors(config.getString("messages.invalid-command", "&cKullanım: /grap <on|off|reload>"));
        playerOnlyMessage = translateColors(config.getString("messages.player-only", "&cSadece oyuncu."));
        noPermissionMessage = translateColors(config.getString("messages.no-permission", "&cYetkin yok."));
        grappleEnabledMessage = translateColors(config.getString("messages.grapple-enabled", "&aGrapple modu açıldı."));
        grappleDisabledMessage = translateColors(config.getString("messages.grapple-disabled", "&cGrapple modu kapatıldı."));
        configReloadedMessage = translateColors(config.getString("messages.config-reloaded", "&aConfig yenilendi."));
        grappleStartedMessage = translateColors(config.getString("messages.grapple-started", "&aGrap bağlantısı kuruldu: {target}"));
        grappleStoppedMessage = translateColors(config.getString("messages.grapple-stopped", "&cGrap bağlantısı kesildi."));
        grappleSneakStoppedMessage = translateColors(config.getString("messages.grapple-sneak-stopped", "&cShift + vurma ile bağlantı kesildi."));
        grappleFailedMessage = translateColors(config.getString("messages.grapple-failed", "&cGrapple başlatılamadı."));
        distanceMessage = translateColors(config.getString("messages.distance", "&eMesafe: {distance}"));
    }

    private void loadSounds(org.bukkit.configuration.file.FileConfiguration config) {
        try {
            grappleStartSound = Sound.valueOf(config.getString("sounds.grapple-start", "ENTITY_ENDER_DRAGON_FLAP"));
            grappleUpdateSound = Sound.valueOf(config.getString("sounds.grapple-update", "BLOCK_PISTON_EXTEND"));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning(config.getString("messages.invalid-sound", "Invalid sound name, using defaults"));
            grappleStartSound = Sound.ENTITY_ENDER_DRAGON_FLAP;
            grappleUpdateSound = Sound.BLOCK_PISTON_EXTEND;
        }
    }

    private String translateColors(String text) {
        return ChatColor.translateAlternateColorCodes('&', text != null ? text : "");
    }

    public void reload() {
        plugin.reloadConfig();
        loadConfiguration();
    }

    public double getDefaultDistance() { return defaultDistance; }
    public double getMinDistance() { return minDistance; }
    public double getMaxDistance() { return maxDistance; }
    public double getDistanceStep() { return distanceStep; }
    public boolean isGrappleSoundEnabled() { return grappleSoundEnabled; }
    public boolean isGlowEffectEnabled() { return glowEffectEnabled; }
    public boolean isTitleEnabled() { return titleEnabled; }
    public boolean isGodModeEnabled() { return godModeEnabled; }
    public String getTitleText() { return titleText; }
    public String getSubtitleText() { return subtitleText; }
    public Sound getGrappleStartSound() { return grappleStartSound; }
    public Sound getGrappleUpdateSound() { return grappleUpdateSound; }
    public float getSoundVolume() { return soundVolume; }
    public float getSoundPitch() { return soundPitch; }
    public String getTargetDisconnectedMessage() { return targetDisconnectedMessage; }
    public String getTargetTooFarMessage() { return targetTooFarMessage; }
    public String getOpCannotGrappleMessage() { return opCannotGrappleMessage; }
    public String getInvalidCommandMessage() { return invalidCommandMessage; }
    public String getPlayerOnlyMessage() { return playerOnlyMessage; }
    public String getNoPermissionMessage() { return noPermissionMessage; }
    public String getGrappleEnabledMessage() { return grappleEnabledMessage; }
    public String getGrappleDisabledMessage() { return grappleDisabledMessage; }
    public String getConfigReloadedMessage() { return configReloadedMessage; }
    public String getGrappleStartedMessage() { return grappleStartedMessage; }
    public String getGrappleStoppedMessage() { return grappleStoppedMessage; }
    public String getGrappleSneakStoppedMessage() { return grappleSneakStoppedMessage; }
    public String getGrappleFailedMessage() { return grappleFailedMessage; }
    public String getDistanceMessage() { return distanceMessage; }
}

class GrappleManager {
    
    private static final String PERMISSION_USE = "graptool.use";
    private static final String PERMISSION_ADMIN = "graptool.admin";
    
    private final JavaPlugin plugin;
    private final GrappleConfig config;
    private final Set<UUID> enabledPlayers;
    private final Map<UUID, GrappleSession> activeSessions;
    private final Map<UUID, UUID> targetLookup;
    private final GrappleEffectManager effectManager;
    private final GrappleMovementHandler movementHandler;
    private final MessageHandler messageHandler;

    public GrappleManager(JavaPlugin plugin, GrappleConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.enabledPlayers = ConcurrentHashMap.newKeySet();
        this.activeSessions = new ConcurrentHashMap<>();
        this.targetLookup = new ConcurrentHashMap<>();
        this.effectManager = new GrappleEffectManager(plugin, config);
        this.movementHandler = new GrappleMovementHandler(plugin, config);
        this.messageHandler = new MessageHandler();
    }

    public boolean isPlayerEnabled(UUID playerId) {
        return enabledPlayers.contains(playerId);
    }

    public void enablePlayer(UUID playerId) {
        enabledPlayers.add(playerId);
    }

    public void disablePlayer(UUID playerId) {
        enabledPlayers.remove(playerId);
        stopGrapple(playerId);
    }

    public boolean hasPermission(Player player, String permission) {
        return player.hasPermission(permission);
    }

    public boolean startGrapple(Player player, org.bukkit.entity.LivingEntity target) {
        if (!validateGrappleTarget(player, target)) {
            return false;
        }

        UUID playerId = player.getUniqueId();
        stopGrapple(playerId);

        try {
            GrappleSession session = new GrappleSession(target, config.getDefaultDistance(), player.getName());
            activeSessions.put(playerId, session);
            targetLookup.put(playerId, target.getUniqueId());

            effectManager.applyGrappleEffects(target, session, player);
            playSoundIfEnabled(player, config.getGrappleStartSound());

            return true;
        } catch (Exception e) {
            plugin.getLogger().warning(config.getString("messages.error-starting-grapple", "Failed to start grapple: ") + e.getMessage());
            return false;
        }
    }

    public void stopGrapple(UUID playerId) {
        GrappleSession session = activeSessions.remove(playerId);
        targetLookup.remove(playerId);
        
        if (session != null) {
            effectManager.removeGrappleEffects(session);
        }
    }

    public void updateGrapple(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        GrappleSession session = activeSessions.get(playerId);
        
        if (player == null || session == null || !session.isValid()) {
            stopGrapple(playerId);
            return;
        }

        movementHandler.updateTargetPosition(player, session);
        effectManager.playUpdateSound(session);
    }

    public void adjustDistance(UUID playerId, boolean increase) {
        GrappleSession session = activeSessions.get(playerId);
        if (session != null) {
            session.adjustDistance(increase, config.getDistanceStep(), config.getMinDistance(), config.getMaxDistance());
        }
    }

    public void cleanup() {
        activeSessions.values().forEach(effectManager::removeGrappleEffects);
        activeSessions.clear();
        targetLookup.clear();
        enabledPlayers.clear();
    }

    public void cleanupPlayer(UUID playerId) {
        disablePlayer(playerId);
        cleanupPlayerAsTarget(playerId);
    }

    private void cleanupPlayerAsTarget(UUID targetId) {
        UUID grapplerPlayerId = targetLookup.entrySet().stream()
                .filter(entry -> entry.getValue().equals(targetId))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);

        if (grapplerPlayerId != null) {
            Player grappler = Bukkit.getPlayer(grapplerPlayerId);
            if (grappler != null && grappler.isOnline()) {
                stopGrapple(grapplerPlayerId);
                messageHandler.sendActionBar(grappler, config.getTargetDisconnectedMessage());
            }
        }
    }

    private boolean validateGrappleTarget(Player player, org.bukkit.entity.LivingEntity target) {
        if (target == null || target.isDead() || !target.isValid()) {
            return false;
        }

        if (target.equals(player)) {
            return false;
        }

        double distance = player.getLocation().distance(target.getLocation());
        if (distance > config.getMaxDistance() * 2) {
            messageHandler.sendActionBar(player, config.getTargetTooFarMessage());
            return false;
        }

        if (target instanceof Player targetPlayer) {
            if (player.isOp() && targetPlayer.isOp()) {
                messageHandler.sendActionBar(player, config.getOpCannotGrappleMessage());
                return false;
            }
        }

        return true;
    }

    private void playSoundIfEnabled(Player player, Sound sound) {
        if (config.isGrappleSoundEnabled()) {
            player.getWorld().playSound(player.getLocation(), sound, config.getSoundVolume(), config.getSoundPitch());
        }
    }

    public GrappleSession getSession(UUID playerId) {
        return activeSessions.get(playerId);
    }

    public boolean isGrappleActive(UUID playerId) {
        return activeSessions.containsKey(playerId);
    }

    public boolean isTargetBeingGrappled(org.bukkit.entity.LivingEntity target) {
        return activeSessions.values().stream()
                .anyMatch(session -> session.getTarget().equals(target));
    }

    public String getPermissionUse() {
        return PERMISSION_USE;
    }

    public String getPermissionAdmin() {
        return PERMISSION_ADMIN;
    }

    public MessageHandler getMessageHandler() {
        return messageHandler;
    }

    public Collection<GrappleSession> getAllSessions() {
        return activeSessions.values();
    }

    public UUID getGrapplerIdFromSession(GrappleSession session) {
        return activeSessions.entrySet().stream()
                .filter(entry -> entry.getValue().equals(session))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }
}

class GrappleSession {
    
    private final org.bukkit.entity.LivingEntity target;
    private final String graplerName;
    private final PlayerFlightState flightState;
    private final EntityAIState aiState;
    private double distance;
    private long lastSoundTime;

    public GrappleSession(org.bukkit.entity.LivingEntity target, double distance, String graplerName) {
        this.target = target;
        this.distance = distance;
        this.graplerName = graplerName;
        this.flightState = new PlayerFlightState();
        this.aiState = new EntityAIState();
        this.lastSoundTime = 0;
        
        initializeStates();
    }

    private void initializeStates() {
        if (target instanceof Player player) {
            flightState.saveState(player);
            player.setAllowFlight(true);
            player.setFlying(true);
        }
        
        if (target instanceof org.bukkit.entity.Creature creature) {
            aiState.saveState(creature);
            creature.setTarget(null);
            creature.setAI(false);
        }
    }

    public void adjustDistance(boolean increase, double step, double minDistance, double maxDistance) {
        if (increase) {
            distance = Math.min(distance + step, maxDistance);
        } else {
            distance = Math.max(distance - step, minDistance);
        }
    }

    public boolean shouldPlaySound() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSoundTime > 100) {
            lastSoundTime = currentTime;
            return true;
        }
        return false;
    }

    public boolean isValid() {
        return target != null && !target.isDead() && target.isValid();
    }

    public void restoreStates() {
        if (target instanceof Player player) {
            flightState.restoreState(player);
        }
        
        if (target instanceof org.bukkit.entity.Creature creature) {
            aiState.restoreState(creature);
        }
    }

    public org.bukkit.entity.LivingEntity getTarget() { return target; }
    public String getGraplerName() { return graplerName; }
    public double getDistance() { return distance; }
}

class PlayerFlightState {
    private boolean originalFlightAllowed;

    public void saveState(Player player) {
        originalFlightAllowed = player.getAllowFlight();
    }

    public void restoreState(Player player) {
        player.setAllowFlight(originalFlightAllowed);
        if (!originalFlightAllowed) {
            player.setFlying(false);
        }
    }
}

class EntityAIState {
    private boolean originalAIEnabled;

    public void saveState(org.bukkit.entity.Creature creature) {
        originalAIEnabled = creature.hasAI();
    }

    public void restoreState(org.bukkit.entity.Creature creature) {
        creature.setAI(originalAIEnabled);
    }
}

class GrappleEffectManager {
    
    private final JavaPlugin plugin;
    private final GrappleConfig config;
    private final GlowEffectHandler glowHandler;
    private final TitleEffectHandler titleHandler;

    public GrappleEffectManager(JavaPlugin plugin, GrappleConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.glowHandler = new GlowEffectHandler(plugin, config);
        this.titleHandler = new TitleEffectHandler(config);
    }

    public void applyGrappleEffects(org.bukkit.entity.LivingEntity target, GrappleSession session, Player grappler) {
        if (config.isGlowEffectEnabled()) {
            glowHandler.applyGlow(target);
        }
        
        if (target instanceof Player targetPlayer && config.isTitleEnabled()) {
            titleHandler.showTitle(targetPlayer, session.getGraplerName());
            lookAtGrappler(targetPlayer, grappler);
        }
    }

    public void removeGrappleEffects(GrappleSession session) {
        org.bukkit.entity.LivingEntity target = session.getTarget();
        
        if (target == null || target.isDead() || !target.isValid()) {
            return;
        }

        if (config.isGlowEffectEnabled()) {
            glowHandler.removeGlow(target);
        }
        
        if (target instanceof Player targetPlayer && config.isTitleEnabled()) {
            titleHandler.clearTitle(targetPlayer);
        }
        
        session.restoreStates();
    }

    public void playUpdateSound(GrappleSession session) {
        if (config.isGrappleSoundEnabled() && session.shouldPlaySound()) {
            org.bukkit.entity.Entity target = session.getTarget();
            target.getWorld().playSound(
                target.getLocation(),
                config.getGrappleUpdateSound(),
                config.getSoundVolume() * 0.3f,
                config.getSoundPitch()
            );
        }
    }

    private void lookAtGrappler(Player target, Player grappler) {
        Location targetLocation = target.getEyeLocation();
        Location grapplerLocation = grappler.getEyeLocation();
        
        org.bukkit.util.Vector direction = grapplerLocation.toVector()
                .subtract(targetLocation.toVector())
                .normalize();
        
        targetLocation.setYaw((float) (Math.atan2(-direction.getX(), direction.getZ()) * 180.0 / Math.PI));
        targetLocation.setPitch((float) (Math.asin(-direction.getY()) * 80.0 / Math.PI));
        
        target.teleport(targetLocation);
    }
}

class GlowEffectHandler {
    
    private static final String TEAM_NAME = "GrappleTeam";
    private final JavaPlugin plugin;
    private final GrappleConfig config;
    private org.bukkit.scoreboard.Team grappleTeam;

    public GlowEffectHandler(JavaPlugin plugin, GrappleConfig config) {
        this.plugin = plugin;
        this.config = config;
        initializeTeam();
    }

    private void initializeTeam() {
        if (!config.isGlowEffectEnabled()) return;
        
        try {
            org.bukkit.scoreboard.Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
            org.bukkit.scoreboard.Team existingTeam = scoreboard.getTeam(TEAM_NAME);
            
            if (existingTeam != null) {
                existingTeam.unregister();
            }
            
            grappleTeam = scoreboard.registerNewTeam(TEAM_NAME);
            grappleTeam.setColor(ChatColor.RED);
            grappleTeam.setOption(org.bukkit.scoreboard.Team.Option.COLLISION_RULE, 
                                org.bukkit.scoreboard.Team.OptionStatus.FOR_OTHER_TEAMS);
        } catch (Exception e) {
            plugin.getLogger().warning(config.getString("messages.error-glow-team", "Failed to initialize glow team: ") + e.getMessage());
        }
    }

    public void applyGlow(org.bukkit.entity.LivingEntity entity) {
        entity.setGlowing(true);
        if (grappleTeam != null) {
            grappleTeam.addEntry(getEntityEntry(entity));
        }
    }

    public void removeGlow(org.bukkit.entity.LivingEntity entity) {
        entity.setGlowing(false);
        if (grappleTeam != null) {
            grappleTeam.removeEntry(getEntityEntry(entity));
        }
    }

    private String getEntityEntry(org.bukkit.entity.LivingEntity entity) {
        return entity instanceof Player ? entity.getName() : entity.getUniqueId().toString();
    }
}

class TitleEffectHandler {
    
    private static final int TITLE_FADE_IN = 10;
    private static final int TITLE_STAY = 60;
    private static final int TITLE_FADE_OUT = 10;
    
    private final GrappleConfig config;

    public TitleEffectHandler(GrappleConfig config) {
        this.config = config;
    }

    public void showTitle(Player player, String grapplerName) {
        String title = config.getTitleText().replace("{grapper}", grapplerName);
        player.sendTitle(title, config.getSubtitleText(), TITLE_FADE_IN, TITLE_STAY, TITLE_FADE_OUT);
    }

    public void clearTitle(Player player) {
        player.sendTitle("", "", 0, 1, 0);
    }
}

class GrappleMovementHandler {
    
    private static final double PLAYER_HEIGHT_OFFSET = 1.0;
    private static final double MIN_MOVE_DISTANCE_SQUARED = 0.04;
    private static final double PLAYER_VELOCITY_FACTOR = 0.35;
    private static final double PLAYER_MAX_VELOCITY = 1.5;
    private static final double PLAYER_MAX_VERTICAL_VELOCITY = 0.6;
    private static final double ENTITY_VELOCITY_FACTOR = 0.25;
    private static final double ENTITY_MAX_VELOCITY = 1.2;
    private static final double ENTITY_MAX_VERTICAL_VELOCITY = 0.4;
    private static final double TELEPORT_THRESHOLD_PLAYER = 9.0;
    private static final double TELEPORT_THRESHOLD_ENTITY = 6.0;
    
    private final JavaPlugin plugin;
    private final GrappleConfig config;

    public GrappleMovementHandler(JavaPlugin plugin, GrappleConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void updateTargetPosition(Player grappler, GrappleSession session) {
        org.bukkit.entity.LivingEntity target = session.getTarget();
        Location desiredPosition = calculateDesiredPosition(grappler, session.getDistance());
        
        if (target instanceof Player targetPlayer) {
            movePlayer(targetPlayer, desiredPosition);
        } else {
            moveEntity(target, desiredPosition);
        }
    }

    private Location calculateDesiredPosition(Player grappler, double distance) {
        Location base = grappler.getLocation().clone();
        base.setY(base.getY() + PLAYER_HEIGHT_OFFSET);
        
        org.bukkit.util.Vector direction = grappler.getEyeLocation().getDirection().normalize();
        return base.add(direction.multiply(distance));
    }

    private void movePlayer(Player target, Location desiredPosition) {
        org.bukkit.util.Vector delta = desiredPosition.toVector().subtract(target.getLocation().toVector());
        
        if (delta.lengthSquared() < MIN_MOVE_DISTANCE_SQUARED) {
            return;
        }

        target.setFallDistance(0);

        if (delta.lengthSquared() > TELEPORT_THRESHOLD_PLAYER) {
            Location teleportLocation = desiredPosition.clone();
            teleportLocation.setYaw(target.getLocation().getYaw());
            teleportLocation.setPitch(target.getLocation().getPitch());
            target.teleport(teleportLocation);
        } else {
            org.bukkit.util.Vector velocity = calculatePlayerVelocity(delta);
            target.setVelocity(velocity);
        }
    }

    private void moveEntity(org.bukkit.entity.LivingEntity target, Location desiredPosition) {
        org.bukkit.util.Vector delta = desiredPosition.toVector().subtract(target.getLocation().toVector());
        
        if (delta.lengthSquared() < MIN_MOVE_DISTANCE_SQUARED) {
            return;
        }

        target.setFallDistance(0);

        if (delta.lengthSquared() > TELEPORT_THRESHOLD_ENTITY) {
            Location teleportLocation = desiredPosition.clone();
            teleportLocation.setYaw(target.getLocation().getYaw());
            teleportLocation.setPitch(target.getLocation().getPitch());
            target.teleport(teleportLocation);
        } else {
            org.bukkit.util.Vector velocity = calculateEntityVelocity(delta);
            target.setVelocity(velocity);
            
            scheduleEntityCorrection(target, desiredPosition);
        }
    }

    private org.bukkit.util.Vector calculatePlayerVelocity(org.bukkit.util.Vector delta) {
        org.bukkit.util.Vector velocity = delta.multiply(PLAYER_VELOCITY_FACTOR);
        
        if (velocity.length() > PLAYER_MAX_VELOCITY) {
            velocity = velocity.normalize().multiply(PLAYER_MAX_VELOCITY);
        }
        
        velocity.setY(Math.max(Math.min(velocity.getY(), PLAYER_MAX_VERTICAL_VELOCITY), -PLAYER_MAX_VERTICAL_VELOCITY));
        return velocity;
    }

    private org.bukkit.util.Vector calculateEntityVelocity(org.bukkit.util.Vector delta) {
        org.bukkit.util.Vector velocity = delta.multiply(ENTITY_VELOCITY_FACTOR);
        
        if (velocity.length() > ENTITY_MAX_VELOCITY) {
            velocity = velocity.normalize().multiply(ENTITY_MAX_VELOCITY);
        }
        
        velocity.setY(Math.max(Math.min(velocity.getY(), ENTITY_MAX_VERTICAL_VELOCITY), -ENTITY_MAX_VERTICAL_VELOCITY));
        return velocity;
    }

    private void scheduleEntityCorrection(org.bukkit.entity.LivingEntity target, Location desiredPosition) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (target.isValid() && !target.isDead()) {
                    double currentDistance = target.getLocation().distance(desiredPosition);
                    if (currentDistance > 1.0) {
                        Location correctionLocation = desiredPosition.clone();
                        correctionLocation.setYaw(target.getLocation().getYaw());
                        correctionLocation.setPitch(target.getLocation().getPitch());
                        target.teleport(correctionLocation);
                    }
                }
            }
        }.runTaskLater(plugin, 5L);
    }
}

class CommandHandler {
    
    private final JavaPlugin plugin;
    private final GrappleManager grappleManager;
    private final MessageHandler messageHandler;

    public CommandHandler(JavaPlugin plugin, GrappleManager grappleManager) {
        this.plugin = plugin;
        this.grappleManager = grappleManager;
        this.messageHandler = grappleManager.getMessageHandler();
    }

    public boolean handleCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(((GrapTool) plugin).getGrappleConfig().getPlayerOnlyMessage());
            return true;
        }

        if (args.length != 1) {
            messageHandler.sendActionBar(player, ((GrapTool) plugin).getGrappleConfig().getInvalidCommandMessage());
            return true;
        }

        String action = args[0].toLowerCase();
        
        switch (action) {
            case "on" -> handleEnableCommand(player);
            case "off" -> handleDisableCommand(player);
            case "reload" -> handleReloadCommand(player);
            default -> messageHandler.sendActionBar(player, ((GrapTool) plugin).getGrappleConfig().getInvalidCommandMessage());
        }
        
        return true;
    }

    public List<String> handleTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }

        String input = args[0].toLowerCase();
        List<String> completions = new ArrayList<>();
        
        if ("on".startsWith(input)) completions.add("on");
        if ("off".startsWith(input)) completions.add("off");
        if (sender.hasPermission(grappleManager.getPermissionAdmin()) && "reload".startsWith(input)) {
            completions.add("reload");
        }
        
        return completions;
    }

    private void handleEnableCommand(Player player) {
        if (!grappleManager.hasPermission(player, grappleManager.getPermissionUse())) {
            messageHandler.sendNoPermission(player);
            return;
        }
        
        grappleManager.enablePlayer(player.getUniqueId());
        messageHandler.sendActionBar(player, ((GrapTool) plugin).getGrappleConfig().getGrappleEnabledMessage());
    }

    private void handleDisableCommand(Player player) {
        if (!grappleManager.hasPermission(player, grappleManager.getPermissionUse())) {
            messageHandler.sendNoPermission(player);
            return;
        }
        
        grappleManager.disablePlayer(player.getUniqueId());
        messageHandler.sendActionBar(player, ((GrapTool) plugin).getGrappleConfig().getGrappleDisabledMessage());
    }

    private void handleReloadCommand(Player player) {
        if (!grappleManager.hasPermission(player, grappleManager.getPermissionAdmin())) {
            messageHandler.sendNoPermission(player);
            return;
        }
        
        plugin.reloadConfig();
        ((GrapTool) plugin).getGrappleConfig().reload();
        messageHandler.sendActionBar(player, ((GrapTool) plugin).getGrappleConfig().getConfigReloadedMessage());
    }
}

class EventController {
    
    private final JavaPlugin plugin;
    private final GrappleManager grappleManager;
    private final GrappleConfig config;
    private final MessageHandler messageHandler;

    public EventController(JavaPlugin plugin, GrappleManager grappleManager, GrappleConfig config) {
        this.plugin = plugin;
        this.grappleManager = grappleManager;
        this.config = config;
        this.messageHandler = grappleManager.getMessageHandler();
    }

    public void handlePlayerQuit(PlayerQuitEvent event) {
        grappleManager.cleanupPlayer(event.getPlayer().getUniqueId());
    }

    public void handleEntityDamage(EntityDamageEvent event) {
        if (!config.isGodModeEnabled() || !(event.getEntity() instanceof Player player)) {
            return;
        }

        if (grappleManager.isTargetBeingGrappled(player)) {
            event.setCancelled(true);
        }
    }

    public void handleEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        
        if (!(event.getEntity() instanceof org.bukkit.entity.LivingEntity target)) {
            return;
        }
        
        if (target.equals(player)) {
            return;
        }

        if (!grappleManager.isPlayerEnabled(player.getUniqueId()) || 
            !grappleManager.hasPermission(player, grappleManager.getPermissionUse())) {
            return;
        }

        event.setCancelled(true);
        handleGrappleInteraction(player, target);
    }

    public void handlePlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        
        if (!grappleManager.hasPermission(player, grappleManager.getPermissionUse())) {
            return;
        }

        GrappleSession session = grappleManager.getSession(player.getUniqueId());
        if (session == null) {
            return;
        }

        boolean isScrollUp = isScrollingUp(event.getPreviousSlot(), event.getNewSlot());
        boolean isScrollDown = isScrollingDown(event.getPreviousSlot(), event.getNewSlot());

        if (isScrollUp || isScrollDown) {
            grappleManager.adjustDistance(player.getUniqueId(), isScrollUp);
            messageHandler.sendActionBar(player, config.getDistanceMessage().replace("{distance}", String.format("%.1f", session.getDistance())));
        }
    }

    private void handleGrappleInteraction(Player player, org.bukkit.entity.LivingEntity target) {
        if (player.isSneaking()) {
            handleSneakInteraction(player, target);
            return;
        }

        if (grappleManager.isGrappleActive(player.getUniqueId())) {
            grappleManager.stopGrapple(player.getUniqueId());
            messageHandler.sendActionBar(player, config.getGrappleStoppedMessage());
            return;
        }

        if (grappleManager.startGrapple(player, target)) {
            String targetName = getTargetName(target);
            messageHandler.sendActionBar(player, config.getGrappleStartedMessage().replace("{target}", targetName));
        } else {
            messageHandler.sendActionBar(player, config.getGrappleFailedMessage());
        }
    }

    private void handleSneakInteraction(Player player, org.bukkit.entity.LivingEntity target) {
        GrappleSession session = grappleManager.getSession(player.getUniqueId());
        
        if (session != null && session.getTarget().equals(target)) {
            grappleManager.stopGrapple(player.getUniqueId());
            messageHandler.sendActionBar(player, config.getGrappleSneakStoppedMessage());
        }
    }

    private boolean isScrollingUp(int previousSlot, int newSlot) {
        return (newSlot == previousSlot + 1) || (previousSlot == 8 && newSlot == 0);
    }

    private boolean isScrollingDown(int previousSlot, int newSlot) {
        return (newSlot == previousSlot - 1) || (previousSlot == 0 && newSlot == 8);
    }

    private String getTargetName(org.bukkit.entity.LivingEntity target) {
        return target instanceof Player ? ((Player) target).getName() : target.getType().name();
    }
}

class TaskManager {
    
    private static final int UPDATE_TASK_INTERVAL = 2;
    private static final int TITLE_UPDATE_INTERVAL = 40;
    
    private final JavaPlugin plugin;
    private final GrappleManager grappleManager;
    private final GrappleConfig config;
    private int updateTaskId = -1;
    private int titleTaskId = -1;

    public TaskManager(JavaPlugin plugin, GrappleManager grappleManager, GrappleConfig config) {
        this.plugin = plugin;
        this.grappleManager = grappleManager;
        this.config = config;
    }

    public void startUpdateTask() {
        updateTaskId = new BukkitRunnable() {
            @Override
            public void run() {
                grappleManager.getAllSessions().forEach(session -> {
                    UUID playerId = grappleManager.getGrapplerIdFromSession(session);
                    if (playerId != null) {
                        Player player = Bukkit.getPlayer(playerId);
                        if (player != null) {
                            grappleManager.updateGrapple(playerId);
                        }
                    }
                });
            }
        }.runTaskTimer(plugin, 0L, UPDATE_TASK_INTERVAL).getTaskId();
    }

    public void startTitleTask() {
        titleTaskId = new BukkitRunnable() {
            @Override
            public void run() {
                grappleManager.getAllSessions().forEach(session -> {
                    if (session.getTarget() instanceof Player targetPlayer && targetPlayer.isOnline()) {
                        new TitleEffectHandler(config).showTitle(targetPlayer, session.getGraplerName());
                    }
                });
            }
        }.runTaskTimer(plugin, 0L, TITLE_UPDATE_INTERVAL).getTaskId();
    }

    public void shutdown() {
        if (updateTaskId != -1) {
            Bukkit.getScheduler().cancelTask(updateTaskId);
        }
        if (titleTaskId != -1) {
            Bukkit.getScheduler().cancelTask(titleTaskId);
        }
    }
}

class MessageHandler {
    
    public void sendActionBar(Player player, String message) {
        try {
            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, 
                                      new net.md_5.bungee.api.chat.TextComponent(message));
        } catch (Exception e) {
            player.sendMessage(message);
        }
    }

    public void sendNoPermission(Player player) {
        sendActionBar(player, ((GrapTool) Bukkit.getPluginManager().getPlugin("GrapTool")).getGrappleConfig().getNoPermissionMessage());
    }

    public void sendMessage(Player player, String message) {
        player.sendMessage(message);
    }
}

class PlayerStateManager {
    
    private final Map<UUID, PlayerState> playerStates = new ConcurrentHashMap<>();

    public void savePlayerState(Player player) {
        playerStates.put(player.getUniqueId(), new PlayerState(player));
    }

    public void restorePlayerState(Player player) {
        PlayerState state = playerStates.remove(player.getUniqueId());
        if (state != null) {
            state.restore(player);
        }
    }

    public void clearPlayerState(UUID playerId) {
        playerStates.remove(playerId);
    }

    private static class PlayerState {
        private final boolean flyAllowed;
        private final boolean flying;
        private final double health;
        private final int foodLevel;

        public PlayerState(Player player) {
            this.flyAllowed = player.getAllowFlight();
            this.flying = player.isFlying();
            this.health = player.getHealth();
            this.foodLevel = player.getFoodLevel();
        }

        public void restore(Player player) {
            player.setAllowFlight(flyAllowed);
            player.setFlying(flying);
            player.setHealth(health);
            player.setFoodLevel(foodLevel);
        }
    }
}
