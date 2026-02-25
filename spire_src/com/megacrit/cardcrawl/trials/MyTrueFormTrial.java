package com.megacrit.cardcrawl.trials;

import java.util.ArrayList;

public class MyTrueFormTrial extends AbstractTrial {
   @Override
   public ArrayList<String> dailyModIDs() {
      ArrayList<String> retVal = new ArrayList<>();
      retVal.add("Demon Form");
      retVal.add("Wraith Form v2");
      retVal.add("Echo Form");
      retVal.add("DevaForm");
      return retVal;
   }
}
