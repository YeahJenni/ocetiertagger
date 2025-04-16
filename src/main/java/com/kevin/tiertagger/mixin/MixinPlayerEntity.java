package com.kevin.tiertagger.mixin;

import com.kevin.tiertagger.TierTagger;
import com.kevin.tiertagger.TierCache;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class MixinPlayerEntity {

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void onGetDisplayName(CallbackInfoReturnable<Text> cir) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        MinecraftClient client = MinecraftClient.getInstance();

        if (player.isInvisible()) return;

        boolean isLocalPlayer = player.equals(client.player);

        if (isLocalPlayer) {
            String gameMode = TierTagger.getManager().getConfig().getGameMode();
            String username = player.getName().getString();
            TierCache.getPlayerTier(username, gameMode);
        }

        cir.setReturnValue(TierTagger.appendTier(player, cir.getReturnValue()));
    }
}
