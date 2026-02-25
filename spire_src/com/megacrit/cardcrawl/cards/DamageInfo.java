package com.megacrit.cardcrawl.cards;

import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.powers.AbstractPower;

public class DamageInfo {
   public AbstractCreature owner;
   public String name;
   public DamageInfo.DamageType type;
   public int base;
   public int output;
   public boolean isModified = false;

   public DamageInfo(AbstractCreature damageSource, int base, DamageInfo.DamageType type) {
      this.owner = damageSource;
      this.type = type;
      this.base = base;
      this.output = base;
   }

   public DamageInfo(AbstractCreature owner, int base) {
      this(owner, base, DamageInfo.DamageType.NORMAL);
   }

   public void applyPowers(AbstractCreature owner, AbstractCreature target) {
      this.output = this.base;
      this.isModified = false;
      float tmp = this.output;
      if (!owner.isPlayer) {
         if (Settings.isEndless && AbstractDungeon.player.hasBlight("DeadlyEnemies")) {
            float mod = AbstractDungeon.player.getBlight("DeadlyEnemies").effectFloat();
            tmp *= mod;
            if (this.base != (int)tmp) {
               this.isModified = true;
            }
         }

         for (AbstractPower p : owner.powers) {
            tmp = p.atDamageGive(tmp, this.type);
            if (this.base != (int)tmp) {
               this.isModified = true;
            }
         }

         for (AbstractPower px : target.powers) {
            tmp = px.atDamageReceive(tmp, this.type);
            if (this.base != (int)tmp) {
               this.isModified = true;
            }
         }

         tmp = AbstractDungeon.player.stance.atDamageReceive(tmp, this.type);
         if (this.base != (int)tmp) {
            this.isModified = true;
         }

         for (AbstractPower pxx : owner.powers) {
            tmp = pxx.atDamageFinalGive(tmp, this.type);
            if (this.base != (int)tmp) {
               this.isModified = true;
            }
         }

         for (AbstractPower pxxx : target.powers) {
            tmp = pxxx.atDamageFinalReceive(tmp, this.type);
            if (this.base != (int)tmp) {
               this.isModified = true;
            }
         }

         this.output = MathUtils.floor(tmp);
         if (this.output < 0) {
            this.output = 0;
         }
      } else {
         for (AbstractPower pxxxx : owner.powers) {
            tmp = pxxxx.atDamageGive(tmp, this.type);
            if (this.base != (int)tmp) {
               this.isModified = true;
            }
         }

         tmp = AbstractDungeon.player.stance.atDamageGive(tmp, this.type);
         if (this.base != (int)tmp) {
            this.isModified = true;
         }

         for (AbstractPower pxxxxx : target.powers) {
            tmp = pxxxxx.atDamageReceive(tmp, this.type);
            if (this.base != (int)tmp) {
               this.isModified = true;
            }
         }

         for (AbstractPower pxxxxxx : owner.powers) {
            tmp = pxxxxxx.atDamageFinalGive(tmp, this.type);
            if (this.base != (int)tmp) {
               this.isModified = true;
            }
         }

         for (AbstractPower pxxxxxxx : target.powers) {
            tmp = pxxxxxxx.atDamageFinalReceive(tmp, this.type);
            if (this.base != (int)tmp) {
               this.isModified = true;
            }
         }

         this.output = MathUtils.floor(tmp);
         if (this.output < 0) {
            this.output = 0;
         }
      }
   }

   public void applyEnemyPowersOnly(AbstractCreature target) {
      this.output = this.base;
      this.isModified = false;
      float tmp = this.output;

      for (AbstractPower p : target.powers) {
         tmp = p.atDamageReceive(this.output, this.type);
         if (this.base != this.output) {
            this.isModified = true;
         }
      }

      for (AbstractPower px : target.powers) {
         tmp = px.atDamageFinalReceive(this.output, this.type);
         if (this.base != this.output) {
            this.isModified = true;
         }
      }

      if (tmp < 0.0F) {
         tmp = 0.0F;
      }

      this.output = MathUtils.floor(tmp);
   }

   public static int[] createDamageMatrix(int baseDamage) {
      return createDamageMatrix(baseDamage, false);
   }

   public static int[] createDamageMatrix(int baseDamage, boolean isPureDamage) {
      int[] retVal = new int[AbstractDungeon.getMonsters().monsters.size()];

      for (int i = 0; i < retVal.length; i++) {
         DamageInfo info = new DamageInfo(AbstractDungeon.player, baseDamage);
         if (!isPureDamage) {
            info.applyPowers(AbstractDungeon.player, AbstractDungeon.getMonsters().monsters.get(i));
         }

         retVal[i] = info.output;
      }

      return retVal;
   }

   public static int[] createDamageMatrix(int baseDamage, boolean isPureDamage, boolean isOrbDamage) {
      int[] retVal = new int[AbstractDungeon.getMonsters().monsters.size()];

      for (int i = 0; i < retVal.length; i++) {
         DamageInfo info = new DamageInfo(AbstractDungeon.player, baseDamage);
         if (isOrbDamage && AbstractDungeon.getMonsters().monsters.get(i).hasPower("Lockon")) {
            info.output = (int)(info.base * 1.5F);
         }

         retVal[i] = info.output;
      }

      return retVal;
   }

   public static enum DamageType {
      NORMAL,
      THORNS,
      HP_LOSS;
   }
}
