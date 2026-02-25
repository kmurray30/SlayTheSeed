package com.megacrit.cardcrawl.monsters.beyond;

import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.AnimationState;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.ChangeStateAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.MonsterStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.FadingPower;
import com.megacrit.cardcrawl.powers.ShiftingPower;
import com.megacrit.cardcrawl.unlock.UnlockTracker;

public class Transient extends AbstractMonster {
   public static final String ID = "Transient";
   private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings("Transient");
   public static final String NAME;
   public static final String[] MOVES;
   public static final String[] DIALOG;
   private static final int HP = 999;
   private int count = 0;
   private static final int DEATH_DMG = 30;
   private static final int INCREMENT_DMG = 10;
   private static final int A_2_DEATH_DMG = 40;
   private int startingDeathDmg;
   private static final byte ATTACK = 1;

   public Transient() {
      super(NAME, "Transient", 999, 0.0F, -15.0F, 370.0F, 340.0F, null, 0.0F, 20.0F);
      this.loadAnimation("images/monsters/theForest/transient/skeleton.atlas", "images/monsters/theForest/transient/skeleton.json", 1.0F);
      AnimationState.TrackEntry e = this.state.setAnimation(0, "Idle", true);
      e.setTime(e.getEndTime() * MathUtils.random());
      this.gold = 1;
      this.dialogX = -100.0F * Settings.scale;
      this.dialogY = this.dialogY - 20.0F * Settings.scale;
      if (AbstractDungeon.ascensionLevel >= 2) {
         this.startingDeathDmg = 40;
      } else {
         this.startingDeathDmg = 30;
      }

      this.damage.add(new DamageInfo(this, this.startingDeathDmg));
      this.damage.add(new DamageInfo(this, this.startingDeathDmg + 10));
      this.damage.add(new DamageInfo(this, this.startingDeathDmg + 20));
      this.damage.add(new DamageInfo(this, this.startingDeathDmg + 30));
      this.damage.add(new DamageInfo(this, this.startingDeathDmg + 40));
      this.damage.add(new DamageInfo(this, this.startingDeathDmg + 50));
      this.damage.add(new DamageInfo(this, this.startingDeathDmg + 60));
   }

   @Override
   public void usePreBattleAction() {
      if (AbstractDungeon.ascensionLevel >= 17) {
         AbstractDungeon.actionManager.addToBottom(new ApplyPowerAction(this, this, new FadingPower(this, 6)));
      } else {
         AbstractDungeon.actionManager.addToBottom(new ApplyPowerAction(this, this, new FadingPower(this, 5)));
      }

      AbstractDungeon.actionManager.addToBottom(new ApplyPowerAction(this, this, new ShiftingPower(this)));
   }

   @Override
   public void takeTurn() {
      switch (this.nextMove) {
         case 1:
            AbstractDungeon.actionManager.addToBottom(new ChangeStateAction(this, "ATTACK"));
            AbstractDungeon.actionManager.addToBottom(new WaitAction(0.4F));
            AbstractDungeon.actionManager
               .addToBottom(new DamageAction(AbstractDungeon.player, this.damage.get(this.count), AbstractGameAction.AttackEffect.BLUNT_HEAVY));
            this.count++;
            this.setMove((byte)1, AbstractMonster.Intent.ATTACK, this.startingDeathDmg + this.count * 10);
      }
   }

   @Override
   public void damage(DamageInfo info) {
      super.damage(info);
      if (info.owner != null && info.type != DamageInfo.DamageType.THORNS && info.output > 0) {
         this.state.setAnimation(0, "Hurt", false);
         this.state.addAnimation(0, "Idle", true, 0.0F);
      }
   }

   @Override
   public void changeState(String key) {
      byte var3 = -1;
      switch (key.hashCode()) {
         case 1941037640:
            if (key.equals("ATTACK")) {
               var3 = 0;
            }
         default:
            switch (var3) {
               case 0:
                  this.state.setAnimation(0, "Attack", false);
                  this.state.addAnimation(0, "Idle", true, 0.0F);
            }
      }
   }

   @Override
   public void die() {
      super.die();
      UnlockTracker.unlockAchievement("TRANSIENT");
   }

   @Override
   protected void getMove(int num) {
      this.setMove((byte)1, AbstractMonster.Intent.ATTACK, this.startingDeathDmg + this.count * 10);
   }

   static {
      NAME = monsterStrings.NAME;
      MOVES = monsterStrings.MOVES;
      DIALOG = monsterStrings.DIALOG;
   }
}
