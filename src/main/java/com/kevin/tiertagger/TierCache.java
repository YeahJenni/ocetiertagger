package com.kevin.tiertagger;

import com.kevin.tiertagger.model.GameMode;
import com.kevin.tiertagger.model.OCETierPlayer;
import com.kevin.tiertagger.model.OCETierPlayer.GameModeTier;
import com.kevin.tiertagger.model.PlayerInfo;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class TierCache {
    private static final String API_BASE_URL = "https://api.yeahjenni.xyz/ocetiers/player/";
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final Gson GSON = new GsonBuilder().create();

    private static final Map<String, OCETierPlayer> USERNAME_CACHE = new ConcurrentHashMap<>();

    private static final Set<String> NOT_FOUND_PLAYERS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static final Map<String, CompletableFuture<OCETierPlayer>> PENDING_REQUESTS = new ConcurrentHashMap<>();

    private static final OCETierPlayer NOT_FOUND_PLACEHOLDER = new OCETierPlayer(
        "not-found", "not-found", 0, 0, "", Collections.emptyMap()
    );

    // List of valid game modes
    public static final List<String> GAME_MODES = Arrays.asList(
        "sword", "diamondPot", "netheritePot", "axe", 
        "uhc", "smp", "crystal", "diamondSmp", "cart", "mace"
    );

    // Display names for game modes
    private static final Map<String, String> GAME_MODE_DISPLAY = Map.of(
        "sword", "Sword",
        "diamondPot", "Diamond Pot",
        "netheritePot", "Netherite Pot",
        "axe", "Axe",
        "uhc", "UHC",
        "smp", "SMP",
        "crystal", "Crystal",
        "diamondSmp", "Diamond SMP",
        "cart", "Cart",
        "mace", "Mace"
    );

    /**
     * Fetch player data from the API by username
     */
    public static CompletableFuture<OCETierPlayer> fetchPlayerByUsername(String username) {
        String lowerUsername = username.toLowerCase();

        if (USERNAME_CACHE.containsKey(lowerUsername)) {
            return CompletableFuture.completedFuture(USERNAME_CACHE.get(lowerUsername));
        }

        if (NOT_FOUND_PLAYERS.contains(lowerUsername)) {
            return CompletableFuture.completedFuture(NOT_FOUND_PLACEHOLDER);
        }

        if (PENDING_REQUESTS.containsKey(lowerUsername)) {
            return PENDING_REQUESTS.get(lowerUsername);
        }

        CompletableFuture<OCETierPlayer> future = new CompletableFuture<>();
        PENDING_REQUESTS.put(lowerUsername, future);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_BASE_URL + username))
            .GET()
            .build();

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                if (response.statusCode() == 200) {
                    String responseBody = response.body();
                    TierTagger.getLogger().info("API Response for {}: {}", username, responseBody); 

                    OCETierPlayer player = GSON.fromJson(responseBody, OCETierPlayer.class);
                    USERNAME_CACHE.put(lowerUsername, player);
                    return player;
                } else if (response.statusCode() == 404) {
                    NOT_FOUND_PLAYERS.add(lowerUsername);
                    return NOT_FOUND_PLACEHOLDER;
                } else {
                    throw new RuntimeException("Unexpected response code: " + response.statusCode());
                }
            })
            .whenComplete((result, error) -> {
                PENDING_REQUESTS.remove(lowerUsername);
                if (error != null) {
                    future.completeExceptionally(error);
                } else {
                    future.complete(result);
                }
            });
    }

    /**
     * Get the tier for a specific player and game mode
     */
    public static CompletableFuture<String> getPlayerTier(String username, String gameMode) {
        return fetchPlayerByUsername(username).thenApply(player -> {
            if (player == null || player == NOT_FOUND_PLACEHOLDER) {
                return null;
            }
            OCETierPlayer.GameModeTier tierData = player.gameModes().get(gameMode);
            if (tierData == null || tierData.tier() == null) {
                return null;
            }
            return tierData.tier(); 
        });
    }

    /**
     * Get the cached tier for a specific player and game mode
     */
    public static String getCachedTier(String username, String gameMode) {
        OCETierPlayer player = USERNAME_CACHE.get(username.toLowerCase());
        if (player == null) return null;
        OCETierPlayer.GameModeTier tierData = player.gameModes().get(gameMode);
        if (tierData == null || tierData.tier() == null) return null;
        return tierData.tier(); 
    }

    /**
     * Search for player information by username
     */
    public static CompletableFuture<PlayerInfo> searchPlayer(String username) {
        return fetchPlayerByUsername(username).thenApply(player -> {
            if (player == null || player == NOT_FOUND_PLACEHOLDER) return null;

            // Convert OCETierPlayer to PlayerInfo
            Map<String, PlayerInfo.Ranking> rankings = new HashMap<>();
            if (player.gameModes() != null) {
                player.gameModes().forEach((mode, tierData) -> {
                    if (tierData.tier() != null) {
                        rankings.put(mode, new PlayerInfo.Ranking(
                            tierData.tier(),
                            Instant.now().getEpochSecond(), 
                            0, 
                            tierData.isLT()
                        ));
                    }
                });
            }

            return new PlayerInfo(
                player.username(),
                player.id(),
                "OCE",
                player.score(),
                player.leaderboardPosition(),
                rankings
            );
        });
    }

    /**
     * Get player information by username
     */
    public static CompletableFuture<PlayerInfo> getPlayerInfo(String username) {
        return searchPlayer(username);
    }

    /**
     * Clear all cached data
     */
    public static void clearCache() {
        USERNAME_CACHE.clear();
        NOT_FOUND_PLAYERS.clear();
        PENDING_REQUESTS.clear();
    }

    /**
     * Find a game mode by its ID
     */
    public static GameMode findMode(String id) {
        String title = GAME_MODE_DISPLAY.getOrDefault(id, id);
        return new GameMode(id, title);
    }

    /**
     * Find the next game mode in the list
     */
    public static String findNextMode(String currentMode) {
        int index = GAME_MODES.indexOf(currentMode);
        if (index == -1) return GAME_MODES.get(0); 
        return GAME_MODES.get((index + 1) % GAME_MODES.size());
    }

    /**
     * Get the display name for a game mode
     */
    public static String getGameModeDisplay(String gameMode) {
        return GAME_MODE_DISPLAY.getOrDefault(gameMode, gameMode);
    }
}
