package com.kevin.tiertagger;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class Ukutils {
    /**
     * Get the username for a UUID
     */
    public static CompletableFuture<String> getPlayerUsername(UUID uuid) {
        String username = null;
        if (MinecraftClient.getInstance().getNetworkHandler() != null) {
            var player = MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(uuid);
            if (player != null) {
                username = player.getProfile().getName();
            }
        }
        
        return CompletableFuture.completedFuture(username != null ? username : uuid.toString());
    }
    
    /**
     * Check if a texture exists
     */
    public static boolean textureExists(Identifier id) {
        return MinecraftClient.getInstance().getTextureManager().getTexture(id) != null;
    }
    
    /**
     * Send a toast notification
     */
    public static void sendToast(Text title, Text description) {
        MinecraftClient.getInstance().getToastManager().add(
            new SystemToast(
                SystemToast.Type.NARRATOR_TOGGLE, 
                title,
                description
            )
        );
    }
}