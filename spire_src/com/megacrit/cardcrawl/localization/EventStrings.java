package com.megacrit.cardcrawl.localization;

public class EventStrings {
   public String NAME;
   public String[] DESCRIPTIONS;
   public String[] OPTIONS;

   public static EventStrings getMockEventString() {
      EventStrings retVal = new EventStrings();
      retVal.NAME = "[MISSING_NAME]";
      retVal.DESCRIPTIONS = LocalizedStrings.createMockStringArray(12);
      retVal.OPTIONS = LocalizedStrings.createMockStringArray(12);
      return retVal;
   }
}
