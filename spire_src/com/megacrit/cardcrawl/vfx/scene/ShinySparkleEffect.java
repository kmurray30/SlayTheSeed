package com.megacrit.cardcrawl.vfx.scene;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class ShinySparkleEffect extends AbstractGameEffect {
   private float x;
   private float y;
   private float vX;
   private float vY;
   private float aV;
   private static final int W = 32;

   public ShinySparkleEffect() {
      this.startingDuration = MathUtils.random(1.0F, 2.0F);
      this.duration = this.startingDuration;
      this.scale = Settings.scale * MathUtils.random(0.4F, 1.0F);
      this.x = MathUtils.random(0, Settings.WIDTH);
      this.y = MathUtils.random(-100.0F, 550.0F) * Settings.scale + AbstractDungeon.floorY;
      this.vX = MathUtils.random(-24.0F, 24.0F) * Settings.scale;
      this.vY = MathUtils.random(-24.0F, 36.0F) * Settings.scale;
      float colorTmp = MathUtils.random(0.6F, 1.0F);
      this.color = new Color(colorTmp - 0.3F, colorTmp, colorTmp + MathUtils.random(-0.1F, 0.1F), 0.0F);
      this.color.a = 0.0F;
      this.aV = MathUtils.random(-120.0F, 120.0F);
   }

   @Override
   public void update() {
      this.rotation = this.rotation + this.aV * Gdx.graphics.getDeltaTime();
      this.duration = this.duration - Gdx.graphics.getDeltaTime();
      this.x = this.x + this.vX * Gdx.graphics.getDeltaTime();
      this.y = this.y + this.vY * Gdx.graphics.getDeltaTime();
      if (this.duration < 0.0F) {
         this.isDone = true;
      }

      if (this.duration > this.startingDuration / 2.0F) {
         float tmp = this.duration - this.startingDuration / 2.0F;
         this.color.a = Interpolation.fade.apply(0.0F, 1.0F, this.startingDuration / 2.0F - tmp) / 4.0F;
      } else {
         this.color.a = Interpolation.fade.apply(0.0F, 1.0F, this.duration / (this.startingDuration / 2.0F)) / 4.0F;
      }
   }

   @Override
   public void render(SpriteBatch sb) {
      sb.setColor(this.color);
      sb.draw(
         ImageMaster.WOBBLY_ORB_VFX,
         this.x - 16.0F,
         this.y - 16.0F,
         16.0F,
         16.0F,
         32.0F,
         32.0F,
         this.scale * MathUtils.random(1.0F, 1.2F),
         this.scale / 4.0F,
         0.0F,
         0,
         0,
         32,
         32,
         false,
         false
      );
      sb.draw(
         ImageMaster.WOBBLY_ORB_VFX,
         this.x - 16.0F,
         this.y - 16.0F,
         16.0F,
         16.0F,
         32.0F,
         32.0F,
         this.scale * MathUtils.random(1.0F, 1.5F),
         this.scale / 4.0F,
         90.0F,
         0,
         0,
         32,
         32,
         false,
         false
      );
   }

   @Override
   public void dispose() {
   }
}
