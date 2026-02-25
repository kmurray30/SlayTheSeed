package com.megacrit.cardcrawl.cards.red;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class PommelStrike extends AbstractCard {
   public static final String ID = "Pommel Strike";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Pommel Strike");

   public PommelStrike() {
      super(
         "Pommel Strike",
         cardStrings.NAME,
         "red/attack/pommel_strike",
         1,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.ATTACK,
         AbstractCard.CardColor.RED,
         AbstractCard.CardRarity.COMMON,
         AbstractCard.CardTarget.ENEMY
      );
      this.baseDamage = 9;
      this.baseMagicNumber = 1;
      this.magicNumber = this.baseMagicNumber;
      this.tags.add(AbstractCard.CardTags.STRIKE);
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      this.addToBot(new DamageAction(m, new DamageInfo(p, this.damage, this.damageTypeForTurn), AbstractGameAction.AttackEffect.SLASH_DIAGONAL));
      this.addToBot(new DrawCardAction(p, this.magicNumber));
   }

   @Override
   public void upgrade() {
      if (!this.upgraded) {
         this.upgradeName();
         this.upgradeDamage(1);
         this.upgradeMagicNumber(1);
         this.rawDescription = cardStrings.UPGRADE_DESCRIPTION;
         this.initializeDescription();
      }
   }

   @Override
   public AbstractCard makeCopy() {
      return new PommelStrike();
   }
}
