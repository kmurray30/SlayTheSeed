package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;

public class RewardGlowEffect extends AbstractGameEffect {
   private static final int W = 64;
   private static final float DURATION = 1.1F;
   private float scale;
   private float x;
   private float y;
   private float angle;

   public RewardGlowEffect(float x, float y) {
      this.x = x;
      this.y = y;
      this.color = Color.WHITE.cpy();
      this.duration = 1.1F;
      this.scale = Settings.scale;
   }

   @Override
   public void update() {
      this.duration = this.duration - Gdx.graphics.getDeltaTime();
      if (this.duration < 0.0F) {
         this.isDone = true;
      }

      this.scale = this.scale + Settings.scale * Gdx.graphics.getDeltaTime() / 20.0F;
      this.color.a = Interpolation.fade.apply(this.duration / 1.1F) / 12.0F;
   }

   @Override
   public void render(SpriteBatch sb) {
      sb.setColor(this.color);
      sb.setBlendFunction(770, 1);
      sb.draw(
         ImageMaster.REWARD_SCREEN_ITEM,
         this.x - 232.0F,
         this.y - 49.0F,
         232.0F,
         49.0F,
         464.0F,
         98.0F,
         Settings.xScale,
         this.scale + Settings.scale * 0.05F,
         0.0F,
         0,
         0,
         464,
         98,
         false,
         false
      );
      sb.setBlendFunction(770, 771);
   }

   @Override
   public void dispose() {
   }

   public void render(SpriteBatch sb, Color tint) {
      sb.setColor(this.color);
      sb.setBlendFunction(770, 1);
      sb.draw(
         ImageMaster.WHITE_SQUARE_IMG,
         this.x - 32.0F,
         this.y - 32.0F,
         32.0F,
         32.0F,
         64.0F,
         64.0F,
         this.scale * Settings.scale / 2.0F,
         this.scale * Settings.scale / 2.0F,
         this.angle,
         0,
         0,
         64,
         64,
         false,
         false
      );
      sb.setBlendFunction(770, 771);
   }
}
