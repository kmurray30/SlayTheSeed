package com.megacrit.cardcrawl.trials;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StarterDeckTrial extends AbstractTrial {
   @Override
   public List<String> extraStartingRelicIDs() {
      return Collections.singletonList("Busted Crown");
   }

   @Override
   public ArrayList<String> dailyModIDs() {
      ArrayList<String> retVal = new ArrayList<>();
      retVal.add("Binary");
      return retVal;
   }
}
