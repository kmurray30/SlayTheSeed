package com.megacrit.cardcrawl.trials;

import java.util.ArrayList;

public class CursedTrial extends AbstractTrial {
   @Override
   public ArrayList<String> dailyModIDs() {
      ArrayList<String> retVal = new ArrayList<>();
      retVal.add("Cursed Run");
      return retVal;
   }
}
