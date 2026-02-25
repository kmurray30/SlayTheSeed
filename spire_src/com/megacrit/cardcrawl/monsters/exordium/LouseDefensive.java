package com.megacrit.cardcrawl.monsters.exordium;

import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.AnimationState;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.AnimateSlowAttackAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.ChangeStateAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.common.RollMoveAction;
import com.megacrit.cardcrawl.actions.utility.SFXAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.MonsterStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.CurlUpPower;
import com.megacrit.cardcrawl.powers.WeakPower;
import com.megacrit.cardcrawl.vfx.combat.WebEffect;

public class LouseDefensive extends AbstractMonster {
   public static final String ID = "FuzzyLouseDefensive";
   private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings("FuzzyLouseDefensive");
   public static final String NAME;
   public static final String[] MOVES;
   public static final String[] DIALOG;
   private static final int HP_MIN = 11;
   private static final int HP_MAX = 17;
   private static final int A_2_HP_MIN = 12;
   private static final int A_2_HP_MAX = 18;
   private static final byte BITE = 3;
   private static final byte WEAKEN = 4;
   private boolean isOpen = true;
   private static final String CLOSED_STATE = "CLOSED";
   private static final String OPEN_STATE = "OPEN";
   private static final String REAR_IDLE = "REAR_IDLE";
   private final int biteDamage;
   private static final int WEAK_AMT = 2;

   public LouseDefensive(float x, float y) {
      super(NAME, "FuzzyLouseDefensive", 17, 0.0F, -5.0F, 180.0F, 140.0F, null, x, y);
      this.loadAnimation("images/monsters/theBottom/louseGreen/skeleton.atlas", "images/monsters/theBottom/louseGreen/skeleton.json", 1.0F);
      AnimationState.TrackEntry e = this.state.setAnimation(0, "idle", true);
      e.setTime(e.getEndTime() * MathUtils.random());
      if (AbstractDungeon.ascensionLevel >= 7) {
         this.setHp(12, 18);
      } else {
         this.setHp(11, 17);
      }

      if (AbstractDungeon.ascensionLevel >= 2) {
         this.biteDamage = AbstractDungeon.monsterHpRng.random(6, 8);
      } else {
         this.biteDamage = AbstractDungeon.monsterHpRng.random(5, 7);
      }

      this.damage.add(new DamageInfo(this, this.biteDamage));
   }

   @Override
   public void usePreBattleAction() {
      if (AbstractDungeon.ascensionLevel >= 17) {
         AbstractDungeon.actionManager.addToBottom(new ApplyPowerAction(this, this, new CurlUpPower(this, AbstractDungeon.monsterHpRng.random(9, 12))));
      } else if (AbstractDungeon.ascensionLevel >= 7) {
         AbstractDungeon.actionManager.addToBottom(new ApplyPowerAction(this, this, new CurlUpPower(this, AbstractDungeon.monsterHpRng.random(4, 8))));
      } else {
         AbstractDungeon.actionManager.addToBottom(new ApplyPowerAction(this, this, new CurlUpPower(this, AbstractDungeon.monsterHpRng.random(3, 7))));
      }
   }

   @Override
   public void takeTurn() {
      switch (this.nextMove) {
         case 3:
            if (!this.isOpen) {
               AbstractDungeon.actionManager.addToBottom(new ChangeStateAction(this, "OPEN"));
               AbstractDungeon.actionManager.addToBottom(new WaitAction(0.5F));
            }

            AbstractDungeon.actionManager.addToBottom(new AnimateSlowAttackAction(this));
            AbstractDungeon.actionManager
               .addToBottom(new DamageAction(AbstractDungeon.player, this.damage.get(0), AbstractGameAction.AttackEffect.BLUNT_LIGHT));
            break;
         case 4:
            if (!this.isOpen) {
               AbstractDungeon.actionManager.addToBottom(new ChangeStateAction(this, "REAR"));
               AbstractDungeon.actionManager.addToBottom(new WaitAction(1.2F));
               AbstractDungeon.actionManager.addToBottom(new SFXAction("ATTACK_MAGIC_FAST_3", MathUtils.random(0.88F, 0.92F), true));
               AbstractDungeon.actionManager
                  .addToBottom(new VFXAction(new WebEffect(AbstractDungeon.player, this.hb.cX - 70.0F * Settings.scale, this.hb.cY + 10.0F * Settings.scale)));
            } else {
               AbstractDungeon.actionManager.addToBottom(new ChangeStateAction(this, "REAR_IDLE"));
               AbstractDungeon.actionManager.addToBottom(new WaitAction(0.9F));
               AbstractDungeon.actionManager.addToBottom(new SFXAction("ATTACK_MAGIC_FAST_3", MathUtils.random(0.88F, 0.92F), true));
               AbstractDungeon.actionManager
                  .addToBottom(new VFXAction(new WebEffect(AbstractDungeon.player, this.hb.cX - 70.0F * Settings.scale, this.hb.cY + 10.0F * Settings.scale)));
            }

            AbstractDungeon.actionManager.addToBottom(new ApplyPowerAction(AbstractDungeon.player, this, new WeakPower(AbstractDungeon.player, 2, true), 2));
      }

      AbstractDungeon.actionManager.addToBottom(new RollMoveAction(this));
   }

   @Override
   public void changeState(String stateName) {
      if (stateName.equals("CLOSED")) {
         this.state.setAnimation(0, "transitiontoclosed", false);
         this.state.addAnimation(0, "idle closed", true, 0.0F);
         this.isOpen = false;
      } else if (stateName.equals("OPEN")) {
         this.state.setAnimation(0, "transitiontoopened", false);
         this.state.addAnimation(0, "idle", true, 0.0F);
         this.isOpen = true;
      } else if (stateName.equals("REAR_IDLE")) {
         this.state.setAnimation(0, "rear", false);
         this.state.addAnimation(0, "idle", true, 0.0F);
         this.isOpen = true;
      } else {
         this.state.setAnimation(0, "transitiontoopened", false);
         this.state.addAnimation(0, "rear", false, 0.0F);
         this.state.addAnimation(0, "idle", true, 0.0F);
         this.isOpen = true;
      }
   }

   @Override
   protected void getMove(int num) {
      if (AbstractDungeon.ascensionLevel >= 17) {
         if (num < 25) {
            if (this.lastMove((byte)4)) {
               this.setMove((byte)3, AbstractMonster.Intent.ATTACK, this.damage.get(0).base);
            } else {
               this.setMove(MOVES[0], (byte)4, AbstractMonster.Intent.DEBUFF);
            }
         } else if (this.lastTwoMoves((byte)3)) {
            this.setMove(MOVES[0], (byte)4, AbstractMonster.Intent.DEBUFF);
         } else {
            this.setMove((byte)3, AbstractMonster.Intent.ATTACK, this.damage.get(0).base);
         }
      } else if (num < 25) {
         if (this.lastTwoMoves((byte)4)) {
            this.setMove((byte)3, AbstractMonster.Intent.ATTACK, this.damage.get(0).base);
         } else {
            this.setMove(MOVES[0], (byte)4, AbstractMonster.Intent.DEBUFF);
         }
      } else if (this.lastTwoMoves((byte)3)) {
         this.setMove(MOVES[0], (byte)4, AbstractMonster.Intent.DEBUFF);
      } else {
         this.setMove((byte)3, AbstractMonster.Intent.ATTACK, this.damage.get(0).base);
      }
   }

   static {
      NAME = monsterStrings.NAME;
      MOVES = monsterStrings.MOVES;
      DIALOG = monsterStrings.DIALOG;
   }
}
