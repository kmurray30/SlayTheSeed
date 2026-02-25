package com.megacrit.cardcrawl.trials;

import java.util.ArrayList;

public class AnyColorDraftTrial extends AbstractTrial {
   @Override
   public boolean keepsStarterCards() {
      return false;
   }

   @Override
   public ArrayList<String> dailyModIDs() {
      ArrayList<String> retVal = new ArrayList<>();
      retVal.add("Diverse");
      retVal.add("Draft");
      return retVal;
   }
}
