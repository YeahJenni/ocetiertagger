package com.kevin.tiertagger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kevin.tiertagger.config.TierTaggerConfig;
import com.kevin.tiertagger.model.GameMode;
import com.kevin.tiertagger.model.PlayerInfo;
import com.kevin.tiertagger.model.OCETierPlayer;
import com.mojang.brigadier.context.CommandContext;
import lombok.Getter;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.uku3lig.ukulib.config.ConfigManager;
import net.uku3lig.ukulib.utils.PlayerArgumentType;
import net.uku3lig.ukulib.utils.Ukutils;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class TierTagger implements ModInitializer {
    public static final String MOD_ID = "ocetiertagger";
    private static final String UPDATE_URL_FORMAT = "https://api.modrinth.com/v2/project/yoB88RtH/version?game_versions=%s";

    public static final Gson GSON = new GsonBuilder().create();

    @Getter
    private static final ConfigManager<TierTaggerConfig> manager = ConfigManager.createDefault(TierTaggerConfig.class, MOD_ID);
    @Getter
    private static final Logger logger = LoggerFactory.getLogger(TierTagger.class);
    @Getter
    private static final HttpClient client = HttpClient.newHttpClient();

    // === version checker stuff ===
    @Getter
    private static Version latestVersion = null;
    private static final AtomicBoolean isObsolete = new AtomicBoolean(false);

    private static int tickCounter = 0;

    @Override
    public void onInitialize() {
        TierCache.clearCache();

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            TierCache.clearCache();
            logger.info("TierTagger cache cleared on game exit");
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(
                literal(MOD_ID)
                        .then(argument("player", PlayerArgumentType.player())
                                .executes(TierTagger::displayTierInfo))));

        KeyBinding keyBinding = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("tiertagger.keybind.gamemode", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "tiertagger.name")
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (keyBinding.wasPressed()) {
                cycleGamemode();
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tickCounter++;

            if (client.world != null && client.player != null && tickCounter % 200 == 0) {
                client.world.getPlayers().forEach(player -> {
                    String username = player.getNameForScoreboard();
                    if (username != null && !username.isEmpty() && isValidUsername(username)) {
                        TierCache.fetchPlayerByUsername(username);
                    }
                });
            }
        });

        checkForUpdates();
    }

    private static boolean isValidUsername(String username) {
        String usernamePattern = "^[a-zA-Z0-9_]{3,16}$"; 
        return username.matches(usernamePattern);
    }

    public static Text appendTier(PlayerEntity player, Text text) {
        if (!manager.getConfig().isEnabled()) {
            return text;
        }

        String gameMode = manager.getConfig().getGameMode();
        String username = player.getName().getString();

        String tier = TierCache.getCachedTier(username, gameMode);
        if (tier != null) {
            int color = getTierColor(tier);
            MutableText tierText = Text.literal(tier)
                .styled(style -> style.withColor(color))
                .append(Text.literal(" | "));
            tierText.append(text);
            return tierText;
        }

        TierCache.getPlayerTier(username, gameMode).thenAccept(fetchedTier -> {
            if (fetchedTier != null) {
                MinecraftClient.getInstance().execute(() -> {
                    player.setCustomNameVisible(!player.isCustomNameVisible());
                    player.setCustomNameVisible(!player.isCustomNameVisible());
                });
            }
        });

        return text;
    }

    public static int getTierColor(String tier) {
        if (tier == null) return 0xD3D3D3;

        return manager.getConfig().getTierColors().getOrDefault(tier, 0xD3D3D3);
    }

    private static int displayTierInfo(CommandContext<FabricClientCommandSource> ctx) {
        PlayerArgumentType.PlayerSelector selector = ctx.getArgument("player", PlayerArgumentType.PlayerSelector.class);

        Optional<PlayerInfo> playerInfo = ctx.getSource().getWorld().getPlayers().stream()
                .filter(p -> p.getNameForScoreboard().equalsIgnoreCase(selector.name()) || p.getUuidAsString().equalsIgnoreCase(selector.name()))
                .findFirst()
                .map(Entity::getUuid)
                .map(uuid -> {
                    try {
                        MinecraftClient client = MinecraftClient.getInstance();
                        PlayerEntity player = client.world.getPlayerByUuid(uuid);
                        String username = player.getName().getString();

                        return TierCache.getPlayerInfo(username).get(200, TimeUnit.MILLISECONDS); // Pass the username
                    } catch (Exception e) {
                        return null;
                    }
                });

        if (playerInfo.isPresent()) {
            ctx.getSource().sendFeedback(printPlayerInfo(playerInfo.get()));
        } else {
            ctx.getSource().sendFeedback(Text.of("[TierTagger] Searching..."));
            TierCache.searchPlayer(selector.name())
                    .thenAccept(p -> ctx.getSource().sendFeedback(printPlayerInfo(p)))
                    .exceptionally(t -> {
                        ctx.getSource().sendError(Text.of("Could not find player " + selector.name()));
                        return null;
                    });
        }

        return 0;
    }

    private static Text printPlayerInfo(PlayerInfo info) {
        MutableText text = Text.empty().append("=== Rankings for " + info.getName() + " ===");

        info.getRankings().forEach((m, r) -> {
            if (m == null) return;
            GameMode mode = TierCache.findMode(m);
            String tier = getTierText(r);

            Text tierText = Text.literal(tier).styled(s -> s.withColor(getTierColor(tier)));
            text.append(Text.literal("\n").append(mode.getTitle()).append(": ").append(tierText));
        });

        return text;
    }

    @NotNull
    public static String getTierText(PlayerInfo.Ranking ranking) {
        if (manager.getConfig().isShowRetired() && ranking.isRetired() && 
            ranking.getPeakTier() != null && ranking.getPeakPos() > -1) {
            return "R" + (ranking.getPeakPos() == 0 ? "H" : "L") + "T" + ranking.getPeakTier();
        } else {
            return (ranking.getPos() == 0 ? "H" : "L") + "T" + ranking.getTier();
        }
    }

    private static void checkForUpdates() {
        String versionParam = "[\"%s\"]".formatted(SharedConstants.getGameVersion().getName());
        String fullUrl = UPDATE_URL_FORMAT.formatted(URLEncoder.encode(versionParam, StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(URI.create(fullUrl)).GET().build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(r -> {
                    String body = r.body();
                    JsonArray array = GSON.fromJson(body, JsonArray.class);

                    if (!array.isEmpty()) {
                        JsonObject root = array.get(0).getAsJsonObject();

                        String versionName = root.get("name").getAsString();
                        if (versionName != null && versionName.toLowerCase(Locale.ROOT).startsWith("[o")) {
                            isObsolete.set(true);
                        }

                        String latestVer = root.get("version_number").getAsString();
                        try {
                            return Version.parse(latestVer);
                        } catch (VersionParsingException e) {
                            logger.warn("Could not parse version number {}", latestVer);
                        }
                    }

                    return null;
                })
                .exceptionally(t -> {
                    logger.warn("Error checking for updates", t);
                    return null;
                }).thenAccept(v -> {
                    logger.info("Found latest version {}", v.getFriendlyString());
                    latestVersion = v;
                });
    }

    public static boolean isObsolete() {
        return isObsolete.get();
    }

    private void cycleGamemode() {
        String nextMode = TierCache.findNextMode(manager.getConfig().getGameMode());
        manager.getConfig().setGameMode(nextMode);
        manager.saveConfig();

        TierCache.clearCache();

        String displayName = TierCache.getGameModeDisplay(nextMode);
        Ukutils.sendToast(
                Text.literal("Game mode switched!"),
                Text.literal("Now showing ").append(Text.literal(displayName).formatted(Formatting.GOLD)).append(" tiers.")
        );
    }
}