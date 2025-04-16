package com.kevin.tiertagger.mixin;

import com.kevin.tiertagger.TierTagger;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(TitleScreen.class)
public class MixinTitleScreen extends Screen {
    @Unique
    private static final AtomicBoolean hasCheckedVersion = new AtomicBoolean(false);

    protected MixinTitleScreen(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    public void showUpdateScreen(CallbackInfo ci) {
        if (hasCheckedVersion.get()) return;

        Version currentVersion = FabricLoader.getInstance().getModContainer("tier-tagger").map(m -> m.getMetadata().getVersion()).orElse(null);
        Version latestVersion = TierTagger.getLatestVersion();

        if (TierTagger.isObsolete()) {
            MinecraftClient.getInstance().setScreen(new ConfirmScreen(
                    b -> {
                        if (b) {
                            MinecraftClient.getInstance().scheduleStop();
                        } else {
                            MinecraftClient.getInstance().setScreen(this);
                        }
                    },
                    Text.translatable("tiertagger.obsolete.title"),
                    Text.translatable("tiertagger.obsolete.desc"),
                    Text.translatable("menu.quit"),
                    Text.translatable("tiertagger.outdated.ignore")
            ));
        } else if (currentVersion != null && latestVersion != null && currentVersion.compareTo(latestVersion) < 0) {
            Text newVersion = Text.literal(latestVersion.getFriendlyString()).formatted(Formatting.GREEN);

            MinecraftClient.getInstance().setScreen(new ConfirmScreen(
                    b -> {
                        if (b) {
                            String url = "https://modrinth.com/mod/ocetiertagger/version/" + latestVersion.getFriendlyString();
                            Util.getOperatingSystem().open(url);
                        }

                        MinecraftClient.getInstance().setScreen(this);
                    },
                    Text.translatable("tiertagger.outdated.title"),
                    Text.translatable("tiertagger.outdated.desc", newVersion),
                    Text.translatable("tiertagger.outdated.download"),
                    Text.translatable("tiertagger.outdated.ignore")
            ));
        }

        hasCheckedVersion.set(true);
    }
}
