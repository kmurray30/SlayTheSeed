package com.megacrit.cardcrawl.localization;

public class ScoreBonusStrings {
   public String NAME;
   public String[] DESCRIPTIONS;

   public static ScoreBonusStrings getScoreBonusString() {
      ScoreBonusStrings retVal = new ScoreBonusStrings();
      retVal.NAME = "[MISSING_NAME]";
      retVal.DESCRIPTIONS = LocalizedStrings.createMockStringArray(1);
      return retVal;
   }
}
