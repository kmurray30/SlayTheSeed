package com.megacrit.cardcrawl.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.MathHelper;

public class SpeechWord {
   private BitmapFont font;
   private DialogWord.WordEffect effect;
   private DialogWord.WordColor wColor;
   public String word;
   public int line = 0;
   private float x;
   private float y;
   private float target_x;
   private float target_y;
   private float offset_x;
   private float offset_y;
   private float timer = 0.0F;
   private Color color;
   private Color targetColor;
   private float scale = 1.0F;
   private float targetScale = 1.0F;
   private static final float BUMP_OFFSET = 20.0F * Settings.scale;
   private static GlyphLayout gl;
   private static final float COLOR_LERP_SPEED = 8.0F;
   private static final float SHAKE_AMT = 2.0F * Settings.scale;
   private static final float DIALOG_FADE_Y = 50.0F * Settings.scale;
   private static final float WAVY_DIST = 3.0F * Settings.scale;
   private static final float SHAKE_INTERVAL = 0.02F;

   public SpeechWord(
      BitmapFont font, String word, DialogWord.AppearEffect a_effect, DialogWord.WordEffect effect, DialogWord.WordColor wColor, float x, float y, int line
   ) {
      if (gl == null) {
         gl = new GlyphLayout();
      }

      this.font = font;
      this.effect = effect;
      this.wColor = wColor;
      this.word = word;
      this.x = x;
      this.y = y;
      this.target_x = x;
      this.target_y = y;
      this.targetColor = this.getColor();
      this.line = line;
      this.color = new Color(this.targetColor.r, this.targetColor.g, this.targetColor.b, 0.0F);
      if (effect == DialogWord.WordEffect.WAVY) {
         this.timer = MathUtils.random((float) (Math.PI / 2));
      }

      switch (a_effect) {
         case FADE_IN:
         default:
            break;
         case GROW_IN:
            this.y = this.y - BUMP_OFFSET;
            this.scale = 0.0F;
            break;
         case BUMP_IN:
            this.y = this.y - BUMP_OFFSET;
      }
   }

   private Color getColor() {
      switch (this.wColor) {
         case RED:
            return new Color(1.0F, 0.2F, 0.3F, 1.0F);
         case GREEN:
            return new Color(0.3F, 1.0F, 0.1F, 1.0F);
         case BLUE:
            return Settings.BLUE_TEXT_COLOR.cpy();
         case GOLD:
            return Settings.GOLD_COLOR.cpy();
         case WHITE:
            return Settings.CREAM_COLOR.cpy();
         default:
            return Color.DARK_GRAY.cpy();
      }
   }

   public void update() {
      if (this.x != this.target_x) {
         this.x = MathUtils.lerp(this.x, this.target_x, Gdx.graphics.getDeltaTime() * 12.0F);
      }

      if (this.y != this.target_y) {
         this.y = MathUtils.lerp(this.y, this.target_y, Gdx.graphics.getDeltaTime() * 12.0F);
      }

      this.color = this.color.lerp(this.targetColor, Gdx.graphics.getDeltaTime() * 8.0F);
      if (this.scale != this.targetScale) {
         this.scale = MathHelper.scaleLerpSnap(this.scale, this.targetScale);
      }

      this.applyEffects();
   }

   private void applyEffects() {
      switch (this.effect) {
         case SHAKY:
            this.timer = this.timer - Gdx.graphics.getDeltaTime();
            if (this.timer < 0.0F) {
               this.offset_x = MathUtils.random(-SHAKE_AMT, SHAKE_AMT);
               this.offset_y = MathUtils.random(-SHAKE_AMT, SHAKE_AMT);
               this.timer = 0.02F;
            }
            break;
         case WAVY:
            this.timer = this.timer + Gdx.graphics.getDeltaTime() * 5.0F;
            break;
         case SLOW_WAVY:
            this.timer = this.timer + Gdx.graphics.getDeltaTime() * 2.5F;
      }
   }

   public void fadeOut() {
      this.targetColor = new Color(0.0F, 0.0F, 0.0F, 0.0F);
   }

   public void dialogFadeOut() {
      this.targetColor = new Color(0.0F, 0.0F, 0.0F, 0.0F);
      this.target_y = this.target_y - DIALOG_FADE_Y;
   }

   public void shiftY(float shiftAmount) {
      this.target_y += shiftAmount;
   }

   public void shiftX(float shiftAmount) {
      this.target_x += shiftAmount;
   }

   public void setX(float newX) {
      this.target_x = newX;
   }

   public static DialogWord.WordEffect identifyWordEffect(String word) {
      if (word.length() > 2) {
         if (word.charAt(0) == '@' && word.charAt(word.length() - 1) == '@') {
            return DialogWord.WordEffect.SHAKY;
         }

         if (word.charAt(0) == '~' && word.charAt(word.length() - 1) == '~') {
            return DialogWord.WordEffect.WAVY;
         }
      }

      return DialogWord.WordEffect.NONE;
   }

   public static DialogWord.WordColor identifyWordColor(String word) {
      if (word.charAt(0) == '#') {
         switch (word.charAt(1)) {
            case 'b':
               return DialogWord.WordColor.BLUE;
            case 'g':
               return DialogWord.WordColor.GREEN;
            case 'r':
               return DialogWord.WordColor.RED;
            case 'y':
               return DialogWord.WordColor.GOLD;
         }
      }

      return DialogWord.WordColor.DEFAULT;
   }

   public void render(SpriteBatch sb) {
      this.font.setColor(this.color);
      this.font.getData().setScale(this.scale);
      switch (this.effect) {
         case SHAKY:
            float charOffset2 = 0.0F;

            for (char c : this.word.toCharArray()) {
               String i = Character.toString(c);
               gl.setText(this.font, i);
               this.font
                  .draw(sb, i, this.x + MathUtils.random(-2.0F, 2.0F) * Settings.scale + charOffset2, this.y + MathUtils.random(-2.0F, 2.0F) * Settings.scale);
               charOffset2 += gl.width;
            }
            break;
         case WAVY:
            float charOffset = 0.0F;
            int j = 0;

            for (char c : this.word.toCharArray()) {
               String i = Character.toString(c);
               gl.setText(this.font, i);
               this.font
                  .draw(
                     sb,
                     i,
                     this.x + this.offset_x + charOffset,
                     this.y + MathUtils.cosDeg((float)((System.currentTimeMillis() + j * 70) / 4L % 360L)) * WAVY_DIST
                  );
               charOffset += gl.width;
               j++;
            }
            break;
         case SLOW_WAVY:
            float charOffset3 = 0.0F;
            int j3 = 0;

            for (char c : this.word.toCharArray()) {
               String i = Character.toString(c);
               gl.setText(this.font, i);
               this.font
                  .draw(
                     sb,
                     i,
                     this.x + this.offset_x + charOffset3,
                     this.y + MathUtils.cosDeg((float)((System.currentTimeMillis() + j3 * 70) / 4L % 360L)) * (WAVY_DIST / 2.0F)
                  );
               charOffset3 += gl.width;
               j3++;
            }
            break;
         default:
            this.font.draw(sb, this.word, this.x + this.offset_x, this.y + this.offset_y);
      }

      this.font.getData().setScale(1.0F);
   }

   public void render(SpriteBatch sb, float y2) {
      this.font.setColor(this.color);
      this.font.getData().setScale(this.scale);
      this.font.draw(sb, this.word, this.x + this.offset_x, this.y + this.offset_y + y2);
      this.font.getData().setScale(1.0F);
   }
}
