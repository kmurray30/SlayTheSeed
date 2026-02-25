package com.badlogic.gdx.graphics.g3d.attributes;

import com.badlogic.gdx.graphics.Cubemap;
import com.badlogic.gdx.graphics.g3d.Attribute;
import com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor;
import com.badlogic.gdx.utils.GdxRuntimeException;

public class CubemapAttribute extends Attribute {
   public static final String EnvironmentMapAlias = "environmentCubemap";
   public static final long EnvironmentMap = register("environmentCubemap");
   protected static long Mask = EnvironmentMap;
   public final TextureDescriptor<Cubemap> textureDescription;

   public static final boolean is(long mask) {
      return (mask & Mask) != 0L;
   }

   public CubemapAttribute(long type) {
      super(type);
      if (!is(type)) {
         throw new GdxRuntimeException("Invalid type specified");
      } else {
         this.textureDescription = new TextureDescriptor<>();
      }
   }

   public <T extends Cubemap> CubemapAttribute(long type, TextureDescriptor<T> textureDescription) {
      this(type);
      this.textureDescription.set(textureDescription);
   }

   public CubemapAttribute(long type, Cubemap texture) {
      this(type);
      this.textureDescription.texture = texture;
   }

   public CubemapAttribute(CubemapAttribute copyFrom) {
      this(copyFrom.type, copyFrom.textureDescription);
   }

   @Override
   public Attribute copy() {
      return new CubemapAttribute(this);
   }

   @Override
   public int hashCode() {
      int result = super.hashCode();
      return 967 * result + this.textureDescription.hashCode();
   }

   public int compareTo(Attribute o) {
      return this.type != o.type ? (int)(this.type - o.type) : this.textureDescription.compareTo(((CubemapAttribute)o).textureDescription);
   }
}
