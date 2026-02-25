package com.megacrit.cardcrawl.helpers;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;

public abstract class AbstractDrawable implements Comparable<AbstractDrawable> {
   public int z;

   public abstract void render(SpriteBatch var1);

   public int compareTo(AbstractDrawable other) {
      return Integer.compare(this.z, other.z);
   }
}
