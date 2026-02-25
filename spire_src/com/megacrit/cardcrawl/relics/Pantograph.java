package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.common.HealAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Pantograph extends AbstractRelic {
   public static final String ID = "Pantograph";
   private static final int HEAL_AMT = 25;

   public Pantograph() {
      super("Pantograph", "pantograph.png", AbstractRelic.RelicTier.UNCOMMON, AbstractRelic.LandingSound.CLINK);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0] + 25 + this.DESCRIPTIONS[1];
   }

   @Override
   public void atBattleStart() {
      for (AbstractMonster m : AbstractDungeon.getMonsters().monsters) {
         if (m.type == AbstractMonster.EnemyType.BOSS) {
            this.flash();
            this.addToTop(new HealAction(AbstractDungeon.player, AbstractDungeon.player, 25, 0.0F));
            this.addToTop(new RelicAboveCreatureAction(AbstractDungeon.player, this));
            return;
         }
      }
   }

   @Override
   public AbstractRelic makeCopy() {
      return new Pantograph();
   }
}
