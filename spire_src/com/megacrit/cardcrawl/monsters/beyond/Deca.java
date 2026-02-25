package com.megacrit.cardcrawl.monsters.beyond;

import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.AnimationState;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.ChangeStateAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.actions.common.MakeTempCardInDiscardAction;
import com.megacrit.cardcrawl.actions.common.RollMoveAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.cards.status.Dazed;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.MonsterStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.ArtifactPower;
import com.megacrit.cardcrawl.powers.PlatedArmorPower;
import com.megacrit.cardcrawl.unlock.UnlockTracker;

public class Deca extends AbstractMonster {
   public static final String ID = "Deca";
   private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings("Deca");
   public static final String NAME;
   public static final String[] MOVES;
   public static final String[] DIALOG;
   public static final String ENC_NAME = "Donu and Deca";
   public static final int HP = 250;
   public static final int A_2_HP = 265;
   private static final byte BEAM = 0;
   private static final byte SQUARE_OF_PROTECTION = 2;
   private static final int ARTIFACT_AMT = 2;
   private static final int BEAM_DMG = 10;
   private static final int BEAM_AMT = 2;
   private static final int A_2_BEAM_DMG = 12;
   private int beamDmg;
   private static final int BEAM_DAZE_AMT = 2;
   private static final int PROTECT_BLOCK = 16;
   private boolean isAttacking;

   public Deca() {
      super(NAME, "Deca", 250, 0.0F, -26.0F, 390.0F, 390.0F, null, -350.0F, 30.0F);
      this.loadAnimation("images/monsters/theForest/deca/skeleton.atlas", "images/monsters/theForest/deca/skeleton.json", 1.0F);
      AnimationState.TrackEntry e = this.state.setAnimation(0, "Idle", true);
      e.setTime(e.getEndTime() * MathUtils.random());
      this.stateData.setMix("Hit", "Idle", 0.1F);
      this.stateData.setMix("Attack_2", "Idle", 0.1F);
      this.type = AbstractMonster.EnemyType.BOSS;
      this.dialogX = -200.0F * Settings.scale;
      this.dialogY = 10.0F * Settings.scale;
      if (AbstractDungeon.ascensionLevel >= 9) {
         this.setHp(265);
      } else {
         this.setHp(250);
      }

      if (AbstractDungeon.ascensionLevel >= 4) {
         this.beamDmg = 12;
      } else {
         this.beamDmg = 10;
      }

      this.damage.add(new DamageInfo(this, this.beamDmg));
      this.isAttacking = true;
   }

   @Override
   public void changeState(String stateName) {
      byte var3 = -1;
      switch (stateName.hashCode()) {
         case 1941037640:
            if (stateName.equals("ATTACK")) {
               var3 = 0;
            }
         default:
            switch (var3) {
               case 0:
                  this.state.setAnimation(0, "Attack_2", false);
                  this.state.addAnimation(0, "Idle", true, 0.0F);
            }
      }
   }

   @Override
   public void damage(DamageInfo info) {
      super.damage(info);
      if (info.owner != null && info.type != DamageInfo.DamageType.THORNS && info.output > 0) {
         this.state.setAnimation(0, "Hit", false);
         this.state.addAnimation(0, "Idle", true, 0.0F);
      }
   }

   @Override
   public void usePreBattleAction() {
      CardCrawlGame.music.unsilenceBGM();
      AbstractDungeon.scene.fadeOutAmbiance();
      AbstractDungeon.getCurrRoom().playBgmInstantly("BOSS_BEYOND");
      if (AbstractDungeon.ascensionLevel >= 19) {
         AbstractDungeon.actionManager.addToBottom(new ApplyPowerAction(this, this, new ArtifactPower(this, 3)));
      } else {
         AbstractDungeon.actionManager.addToBottom(new ApplyPowerAction(this, this, new ArtifactPower(this, 2)));
      }

      UnlockTracker.markBossAsSeen("DONUT");
   }

   @Override
   public void takeTurn() {
      switch (this.nextMove) {
         case 0:
            AbstractDungeon.actionManager.addToBottom(new ChangeStateAction(this, "ATTACK"));
            AbstractDungeon.actionManager.addToBottom(new WaitAction(0.5F));

            for (int i = 0; i < 2; i++) {
               AbstractDungeon.actionManager.addToBottom(new DamageAction(AbstractDungeon.player, this.damage.get(0), AbstractGameAction.AttackEffect.FIRE));
            }

            AbstractDungeon.actionManager.addToBottom(new MakeTempCardInDiscardAction(new Dazed(), 2));
            this.isAttacking = false;
            break;
         case 2:
            for (AbstractMonster m : AbstractDungeon.getMonsters().monsters) {
               AbstractDungeon.actionManager.addToBottom(new GainBlockAction(m, this, 16));
               if (AbstractDungeon.ascensionLevel >= 19) {
                  AbstractDungeon.actionManager.addToBottom(new ApplyPowerAction(m, this, new PlatedArmorPower(m, 3), 3));
               }
            }

            this.isAttacking = true;
      }

      AbstractDungeon.actionManager.addToBottom(new RollMoveAction(this));
   }

   @Override
   protected void getMove(int num) {
      if (this.isAttacking) {
         this.setMove((byte)0, AbstractMonster.Intent.ATTACK_DEBUFF, this.damage.get(0).base, 2, true);
      } else if (AbstractDungeon.ascensionLevel >= 19) {
         this.setMove((byte)2, AbstractMonster.Intent.DEFEND_BUFF);
      } else {
         this.setMove((byte)2, AbstractMonster.Intent.DEFEND);
      }
   }

   @Override
   public void die() {
      super.die();
      if (AbstractDungeon.getMonsters().areMonstersBasicallyDead()) {
         this.useFastShakeAnimation(5.0F);
         CardCrawlGame.screenShake.rumble(4.0F);
         this.onBossVictoryLogic();
         UnlockTracker.hardUnlockOverride("DONUT");
         UnlockTracker.unlockAchievement("SHAPES");
         this.onFinalBossVictoryLogic();
      }
   }

   static {
      NAME = monsterStrings.NAME;
      MOVES = monsterStrings.MOVES;
      DIALOG = monsterStrings.DIALOG;
   }
}
