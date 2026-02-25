package com.megacrit.cardcrawl.cards.red;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.common.LoseHPAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.vfx.combat.HemokinesisEffect;

public class Hemokinesis extends AbstractCard {
   public static final String ID = "Hemokinesis";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Hemokinesis");

   public Hemokinesis() {
      super(
         "Hemokinesis",
         cardStrings.NAME,
         "red/attack/hemokinesis",
         1,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.ATTACK,
         AbstractCard.CardColor.RED,
         AbstractCard.CardRarity.UNCOMMON,
         AbstractCard.CardTarget.ENEMY
      );
      this.baseDamage = 15;
      this.baseMagicNumber = 2;
      this.magicNumber = this.baseMagicNumber;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      if (m != null) {
         this.addToBot(new VFXAction(new HemokinesisEffect(p.hb.cX, p.hb.cY, m.hb.cX, m.hb.cY), 0.5F));
      }

      this.addToBot(new LoseHPAction(p, p, this.magicNumber));
      this.addToBot(new DamageAction(m, new DamageInfo(p, this.damage, this.damageTypeForTurn), AbstractGameAction.AttackEffect.BLUNT_HEAVY));
   }

   @Override
   public void upgrade() {
      if (!this.upgraded) {
         this.upgradeName();
         this.upgradeDamage(5);
      }
   }

   @Override
   public AbstractCard makeCopy() {
      return new Hemokinesis();
   }
}
