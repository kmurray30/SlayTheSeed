package com.megacrit.cardcrawl.cards.red;

import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.StrengthPower;
import com.megacrit.cardcrawl.vfx.combat.InflameEffect;

public class Inflame extends AbstractCard {
   public static final String ID = "Inflame";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Inflame");

   public Inflame() {
      super(
         "Inflame",
         cardStrings.NAME,
         "red/power/inflame",
         1,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.POWER,
         AbstractCard.CardColor.RED,
         AbstractCard.CardRarity.UNCOMMON,
         AbstractCard.CardTarget.SELF
      );
      this.baseMagicNumber = 2;
      this.magicNumber = this.baseMagicNumber;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      this.addToBot(new VFXAction(p, new InflameEffect(p), 1.0F));
      this.addToBot(new ApplyPowerAction(p, p, new StrengthPower(p, this.magicNumber), this.magicNumber));
   }

   @Override
   public void upgrade() {
      if (!this.upgraded) {
         this.upgradeName();
         this.upgradeMagicNumber(1);
         this.initializeDescription();
      }
   }

   @Override
   public AbstractCard makeCopy() {
      return new Inflame();
   }
}
