package com.megacrit.cardcrawl.localization;

public class CreditStrings {
   public String HEADER;
   public String[] NAMES;

   public static CreditStrings getMockCreditString() {
      CreditStrings retVal = new CreditStrings();
      retVal.HEADER = "[MISSING_HEADER]";
      retVal.NAMES = LocalizedStrings.createMockStringArray(8);
      return null;
   }
}
