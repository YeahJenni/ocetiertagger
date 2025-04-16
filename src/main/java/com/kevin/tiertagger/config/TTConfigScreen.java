package com.kevin.tiertagger.config;

import com.kevin.tiertagger.TierCache;
import com.kevin.tiertagger.TierTagger;
import com.kevin.tiertagger.tierlist.PlayerSearchScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tab.Tab;
import net.minecraft.text.Text;
import net.uku3lig.ukulib.config.option.*;
import net.uku3lig.ukulib.config.option.widget.ButtonTab;
import net.uku3lig.ukulib.config.screen.TabbedConfigScreen;

import java.util.*;
import java.util.stream.Collectors;

public class TTConfigScreen extends TabbedConfigScreen<TierTaggerConfig> {
    public TTConfigScreen(Screen parent) {
        super("TierTagger Config", parent, TierTagger.getManager());
    }

    @Override
    protected Tab[] getTabs(TierTaggerConfig config) {
        return new Tab[]{new MainSettingsTab(), new ColorsTab()};
    }

    public class MainSettingsTab extends ButtonTab<TierTaggerConfig> {
        public MainSettingsTab() {
            super("tiertagger.config", TTConfigScreen.this.manager);
        }

        @Override
        protected WidgetCreator[] getWidgets(TierTaggerConfig config) {
            return new WidgetCreator[]{
                    CyclingOption.ofBoolean("tiertagger.config.enabled", config.isEnabled(), config::setEnabled),
                    new CyclingOption<>("tiertagger.config.gamemode", TierCache.GAME_MODES, config.getGameMode(),
                            config::setGameMode, mode -> Text.literal(TierCache.getGameModeDisplay(mode))),
                    CyclingOption.ofBoolean("tiertagger.config.retired", config.isShowRetired(), config::setShowRetired),
                    new CyclingOption<>("tiertagger.config.statistic", 
                            Arrays.asList(TierTaggerConfig.Statistic.values()),
                            config.getShownStatistic(), 
                            config::setShownStatistic,
                            stat -> Text.literal(stat.name())),
                    new SimpleButton("tiertagger.clear", b -> TierCache.clearCache()),
                    new ScreenOpenButton("tiertagger.config.search", PlayerSearchScreen::new)
            };
        }
    }

    public class ColorsTab extends ButtonTab<TierTaggerConfig> {
        protected ColorsTab() {
            super("tiertagger.colors", TTConfigScreen.this.manager);
        }

        @Override
        protected WidgetCreator[] getWidgets(TierTaggerConfig config) {
            // i genuinely don't understand but chaining the calls just EXPLODES????
            Comparator<Map.Entry<String, Integer>> comparator = Comparator.comparing(e -> e.getKey().charAt(2));
            comparator = comparator.thenComparing(e -> e.getKey().charAt(0));

            List<ColorOption> tiers = config.getTierColors().entrySet().stream()
                    .sorted(comparator)
                    .map(e -> new ColorOption(e.getKey(), e.getValue(), val -> config.getTierColors().put(e.getKey(), val)))
                    .collect(Collectors.toList());

            tiers.add(new ColorOption("tiertagger.colors.retired", config.getRetiredColor(), config::setRetiredColor));

            return tiers.toArray(WidgetCreator[]::new);
        }
    }
}
