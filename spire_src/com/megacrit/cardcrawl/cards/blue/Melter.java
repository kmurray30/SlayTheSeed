package com.megacrit.cardcrawl.cards.blue;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.common.RemoveAllBlockAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Melter extends AbstractCard {
   public static final String ID = "Melter";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Melter");

   public Melter() {
      super(
         "Melter",
         cardStrings.NAME,
         "blue/attack/melter",
         1,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.ATTACK,
         AbstractCard.CardColor.BLUE,
         AbstractCard.CardRarity.UNCOMMON,
         AbstractCard.CardTarget.ENEMY
      );
      this.baseDamage = 10;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      this.addToBot(new RemoveAllBlockAction(m, p));
      this.addToBot(new DamageAction(m, new DamageInfo(p, this.damage, this.damageTypeForTurn), AbstractGameAction.AttackEffect.FIRE));
   }

   @Override
   public void upgrade() {
      if (!this.upgraded) {
         this.upgradeName();
         this.upgradeDamage(4);
      }
   }

   @Override
   public AbstractCard makeCopy() {
      return new Melter();
   }
}
