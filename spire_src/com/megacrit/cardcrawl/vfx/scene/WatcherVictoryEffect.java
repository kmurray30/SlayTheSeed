package com.megacrit.cardcrawl.vfx.scene;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class WatcherVictoryEffect extends AbstractGameEffect {
   private float x;
   private float y;
   private static TextureAtlas.AtlasRegion img;

   public WatcherVictoryEffect() {
      this.renderBehind = true;
      img = ImageMaster.EYE_ANIM_0;
      this.x = Settings.WIDTH / 2.0F - img.packedWidth / 2.0F;
      this.y = Settings.HEIGHT / 2.0F - img.packedHeight / 2.0F;
      this.scale = 1.5F * Settings.scale;
      this.color = new Color(0.0F, 0.0F, 0.0F, 0.0F);
   }

   @Override
   public void update() {
      this.color.a = MathHelper.slowColorLerpSnap(this.color.a, 1.0F);
      this.duration = this.duration + Gdx.graphics.getDeltaTime();
      this.rotation = this.rotation + 5.0F * Gdx.graphics.getDeltaTime();
   }

   private void renderHelper(SpriteBatch sb, float offsetX, float offsetY, float rotation, Color color, float scaleMod) {
      sb.setColor(color);
      offsetX *= Settings.scale;
      offsetY *= Settings.scale;
      sb.draw(
         this.getImg(this.rotation + rotation + offsetX / 100.0F),
         this.x,
         this.y,
         img.packedWidth / 2.0F - offsetX,
         img.packedHeight / 2.0F - offsetY,
         img.packedWidth,
         img.packedHeight,
         this.scale,
         this.scale * 2.0F,
         this.rotation + rotation
      );
   }

   private TextureAtlas.AtlasRegion getImg(float input) {
      input %= 10.0F;
      if (input < 0.5F) {
         return ImageMaster.EYE_ANIM_1;
      } else if (input < 1.2F) {
         return ImageMaster.EYE_ANIM_2;
      } else if (input < 2.0F) {
         return ImageMaster.EYE_ANIM_3;
      } else if (input < 3.0F) {
         return ImageMaster.EYE_ANIM_4;
      } else if (input < 4.2F) {
         return ImageMaster.EYE_ANIM_5;
      } else if (input < 6.0F) {
         return ImageMaster.EYE_ANIM_6;
      } else if (input < 7.5F) {
         return ImageMaster.EYE_ANIM_5;
      } else if (input < 8.5F) {
         return ImageMaster.EYE_ANIM_4;
      } else {
         return input < 9.3F ? ImageMaster.EYE_ANIM_3 : ImageMaster.EYE_ANIM_2;
      }
   }

   @Override
   public void render(SpriteBatch sb) {
      sb.setBlendFunction(770, 1);
      float angle = 0.0F;

      for (int i = 0; i < 24; i++) {
         this.color.r = 0.9F;
         this.color.g = 0.46F + i * 0.01F;
         this.color.b = 0.3F + (12 - i) * 0.05F;
         this.renderHelper(sb, -760.0F, -760.0F, angle, this.color, 1.0F);
         this.renderHelper(sb, -630.0F, -630.0F, angle, this.color, 1.0F);
         this.renderHelper(sb, -510.0F, -510.0F, angle, this.color, 1.0F);
         this.renderHelper(sb, -400.0F, -400.0F, angle, this.color, 1.0F);
         this.renderHelper(sb, -300.0F, -300.0F, angle, this.color, 1.0F);
         this.renderHelper(sb, -220.0F, -220.0F, angle, this.color, 1.0F);
         this.renderHelper(sb, -170.0F, -170.0F, angle, this.color, 1.0F);
         this.renderHelper(sb, -130.0F, -130.0F, angle, this.color, 1.0F);
         this.renderHelper(sb, -100.0F, -100.0F, angle, this.color, 1.0F);
         this.renderHelper(sb, 760.0F, -760.0F, angle, this.color, 1.0F);
         this.renderHelper(sb, 630.0F, -630.0F, angle, this.color, 1.0F);
         this.renderHelper(sb, 510.0F, -510.0F, angle, this.color, 1.0F);
         this.renderHelper(sb, 400.0F, -400.0F, angle, this.color, 1.0F);
         this.renderHelper(sb, 300.0F, -300.0F, angle, this.color, 1.0F);
         this.renderHelper(sb, 220.0F, -220.0F, angle, this.color, 1.0F);
         this.renderHelper(sb, 170.0F, -170.0F, angle, this.color, 1.0F);
         this.renderHelper(sb, 130.0F, -130.0F, angle, this.color, 1.0F);
         this.renderHelper(sb, 100.0F, -100.0F, angle, this.color, 1.0F);
         angle += 15.0F;
      }

      sb.setBlendFunction(770, 771);
   }

   @Override
   public void dispose() {
   }
}
