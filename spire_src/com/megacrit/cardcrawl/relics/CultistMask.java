package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.animations.TalkAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.actions.utility.SFXAction;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class CultistMask extends AbstractRelic {
   public static final String ID = "CultistMask";

   public CultistMask() {
      super("CultistMask", "cultistMask.png", AbstractRelic.RelicTier.SPECIAL, AbstractRelic.LandingSound.CLINK);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public void atBattleStart() {
      this.flash();
      this.addToBot(new RelicAboveCreatureAction(AbstractDungeon.player, this));
      this.addToBot(new SFXAction("VO_CULTIST_1A"));
      this.addToBot(new TalkAction(true, this.DESCRIPTIONS[1], 1.0F, 2.0F));
   }

   @Override
   public AbstractRelic makeCopy() {
      return new CultistMask();
   }
}
