package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.actions.unique.CodexAction;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class NilrysCodex extends AbstractRelic {
   public static final String ID = "Nilry's Codex";

   public NilrysCodex() {
      super("Nilry's Codex", "codex.png", AbstractRelic.RelicTier.SPECIAL, AbstractRelic.LandingSound.MAGICAL);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public void onPlayerEndTurn() {
      this.addToBot(new RelicAboveCreatureAction(AbstractDungeon.player, this));
      this.addToBot(new CodexAction());
   }

   @Override
   public AbstractRelic makeCopy() {
      return new NilrysCodex();
   }
}
