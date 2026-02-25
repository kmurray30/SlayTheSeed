package com.megacrit.cardcrawl.integrations;

import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.screens.leaderboards.FilterButton;

public interface PublisherIntegration {
   boolean isInitialized();

   void dispose();

   void deleteAllCloudFiles();

   boolean setStat(String var1, int var2);

   int getStat(String var1);

   boolean incrementStat(String var1, int var2);

   long getGlobalStat(String var1);

   void uploadDailyLeaderboardScore(String var1, int var2);

   void uploadLeaderboardScore(String var1, int var2);

   void unlockAchievement(String var1);

   void getLeaderboardEntries(AbstractPlayer.PlayerClass var1, FilterButton.RegionSetting var2, FilterButton.LeaderboardType var3, int var4, int var5);

   void getDailyLeaderboard(long var1, int var3, int var4);

   void setRichPresenceDisplayPlaying(int var1, String var2);

   void setRichPresenceDisplayPlaying(int var1, int var2, String var3);

   void setRichPresenceDisplayInMenu();

   int getNumUnlockedAchievements();

   DistributorFactory.Distributor getType();
}
