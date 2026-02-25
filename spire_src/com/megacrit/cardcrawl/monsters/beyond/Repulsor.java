package com.megacrit.cardcrawl.monsters.beyond;

import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.AnimationState;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.AnimateSlowAttackAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.common.MakeTempCardInDrawPileAction;
import com.megacrit.cardcrawl.actions.common.RollMoveAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.cards.status.Dazed;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.MonsterStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Repulsor extends AbstractMonster {
   public static final String ID = "Repulsor";
   private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings("Repulsor");
   public static final String NAME;
   public static final String[] MOVES;
   public static final String[] DIALOG;
   public static final String ENCOUNTER_NAME_W = "Ancient Shapes Weak";
   public static final String ENCOUNTER_NAME = "Ancient Shapes";
   private static final float HB_X = -8.0F;
   private static final float HB_Y = -10.0F;
   private static final float HB_W = 150.0F;
   private static final float HB_H = 150.0F;
   private static final byte DAZE = 1;
   private static final byte ATTACK = 2;
   private int attackDmg;
   private int dazeAmt;

   public Repulsor(float x, float y) {
      super(NAME, "Repulsor", 35, -8.0F, -10.0F, 150.0F, 150.0F, null, x, y + 10.0F);
      this.loadAnimation("images/monsters/theForest/repulser/skeleton.atlas", "images/monsters/theForest/repulser/skeleton.json", 1.0F);
      AnimationState.TrackEntry e = this.state.setAnimation(0, "idle", true);
      e.setTime(e.getEndTime() * MathUtils.random());
      this.dazeAmt = 2;
      if (AbstractDungeon.ascensionLevel >= 7) {
         this.setHp(31, 38);
      } else {
         this.setHp(29, 35);
      }

      if (AbstractDungeon.ascensionLevel >= 2) {
         this.attackDmg = 13;
      } else {
         this.attackDmg = 11;
      }

      this.damage.add(new DamageInfo(this, this.attackDmg));
   }

   @Override
   public void takeTurn() {
      switch (this.nextMove) {
         case 1:
            AbstractDungeon.actionManager.addToBottom(new MakeTempCardInDrawPileAction(new Dazed(), this.dazeAmt, true, true));
            break;
         case 2:
            AbstractDungeon.actionManager.addToBottom(new AnimateSlowAttackAction(this));
            AbstractDungeon.actionManager
               .addToBottom(new DamageAction(AbstractDungeon.player, this.damage.get(0), AbstractGameAction.AttackEffect.SLASH_HORIZONTAL));
      }

      AbstractDungeon.actionManager.addToBottom(new RollMoveAction(this));
   }

   @Override
   protected void getMove(int num) {
      if (num < 20 && !this.lastMove((byte)2)) {
         this.setMove((byte)2, AbstractMonster.Intent.ATTACK, this.damage.get(0).base);
      } else {
         this.setMove((byte)1, AbstractMonster.Intent.DEBUFF);
      }
   }

   static {
      NAME = monsterStrings.NAME;
      MOVES = monsterStrings.MOVES;
      DIALOG = monsterStrings.DIALOG;
   }
}
