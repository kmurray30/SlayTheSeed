package com.megacrit.cardcrawl.trials;

import java.util.ArrayList;

public class HoarderTrial extends AbstractTrial {
   @Override
   public ArrayList<String> dailyModIDs() {
      ArrayList<String> retVal = new ArrayList<>();
      retVal.add("Hoarder");
      return retVal;
   }
}
