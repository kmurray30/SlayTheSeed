package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.orbs.Dark;

public class SymbioticVirus extends AbstractRelic {
   public static final String ID = "Symbiotic Virus";

   public SymbioticVirus() {
      super("Symbiotic Virus", "virus.png", AbstractRelic.RelicTier.UNCOMMON, AbstractRelic.LandingSound.MAGICAL);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public void atPreBattle() {
      AbstractDungeon.player.channelOrb(new Dark());
   }

   @Override
   public AbstractRelic makeCopy() {
      return new SymbioticVirus();
   }
}
