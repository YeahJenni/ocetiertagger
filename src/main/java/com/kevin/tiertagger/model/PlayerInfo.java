package com.kevin.tiertagger.model;

import com.kevin.tiertagger.TierCache;
import lombok.Value;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Value
public class PlayerInfo {
    String name;
    String id;
    String region;
    int points;
    int rank;
    Map<String, Ranking> rankings;

    public String getName() { return name; }
    public String getId() { return id; }
    public String getRegion() { return region; }
    public int getPoints() { return points; }
    public int getRank() { return rank; }
    public Map<String, Ranking> getRankings() { return rankings; }

    public String name() { return name; }
    public Map<String, Ranking> rankings() { return rankings; }

    public List<NamedRanking> getSortedTiers() {
        List<NamedRanking> tiers = new ArrayList<>();

        if (rankings != null) {
            tiers = rankings.entrySet().stream()
                    .map(e -> new NamedRanking(TierCache.findMode(e.getKey()), e.getValue()))
                    .sorted(Comparator.comparing(nr -> nr.getMode().title()))
                    .collect(Collectors.toList());
        }

        return tiers;
    }

    public int overall() { return rank; }
    public int getOverall() { return rank; }

    public int getRegionColor() {
        return 0x00AAFF;
    }

    public PointInfo getPointInfo() {
        return new PointInfo(points);
    }

    @Value
    public static class Ranking {
        String tier;
        long attained;
        int position;
        boolean retired;

        public String getTier() { return tier; }
        public long getAttained() { return attained; }
        public int getPosition() { return position; }
        public boolean isRetired() { return retired; }

        public int pos() { return position; }
        public int getPos() { return position; }

        public String tier() { return tier; }

        public String peakTier() { return tier; }
        public String getPeakTier() { return tier; }

        public int peakPos() { return position; }
        public int getPeakPos() { return position; }

        public boolean retired() { return retired; }

        public int comparableTier() { 
            return tier != null ? Integer.parseInt(tier) : 0;
        }

        public int getComparableTier() {
            return comparableTier();
        }

        public int comparablePeak() {
            return tier != null ? Integer.parseInt(tier) : 0;
        }

        public int getComparablePeak() {
            return comparablePeak();
        }
    }

    @Value
    public static class NamedRanking {
        GameMode mode;
        Ranking ranking;

        public GameMode getMode() { return mode; }
        public Ranking getRanking() { return ranking; }

        public GameMode mode() { return mode; }
        public Ranking ranking() { return ranking; }
    }

    @Value
    public static class PointInfo {
        int points;

        public int getColor() {
            if (points > 100) return 0xFF0000;
            if (points > 50) return 0xFF9900;
            return 0x00FF00;
        }

        public String getTitle() {
            if (points > 100) return "Expert";
            if (points > 50) return "Advanced";
            if (points > 20) return "Intermediate";
            return "Beginner";
        }

        public int getAccentColor() {
            if (points > 100) return 0xAA0000;
            if (points > 50) return 0xAA5500;
            return 0x005500;
        }
    }
}