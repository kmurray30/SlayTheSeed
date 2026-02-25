package com.megacrit.cardcrawl.cards.deprecated;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.GainStrengthPower;
import com.megacrit.cardcrawl.powers.StrengthPower;

public class DEPRECATEDPeace extends AbstractCard {
   public static final String ID = "Peace";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Peace");

   public DEPRECATEDPeace() {
      super(
         "Peace",
         cardStrings.NAME,
         null,
         1,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.SKILL,
         AbstractCard.CardColor.COLORLESS,
         AbstractCard.CardRarity.SPECIAL,
         AbstractCard.CardTarget.ALL_ENEMY
      );
      this.baseMagicNumber = 5;
      this.magicNumber = this.baseMagicNumber;
      this.selfRetain = true;
      this.exhaust = true;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      for (AbstractMonster mo : AbstractDungeon.getCurrRoom().monsters.monsters) {
         this.addToBot(new ApplyPowerAction(mo, p, new StrengthPower(mo, -this.magicNumber), -this.magicNumber, true, AbstractGameAction.AttackEffect.NONE));
      }

      for (AbstractMonster mo : AbstractDungeon.getCurrRoom().monsters.monsters) {
         if (!mo.hasPower("Artifact")) {
            this.addToBot(
               new ApplyPowerAction(mo, p, new GainStrengthPower(mo, this.magicNumber), this.magicNumber, true, AbstractGameAction.AttackEffect.NONE)
            );
         }
      }
   }

   @Override
   public AbstractCard makeCopy() {
      return new DEPRECATEDPeace();
   }

   @Override
   public void upgrade() {
      if (!this.upgraded) {
         this.upgradeName();
         this.upgradeMagicNumber(1);
      }
   }
}
