package com.megacrit.cardcrawl.monsters.city;

import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.AnimationState;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.AnimateFastAttackAction;
import com.megacrit.cardcrawl.actions.animations.AnimateSlowAttackAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.ChangeStateAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.actions.common.RollMoveAction;
import com.megacrit.cardcrawl.actions.utility.SFXAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.MonsterStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.ArtifactPower;
import com.megacrit.cardcrawl.powers.BarricadePower;
import com.megacrit.cardcrawl.powers.FrailPower;

public class SphericGuardian extends AbstractMonster {
   public static final String ID = "SphericGuardian";
   private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings("SphericGuardian");
   public static final String NAME;
   public static final String[] MOVES;
   public static final String[] DIALOG;
   private static final float IDLE_TIMESCALE = 0.8F;
   private static final float HB_X = 0.0F;
   private static final float HB_Y = 10.0F;
   private static final float HB_W = 280.0F;
   private static final float HB_H = 280.0F;
   private static final int DMG = 10;
   private static final int A_2_DMG = 11;
   private int dmg;
   private static final int SLAM_AMT = 2;
   private static final int HARDEN_BLOCK = 15;
   private static final int FRAIL_AMT = 5;
   private static final int ACTIVATE_BLOCK = 25;
   private static final int ARTIFACT_AMT = 3;
   private static final int STARTING_BLOCK_AMT = 40;
   private static final byte BIG_ATTACK = 1;
   private static final byte INITIAL_BLOCK_GAIN = 2;
   private static final byte BLOCK_ATTACK = 3;
   private static final byte FRAIL_ATTACK = 4;
   private boolean firstMove = true;
   private boolean secondMove = true;

   public SphericGuardian() {
      this(0.0F, 0.0F);
   }

   public SphericGuardian(float x, float y) {
      super(NAME, "SphericGuardian", 20, 0.0F, 10.0F, 280.0F, 280.0F, null, x, y);
      if (AbstractDungeon.ascensionLevel >= 2) {
         this.dmg = 11;
      } else {
         this.dmg = 10;
      }

      this.damage.add(new DamageInfo(this, this.dmg));
      this.loadAnimation("images/monsters/theCity/sphere/skeleton.atlas", "images/monsters/theCity/sphere/skeleton.json", 1.0F);
      AnimationState.TrackEntry e = this.state.setAnimation(0, "Idle", true);
      e.setTime(e.getEndTime() * MathUtils.random());
      this.stateData.setMix("Hit", "Idle", 0.2F);
      this.stateData.setMix("Idle", "Attack", 0.1F);
      this.state.setTimeScale(0.8F);
   }

   @Override
   public void usePreBattleAction() {
      AbstractDungeon.actionManager.addToBottom(new ApplyPowerAction(this, this, new BarricadePower(this)));
      AbstractDungeon.actionManager.addToBottom(new ApplyPowerAction(this, this, new ArtifactPower(this, 3)));
      AbstractDungeon.actionManager.addToBottom(new GainBlockAction(this, this, 40));
   }

   @Override
   public void takeTurn() {
      switch (this.nextMove) {
         case 1:
            AbstractDungeon.actionManager.addToBottom(new ChangeStateAction(this, "ATTACK"));
            AbstractDungeon.actionManager.addToBottom(new WaitAction(0.4F));
            AbstractDungeon.actionManager
               .addToBottom(new DamageAction(AbstractDungeon.player, this.damage.get(0), AbstractGameAction.AttackEffect.BLUNT_HEAVY, true));
            AbstractDungeon.actionManager
               .addToBottom(new DamageAction(AbstractDungeon.player, this.damage.get(0), AbstractGameAction.AttackEffect.BLUNT_HEAVY));
            break;
         case 2:
            if (AbstractDungeon.ascensionLevel >= 17) {
               AbstractDungeon.actionManager.addToBottom(new GainBlockAction(this, this, 35));
            } else {
               AbstractDungeon.actionManager.addToBottom(new GainBlockAction(this, this, 25));
            }

            AbstractDungeon.actionManager.addToBottom(new WaitAction(0.2F));
            if (MathUtils.randomBoolean()) {
               AbstractDungeon.actionManager.addToBottom(new SFXAction("SPHERE_DETECT_VO_1"));
            } else {
               AbstractDungeon.actionManager.addToBottom(new SFXAction("SPHERE_DETECT_VO_2"));
            }
            break;
         case 3:
            AbstractDungeon.actionManager.addToBottom(new GainBlockAction(this, this, 15));
            AbstractDungeon.actionManager.addToBottom(new AnimateFastAttackAction(this));
            AbstractDungeon.actionManager
               .addToBottom(new DamageAction(AbstractDungeon.player, this.damage.get(0), AbstractGameAction.AttackEffect.BLUNT_HEAVY));
            break;
         case 4:
            AbstractDungeon.actionManager.addToBottom(new AnimateSlowAttackAction(this));
            AbstractDungeon.actionManager
               .addToBottom(new DamageAction(AbstractDungeon.player, this.damage.get(0), AbstractGameAction.AttackEffect.BLUNT_LIGHT));
            AbstractDungeon.actionManager.addToBottom(new ApplyPowerAction(AbstractDungeon.player, this, new FrailPower(AbstractDungeon.player, 5, true), 5));
      }

      AbstractDungeon.actionManager.addToBottom(new RollMoveAction(this));
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
                  this.state.setTimeScale(0.8F);
                  this.state.addAnimation(0, "Idle", true, 0.0F);
            }
      }
   }

   @Override
   public void damage(DamageInfo info) {
      super.damage(info);
      if (info.owner != null && info.type != DamageInfo.DamageType.THORNS && info.output > 0) {
         this.state.setAnimation(0, "Hit", false);
         this.state.setTimeScale(0.8F);
         this.state.addAnimation(0, "Idle", true, 0.0F);
      }
   }

   @Override
   protected void getMove(int num) {
      if (this.firstMove) {
         this.firstMove = false;
         this.setMove((byte)2, AbstractMonster.Intent.DEFEND);
      } else if (this.secondMove) {
         this.secondMove = false;
         this.setMove((byte)4, AbstractMonster.Intent.ATTACK_DEBUFF, this.damage.get(0).base);
      } else {
         if (this.lastMove((byte)1)) {
            this.setMove((byte)3, AbstractMonster.Intent.ATTACK_DEFEND, this.damage.get(0).base);
         } else {
            this.setMove((byte)1, AbstractMonster.Intent.ATTACK, this.damage.get(0).base, 2, true);
         }
      }
   }

   @Override
   public void die() {
      super.die();
      if (MathUtils.randomBoolean()) {
         AbstractDungeon.actionManager.addToBottom(new SFXAction("SPHERE_DETECT_VO_1"));
      } else {
         AbstractDungeon.actionManager.addToBottom(new SFXAction("SPHERE_DETECT_VO_2"));
      }
   }

   static {
      NAME = monsterStrings.NAME;
      MOVES = monsterStrings.MOVES;
      DIALOG = monsterStrings.DIALOG;
   }
}
