package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;

public class StaffFireEffect extends AbstractGameEffect {
   private Texture img;
   private float x;
   private float y;
   private float vX;
   private float vY;
   private static final int W = 128;
   private boolean flippedX = MathUtils.randomBoolean();
   private static final float DUR = 0.7F;

   public StaffFireEffect(float x, float y) {
      this.img = this.getImg();
      this.x = x;
      this.y = y;
      this.vX = MathUtils.random(-50.0F, 50.0F) * Settings.scale;
      this.vY = MathUtils.random(20.0F, 140.0F) * Settings.scale;
      this.duration = 0.7F;
      this.color = Color.CHARTREUSE.cpy();
      this.color.a = 0.0F;
      this.scale = MathUtils.random(1.1F, 1.2F) * Settings.scale;
      this.renderBehind = true;
   }

   private Texture getImg() {
      return MathUtils.randomBoolean() ? ImageMaster.GHOST_ORB_1 : ImageMaster.GHOST_ORB_2;
   }

   @Override
   public void update() {
      this.x = this.x + this.vX * Gdx.graphics.getDeltaTime();
      this.y = this.y + this.vY * Gdx.graphics.getDeltaTime();
      this.duration = this.duration - Gdx.graphics.getDeltaTime();
      if (this.duration < 0.0F) {
         this.isDone = true;
      }

      this.color.a = this.duration * 0.75F;
   }

   @Override
   public void render(SpriteBatch sb) {
      sb.setBlendFunction(770, 1);
      sb.setColor(this.color);
      sb.draw(
         this.img,
         this.x - 64.0F,
         this.y - 64.0F,
         64.0F,
         64.0F,
         128.0F,
         128.0F,
         this.scale * MathUtils.random(1.05F, 1.1F),
         this.scale,
         0.0F,
         0,
         0,
         128,
         128,
         this.flippedX,
         false
      );
      sb.setBlendFunction(770, 771);
   }

   @Override
   public void dispose() {
   }
}
