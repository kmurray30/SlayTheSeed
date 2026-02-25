package com.megacrit.cardcrawl.localization;

public class PotionStrings {
   public String NAME;
   public String[] DESCRIPTIONS;

   public static PotionStrings getMockPotionString() {
      PotionStrings retVal = new PotionStrings();
      retVal.NAME = "[MISSING_NAME]";
      retVal.DESCRIPTIONS = LocalizedStrings.createMockStringArray(3);
      return retVal;
   }
}
