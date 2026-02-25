package com.megacrit.cardcrawl.cards.red;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.vfx.combat.ClashEffect;

public class Clash extends AbstractCard {
   public static final String ID = "Clash";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Clash");

   public Clash() {
      super(
         "Clash",
         cardStrings.NAME,
         "red/attack/clash",
         0,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.ATTACK,
         AbstractCard.CardColor.RED,
         AbstractCard.CardRarity.COMMON,
         AbstractCard.CardTarget.ENEMY
      );
      this.baseDamage = 14;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      if (m != null) {
         this.addToBot(new VFXAction(new ClashEffect(m.hb.cX, m.hb.cY), 0.1F));
      }

      this.addToBot(new DamageAction(m, new DamageInfo(p, this.damage, this.damageTypeForTurn), AbstractGameAction.AttackEffect.NONE));
   }

   @Override
   public boolean canUse(AbstractPlayer p, AbstractMonster m) {
      boolean canUse = super.canUse(p, m);
      if (!canUse) {
         return false;
      } else {
         for (AbstractCard c : p.hand.group) {
            if (c.type != AbstractCard.CardType.ATTACK) {
               canUse = false;
               this.cantUseMessage = cardStrings.EXTENDED_DESCRIPTION[0];
            }
         }

         return canUse;
      }
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
      return new Clash();
   }
}
