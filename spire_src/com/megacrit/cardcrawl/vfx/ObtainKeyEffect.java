package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;

public class ObtainKeyEffect extends AbstractGameEffect {
   private Texture img;
   private float x;
   private float y;
   private ObtainKeyEffect.KeyColor keyColor = null;

   public ObtainKeyEffect(ObtainKeyEffect.KeyColor keyColor) {
      this.keyColor = keyColor;
      switch (keyColor) {
         case RED:
            this.img = ImageMaster.RUBY_KEY;
            break;
         case GREEN:
            this.img = ImageMaster.EMERALD_KEY;
            break;
         case BLUE:
            this.img = ImageMaster.SAPPHIRE_KEY;
      }

      this.duration = 0.33F;
      this.startingDuration = 0.33F;
      this.x = -32.0F + 46.0F * Settings.scale;
      this.y = Settings.HEIGHT - 32 - 35.0F * Settings.scale;
      this.color = Color.WHITE.cpy();
      this.color.a = 0.0F;
      this.rotation = 180.0F;
   }

   @Override
   public void update() {
      this.duration = this.duration - Gdx.graphics.getDeltaTime();
      if (this.duration < 0.0F) {
         this.isDone = true;
         this.duration = 0.0F;
         this.color.a = 0.0F;
         CardCrawlGame.sound.playA("KEY_OBTAIN", -0.2F);
         switch (this.keyColor) {
            case RED:
               this.img = ImageMaster.RUBY_KEY;
               Settings.hasRubyKey = true;
               break;
            case GREEN:
               this.img = ImageMaster.EMERALD_KEY;
               Settings.hasEmeraldKey = true;
               break;
            case BLUE:
               this.img = ImageMaster.SAPPHIRE_KEY;
               Settings.hasSapphireKey = true;
         }
      } else {
         this.color.a = Interpolation.fade.apply(1.0F, 0.0F, this.duration * 3.0F);
         this.scale = Interpolation.pow4In.apply(1.1F, 5.0F, this.duration * 3.0F) * Settings.scale;
         this.rotation = Interpolation.pow4In.apply(0.0F, 180.0F, this.duration * 3.0F);
      }
   }

   @Override
   public void render(SpriteBatch sb) {
      sb.setColor(this.color);
      sb.draw(this.img, this.x, this.y, 32.0F, 32.0F, 64.0F, 64.0F, this.scale, this.scale, this.rotation, 0, 0, 64, 64, false, false);
      sb.setColor(new Color(1.0F, 1.0F, 1.0F, this.duration * 3.0F));
      sb.setBlendFunction(770, 1);
      sb.draw(this.img, this.x, this.y, 32.0F, 32.0F, 64.0F, 64.0F, this.scale, this.scale, this.rotation, 0, 0, 64, 64, false, false);
      sb.setBlendFunction(770, 771);
   }

   @Override
   public void dispose() {
   }

   public static enum KeyColor {
      RED,
      GREEN,
      BLUE;
   }
}
