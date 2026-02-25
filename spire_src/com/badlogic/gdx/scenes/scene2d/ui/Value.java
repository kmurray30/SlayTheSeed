package com.badlogic.gdx.scenes.scene2d.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.Layout;

public abstract class Value {
   public static final Value.Fixed zero = new Value.Fixed(0.0F);
   public static Value minWidth = new Value() {
      @Override
      public float get(Actor context) {
         if (context instanceof Layout) {
            return ((Layout)context).getMinWidth();
         } else {
            return context == null ? 0.0F : context.getWidth();
         }
      }
   };
   public static Value minHeight = new Value() {
      @Override
      public float get(Actor context) {
         if (context instanceof Layout) {
            return ((Layout)context).getMinHeight();
         } else {
            return context == null ? 0.0F : context.getHeight();
         }
      }
   };
   public static Value prefWidth = new Value() {
      @Override
      public float get(Actor context) {
         if (context instanceof Layout) {
            return ((Layout)context).getPrefWidth();
         } else {
            return context == null ? 0.0F : context.getWidth();
         }
      }
   };
   public static Value prefHeight = new Value() {
      @Override
      public float get(Actor context) {
         if (context instanceof Layout) {
            return ((Layout)context).getPrefHeight();
         } else {
            return context == null ? 0.0F : context.getHeight();
         }
      }
   };
   public static Value maxWidth = new Value() {
      @Override
      public float get(Actor context) {
         if (context instanceof Layout) {
            return ((Layout)context).getMaxWidth();
         } else {
            return context == null ? 0.0F : context.getWidth();
         }
      }
   };
   public static Value maxHeight = new Value() {
      @Override
      public float get(Actor context) {
         if (context instanceof Layout) {
            return ((Layout)context).getMaxHeight();
         } else {
            return context == null ? 0.0F : context.getHeight();
         }
      }
   };

   public abstract float get(Actor var1);

   public static Value percentWidth(final float percent) {
      return new Value() {
         @Override
         public float get(Actor actor) {
            return actor.getWidth() * percent;
         }
      };
   }

   public static Value percentHeight(final float percent) {
      return new Value() {
         @Override
         public float get(Actor actor) {
            return actor.getHeight() * percent;
         }
      };
   }

   public static Value percentWidth(final float percent, final Actor actor) {
      if (actor == null) {
         throw new IllegalArgumentException("actor cannot be null.");
      } else {
         return new Value() {
            @Override
            public float get(Actor context) {
               return actor.getWidth() * percent;
            }
         };
      }
   }

   public static Value percentHeight(final float percent, final Actor actor) {
      if (actor == null) {
         throw new IllegalArgumentException("actor cannot be null.");
      } else {
         return new Value() {
            @Override
            public float get(Actor context) {
               return actor.getHeight() * percent;
            }
         };
      }
   }

   public static class Fixed extends Value {
      private final float value;

      public Fixed(float value) {
         this.value = value;
      }

      @Override
      public float get(Actor context) {
         return this.value;
      }
   }
}
