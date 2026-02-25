package com.badlogic.gdx.graphics;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.glutils.ETC1TextureData;
import com.badlogic.gdx.graphics.glutils.FileTextureData;
import com.badlogic.gdx.graphics.glutils.KTXTextureData;

public interface TextureData {
   TextureData.TextureDataType getType();

   boolean isPrepared();

   void prepare();

   Pixmap consumePixmap();

   boolean disposePixmap();

   void consumeCustomData(int var1);

   int getWidth();

   int getHeight();

   Pixmap.Format getFormat();

   boolean useMipMaps();

   boolean isManaged();

   public static class Factory {
      public static TextureData loadFromFile(FileHandle file, boolean useMipMaps) {
         return loadFromFile(file, null, useMipMaps);
      }

      public static TextureData loadFromFile(FileHandle file, Pixmap.Format format, boolean useMipMaps) {
         if (file == null) {
            return null;
         } else if (file.name().endsWith(".cim")) {
            return new FileTextureData(file, PixmapIO.readCIM(file), format, useMipMaps);
         } else if (file.name().endsWith(".etc1")) {
            return new ETC1TextureData(file, useMipMaps);
         } else {
            return (TextureData)(!file.name().endsWith(".ktx") && !file.name().endsWith(".zktx")
               ? new FileTextureData(file, new Pixmap(file), format, useMipMaps)
               : new KTXTextureData(file, useMipMaps));
         }
      }
   }

   public static enum TextureDataType {
      Pixmap,
      Custom;
   }
}
