package com.megacrit.cardcrawl.monsters.beyond;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.AnimationState;
import com.esotericsoftware.spine.Bone;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.ChangeStateAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.common.MakeTempCardInDiscardAction;
import com.megacrit.cardcrawl.actions.common.RollMoveAction;
import com.megacrit.cardcrawl.actions.utility.SFXAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.cards.status.Burn;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.MonsterStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.IntangiblePower;
import com.megacrit.cardcrawl.vfx.NemesisFireParticle;
import com.megacrit.cardcrawl.vfx.combat.ShockWaveEffect;

public class Nemesis extends AbstractMonster {
   public static final String ID = "Nemesis";
   private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings("Nemesis");
   public static final String NAME;
   private static final int HP = 185;
   private static final int A_2_HP = 200;
   private static final int SCYTHE_COOLDOWN_TURNS = 2;
   private static final float HB_X = 5.0F;
   private static final float HB_Y = -10.0F;
   private static final int SCYTHE_DMG = 45;
   private static final int FIRE_DMG = 6;
   private static final int FIRE_TIMES = 3;
   private static final int A_2_FIRE_DMG = 7;
   private static final int BURN_AMT = 3;
   private int fireDmg;
   private int scytheCooldown = 0;
   private static final byte TRI_ATTACK = 2;
   private static final byte SCYTHE = 3;
   private static final byte TRI_BURN = 4;
   private float fireTimer = 0.0F;
   private static final float FIRE_TIME = 0.05F;
   private Bone eye1;
   private Bone eye2;
   private Bone eye3;
   private boolean firstMove = true;

   public Nemesis() {
      super(NAME, "Nemesis", 185, 5.0F, -10.0F, 350.0F, 440.0F, null, 0.0F, 0.0F);
      this.type = AbstractMonster.EnemyType.ELITE;
      this.loadAnimation("images/monsters/theForest/nemesis/skeleton.atlas", "images/monsters/theForest/nemesis/skeleton.json", 1.0F);
      AnimationState.TrackEntry e = this.state.setAnimation(0, "Idle", true);
      e.setTime(e.getEndTime() * MathUtils.random());
      this.stateData.setMix("Hit", "Idle", 0.1F);
      e.setTimeScale(0.8F);
      this.eye1 = this.skeleton.findBone("eye0");
      this.eye2 = this.skeleton.findBone("eye1");
      this.eye3 = this.skeleton.findBone("eye2");
      if (AbstractDungeon.ascensionLevel >= 8) {
         this.setHp(200);
      } else {
         this.setHp(185);
      }

      if (AbstractDungeon.ascensionLevel >= 3) {
         this.fireDmg = 7;
      } else {
         this.fireDmg = 6;
      }

      this.damage.add(new DamageInfo(this, 45));
      this.damage.add(new DamageInfo(this, this.fireDmg));
   }

   @Override
   public void takeTurn() {
      switch (this.nextMove) {
         case 2:
            for (int i = 0; i < 3; i++) {
               AbstractDungeon.actionManager.addToBottom(new DamageAction(AbstractDungeon.player, this.damage.get(1), AbstractGameAction.AttackEffect.FIRE));
            }
            break;
         case 3:
            AbstractDungeon.actionManager.addToBottom(new ChangeStateAction(this, "ATTACK"));
            this.playSfx();
            AbstractDungeon.actionManager.addToBottom(new WaitAction(0.4F));
            AbstractDungeon.actionManager
               .addToBottom(new DamageAction(AbstractDungeon.player, this.damage.get(0), AbstractGameAction.AttackEffect.SLASH_HEAVY));
            break;
         case 4:
            AbstractDungeon.actionManager.addToBottom(new SFXAction("VO_NEMESIS_1C"));
            AbstractDungeon.actionManager
               .addToBottom(
                  new VFXAction(this, new ShockWaveEffect(this.hb.cX, this.hb.cY, Settings.GREEN_TEXT_COLOR, ShockWaveEffect.ShockWaveType.CHAOTIC), 1.5F)
               );
            if (AbstractDungeon.ascensionLevel >= 18) {
               AbstractDungeon.actionManager.addToBottom(new MakeTempCardInDiscardAction(new Burn(), 5));
            } else {
               AbstractDungeon.actionManager.addToBottom(new MakeTempCardInDiscardAction(new Burn(), 3));
            }
      }

      if (!this.hasPower("Intangible")) {
         AbstractDungeon.actionManager.addToBottom(new ApplyPowerAction(this, this, new IntangiblePower(this, 1)));
      }

      AbstractDungeon.actionManager.addToBottom(new RollMoveAction(this));
   }

   @Override
   public void damage(DamageInfo info) {
      if (info.output > 0 && this.hasPower("Intangible")) {
         info.output = 1;
      }

      if (info.owner != null && info.type != DamageInfo.DamageType.THORNS && info.output > 0) {
         AnimationState.TrackEntry e = this.state.setAnimation(0, "Hit", false);
         this.state.addAnimation(0, "Idle", true, 0.0F);
         e.setTimeScale(0.8F);
      }

      super.damage(info);
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
                  AnimationState.TrackEntry e = this.state.setAnimation(0, "Attack", false);
                  this.state.addAnimation(0, "Idle", true, 0.0F);
                  e.setTimeScale(0.8F);
            }
      }
   }

   @Override
   protected void getMove(int num) {
      this.scytheCooldown--;
      if (this.firstMove) {
         this.firstMove = false;
         if (num < 50) {
            this.setMove((byte)2, AbstractMonster.Intent.ATTACK, this.fireDmg, 3, true);
         } else {
            this.setMove((byte)4, AbstractMonster.Intent.DEBUFF);
         }
      } else {
         if (num < 30) {
            if (!this.lastMove((byte)3) && this.scytheCooldown <= 0) {
               this.setMove((byte)3, AbstractMonster.Intent.ATTACK, 45);
               this.scytheCooldown = 2;
            } else if (AbstractDungeon.aiRng.randomBoolean()) {
               if (!this.lastTwoMoves((byte)2)) {
                  this.setMove((byte)2, AbstractMonster.Intent.ATTACK, this.fireDmg, 3, true);
               } else {
                  this.setMove((byte)4, AbstractMonster.Intent.DEBUFF);
               }
            } else if (!this.lastMove((byte)4)) {
               this.setMove((byte)4, AbstractMonster.Intent.DEBUFF);
            } else {
               this.setMove((byte)2, AbstractMonster.Intent.ATTACK, this.fireDmg, 3, true);
            }
         } else if (num < 65) {
            if (!this.lastTwoMoves((byte)2)) {
               this.setMove((byte)2, AbstractMonster.Intent.ATTACK, this.fireDmg, 3, true);
            } else if (AbstractDungeon.aiRng.randomBoolean()) {
               if (this.scytheCooldown > 0) {
                  this.setMove((byte)4, AbstractMonster.Intent.DEBUFF);
               } else {
                  this.setMove((byte)3, AbstractMonster.Intent.ATTACK, 45);
                  this.scytheCooldown = 2;
               }
            } else {
               this.setMove((byte)4, AbstractMonster.Intent.DEBUFF);
            }
         } else if (!this.lastMove((byte)4)) {
            this.setMove((byte)4, AbstractMonster.Intent.DEBUFF);
         } else if (AbstractDungeon.aiRng.randomBoolean() && this.scytheCooldown <= 0) {
            this.setMove((byte)3, AbstractMonster.Intent.ATTACK, 45);
            this.scytheCooldown = 2;
         } else {
            this.setMove((byte)2, AbstractMonster.Intent.ATTACK, this.fireDmg, 3, true);
         }
      }
   }

   private void playSfx() {
      int roll = MathUtils.random(1);
      if (roll == 0) {
         AbstractDungeon.actionManager.addToBottom(new SFXAction("VO_NEMESIS_1A"));
      } else {
         AbstractDungeon.actionManager.addToBottom(new SFXAction("VO_NEMESIS_1B"));
      }
   }

   private void playDeathSfx() {
      int roll = MathUtils.random(1);
      if (roll == 0) {
         CardCrawlGame.sound.play("VO_NEMESIS_2A");
      } else {
         CardCrawlGame.sound.play("VO_NEMESIS_2B");
      }
   }

   @Override
   public void die() {
      this.playDeathSfx();
      super.die();
   }

   @Override
   public void update() {
      super.update();
      if (!this.isDying) {
         this.fireTimer = this.fireTimer - Gdx.graphics.getDeltaTime();
         if (this.fireTimer < 0.0F) {
            this.fireTimer = 0.05F;
            AbstractDungeon.effectList.add(new NemesisFireParticle(this.skeleton.getX() + this.eye1.getWorldX(), this.skeleton.getY() + this.eye1.getWorldY()));
            AbstractDungeon.effectList.add(new NemesisFireParticle(this.skeleton.getX() + this.eye2.getWorldX(), this.skeleton.getY() + this.eye2.getWorldY()));
            AbstractDungeon.effectList.add(new NemesisFireParticle(this.skeleton.getX() + this.eye3.getWorldX(), this.skeleton.getY() + this.eye3.getWorldY()));
         }
      }
   }

   static {
      NAME = monsterStrings.NAME;
   }
}
