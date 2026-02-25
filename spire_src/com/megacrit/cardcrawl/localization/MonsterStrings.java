package com.megacrit.cardcrawl.localization;

public class MonsterStrings {
   public String NAME;
   public String[] DIALOG;
   public String[] MOVES;

   public static MonsterStrings getMockMonsterString() {
      MonsterStrings retVal = new MonsterStrings();
      retVal.NAME = "[MISSING_NAME]";
      retVal.DIALOG = LocalizedStrings.createMockStringArray(5);
      retVal.MOVES = LocalizedStrings.createMockStringArray(5);
      return retVal;
   }
}
