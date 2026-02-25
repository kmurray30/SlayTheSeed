package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.actions.common.MakeTempCardAtBottomOfDeckAction;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class ControlledChaos extends AbstractDailyMod {
   public static final String ID = "ControlledChaos";
   private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("ControlledChaos");
   public static final String NAME;
   public static final String DESC = modStrings.DESCRIPTION;

   public ControlledChaos() {
      super("ControlledChaos", NAME, DESC, "controlled_chaos.png", true);
   }

   public static void modAction() {
      AbstractDungeon.actionManager.addToBottom(new MakeTempCardAtBottomOfDeckAction(10));
   }

   static {
      NAME = modStrings.NAME;
   }
}
