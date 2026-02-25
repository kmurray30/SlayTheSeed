package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.powers.BufferPower;
import com.megacrit.cardcrawl.rooms.AbstractRoom;

public class FossilizedHelix extends AbstractRelic {
   public static final String ID = "FossilizedHelix";

   public FossilizedHelix() {
      super("FossilizedHelix", "helix.png", AbstractRelic.RelicTier.RARE, AbstractRelic.LandingSound.HEAVY);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public void atBattleStart() {
      this.flash();
      this.addToBot(new RelicAboveCreatureAction(AbstractDungeon.player, this));
      this.addToBot(new ApplyPowerAction(AbstractDungeon.player, AbstractDungeon.player, new BufferPower(AbstractDungeon.player, 1), 1));
      this.grayscale = true;
   }

   @Override
   public void justEnteredRoom(AbstractRoom room) {
      this.grayscale = false;
   }

   @Override
   public AbstractRelic makeCopy() {
      return new FossilizedHelix();
   }
}
