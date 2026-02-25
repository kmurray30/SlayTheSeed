package com.badlogic.gdx.backends.lwjgl;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import java.io.File;

public final class LwjglFileHandle extends FileHandle {
   public LwjglFileHandle(String fileName, Files.FileType type) {
      super(fileName, type);
   }

   public LwjglFileHandle(File file, Files.FileType type) {
      super(file, type);
   }

   @Override
   public FileHandle child(String name) {
      return this.file.getPath().length() == 0 ? new LwjglFileHandle(new File(name), this.type) : new LwjglFileHandle(new File(this.file, name), this.type);
   }

   @Override
   public FileHandle sibling(String name) {
      if (this.file.getPath().length() == 0) {
         throw new GdxRuntimeException("Cannot get the sibling of the root.");
      } else {
         return new LwjglFileHandle(new File(this.file.getParent(), name), this.type);
      }
   }

   @Override
   public FileHandle parent() {
      File parent = this.file.getParentFile();
      if (parent == null) {
         if (this.type == Files.FileType.Absolute) {
            parent = new File("/");
         } else {
            parent = new File("");
         }
      }

      return new LwjglFileHandle(parent, this.type);
   }

   @Override
   public File file() {
      if (this.type == Files.FileType.External) {
         return new File(LwjglFiles.externalPath, this.file.getPath());
      } else {
         return this.type == Files.FileType.Local ? new File(LwjglFiles.localPath, this.file.getPath()) : this.file;
      }
   }
}
