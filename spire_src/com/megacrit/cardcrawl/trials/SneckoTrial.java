package com.megacrit.cardcrawl.trials;

import java.util.ArrayList;

public class SneckoTrial extends AbstractTrial {
   @Override
   public boolean keepStarterRelic() {
      return false;
   }

   @Override
   public ArrayList<String> dailyModIDs() {
      ArrayList<String> retVal = new ArrayList<>();
      retVal.add("Snecko Eye");
      return retVal;
   }
}
