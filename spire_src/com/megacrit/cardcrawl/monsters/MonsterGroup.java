package com.megacrit.cardcrawl.monsters;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.dungeons.TheCity;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.random.Random;
import com.megacrit.cardcrawl.ui.buttons.PeekButton;
import java.util.ArrayList;
import java.util.Collections;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MonsterGroup {
   private static final Logger logger = LogManager.getLogger(MonsterGroup.class.getName());
   public ArrayList<AbstractMonster> monsters = new ArrayList<>();
   public AbstractMonster hoveredMonster = null;

   public MonsterGroup(AbstractMonster[] input) {
      Collections.addAll(this.monsters, input);
   }

   public void addMonster(int newIndex, AbstractMonster m) {
      if (newIndex < 0) {
         newIndex = 0;
      }

      this.monsters.add(newIndex, m);
   }

   @Deprecated
   public void addMonster(AbstractMonster m) {
      this.monsters.add(m);
   }

   @Deprecated
   public void addSpawnedMonster(AbstractMonster m) {
      this.monsters.add(0, m);
   }

   public MonsterGroup(AbstractMonster m) {
      this(new AbstractMonster[]{m});
   }

   public void showIntent() {
      for (AbstractMonster m : this.monsters) {
         m.createIntent();
      }
   }

   public void init() {
      for (AbstractMonster m : this.monsters) {
         m.init();
      }
   }

   public void add(AbstractMonster m) {
      this.monsters.add(m);
   }

   public void usePreBattleAction() {
      if (!AbstractDungeon.loading_post_combat) {
         for (AbstractMonster m : this.monsters) {
            m.usePreBattleAction();
            m.useUniversalPreBattleAction();
         }
      }
   }

   public boolean areMonstersDead() {
      for (AbstractMonster m : this.monsters) {
         if (!m.isDead && !m.escaped) {
            return false;
         }
      }

      return true;
   }

   public boolean areMonstersBasicallyDead() {
      for (AbstractMonster m : this.monsters) {
         if (!m.isDying && !m.isEscaping) {
            return false;
         }
      }

      return true;
   }

   public void applyPreTurnLogic() {
      for (AbstractMonster m : this.monsters) {
         if (!m.isDying && !m.isEscaping) {
            if (!m.hasPower("Barricade")) {
               m.loseBlock();
            }

            m.applyStartOfTurnPowers();
         }
      }
   }

   public AbstractMonster getMonster(String id) {
      for (AbstractMonster m : this.monsters) {
         if (m.id.equals(id)) {
            return m;
         }
      }

      logger.info("MONSTER GROUP getMonster(): Could not find monster: " + id);
      return null;
   }

   public void queueMonsters() {
      for (AbstractMonster m : this.monsters) {
         if (!m.isDeadOrEscaped() || m.halfDead) {
            AbstractDungeon.actionManager.monsterQueue.add(new MonsterQueueItem(m));
         }
      }
   }

   public boolean haveMonstersEscaped() {
      for (AbstractMonster m : this.monsters) {
         if (!m.escaped) {
            return false;
         }
      }

      return true;
   }

   public boolean isMonsterEscaping() {
      for (AbstractMonster m : this.monsters) {
         if (m.nextMove == 99) {
            return true;
         }
      }

      return false;
   }

   public boolean hasMonsterEscaped() {
      for (AbstractMonster m : this.monsters) {
         if (m.isEscaping) {
            return true;
         }
      }

      return CardCrawlGame.dungeon instanceof TheCity;
   }

   public AbstractMonster getRandomMonster() {
      return this.getRandomMonster(null, false);
   }

   public AbstractMonster getRandomMonster(boolean aliveOnly) {
      return this.getRandomMonster(null, aliveOnly);
   }

   public AbstractMonster getRandomMonster(AbstractMonster exception, boolean aliveOnly, Random rng) {
      if (this.areMonstersBasicallyDead()) {
         return null;
      } else if (exception == null) {
         if (aliveOnly) {
            ArrayList<AbstractMonster> tmp = new ArrayList<>();

            for (AbstractMonster m : this.monsters) {
               if (!m.halfDead && !m.isDying && !m.isEscaping) {
                  tmp.add(m);
               }
            }

            return tmp.size() <= 0 ? null : tmp.get(rng.random(0, tmp.size() - 1));
         } else {
            return this.monsters.get(rng.random(0, this.monsters.size() - 1));
         }
      } else if (this.monsters.size() == 1) {
         return this.monsters.get(0);
      } else if (aliveOnly) {
         ArrayList<AbstractMonster> tmp = new ArrayList<>();

         for (AbstractMonster mx : this.monsters) {
            if (!mx.halfDead && !mx.isDying && !mx.isEscaping && !exception.equals(mx)) {
               tmp.add(mx);
            }
         }

         return tmp.size() == 0 ? null : tmp.get(rng.random(0, tmp.size() - 1));
      } else {
         ArrayList<AbstractMonster> tmp = new ArrayList<>();

         for (AbstractMonster mxx : this.monsters) {
            if (!exception.equals(mxx)) {
               tmp.add(mxx);
            }
         }

         return tmp.get(rng.random(0, tmp.size() - 1));
      }
   }

   public AbstractMonster getRandomMonster(AbstractMonster exception, boolean aliveOnly) {
      if (this.areMonstersBasicallyDead()) {
         return null;
      } else if (exception == null) {
         if (aliveOnly) {
            ArrayList<AbstractMonster> tmp = new ArrayList<>();

            for (AbstractMonster m : this.monsters) {
               if (!m.halfDead && !m.isDying && !m.isEscaping) {
                  tmp.add(m);
               }
            }

            return tmp.size() <= 0 ? null : tmp.get(MathUtils.random(0, tmp.size() - 1));
         } else {
            return this.monsters.get(MathUtils.random(0, this.monsters.size() - 1));
         }
      } else if (this.monsters.size() == 1) {
         return this.monsters.get(0);
      } else if (aliveOnly) {
         ArrayList<AbstractMonster> tmp = new ArrayList<>();

         for (AbstractMonster mx : this.monsters) {
            if (!mx.halfDead && !mx.isDying && !mx.isEscaping && !exception.equals(mx)) {
               tmp.add(mx);
            }
         }

         return tmp.size() == 0 ? null : tmp.get(MathUtils.random(0, tmp.size() - 1));
      } else {
         ArrayList<AbstractMonster> tmp = new ArrayList<>();

         for (AbstractMonster mxx : this.monsters) {
            if (!exception.equals(mxx)) {
               tmp.add(mxx);
            }
         }

         return tmp.get(MathUtils.random(0, tmp.size() - 1));
      }
   }

   public void update() {
      for (AbstractMonster m : this.monsters) {
         m.update();
      }

      if (AbstractDungeon.screen != AbstractDungeon.CurrentScreen.DEATH) {
         this.hoveredMonster = null;

         for (AbstractMonster m : this.monsters) {
            if (!m.isDying && !m.isEscaping) {
               m.hb.update();
               m.intentHb.update();
               m.healthHb.update();
               if ((m.hb.hovered || m.intentHb.hovered || m.healthHb.hovered) && !AbstractDungeon.player.isDraggingCard) {
                  this.hoveredMonster = m;
                  break;
               }
            }
         }

         if (this.hoveredMonster == null) {
            AbstractDungeon.player.hoverEnemyWaitTimer = -1.0F;
         }
      } else {
         this.hoveredMonster = null;
      }
   }

   public void updateAnimations() {
      for (AbstractMonster m : this.monsters) {
         m.updatePowers();
      }
   }

   public boolean shouldFlipVfx() {
      return AbstractDungeon.lastCombatMetricKey.equals("Shield and Spear") && this.monsters.get(1).isDying;
   }

   public void escape() {
      for (AbstractMonster m : this.monsters) {
         m.escape();
      }
   }

   public void unhover() {
      for (AbstractMonster m : this.monsters) {
         m.unhover();
      }
   }

   public void render(SpriteBatch sb) {
      if (this.hoveredMonster != null
         && !this.hoveredMonster.isDead
         && !this.hoveredMonster.escaped
         && AbstractDungeon.player.hoverEnemyWaitTimer < 0.0F
         && (!AbstractDungeon.isScreenUp || PeekButton.isPeeking)) {
         this.hoveredMonster.renderTip(sb);
      }

      for (AbstractMonster m : this.monsters) {
         m.render(sb);
      }
   }

   public void applyEndOfTurnPowers() {
      for (AbstractMonster m : this.monsters) {
         if (!m.isDying && !m.isEscaping) {
            m.applyEndOfTurnTriggers();
         }
      }

      for (AbstractPower p : AbstractDungeon.player.powers) {
         p.atEndOfRound();
      }

      for (AbstractMonster mx : this.monsters) {
         if (!mx.isDying && !mx.isEscaping) {
            for (AbstractPower p : mx.powers) {
               p.atEndOfRound();
            }
         }
      }
   }

   public void renderReticle(SpriteBatch sb) {
      for (AbstractMonster m : this.monsters) {
         if (!m.isDying && !m.isEscaping) {
            m.renderReticle(sb);
         }
      }
   }

   public ArrayList<String> getMonsterNames() {
      ArrayList<String> arr = new ArrayList<>();

      for (AbstractMonster m : this.monsters) {
         arr.add(m.id);
      }

      return arr;
   }
}
