package com.megacrit.cardcrawl.cards.deprecated;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class DEPRECATEDTruth extends AbstractCard {
   public static final String ID = "Truth";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Truth");

   public DEPRECATEDTruth() {
      super(
         "Truth",
         cardStrings.NAME,
         null,
         1,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.ATTACK,
         AbstractCard.CardColor.COLORLESS,
         AbstractCard.CardRarity.SPECIAL,
         AbstractCard.CardTarget.ENEMY
      );
      this.baseDamage = 6;
      this.baseMagicNumber = 1;
      this.magicNumber = this.baseMagicNumber;
      this.selfRetain = true;
      this.exhaust = true;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      this.addToBot(new DamageAction(m, new DamageInfo(p, this.damage, this.damageTypeForTurn), AbstractGameAction.AttackEffect.BLUNT_HEAVY));
      this.addToBot(new DrawCardAction(this.magicNumber));
   }

   @Override
   public void upgrade() {
      if (!this.upgraded) {
         this.upgradeName();
         this.upgradeDamage(2);
         this.upgradeMagicNumber(1);
         this.rawDescription = cardStrings.UPGRADE_DESCRIPTION;
         this.initializeDescription();
      }
   }

   @Override
   public AbstractCard makeCopy() {
      return new DEPRECATEDTruth();
   }
}
