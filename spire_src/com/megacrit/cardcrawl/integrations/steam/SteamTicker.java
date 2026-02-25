package com.megacrit.cardcrawl.integrations.steam;

import com.codedisaster.steamworks.SteamAPI;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SteamTicker implements Runnable {
   private static final Logger logger = LogManager.getLogger(SteamTicker.class.getName());

   @Override
   public void run() {
      logger.info("Steam Ticker initialized.");

      for (; SteamAPI.isSteamRunning(); SteamAPI.runCallbacks()) {
         try {
            Thread.sleep(66L);
         } catch (InterruptedException var2) {
            var2.printStackTrace();
         }
      }

      logger.info("[ERROR] SteamAPI stopped running.");
   }
}
