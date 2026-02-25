package com.megacrit.cardcrawl.monsters.city;

import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.AnimationState;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.AnimateSlowAttackAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.common.MakeTempCardInDiscardAction;
import com.megacrit.cardcrawl.actions.common.RollMoveAction;
import com.megacrit.cardcrawl.actions.utility.SFXAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.cards.status.Wound;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.MonsterStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.StrengthPower;

public class Taskmaster extends AbstractMonster {
   public static final String ID = "SlaverBoss";
   private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings("SlaverBoss");
   public static final String NAME;
   public static final String[] MOVES;
   public static final String[] DIALOG;
   private static final int HP_MIN = 54;
   private static final int HP_MAX = 60;
   private static final int A_2_HP_MIN = 57;
   private static final int A_2_HP_MAX = 64;
   private static final int WHIP_DMG = 4;
   private static final int SCOURING_WHIP_DMG = 7;
   private static final int WOUNDS = 1;
   private static final int A_2_WOUNDS = 2;
   private int woundCount;
   private static final byte SCOURING_WHIP = 2;

   public Taskmaster(float x, float y) {
      super(NAME, "SlaverBoss", AbstractDungeon.monsterHpRng.random(54, 60), -10.0F, -8.0F, 200.0F, 280.0F, null, x, y);
      this.type = AbstractMonster.EnemyType.ELITE;
      if (AbstractDungeon.ascensionLevel >= 8) {
         this.setHp(57, 64);
      } else {
         this.setHp(54, 60);
      }

      if (AbstractDungeon.ascensionLevel >= 18) {
         this.woundCount = 3;
      } else if (AbstractDungeon.ascensionLevel >= 3) {
         this.woundCount = 2;
      } else {
         this.woundCount = 1;
      }

      this.damage.add(new DamageInfo(this, 4));
      this.damage.add(new DamageInfo(this, 7));
      this.loadAnimation("images/monsters/theCity/slaverMaster/skeleton.atlas", "images/monsters/theCity/slaverMaster/skeleton.json", 1.0F);
      AnimationState.TrackEntry e = this.state.setAnimation(0, "idle", true);
      e.setTime(e.getEndTime() * MathUtils.random());
   }

   @Override
   public void takeTurn() {
      switch (this.nextMove) {
         case 2:
            this.playSfx();
            AbstractDungeon.actionManager.addToBottom(new AnimateSlowAttackAction(this));
            AbstractDungeon.actionManager
               .addToBottom(new DamageAction(AbstractDungeon.player, this.damage.get(1), AbstractGameAction.AttackEffect.SLASH_HEAVY));
            AbstractDungeon.actionManager.addToBottom(new MakeTempCardInDiscardAction(new Wound(), this.woundCount));
            if (AbstractDungeon.ascensionLevel >= 18) {
               AbstractDungeon.actionManager.addToBottom(new ApplyPowerAction(this, this, new StrengthPower(this, 1), 1));
            }
         default:
            AbstractDungeon.actionManager.addToBottom(new RollMoveAction(this));
      }
   }

   @Override
   protected void getMove(int num) {
      this.setMove((byte)2, AbstractMonster.Intent.ATTACK_DEBUFF, 7);
   }

   private void playSfx() {
      int roll = MathUtils.random(1);
      if (roll == 0) {
         AbstractDungeon.actionManager.addToBottom(new SFXAction("VO_SLAVERLEADER_1A"));
      } else {
         AbstractDungeon.actionManager.addToBottom(new SFXAction("VO_SLAVERLEADER_1B"));
      }
   }

   private void playDeathSfx() {
      int roll = MathUtils.random(1);
      if (roll == 0) {
         CardCrawlGame.sound.play("VO_SLAVERLEADER_2A");
      } else {
         CardCrawlGame.sound.play("VO_SLAVERLEADER_2B");
      }
   }

   @Override
   public void die() {
      super.die();
      this.playDeathSfx();
   }

   static {
      NAME = monsterStrings.NAME;
      MOVES = monsterStrings.MOVES;
      DIALOG = monsterStrings.DIALOG;
   }
}
