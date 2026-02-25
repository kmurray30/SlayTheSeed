package com.badlogic.gdx.graphics.g2d;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.SynchronousAssetLoader;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.EarClippingTriangulator;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.StreamUtils;
import java.io.BufferedReader;
import java.io.IOException;

public class PolygonRegionLoader extends SynchronousAssetLoader<PolygonRegion, PolygonRegionLoader.PolygonRegionParameters> {
   private PolygonRegionLoader.PolygonRegionParameters defaultParameters = new PolygonRegionLoader.PolygonRegionParameters();
   private EarClippingTriangulator triangulator = new EarClippingTriangulator();

   public PolygonRegionLoader() {
      this(new InternalFileHandleResolver());
   }

   public PolygonRegionLoader(FileHandleResolver resolver) {
      super(resolver);
   }

   public PolygonRegion load(AssetManager manager, String fileName, FileHandle file, PolygonRegionLoader.PolygonRegionParameters parameter) {
      Texture texture = manager.get(manager.getDependencies(fileName).first());
      return this.load(new TextureRegion(texture), file);
   }

   public Array<AssetDescriptor> getDependencies(String fileName, FileHandle file, PolygonRegionLoader.PolygonRegionParameters params) {
      if (params == null) {
         params = this.defaultParameters;
      }

      String image = null;

      try {
         BufferedReader reader = file.reader(params.readerBuffer);

         for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            if (line.startsWith(params.texturePrefix)) {
               image = line.substring(params.texturePrefix.length());
               break;
            }
         }

         reader.close();
      } catch (IOException var10) {
         throw new GdxRuntimeException("Error reading " + fileName, var10);
      }

      if (image == null && params.textureExtensions != null) {
         for (String extension : params.textureExtensions) {
            FileHandle sibling = file.sibling(file.nameWithoutExtension().concat("." + extension));
            if (sibling.exists()) {
               image = sibling.name();
            }
         }
      }

      if (image != null) {
         Array<AssetDescriptor> deps = new Array<>(1);
         deps.add(new AssetDescriptor<>(file.sibling(image), Texture.class));
         return deps;
      } else {
         return null;
      }
   }

   public PolygonRegion load(TextureRegion textureRegion, FileHandle file) {
      BufferedReader reader = file.reader(256);

      try {
         String line;
         do {
            line = reader.readLine();
            if (line == null) {
               throw new GdxRuntimeException("Polygon shape not found: " + file);
            }
         } while (!line.startsWith("s"));

         String[] polygonStrings = line.substring(1).trim().split(",");
         float[] vertices = new float[polygonStrings.length];
         int i = 0;

         for (int n = vertices.length; i < n; i++) {
            vertices[i] = Float.parseFloat(polygonStrings[i]);
         }

         return new PolygonRegion(textureRegion, vertices, this.triangulator.computeTriangles(vertices).toArray());
      } catch (IOException var12) {
         throw new GdxRuntimeException("Error reading polygon shape file: " + file, var12);
      } finally {
         StreamUtils.closeQuietly(reader);
      }
   }

   public static class PolygonRegionParameters extends AssetLoaderParameters<PolygonRegion> {
      public String texturePrefix = "i ";
      public int readerBuffer = 1024;
      public String[] textureExtensions = new String[]{"png", "PNG", "jpeg", "JPEG", "jpg", "JPG", "cim", "CIM", "etc1", "ETC1", "ktx", "KTX", "zktx", "ZKTX"};
   }
}
