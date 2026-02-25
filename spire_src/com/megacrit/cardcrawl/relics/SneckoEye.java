package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.powers.ConfusionPower;

public class SneckoEye extends AbstractRelic {
   public static final String ID = "Snecko Eye";
   public static final int HAND_MODIFICATION = 2;

   public SneckoEye() {
      super("Snecko Eye", "sneckoEye.png", AbstractRelic.RelicTier.BOSS, AbstractRelic.LandingSound.FLAT);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public void onEquip() {
      AbstractDungeon.player.masterHandSize += 2;
   }

   @Override
   public void onUnequip() {
      AbstractDungeon.player.masterHandSize -= 2;
   }

   @Override
   public void atPreBattle() {
      this.flash();
      this.addToBot(new ApplyPowerAction(AbstractDungeon.player, AbstractDungeon.player, new ConfusionPower(AbstractDungeon.player)));
   }

   @Override
   public AbstractRelic makeCopy() {
      return new SneckoEye();
   }
}
