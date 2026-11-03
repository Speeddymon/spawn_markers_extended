package com.spawnmarker;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NpcID;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(
        name = "Spawn Markers Extended",
        description = "Tracks nearby NPC spawn tiles with configurable filters and editable saved markers",
        tags = {"spot", "marker", "npc", "spawn", "tracker"}
)
public class SpawnMarkerPlugin extends Plugin {

    private static final String FILTER_MODE_MIGRATED_KEY = "filterModeMigrated";
    private static final String MARKER_EDITOR_KEY = "markerData";
    private static final String MARKER_EDITOR_HASH_KEY = "markerDataHash";

    @Getter(AccessLevel.PACKAGE)
    private final List<ColorTileMarker> points = new ArrayList<>();
    private final Set<String> malformedRegionsWarned = Collections.synchronizedSet(new HashSet<>());
    private static LocalDateTime startedUpAt;
    private volatile NpcNameFilter npcNameFilter = NpcNameFilter.disabled();
    private volatile NpcNameFilter nonCombatNpcNameAllowList = NpcNameFilter.allowList("");
    private volatile boolean updatingMarkerEditor;
    private volatile boolean markerEditorUpdatePending;
    private volatile boolean markerEditorDraftNeedsAttention;

    static final String CONFIG_GROUP = "spawnMarker";
    private static final String REGION_PREFIX = "region_";
    private static final String RECOVERY_PREFIX = "recovery_";
    private static final int MAX_SAVE_DISTANCE = 12;
    private static final Color DEFAULT_COLOR = new Color(255, 108, 0);
    private static final Set<Integer> EVENT_NPCS = ImmutableSet.of(
            NpcID.BEE_KEEPER_6747,
            NpcID.CAPT_ARNAV,
            NpcID.DR_JEKYLL, NpcID.DR_JEKYLL_314,
            NpcID.DRUNKEN_DWARF,
            NpcID.DUNCE_6749,
            NpcID.EVIL_BOB, NpcID.EVIL_BOB_6754,
            NpcID.FLIPPA_6744,
            NpcID.FREAKY_FORESTER_6748,
            NpcID.FROG_5429,
            NpcID.GENIE, NpcID.GENIE_327,
            NpcID.GILES, NpcID.GILES_5441,
            NpcID.LEO_6746,
            NpcID.MILES, NpcID.MILES_5440,
            NpcID.MYSTERIOUS_OLD_MAN_6750, NpcID.MYSTERIOUS_OLD_MAN_6751,
            NpcID.MYSTERIOUS_OLD_MAN_6752, NpcID.MYSTERIOUS_OLD_MAN_6753,
            NpcID.NILES, NpcID.NILES_5439,
            NpcID.PILLORY_GUARD,
            NpcID.POSTIE_PETE_6738,
            NpcID.QUIZ_MASTER_6755,
            NpcID.RICK_TURPENTINE, NpcID.RICK_TURPENTINE_376,
            NpcID.SANDWICH_LADY,
            NpcID.SERGEANT_DAMIEN_6743
    );

    @Inject
    private Client client;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private SpawnMarkerOverlay overlay;

    @Inject
    private ConfigManager configManager;

    @Inject
    private ClientThread clientThread;

    @Inject
    private SpawnMarkerConfig config;

    @Provides
    SpawnMarkerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(SpawnMarkerConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        migrateNameFilterSettings();
        updateNpcNameFilter();
        initializeMarkerEditor();
        overlayManager.add(overlay);
        startedUpAt = LocalDateTime.now();
        if (client.getGameState() == GameState.LOGGED_IN) {
            clientThread.invoke(this::loadPoints);
        }
    }

    @Override
    protected void shutDown() throws Exception {
        points.clear();
        overlayManager.remove(overlay);
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned npcSpawned) {
        if (LocalDateTime.now().isAfter(startedUpAt.plusSeconds(5))) {
            String npcName = npcSpawned.getActor().getName();

            if (StringUtils.isNotBlank(npcName)
                    && !EVENT_NPCS.contains(npcSpawned.getNpc().getId())
                    && (npcSpawned.getNpc().getCombatLevel() > 0 || nonCombatNpcNameAllowList.contains(npcName))) {
                WorldPoint playerLocation = client.getLocalPlayer().getWorldLocation();
                WorldPoint npcLocation = npcSpawned.getActor().getWorldLocation();

                if (npcLocation.distanceTo(playerLocation) > MAX_SAVE_DISTANCE) {
                    return;
                }
                markTile(npcSpawned.getActor().getLocalLocation(), npcName);
            }
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        startedUpAt = LocalDateTime.now();
        if (gameStateChanged.getGameState() != GameState.LOGGED_IN) {
            return;
        }
        loadPoints();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!CONFIG_GROUP.equals(event.getGroup())) {
            return;
        }

        String key = event.getKey();
        boolean enabled = Boolean.parseBoolean(event.getNewValue());

        if ("filterMode".equals(key) || "hiddenNpcNames".equals(key) || "allowedNpcNames".equals(key)
                || "nonCombatNpcNames".equals(key)) {
            updateNpcNameFilter();
        } else if ("clearMarkers".equals(key) && enabled) {
            clientThread.invoke(() -> {
                clearSavedMarkers();
                configManager.unsetConfiguration(CONFIG_GROUP, key);
            });
        } else if ("clearCurrentRegionMarkers".equals(key) && enabled) {
            clientThread.invoke(this::clearCurrentRegionMarkers);
        } else if (MARKER_EDITOR_KEY.equals(key) && !updatingMarkerEditor) {
            String markerData = event.getNewValue();
            if (markerData == null) {
                clientThread.invoke(() -> {
                    markerEditorUpdatePending = false;
                    markerEditorDraftNeedsAttention = false;
                    refreshMarkerEditor();
                });
            } else {
                markerEditorUpdatePending = true;
                clientThread.invoke(() -> {
                    if (Objects.equals(configManager.getConfiguration(CONFIG_GROUP, MARKER_EDITOR_KEY), markerData)) {
                        applyMarkerEditor(markerData);
                    }
                });
            }
        }
    }

    boolean isNpcNameHidden(String npcName) {
        return npcNameFilter.hides(npcName);
    }

    private void updateNpcNameFilter() {
        nonCombatNpcNameAllowList = NpcNameFilter.allowList(config.nonCombatNpcNames());
        switch (config.filterMode()) {
            case DENY_LIST:
                npcNameFilter = NpcNameFilter.denyList(config.hiddenNpcNames());
                break;
            case ALLOW_LIST:
                npcNameFilter = NpcNameFilter.allowList(config.allowedNpcNames());
                break;
            default:
                npcNameFilter = NpcNameFilter.disabled();
        }
    }

    private void migrateNameFilterSettings() {
        if (configManager.getConfiguration(CONFIG_GROUP, FILTER_MODE_MIGRATED_KEY) != null) {
            return;
        }

        configManager.unsetConfiguration(CONFIG_GROUP, "allowListEnabled");
        configManager.unsetConfiguration(CONFIG_GROUP, "denyListEnabled");
        configManager.setConfiguration(CONFIG_GROUP, FILTER_MODE_MIGRATED_KEY, true);
    }

    private void clearSavedMarkers() {
        List<String> markerKeys = configManager.getConfigurationKeys(CONFIG_GROUP + "." + REGION_PREFIX);
        for (String markerKey : markerKeys) {
            String key = markerKey.substring(CONFIG_GROUP.length() + 1);
            configManager.unsetConfiguration(CONFIG_GROUP, key);
        }

        List<String> recoveryKeys = configManager.getConfigurationKeys(CONFIG_GROUP + "." + RECOVERY_PREFIX);
        for (String recoveryKey : recoveryKeys) {
            String key = recoveryKey.substring(CONFIG_GROUP.length() + 1);
            configManager.unsetConfiguration(CONFIG_GROUP, key);
        }

        points.clear();
        malformedRegionsWarned.clear();
        markerEditorUpdatePending = false;
        markerEditorDraftNeedsAttention = false;
        refreshMarkerEditor();
        log.info("Cleared saved spawn markers from {} regions and {} recovery copies", markerKeys.size(), recoveryKeys.size());
    }

    private void clearCurrentRegionMarkers() {
        GameState gameState = client.getGameState();
        WorldPoint playerWorldPoint = null;
        if (gameState == GameState.LOGGED_IN) {
            Player player = client.getLocalPlayer();
            if (player != null) {
                LocalPoint localLocation = player.getLocalLocation();
                if (localLocation != null) {
                    playerWorldPoint = WorldPoint.fromLocalInstance(client, localLocation);
                }
            }
        }

        boolean cleared = CurrentRegionMarkerClearAction.execute(
                gameState,
                playerWorldPoint,
                this::clearSavedMarkersForRegion,
                () -> configManager.unsetConfiguration(CONFIG_GROUP, "clearCurrentRegionMarkers"));
        if (!cleared) {
            log.warn("Cannot clear current-region spawn markers while the player is not logged in or their location is unavailable");
        }
    }

    private void clearSavedMarkersForRegion(int regionId) {
        String regionKey = REGION_PREFIX + regionId;
        configManager.unsetConfiguration(CONFIG_GROUP, regionKey);
        configManager.unsetConfiguration(CONFIG_GROUP, RECOVERY_PREFIX + regionKey);
        malformedRegionsWarned.remove(regionKey);

        loadPoints();
        refreshMarkerEditor();
        log.info("Cleared saved spawn markers for region {}", regionId);
    }

    private void initializeMarkerEditor() {
        String markerData = configManager.getConfiguration(CONFIG_GROUP, MARKER_EDITOR_KEY);
        if (markerData == null) {
            refreshMarkerEditor();
            return;
        }

        String savedHash = configManager.getConfiguration(CONFIG_GROUP, MARKER_EDITOR_HASH_KEY);
        if (savedHash == null || savedHash.equals(hashMarkerEditor(markerData))) {
            // No hash means this is an editor snapshot written by an older plugin version.
            // Treat region entries as canonical so a stale snapshot cannot overwrite newer saves.
            clientThread.invoke(this::refreshMarkerEditor);
            return;
        }

        markerEditorUpdatePending = true;
        clientThread.invoke(() -> applyMarkerEditor(markerData));
    }

    private void applyMarkerEditor(String markerData) {
        if (Strings.isNullOrEmpty(markerData) || markerData.trim().isEmpty()) {
            // Clear all has its own control. Treat a reset or emptied editor as a request to reload,
            // so recovering from an invalid draft cannot erase the saved region entries.
            markerEditorUpdatePending = false;
            markerEditorDraftNeedsAttention = false;
            refreshMarkerEditor();
            return;
        }

        final List<TilePoint> editedPoints;
        try {
            editedPoints = MarkerEditorCodec.deserialize(markerData);
        } catch (IllegalArgumentException e) {
            markerEditorUpdatePending = false;
            markerEditorDraftNeedsAttention = true;
            log.warn("Invalid saved marker editor data was retained for correction: {}", e.getMessage());
            return;
        }

        List<TilePoint> savedPoints = getAllSavedTilePoints();
        if (!MarkerEditorCodec.serialize(editedPoints).equals(MarkerEditorCodec.serialize(savedPoints))) {
            try {
                replaceAllSavedTilePoints(editedPoints);
            } catch (RuntimeException e) {
                markerEditorUpdatePending = false;
                markerEditorDraftNeedsAttention = true;
                log.warn("Unable to apply saved marker edits; the editor text was retained", e);
                return;
            }

            if (client.getGameState() == GameState.LOGGED_IN) {
                loadPoints();
            } else {
                points.clear();
            }
        }

        markerEditorUpdatePending = false;
        markerEditorDraftNeedsAttention = false;
        refreshMarkerEditor();
    }

    private List<TilePoint> getAllSavedTilePoints() {
        List<TilePoint> allPoints = new ArrayList<>();
        List<String> markerKeys = configManager.getConfigurationKeys(CONFIG_GROUP + "." + REGION_PREFIX);
        for (String markerKey : markerKeys) {
            String key = markerKey.substring(CONFIG_GROUP.length() + 1);
            String json = configManager.getConfiguration(CONFIG_GROUP, key);
            if (Strings.isNullOrEmpty(json)) {
                continue;
            }

            try {
                allPoints.addAll(TilePointCodec.deserialize(json));
            } catch (RuntimeException e) {
                preserveMalformedRegion(key, json, e);
            }
        }

        return allPoints;
    }

    private void replaceAllSavedTilePoints(Collection<TilePoint> replacementPoints) {
        Map<Integer, List<TilePoint>> pointsByRegion = new TreeMap<>();
        for (TilePoint point : replacementPoints) {
            pointsByRegion.computeIfAbsent(point.getRegionId(), ignored -> new ArrayList<>()).add(point);
        }

        Map<String, String> replacementData = new TreeMap<>();
        for (Map.Entry<Integer, List<TilePoint>> entry : pointsByRegion.entrySet()) {
            replacementData.put(REGION_PREFIX + entry.getKey(), TilePointCodec.serialize(entry.getValue()));
        }

        Map<String, String> previousData = new LinkedHashMap<>();
        List<String> markerKeys = configManager.getConfigurationKeys(CONFIG_GROUP + "." + REGION_PREFIX);
        for (String markerKey : markerKeys) {
            String key = markerKey.substring(CONFIG_GROUP.length() + 1);
            previousData.put(key, configManager.getConfiguration(CONFIG_GROUP, key));
        }

        try {
            // Write every replacement before deleting regions removed by the edit.
            for (Map.Entry<String, String> entry : replacementData.entrySet()) {
                configManager.setConfiguration(CONFIG_GROUP, entry.getKey(), entry.getValue());
            }

            for (String oldKey : previousData.keySet()) {
                if (!replacementData.containsKey(oldKey)) {
                    configManager.unsetConfiguration(CONFIG_GROUP, oldKey);
                }
            }
        } catch (RuntimeException e) {
            // ConfigManager has no multi-key transaction, so restore the original values if a write fails.
            Set<String> keysToRestore = new LinkedHashSet<>(replacementData.keySet());
            keysToRestore.addAll(previousData.keySet());
            for (String key : keysToRestore) {
                try {
                    String oldValue = previousData.get(key);
                    if (oldValue == null) {
                        configManager.unsetConfiguration(CONFIG_GROUP, key);
                    } else {
                        configManager.setConfiguration(CONFIG_GROUP, key, oldValue);
                    }
                } catch (RuntimeException restoreException) {
                    e.addSuppressed(restoreException);
                }
            }

            throw new IllegalStateException("Unable to replace saved marker regions", e);
        }
    }

    private void refreshMarkerEditor() {
        if (markerEditorUpdatePending || markerEditorDraftNeedsAttention) {
            return;
        }

        String markerData = MarkerEditorCodec.serialize(getAllSavedTilePoints());
        String currentMarkerData = configManager.getConfiguration(CONFIG_GROUP, MARKER_EDITOR_KEY);
        String markerDataHash = hashMarkerEditor(markerData);
        String currentMarkerDataHash = configManager.getConfiguration(CONFIG_GROUP, MARKER_EDITOR_HASH_KEY);

        updatingMarkerEditor = true;
        try {
            if (!markerData.equals(currentMarkerData)) {
                configManager.setConfiguration(CONFIG_GROUP, MARKER_EDITOR_KEY, markerData);
            }
            if (!markerDataHash.equals(currentMarkerDataHash)) {
                configManager.setConfiguration(CONFIG_GROUP, MARKER_EDITOR_HASH_KEY, markerDataHash);
            }
        } finally {
            updatingMarkerEditor = false;
        }
    }

    private String hashMarkerEditor(String markerData) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(markerData.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private void preserveMalformedRegion(String regionKey, String json, RuntimeException cause) {
        String recoveryKey = RECOVERY_PREFIX + regionKey;
        boolean firstWarning = malformedRegionsWarned.add(regionKey);
        try {
            if (configManager.getConfiguration(CONFIG_GROUP, recoveryKey) == null) {
                configManager.setConfiguration(CONFIG_GROUP, recoveryKey, json);
            }

            if (firstWarning) {
                log.warn("Unable to read saved spawn markers from {}; recovery data is stored under {}",
                        regionKey, recoveryKey, cause);
            }
        } catch (RuntimeException backupFailure) {
            cause.addSuppressed(backupFailure);
            if (firstWarning) {
                log.error("Unable to preserve malformed saved markers from {}", regionKey, cause);
            }
        }
    }

    private void savePoints(int regionId, Collection<TilePoint> points) {
        if (points == null || points.isEmpty()) {
            configManager.unsetConfiguration(CONFIG_GROUP, REGION_PREFIX + regionId);
            return;
        }

        String json = TilePointCodec.serialize(points);
        configManager.setConfiguration(CONFIG_GROUP, REGION_PREFIX + regionId, json);
    }

    private Collection<TilePoint> getPoints(int regionId) {
        String json = configManager.getConfiguration(CONFIG_GROUP, REGION_PREFIX + regionId);
        if (Strings.isNullOrEmpty(json)) {
            return Collections.emptyList();
        }

        try {
            return TilePointCodec.deserialize(json);
        } catch (RuntimeException e) {
            preserveMalformedRegion(REGION_PREFIX + regionId, json, e);
            log.warn("Treating malformed saved spawn markers for region {} as empty", regionId);
            return Collections.emptyList();
        }
    }

    private void loadPoints() {
        points.clear();

        int[] regions = client.getMapRegions();

        if (regions == null) {
            log.debug("Cannot load spawn markers because map regions are unavailable");
            return;
        }

        for (int regionId : regions) {
            Collection<TilePoint> regionPoints = getPoints(regionId);
            Collection<ColorTileMarker> colorTileMarkers = translateToColorTileMarker(regionPoints);
            points.addAll(colorTileMarkers);
        }

        log.debug("Loaded {} spawn markers from {} map regions", points.size(), regions.length);
    }

    /**
     * Translate a collection of tile points to color tile markers, accounting for instances
     *
     * @param points {@link TilePoint}s to be converted to {@link ColorTileMarker}s
     * @return A collection of color tile markers, converted from the passed tile points, accounting for local
     *         instance points. See {@link WorldPoint#toLocalInstance(Client, WorldPoint)}
     */
    private Collection<ColorTileMarker> translateToColorTileMarker(Collection<TilePoint> points) {
        if (points.isEmpty()) {
            return Collections.emptyList();
        }

        return points.stream()
                .map(point -> new ColorTileMarker(
                        WorldPoint.fromRegion(point.getRegionId(), point.getRegionX(), point.getRegionY(), point.getZ()),
                        point.getColor(), point.getLabel()))
                .flatMap(colorTile -> {
                    final Collection<WorldPoint> localWorldPoints = WorldPoint.toLocalInstance(client, colorTile.getWorldPoint());
                    return localWorldPoints.stream().map(wp -> new ColorTileMarker(wp, colorTile.getColor(), colorTile.getLabel()));
                })
                .collect(Collectors.toList());
    }

    private void markTile(LocalPoint localPoint, String label) {
        if (localPoint == null) {
            return;
        }

        WorldPoint worldPoint = WorldPoint.fromLocalInstance(client, localPoint);

        int regionId = worldPoint.getRegionID();
        TilePoint point = new TilePoint(regionId, worldPoint.getRegionX(), worldPoint.getRegionY(), client.getPlane(), DEFAULT_COLOR, label);
        log.debug("Updating point: {} - {}", point, worldPoint);

        List<TilePoint> groundMarkerPoints = new ArrayList<>(getPoints(regionId));
        if (!groundMarkerPoints.contains(point)) {
            groundMarkerPoints.add(point);
        }

        savePoints(regionId, groundMarkerPoints);

        loadPoints();
        refreshMarkerEditor();
    }

}
