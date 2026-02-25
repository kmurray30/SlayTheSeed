package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class SlaversCollar extends AbstractRelic {
   public static final String ID = "SlaversCollar";

   public SlaversCollar() {
      super("SlaversCollar", "collar.png", AbstractRelic.RelicTier.BOSS, AbstractRelic.LandingSound.FLAT);
   }

   @Override
   public String getUpdatedDescription() {
      return AbstractDungeon.player != null ? this.setDescription(AbstractDungeon.player.chosenClass) : this.setDescription(null);
   }

   private String setDescription(AbstractPlayer.PlayerClass c) {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public void updateDescription(AbstractPlayer.PlayerClass c) {
      this.description = this.setDescription(c);
      this.tips.clear();
      this.tips.add(new PowerTip(this.name, this.description));
      this.initializeTips();
   }

   public void beforeEnergyPrep() {
      boolean isEliteOrBoss = AbstractDungeon.getCurrRoom().eliteTrigger;

      for (AbstractMonster m : AbstractDungeon.getMonsters().monsters) {
         if (m.type == AbstractMonster.EnemyType.BOSS) {
            isEliteOrBoss = true;
         }
      }

      if (isEliteOrBoss) {
         this.beginLongPulse();
         this.flash();
         AbstractDungeon.player.energy.energyMaster++;
      }
   }

   @Override
   public void onVictory() {
      if (this.pulse) {
         AbstractDungeon.player.energy.energyMaster--;
         this.stopPulse();
      }
   }

   @Override
   public AbstractRelic makeCopy() {
      return new SlaversCollar();
   }
}
