package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAllEnemiesAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class MercuryHourglass extends AbstractRelic {
   public static final String ID = "Mercury Hourglass";
   private static final int DMG = 3;

   public MercuryHourglass() {
      super("Mercury Hourglass", "hourglass.png", AbstractRelic.RelicTier.UNCOMMON, AbstractRelic.LandingSound.CLINK);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0] + 3 + this.DESCRIPTIONS[1];
   }

   @Override
   public void atTurnStart() {
      this.flash();
      this.addToBot(new RelicAboveCreatureAction(AbstractDungeon.player, this));
      this.addToBot(
         new DamageAllEnemiesAction(null, DamageInfo.createDamageMatrix(3, true), DamageInfo.DamageType.THORNS, AbstractGameAction.AttackEffect.BLUNT_LIGHT)
      );
   }

   @Override
   public AbstractRelic makeCopy() {
      return new MercuryHourglass();
   }
}
