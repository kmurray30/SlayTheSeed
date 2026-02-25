package com.megacrit.cardcrawl.localization;

public class OrbStrings {
   public String NAME;
   public String[] DESCRIPTION;

   public static OrbStrings getMockOrbString() {
      OrbStrings retVal = new OrbStrings();
      retVal.NAME = "[MISSING_NAME]";
      retVal.DESCRIPTION = LocalizedStrings.createMockStringArray(5);
      return retVal;
   }
}
