package com.badlogic.gdx.tools.hiero.unicodefont.effects;

import com.badlogic.gdx.tools.hiero.unicodefont.Glyph;
import com.badlogic.gdx.tools.hiero.unicodefont.UnicodeFont;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class ColorEffect implements ConfigurableEffect {
   private Color color = Color.white;

   public ColorEffect() {
   }

   public ColorEffect(Color color) {
      this.color = color;
   }

   @Override
   public void draw(BufferedImage image, Graphics2D g, UnicodeFont unicodeFont, Glyph glyph) {
      g.setColor(this.color);

      try {
         g.fill(glyph.getShape());
      } catch (Throwable var6) {
      }
   }

   public Color getColor() {
      return this.color;
   }

   public void setColor(Color color) {
      if (color == null) {
         throw new IllegalArgumentException("color cannot be null.");
      } else {
         this.color = color;
      }
   }

   @Override
   public String toString() {
      return "Color";
   }

   @Override
   public List getValues() {
      List values = new ArrayList();
      values.add(EffectUtil.colorValue("Color", this.color));
      return values;
   }

   @Override
   public void setValues(List values) {
      for (ConfigurableEffect.Value value : values) {
         if (value.getName().equals("Color")) {
            this.setColor((Color)value.getObject());
         }
      }
   }
}
