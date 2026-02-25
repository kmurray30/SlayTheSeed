package com.megacrit.cardcrawl.trials;

import java.util.ArrayList;

public class SlowpokeTrial extends AbstractTrial {
   @Override
   public ArrayList<String> dailyModIDs() {
      ArrayList<String> retVal = new ArrayList<>();
      retVal.add("Time Dilation");
      return retVal;
   }
}
