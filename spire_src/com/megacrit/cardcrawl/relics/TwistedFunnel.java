package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.PoisonPower;

public class TwistedFunnel extends AbstractRelic {
   public static final String ID = "TwistedFunnel";
   private static final int POISON_AMT = 4;

   public TwistedFunnel() {
      super("TwistedFunnel", "funnel.png", AbstractRelic.RelicTier.SHOP, AbstractRelic.LandingSound.HEAVY);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public void atBattleStart() {
      this.flash();

      for (AbstractMonster m : AbstractDungeon.getMonsters().monsters) {
         if (!m.isDead && !m.isDying) {
            this.addToBot(new ApplyPowerAction(m, AbstractDungeon.player, new PoisonPower(m, AbstractDungeon.player, 4), 4));
         }
      }
   }

   @Override
   public AbstractRelic makeCopy() {
      return new TwistedFunnel();
   }
}
