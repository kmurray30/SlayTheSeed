package com.badlogic.gdx.assets.loaders.resolvers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.files.FileHandle;

public class ResolutionFileResolver implements FileHandleResolver {
   protected final FileHandleResolver baseResolver;
   protected final ResolutionFileResolver.Resolution[] descriptors;

   public ResolutionFileResolver(FileHandleResolver baseResolver, ResolutionFileResolver.Resolution... descriptors) {
      if (descriptors.length == 0) {
         throw new IllegalArgumentException("At least one Resolution needs to be supplied.");
      } else {
         this.baseResolver = baseResolver;
         this.descriptors = descriptors;
      }
   }

   @Override
   public FileHandle resolve(String fileName) {
      ResolutionFileResolver.Resolution bestResolution = choose(this.descriptors);
      FileHandle originalHandle = new FileHandle(fileName);
      FileHandle handle = this.baseResolver.resolve(this.resolve(originalHandle, bestResolution.folder));
      if (!handle.exists()) {
         handle = this.baseResolver.resolve(fileName);
      }

      return handle;
   }

   protected String resolve(FileHandle originalHandle, String suffix) {
      String parentString = "";
      FileHandle parent = originalHandle.parent();
      if (parent != null && !parent.name().equals("")) {
         parentString = parent + "/";
      }

      return parentString + suffix + "/" + originalHandle.name();
   }

   public static ResolutionFileResolver.Resolution choose(ResolutionFileResolver.Resolution... descriptors) {
      int w = Gdx.graphics.getWidth();
      int h = Gdx.graphics.getHeight();
      ResolutionFileResolver.Resolution best = descriptors[0];
      if (w < h) {
         int i = 0;

         for (int n = descriptors.length; i < n; i++) {
            ResolutionFileResolver.Resolution other = descriptors[i];
            if (w >= other.portraitWidth
               && other.portraitWidth >= best.portraitWidth
               && h >= other.portraitHeight
               && other.portraitHeight >= best.portraitHeight) {
               best = descriptors[i];
            }
         }
      } else {
         int i = 0;

         for (int nx = descriptors.length; i < nx; i++) {
            ResolutionFileResolver.Resolution other = descriptors[i];
            if (w >= other.portraitHeight
               && other.portraitHeight >= best.portraitHeight
               && h >= other.portraitWidth
               && other.portraitWidth >= best.portraitWidth) {
               best = descriptors[i];
            }
         }
      }

      return best;
   }

   public static class Resolution {
      public final int portraitWidth;
      public final int portraitHeight;
      public final String folder;

      public Resolution(int portraitWidth, int portraitHeight, String folder) {
         this.portraitWidth = portraitWidth;
         this.portraitHeight = portraitHeight;
         this.folder = folder;
      }
   }
}
