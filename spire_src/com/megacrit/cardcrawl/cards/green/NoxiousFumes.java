package com.megacrit.cardcrawl.cards.green;

import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.NoxiousFumesPower;

public class NoxiousFumes extends AbstractCard {
   public static final String ID = "Noxious Fumes";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Noxious Fumes");

   public NoxiousFumes() {
      super(
         "Noxious Fumes",
         cardStrings.NAME,
         "green/power/noxious_fumes",
         1,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.POWER,
         AbstractCard.CardColor.GREEN,
         AbstractCard.CardRarity.UNCOMMON,
         AbstractCard.CardTarget.SELF
      );
      this.baseMagicNumber = 2;
      this.magicNumber = this.baseMagicNumber;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      this.addToBot(new ApplyPowerAction(p, p, new NoxiousFumesPower(p, this.magicNumber), this.magicNumber));
   }

   @Override
   public void upgrade() {
      if (!this.upgraded) {
         this.upgradeName();
         this.upgradeMagicNumber(1);
      }
   }

   @Override
   public AbstractCard makeCopy() {
      return new NoxiousFumes();
   }
}
