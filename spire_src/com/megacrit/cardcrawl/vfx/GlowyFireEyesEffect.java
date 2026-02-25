package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;

public class GlowyFireEyesEffect extends AbstractGameEffect {
   private Texture img;
   private float x;
   private float y;
   private float vX;
   private float vY;
   private static final int W = 128;
   private boolean flippedX = MathUtils.randomBoolean();
   private static final float DUR = 1.0F;

   public GlowyFireEyesEffect(float x, float y) {
      this.img = this.getImg();
      this.x = x;
      this.y = y;
      this.vX = MathUtils.random(-10.0F, 10.0F) * Settings.scale;
      this.vY = MathUtils.random(30.0F, 90.0F) * Settings.scale;
      this.duration = 1.0F;
      this.color = Color.CHARTREUSE.cpy();
      this.color.a = 0.0F;
      this.scale = MathUtils.random(0.45F, 0.45F) * Settings.scale;
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

      this.color.a = this.duration / 2.0F;
   }

   @Override
   public void render(SpriteBatch sb) {
      sb.setBlendFunction(770, 1);
      sb.setColor(this.color);
      sb.draw(this.img, this.x - 64.0F, this.y - 64.0F, 64.0F, 64.0F, 128.0F, 128.0F, this.scale, this.scale, 0.0F, 0, 0, 128, 128, this.flippedX, false);
      sb.setBlendFunction(770, 771);
   }

   @Override
   public void dispose() {
   }
}
