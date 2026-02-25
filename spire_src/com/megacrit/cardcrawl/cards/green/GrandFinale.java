package com.megacrit.cardcrawl.cards.green;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.DamageAllEnemiesAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.vfx.combat.GrandFinalEffect;

public class GrandFinale extends AbstractCard {
   public static final String ID = "Grand Finale";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Grand Finale");

   public GrandFinale() {
      super(
         "Grand Finale",
         cardStrings.NAME,
         "green/attack/grand_finale",
         0,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.ATTACK,
         AbstractCard.CardColor.GREEN,
         AbstractCard.CardRarity.RARE,
         AbstractCard.CardTarget.ALL_ENEMY
      );
      this.baseDamage = 50;
      this.isMultiDamage = true;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      if (Settings.FAST_MODE) {
         this.addToBot(new VFXAction(new GrandFinalEffect(), 0.7F));
      } else {
         this.addToBot(new VFXAction(new GrandFinalEffect(), 1.0F));
      }

      this.addToBot(new DamageAllEnemiesAction(p, this.multiDamage, this.damageTypeForTurn, AbstractGameAction.AttackEffect.SLASH_HEAVY));
   }

   @Override
   public void triggerOnGlowCheck() {
      this.glowColor = AbstractCard.BLUE_BORDER_GLOW_COLOR.cpy();
      if (AbstractDungeon.player.drawPile.isEmpty()) {
         this.glowColor = AbstractCard.GOLD_BORDER_GLOW_COLOR.cpy();
      }
   }

   @Override
   public boolean canUse(AbstractPlayer p, AbstractMonster m) {
      boolean canUse = super.canUse(p, m);
      if (!canUse) {
         return false;
      } else if (p.drawPile.size() > 0) {
         this.cantUseMessage = cardStrings.UPGRADE_DESCRIPTION;
         return false;
      } else {
         return canUse;
      }
   }

   @Override
   public void upgrade() {
      if (!this.upgraded) {
         this.upgradeName();
         this.upgradeDamage(10);
      }
   }

   @Override
   public AbstractCard makeCopy() {
      return new GrandFinale();
   }
}
