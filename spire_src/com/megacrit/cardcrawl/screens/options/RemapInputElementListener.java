package com.megacrit.cardcrawl.screens.options;

public interface RemapInputElementListener {
   void didStartRemapping(RemapInputElement var1);

   boolean willRemap(RemapInputElement var1, int var2, int var3);

   boolean willRemapController(RemapInputElement var1, int var2, int var3);
}
