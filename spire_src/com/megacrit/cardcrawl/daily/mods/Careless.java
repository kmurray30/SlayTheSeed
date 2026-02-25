package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.actions.common.MillAction;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class Careless extends AbstractDailyMod {
   public static final String ID = "Careless";
   private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("Careless");
   public static final String NAME;
   public static final String DESC = modStrings.DESCRIPTION;

   public Careless() {
      super("Careless", NAME, DESC, "slow_start.png", false);
   }

   public static void modAction() {
      AbstractDungeon.actionManager.addToBottom(new MillAction(AbstractDungeon.player, AbstractDungeon.player, 1));
   }

   static {
      NAME = modStrings.NAME;
   }
}
