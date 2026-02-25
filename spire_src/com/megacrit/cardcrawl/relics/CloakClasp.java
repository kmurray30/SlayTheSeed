package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class CloakClasp extends AbstractRelic {
   public static final String ID = "CloakClasp";

   public CloakClasp() {
      super("CloakClasp", "clasp.png", AbstractRelic.RelicTier.RARE, AbstractRelic.LandingSound.CLINK);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0] + 1 + this.DESCRIPTIONS[1];
   }

   @Override
   public void onPlayerEndTurn() {
      if (!AbstractDungeon.player.hand.group.isEmpty()) {
         this.flash();
         this.addToBot(new GainBlockAction(AbstractDungeon.player, null, AbstractDungeon.player.hand.group.size() * 1));
      }
   }

   @Override
   public AbstractRelic makeCopy() {
      return new CloakClasp();
   }
}
