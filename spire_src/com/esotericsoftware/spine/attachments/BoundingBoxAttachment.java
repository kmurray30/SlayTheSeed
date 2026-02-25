package com.esotericsoftware.spine.attachments;

import com.badlogic.gdx.graphics.Color;
import com.esotericsoftware.spine.Slot;

public class BoundingBoxAttachment extends VertexAttachment {
   final Color color = new Color(0.38F, 0.94F, 0.0F, 1.0F);

   public BoundingBoxAttachment(String name) {
      super(name);
   }

   @Override
   public void computeWorldVertices(Slot slot, float[] worldVertices) {
      this.computeWorldVertices(slot, 0, this.worldVerticesLength, worldVertices, 0);
   }

   public Color getColor() {
      return this.color;
   }
}
