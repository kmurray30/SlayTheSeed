package com.badlogic.gdx.scenes.scene2d.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;

public class TextTooltip extends Tooltip<Label> {
   public TextTooltip(String text, Skin skin) {
      this(text, TooltipManager.getInstance(), skin.get(TextTooltip.TextTooltipStyle.class));
   }

   public TextTooltip(String text, Skin skin, String styleName) {
      this(text, TooltipManager.getInstance(), skin.get(styleName, TextTooltip.TextTooltipStyle.class));
   }

   public TextTooltip(String text, TextTooltip.TextTooltipStyle style) {
      this(text, TooltipManager.getInstance(), style);
   }

   public TextTooltip(String text, TooltipManager manager, Skin skin) {
      this(text, manager, skin.get(TextTooltip.TextTooltipStyle.class));
   }

   public TextTooltip(String text, TooltipManager manager, Skin skin, String styleName) {
      this(text, manager, skin.get(styleName, TextTooltip.TextTooltipStyle.class));
   }

   public TextTooltip(String text, final TooltipManager manager, TextTooltip.TextTooltipStyle style) {
      super(null, manager);
      Label label = new Label(text, style.label);
      label.setWrap(true);
      this.container.setActor(label);
      this.container.width(new Value() {
         @Override
         public float get(Actor context) {
            return Math.min(manager.maxWidth, TextTooltip.this.container.getActor().getGlyphLayout().width);
         }
      });
      this.setStyle(style);
   }

   public void setStyle(TextTooltip.TextTooltipStyle style) {
      if (style == null) {
         throw new NullPointerException("style cannot be null");
      } else if (!(style instanceof TextTooltip.TextTooltipStyle)) {
         throw new IllegalArgumentException("style must be a TextTooltipStyle.");
      } else {
         this.container.getActor().setStyle(style.label);
         this.container.setBackground(style.background);
         this.container.maxWidth(style.wrapWidth);
      }
   }

   public static class TextTooltipStyle {
      public Label.LabelStyle label;
      public Drawable background;
      public float wrapWidth;

      public TextTooltipStyle() {
      }

      public TextTooltipStyle(Label.LabelStyle label, Drawable background) {
         this.label = label;
         this.background = background;
      }

      public TextTooltipStyle(TextTooltip.TextTooltipStyle style) {
         this.label = new Label.LabelStyle(style.label);
         this.background = style.background;
      }
   }
}
