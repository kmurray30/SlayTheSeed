package com.megacrit.cardcrawl.cards.blue;

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
import com.megacrit.cardcrawl.orbs.AbstractOrb;
import com.megacrit.cardcrawl.orbs.Frost;
import com.megacrit.cardcrawl.vfx.combat.BlizzardEffect;

public class Blizzard extends AbstractCard {
   public static final String ID = "Blizzard";
   private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Blizzard");

   public Blizzard() {
      super(
         "Blizzard",
         cardStrings.NAME,
         "blue/attack/blizzard",
         1,
         cardStrings.DESCRIPTION,
         AbstractCard.CardType.ATTACK,
         AbstractCard.CardColor.BLUE,
         AbstractCard.CardRarity.UNCOMMON,
         AbstractCard.CardTarget.ALL_ENEMY
      );
      this.baseDamage = 0;
      this.baseMagicNumber = 2;
      this.magicNumber = this.baseMagicNumber;
      this.isMultiDamage = true;
   }

   @Override
   public void use(AbstractPlayer p, AbstractMonster m) {
      int frostCount = 0;

      for (AbstractOrb o : AbstractDungeon.actionManager.orbsChanneledThisCombat) {
         if (o instanceof Frost) {
            frostCount++;
         }
      }

      this.baseDamage = frostCount * this.magicNumber;
      this.calculateCardDamage(null);
      if (Settings.FAST_MODE) {
         this.addToBot(new VFXAction(new BlizzardEffect(frostCount, AbstractDungeon.getMonsters().shouldFlipVfx()), 0.25F));
      } else {
         this.addToBot(new VFXAction(new BlizzardEffect(frostCount, AbstractDungeon.getMonsters().shouldFlipVfx()), 1.0F));
      }

      this.addToBot(new DamageAllEnemiesAction(p, this.multiDamage, this.damageTypeForTurn, AbstractGameAction.AttackEffect.BLUNT_HEAVY, false));
   }

   @Override
   public void applyPowers() {
      int frostCount = 0;

      for (AbstractOrb o : AbstractDungeon.actionManager.orbsChanneledThisCombat) {
         if (o instanceof Frost) {
            frostCount++;
         }
      }

      if (frostCount > 0) {
         this.baseDamage = frostCount * this.magicNumber;
         super.applyPowers();
         this.rawDescription = cardStrings.DESCRIPTION + cardStrings.EXTENDED_DESCRIPTION[0];
         this.initializeDescription();
      }
   }

   @Override
   public void onMoveToDiscard() {
      this.rawDescription = cardStrings.DESCRIPTION;
      this.initializeDescription();
   }

   @Override
   public void calculateCardDamage(AbstractMonster mo) {
      super.calculateCardDamage(mo);
      this.rawDescription = cardStrings.DESCRIPTION;
      this.rawDescription = this.rawDescription + cardStrings.EXTENDED_DESCRIPTION[0];
      this.initializeDescription();
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
      return new Blizzard();
   }
}
