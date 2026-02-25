package com.megacrit.cardcrawl.cards.purple;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.vfx.combat.ClashEffect;

public class SignatureMove extends AbstractCard {
   public static final String ID = "SignatureMove";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("SignatureMove");

   public SignatureMove() {
      super(
         "SignatureMove",
         cardStrings.NAME,
         "purple/attack/signature_move",
         2,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.ATTACK,
         AbstractCard.CardColor.PURPLE,
         AbstractCard.CardRarity.UNCOMMON,
         AbstractCard.CardTarget.ENEMY
      );
      this.baseDamage = 30;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      if (m != null) {
         this.addToBot(new VFXAction(new ClashEffect(m.hb.cX, m.hb.cY), 0.1F));
      }

      this.addToBot(new DamageAction(m, new DamageInfo(p, this.damage, this.damageTypeForTurn), AbstractGameAction.AttackEffect.NONE));
   }

   @Override
   public void upgrade() {
      if (!this.upgraded) {
         this.upgradeName();
         this.upgradeDamage(10);
      }
   }

   @Override
   public boolean canUse(AbstractPlayer p, AbstractMonster m) {
      boolean canUse = super.canUse(p, m);
      if (!canUse) {
         return false;
      } else {
         for (AbstractCard c : p.hand.group) {
            if (c.type == AbstractCard.CardType.ATTACK && c != this) {
               canUse = false;
               this.cantUseMessage = CardCrawlGame.languagePack.getUIString("SignatureMoveMessage").TEXT[0];
            }
         }

         return canUse;
      }
   }

   @Override
   public void triggerOnGlowCheck() {
      boolean glow = true;

      for (AbstractCard c : AbstractDungeon.player.hand.group) {
         if (c.type == AbstractCard.CardType.ATTACK && c != this) {
            glow = false;
            break;
         }
      }

      if (glow) {
         this.glowColor = AbstractCard.GOLD_BORDER_GLOW_COLOR.cpy();
      } else {
         this.glowColor = AbstractCard.BLUE_BORDER_GLOW_COLOR.cpy();
      }
   }

   @Override
   public AbstractCard makeCopy() {
      return new SignatureMove();
   }
}
