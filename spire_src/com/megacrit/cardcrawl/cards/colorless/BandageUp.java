package com.megacrit.cardcrawl.cards.colorless;

import com.megacrit.cardcrawl.actions.common.HealAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class BandageUp extends AbstractCard {
   public static final String ID = "Bandage Up";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Bandage Up");

   public BandageUp() {
      super(
         "Bandage Up",
         cardStrings.NAME,
         "colorless/skill/bandage_up",
         0,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.SKILL,
         AbstractCard.CardColor.COLORLESS,
         AbstractCard.CardRarity.UNCOMMON,
         AbstractCard.CardTarget.SELF
      );
      this.baseMagicNumber = 4;
      this.magicNumber = this.baseMagicNumber;
      this.exhaust = true;
      this.tags.add(AbstractCard.CardTags.HEALING);
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      this.addToBot(new HealAction(p, p, this.magicNumber));
   }

   @Override
   public void upgrade() {
      if (!this.upgraded) {
         this.upgradeName();
         this.upgradeMagicNumber(2);
      }
   }

   @Override
   public AbstractCard makeCopy() {
      return new BandageUp();
   }
}
