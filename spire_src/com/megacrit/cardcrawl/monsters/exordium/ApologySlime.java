package com.megacrit.cardcrawl.monsters.exordium;

import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.AnimationState;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.AnimateSlowAttackAction;
import com.megacrit.cardcrawl.actions.animations.TalkAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.SlimeAnimListener;
import com.megacrit.cardcrawl.localization.MonsterStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.WeakPower;

public class ApologySlime extends AbstractMonster {
   public static final String ID = "Apology Slime";
   private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings("Apology Slime");
   public static final String NAME;
   public static final String[] MOVES;
   public static final String[] DIALOG;
   public static final int HP_MIN = 8;
   public static final int HP_MAX = 12;
   public static final int TACKLE_DAMAGE = 3;
   public static final int WEAK_TURNS = 1;
   private static final byte TACKLE = 1;
   private static final byte DEBUFF = 2;

   public ApologySlime() {
      super(NAME, "Apology Slime", AbstractDungeon.monsterHpRng.random(8, 12), 0.0F, -4.0F, 130.0F, 100.0F, null);
      this.damage.add(new DamageInfo(this, 3));
      this.loadAnimation("images/monsters/theBottom/slimeS/skeleton.atlas", "images/monsters/theBottom/slimeS/skeleton.json", 1.0F);
      AnimationState.TrackEntry e = this.state.setAnimation(0, "idle", true);
      e.setTime(e.getEndTime() * MathUtils.random());
      this.state.addListener(new SlimeAnimListener());
   }

   @Override
   public void usePreBattleAction() {
      AbstractDungeon.actionManager.addToBottom(new TalkAction(this, "Aw, something went wrong... NL please let the devs know!", 4.0F, 4.0F));
   }

   @Override
   public void takeTurn() {
      switch (this.nextMove) {
         case 1:
            AbstractDungeon.actionManager.addToBottom(new AnimateSlowAttackAction(this));
            AbstractDungeon.actionManager
               .addToBottom(new DamageAction(AbstractDungeon.player, this.damage.get(0), AbstractGameAction.AttackEffect.BLUNT_HEAVY));
            this.setMove((byte)2, AbstractMonster.Intent.DEBUFF);
            break;
         case 2:
            AbstractDungeon.actionManager.addToBottom(new AnimateSlowAttackAction(this));
            AbstractDungeon.actionManager.addToBottom(new ApplyPowerAction(AbstractDungeon.player, this, new WeakPower(AbstractDungeon.player, 1, true), 1));
            this.setMove((byte)1, AbstractMonster.Intent.ATTACK, this.damage.get(0).base);
      }
   }

   @Override
   protected void getMove(int num) {
      if (AbstractDungeon.aiRng.randomBoolean()) {
         this.setMove((byte)1, AbstractMonster.Intent.ATTACK, this.damage.get(0).base);
      } else {
         this.setMove((byte)2, AbstractMonster.Intent.DEBUFF);
      }
   }

   static {
      NAME = monsterStrings.NAME;
      MOVES = monsterStrings.MOVES;
      DIALOG = monsterStrings.DIALOG;
   }
}
