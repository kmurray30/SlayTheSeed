package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class PreservedInsect extends AbstractRelic {
   public static final String ID = "PreservedInsect";
   private float MODIFIER_AMT = 0.25F;

   public PreservedInsect() {
      super("PreservedInsect", "insect.png", AbstractRelic.RelicTier.COMMON, AbstractRelic.LandingSound.FLAT);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0] + 25 + this.DESCRIPTIONS[1];
   }

   @Override
   public void atBattleStart() {
      if (AbstractDungeon.getCurrRoom().eliteTrigger) {
         this.flash();

         for (AbstractMonster m : AbstractDungeon.getCurrRoom().monsters.monsters) {
            if (m.currentHealth > (int)(m.maxHealth * (1.0F - this.MODIFIER_AMT))) {
               m.currentHealth = (int)(m.maxHealth * (1.0F - this.MODIFIER_AMT));
               m.healthBarUpdatedEvent();
            }
         }

         this.addToTop(new RelicAboveCreatureAction(AbstractDungeon.player, this));
      }
   }

   @Override
   public boolean canSpawn() {
      return Settings.isEndless || AbstractDungeon.floorNum <= 52;
   }

   @Override
   public AbstractRelic makeCopy() {
      return new PreservedInsect();
   }
}
