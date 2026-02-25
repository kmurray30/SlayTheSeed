package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class RainingGoldEffect extends AbstractGameEffect {
   private int amount;
   private int min;
   private int max;
   private float staggerTimer = 0.0F;
   private boolean playerCentered;

   public RainingGoldEffect(int amount) {
      this.amount = amount;
      this.playerCentered = false;
      if (amount < 100) {
         this.min = 1;
         this.max = 7;
      } else {
         this.min = 3;
         this.max = 18;
      }
   }

   public RainingGoldEffect(int amount, boolean centerOnPlayer) {
      this(amount);
      this.playerCentered = centerOnPlayer;
   }

   @Override
   public void update() {
      this.staggerTimer = this.staggerTimer - Gdx.graphics.getDeltaTime();
      if (this.staggerTimer < 0.0F) {
         int goldToSpawn = MathUtils.random(this.min, this.max);
         if (goldToSpawn <= this.amount) {
            this.amount -= goldToSpawn;
         } else {
            goldToSpawn = this.amount;
            this.isDone = true;
         }

         for (int i = 0; i < goldToSpawn; i++) {
            AbstractDungeon.effectsQueue.add(new TouchPickupGold(this.playerCentered));
         }

         this.staggerTimer = MathUtils.random(0.3F);
      }
   }

   @Override
   public void render(SpriteBatch sb) {
   }

   @Override
   public void dispose() {
   }
}
