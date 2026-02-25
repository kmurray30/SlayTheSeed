package com.megacrit.cardcrawl.monsters.city;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.AnimationState;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.TalkAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.actions.common.RollMoveAction;
import com.megacrit.cardcrawl.actions.common.SpawnMonsterAction;
import com.megacrit.cardcrawl.actions.common.SuicideAction;
import com.megacrit.cardcrawl.actions.utility.HideHealthBarAction;
import com.megacrit.cardcrawl.actions.utility.SFXAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.MonsterStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.FrailPower;
import com.megacrit.cardcrawl.powers.StrengthPower;
import com.megacrit.cardcrawl.powers.VulnerablePower;
import com.megacrit.cardcrawl.powers.WeakPower;
import com.megacrit.cardcrawl.unlock.UnlockTracker;
import com.megacrit.cardcrawl.vfx.CollectorCurseEffect;
import com.megacrit.cardcrawl.vfx.GlowyFireEyesEffect;
import com.megacrit.cardcrawl.vfx.StaffFireEffect;
import com.megacrit.cardcrawl.vfx.combat.InflameEffect;
import java.util.HashMap;
import java.util.Map.Entry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TheCollector extends AbstractMonster {
   private static final Logger logger = LogManager.getLogger(TheCollector.class.getName());
   public static final String ID = "TheCollector";
   private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings("TheCollector");
   public static final String NAME;
   public static final String[] MOVES;
   public static final String[] DIALOG;
   public static final int HP = 282;
   public static final int A_2_HP = 300;
   private static final int FIREBALL_DMG = 18;
   private static final int STR_AMT = 3;
   private static final int BLOCK_AMT = 15;
   private static final int A_2_FIREBALL_DMG = 21;
   private static final int A_2_STR_AMT = 4;
   private static final int A_2_BLOCK_AMT = 18;
   private int rakeDmg;
   private int strAmt;
   private int blockAmt;
   private int megaDebuffAmt;
   private static final int MEGA_DEBUFF_AMT = 3;
   private int turnsTaken = 0;
   private float spawnX = -100.0F;
   private float fireTimer = 0.0F;
   private static final float FIRE_TIME = 0.07F;
   private boolean ultUsed = false;
   private boolean initialSpawn = true;
   private HashMap<Integer, AbstractMonster> enemySlots = new HashMap<>();
   private static final byte SPAWN = 1;
   private static final byte FIREBALL = 2;
   private static final byte BUFF = 3;
   private static final byte MEGA_DEBUFF = 4;
   private static final byte REVIVE = 5;

   public TheCollector() {
      super(NAME, "TheCollector", 282, 15.0F, -40.0F, 300.0F, 390.0F, null, 60.0F, 135.0F);
      this.dialogX = -90.0F * Settings.scale;
      this.dialogY = 10.0F * Settings.scale;
      this.type = AbstractMonster.EnemyType.BOSS;
      if (AbstractDungeon.ascensionLevel >= 9) {
         this.setHp(300);
         this.blockAmt = 18;
      } else {
         this.setHp(282);
         this.blockAmt = 15;
      }

      if (AbstractDungeon.ascensionLevel >= 19) {
         this.rakeDmg = 21;
         this.strAmt = 5;
         this.megaDebuffAmt = 5;
      } else if (AbstractDungeon.ascensionLevel >= 4) {
         this.rakeDmg = 21;
         this.strAmt = 4;
         this.megaDebuffAmt = 3;
      } else {
         this.rakeDmg = 18;
         this.strAmt = 3;
         this.megaDebuffAmt = 3;
      }

      this.damage.add(new DamageInfo(this, this.rakeDmg));
      this.loadAnimation("images/monsters/theCity/collector/skeleton.atlas", "images/monsters/theCity/collector/skeleton.json", 1.0F);
      AnimationState.TrackEntry e = this.state.setAnimation(0, "idle", true);
      e.setTime(e.getEndTime() * MathUtils.random());
   }

   @Override
   public void usePreBattleAction() {
      CardCrawlGame.music.unsilenceBGM();
      AbstractDungeon.scene.fadeOutAmbiance();
      AbstractDungeon.getCurrRoom().playBgmInstantly("BOSS_CITY");
      UnlockTracker.markBossAsSeen("COLLECTOR");
   }

   @Override
   public void takeTurn() {
      switch (this.nextMove) {
         case 1:
            for (int i = 1; i < 3; i++) {
               AbstractMonster mxx = new TorchHead(this.spawnX + -185.0F * i, MathUtils.random(-5.0F, 25.0F));
               AbstractDungeon.actionManager.addToBottom(new SFXAction("MONSTER_COLLECTOR_SUMMON"));
               AbstractDungeon.actionManager.addToBottom(new SpawnMonsterAction(mxx, true));
               this.enemySlots.put(i, mxx);
            }

            this.initialSpawn = false;
            break;
         case 2:
            AbstractDungeon.actionManager.addToBottom(new DamageAction(AbstractDungeon.player, this.damage.get(0), AbstractGameAction.AttackEffect.FIRE));
            break;
         case 3:
            if (AbstractDungeon.ascensionLevel >= 19) {
               AbstractDungeon.actionManager.addToBottom(new GainBlockAction(this, this, this.blockAmt + 5));
            } else {
               AbstractDungeon.actionManager.addToBottom(new GainBlockAction(this, this, this.blockAmt));
            }

            for (AbstractMonster mx : AbstractDungeon.getCurrRoom().monsters.monsters) {
               if (!mx.isDead && !mx.isDying && !mx.isEscaping) {
                  AbstractDungeon.actionManager.addToBottom(new ApplyPowerAction(mx, this, new StrengthPower(mx, this.strAmt), this.strAmt));
               }
            }
            break;
         case 4:
            AbstractDungeon.actionManager.addToBottom(new TalkAction(this, DIALOG[0]));
            AbstractDungeon.actionManager.addToBottom(new SFXAction("MONSTER_COLLECTOR_DEBUFF"));
            AbstractDungeon.actionManager
               .addToBottom(new VFXAction(new CollectorCurseEffect(AbstractDungeon.player.hb.cX, AbstractDungeon.player.hb.cY), 2.0F));
            AbstractDungeon.actionManager
               .addToBottom(
                  new ApplyPowerAction(AbstractDungeon.player, this, new WeakPower(AbstractDungeon.player, this.megaDebuffAmt, true), this.megaDebuffAmt)
               );
            AbstractDungeon.actionManager
               .addToBottom(
                  new ApplyPowerAction(AbstractDungeon.player, this, new VulnerablePower(AbstractDungeon.player, this.megaDebuffAmt, true), this.megaDebuffAmt)
               );
            AbstractDungeon.actionManager
               .addToBottom(
                  new ApplyPowerAction(AbstractDungeon.player, this, new FrailPower(AbstractDungeon.player, this.megaDebuffAmt, true), this.megaDebuffAmt)
               );
            this.ultUsed = true;
            break;
         case 5:
            for (Entry<Integer, AbstractMonster> m : this.enemySlots.entrySet()) {
               if (m.getValue().isDying) {
                  AbstractMonster newMonster = new TorchHead(this.spawnX + -185.0F * m.getKey().intValue(), MathUtils.random(-5.0F, 25.0F));
                  int key = m.getKey();
                  this.enemySlots.put(key, newMonster);
                  AbstractDungeon.actionManager.addToBottom(new SpawnMonsterAction(newMonster, true));
               }
            }
            break;
         default:
            logger.info("ERROR: Default Take Turn was called on " + this.name);
      }

      this.turnsTaken++;
      AbstractDungeon.actionManager.addToBottom(new RollMoveAction(this));
   }

   @Override
   protected void getMove(int num) {
      if (this.initialSpawn) {
         this.setMove((byte)1, AbstractMonster.Intent.UNKNOWN);
      } else if (this.turnsTaken >= 3 && !this.ultUsed) {
         this.setMove((byte)4, AbstractMonster.Intent.STRONG_DEBUFF);
      } else if (num <= 25 && this.isMinionDead() && !this.lastMove((byte)5)) {
         this.setMove((byte)5, AbstractMonster.Intent.UNKNOWN);
      } else if (num <= 70 && !this.lastTwoMoves((byte)2)) {
         this.setMove((byte)2, AbstractMonster.Intent.ATTACK, this.damage.get(0).base);
      } else {
         if (!this.lastMove((byte)3)) {
            this.setMove((byte)3, AbstractMonster.Intent.DEFEND_BUFF);
         } else {
            this.setMove((byte)2, AbstractMonster.Intent.ATTACK, this.damage.get(0).base);
         }
      }
   }

   private boolean isMinionDead() {
      for (Entry<Integer, AbstractMonster> m : this.enemySlots.entrySet()) {
         if (m.getValue().isDying) {
            return true;
         }
      }

      return false;
   }

   @Override
   public void update() {
      super.update();
      if (!this.isDying) {
         this.fireTimer = this.fireTimer - Gdx.graphics.getDeltaTime();
         if (this.fireTimer < 0.0F) {
            this.fireTimer = 0.07F;
            AbstractDungeon.effectList
               .add(
                  new GlowyFireEyesEffect(
                     this.skeleton.getX() + this.skeleton.findBone("lefteyefireslot").getX(),
                     this.skeleton.getY() + this.skeleton.findBone("lefteyefireslot").getY() + 140.0F * Settings.scale
                  )
               );
            AbstractDungeon.effectList
               .add(
                  new GlowyFireEyesEffect(
                     this.skeleton.getX() + this.skeleton.findBone("righteyefireslot").getX(),
                     this.skeleton.getY() + this.skeleton.findBone("righteyefireslot").getY() + 140.0F * Settings.scale
                  )
               );
            AbstractDungeon.effectList
               .add(
                  new StaffFireEffect(
                     this.skeleton.getX() + this.skeleton.findBone("fireslot").getX() - 120.0F * Settings.scale,
                     this.skeleton.getY() + this.skeleton.findBone("fireslot").getY() + 390.0F * Settings.scale
                  )
               );
         }
      }
   }

   @Override
   public void die() {
      this.useFastShakeAnimation(5.0F);
      CardCrawlGame.screenShake.rumble(4.0F);
      this.deathTimer++;
      super.die();
      this.onBossVictoryLogic();

      for (AbstractMonster m : AbstractDungeon.getCurrRoom().monsters.monsters) {
         if (!m.isDead && !m.isDying) {
            AbstractDungeon.actionManager.addToTop(new HideHealthBarAction(m));
            AbstractDungeon.actionManager.addToTop(new SuicideAction(m));
            AbstractDungeon.actionManager.addToTop(new VFXAction(m, new InflameEffect(m), 0.2F));
         }
      }

      UnlockTracker.hardUnlockOverride("COLLECTOR");
      UnlockTracker.unlockAchievement("COLLECTOR");
   }

   static {
      NAME = monsterStrings.NAME;
      MOVES = monsterStrings.MOVES;
      DIALOG = monsterStrings.DIALOG;
   }
}
