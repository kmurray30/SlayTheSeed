package com.megacrit.cardcrawl.relics;

import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class Orichalcum extends AbstractRelic {
   public static final String ID = "Orichalcum";
   private static final int BLOCK_AMT = 6;
   public boolean trigger = false;

   public Orichalcum() {
      super("Orichalcum", "orichalcum.png", AbstractRelic.RelicTier.COMMON, AbstractRelic.LandingSound.HEAVY);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0] + 6 + this.DESCRIPTIONS[1];
   }

   @Override
   public void onPlayerEndTurn() {
      if (AbstractDungeon.player.currentBlock == 0 || this.trigger) {
         this.trigger = false;
         this.flash();
         this.stopPulse();
         this.addToTop(new GainBlockAction(AbstractDungeon.player, AbstractDungeon.player, 6));
         this.addToTop(new RelicAboveCreatureAction(AbstractDungeon.player, this));
      }
   }

   @Override
   public void atTurnStart() {
      this.trigger = false;
      if (AbstractDungeon.player.currentBlock == 0) {
         this.beginLongPulse();
      }
   }

   @Override
   public int onPlayerGainedBlock(float blockAmount) {
      if (blockAmount > 0.0F) {
         this.stopPulse();
      }

      return MathUtils.floor(blockAmount);
   }

   @Override
   public void onVictory() {
      this.stopPulse();
   }

   @Override
   public AbstractRelic makeCopy() {
      return new Orichalcum();
   }
}
