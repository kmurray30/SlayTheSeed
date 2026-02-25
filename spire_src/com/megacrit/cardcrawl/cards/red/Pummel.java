package com.megacrit.cardcrawl.cards.red;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.common.PummelDamageAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Pummel extends AbstractCard {
   public static final String ID = "Pummel";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Pummel");

   public Pummel() {
      super(
         "Pummel",
         cardStrings.NAME,
         "red/attack/pummel",
         1,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.ATTACK,
         AbstractCard.CardColor.RED,
         AbstractCard.CardRarity.UNCOMMON,
         AbstractCard.CardTarget.ENEMY
      );
      this.baseDamage = 2;
      this.exhaust = true;
      this.baseMagicNumber = 4;
      this.magicNumber = this.baseMagicNumber;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      for (int i = 1; i < this.magicNumber; i++) {
         this.addToBot(new PummelDamageAction(m, new DamageInfo(p, this.damage, this.damageTypeForTurn)));
      }

      this.addToBot(new DamageAction(m, new DamageInfo(p, this.damage, this.damageTypeForTurn), AbstractGameAction.AttackEffect.BLUNT_HEAVY));
   }

   @Override
   public void upgrade() {
      if (!this.upgraded) {
         this.upgradeName();
         this.upgradeMagicNumber(1);
         this.rawDescription = cardStrings.UPGRADE_DESCRIPTION;
         this.initializeDescription();
      }
   }

   @Override
   public AbstractCard makeCopy() {
      return new Pummel();
   }
}
