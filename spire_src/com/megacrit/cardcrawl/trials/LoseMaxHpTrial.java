package com.megacrit.cardcrawl.trials;

import java.util.ArrayList;

public class LoseMaxHpTrial extends AbstractTrial {
   @Override
   public ArrayList<String> dailyModIDs() {
      ArrayList<String> retVal = new ArrayList<>();
      retVal.add("Night Terrors");
      retVal.add("Terminal");
      return retVal;
   }
}
