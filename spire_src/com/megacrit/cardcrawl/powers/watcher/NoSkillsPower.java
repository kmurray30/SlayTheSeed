package com.megacrit.cardcrawl.powers.watcher;

import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;

public class NoSkillsPower extends AbstractPower {
   public static final String POWER_ID = "NoSkills";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("NoSkills");

   public NoSkillsPower(AbstractCreature owner) {
      this.name = powerStrings.NAME;
      this.ID = "NoSkills";
      this.owner = owner;
      this.amount = 1;
      this.updateDescription();
      this.loadRegion("entangle");
      this.isTurnBased = true;
      this.type = AbstractPower.PowerType.DEBUFF;
   }

   @Override
   public void playApplyPowerSfx() {
      CardCrawlGame.sound.play("POWER_ENTANGLED", 0.05F);
   }

   @Override
   public void updateDescription() {
      this.description = powerStrings.DESCRIPTIONS[0];
   }

   @Override
   public boolean canPlayCard(AbstractCard card) {
      return card.type != AbstractCard.CardType.SKILL;
   }

   @Override
   public void atEndOfTurn(boolean isPlayer) {
      if (isPlayer) {
         this.addToBot(new RemoveSpecificPowerAction(this.owner, this.owner, "NoSkills"));
      }
   }
}
