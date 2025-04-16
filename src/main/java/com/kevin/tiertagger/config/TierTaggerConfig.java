package com.kevin.tiertagger.config;

import com.kevin.tiertagger.TierCache;
import com.google.gson.internal.LinkedTreeMap;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
public class TierTaggerConfig implements Serializable, IConfig {
    private boolean enabled = true;
    private String gameMode = "crystal"; 
    private boolean showRetired = true;
    private Statistic shownStatistic = Statistic.TIER;
    private int retiredColor = 0xa2d6ff;
    private LinkedTreeMap<String, Integer> tierColors = defaultColors();
    
    public String getGameMode() {
        if (!TierCache.GAME_MODES.contains(this.gameMode)) {
            this.gameMode = "crystal"; 
        }
        return this.gameMode;
    }
    
    private static LinkedTreeMap<String, Integer> defaultColors() {
        LinkedTreeMap<String, Integer> colors = new LinkedTreeMap<>();
        colors.put("LT1", 0x0000FF); 
        colors.put("LT2", 0x0066FF); 
        colors.put("LT3", 0x00AAFF); 
        colors.put("LT4", 0x00FFFF); 
        colors.put("LT5", 0x00FFAA);

        colors.put("HT1", 0xFF0000); 
        colors.put("HT2", 0xFF6600); 
        colors.put("HT3", 0xFFFF00); 
        colors.put("HT4", 0xAAFF00); 
        colors.put("HT5", 0x00FF00); 

        return colors;
    }
    
    public enum Statistic  {
        TIER,    
        POINTS,
        RANK
    }
}
