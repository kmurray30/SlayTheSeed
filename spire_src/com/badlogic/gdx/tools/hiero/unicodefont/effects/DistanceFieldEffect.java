package com.badlogic.gdx.tools.hiero.unicodefont.effects;

import com.badlogic.gdx.tools.distancefield.DistanceFieldGenerator;
import com.badlogic.gdx.tools.hiero.unicodefont.Glyph;
import com.badlogic.gdx.tools.hiero.unicodefont.UnicodeFont;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class DistanceFieldEffect implements ConfigurableEffect {
   private Color color = Color.WHITE;
   private int scale = 1;
   private float spread = 1.0F;

   private void drawGlyph(BufferedImage image, Glyph glyph) {
      Graphics2D inputG = (Graphics2D)image.getGraphics();
      inputG.setTransform(AffineTransform.getScaleInstance(this.scale, this.scale));
      inputG.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
      inputG.setColor(Color.WHITE);
      inputG.fill(glyph.getShape());
   }

   @Override
   public void draw(BufferedImage image, Graphics2D g, UnicodeFont unicodeFont, Glyph glyph) {
      BufferedImage input = new BufferedImage(this.scale * glyph.getWidth(), this.scale * glyph.getHeight(), 12);
      this.drawGlyph(input, glyph);
      DistanceFieldGenerator generator = new DistanceFieldGenerator();
      generator.setColor(this.color);
      generator.setDownscale(this.scale);
      generator.setSpread(this.scale * this.spread);
      BufferedImage distanceField = generator.generateDistanceField(input);
      g.drawImage(distanceField, new AffineTransform(), null);
   }

   @Override
   public String toString() {
      return "Distance field";
   }

   @Override
   public List getValues() {
      List values = new ArrayList();
      values.add(EffectUtil.colorValue("Color", this.color));
      values.add(
         EffectUtil.intValue(
            "Scale",
            this.scale,
            "The distance field is computed from an image larger than the output glyph by this factor. Set this to a higher value for more accuracy, but slower font generation."
         )
      );
      values.add(
         EffectUtil.floatValue(
            "Spread",
            this.spread,
            1.0F,
            Float.MAX_VALUE,
            "The maximum distance from edges where the effect of the distance field is seen. Set this to about half the width of lines in your output font."
         )
      );
      return values;
   }

   @Override
   public void setValues(List values) {
      for (ConfigurableEffect.Value value : values) {
         if ("Color".equals(value.getName())) {
            this.color = (Color)value.getObject();
         } else if ("Scale".equals(value.getName())) {
            this.scale = Math.max(1, (Integer)value.getObject());
         } else if ("Spread".equals(value.getName())) {
            this.spread = Math.max(0.0F, (Float)value.getObject());
         }
      }
   }
}
