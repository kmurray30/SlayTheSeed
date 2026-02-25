package com.megacrit.cardcrawl.localization;

public class TutorialStrings {
   public String[] TEXT;
   public String[] LABEL;

   public static TutorialStrings getMockTutorialString() {
      TutorialStrings retVal = new TutorialStrings();
      retVal.TEXT = LocalizedStrings.createMockStringArray(25);
      retVal.LABEL = LocalizedStrings.createMockStringArray(8);
      return retVal;
   }
}
