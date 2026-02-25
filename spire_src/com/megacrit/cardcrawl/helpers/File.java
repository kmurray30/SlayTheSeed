package com.megacrit.cardcrawl.helpers;

import com.badlogic.gdx.Gdx;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class File {
   private static final Logger logger = LogManager.getLogger(File.class.getName());
   private String filepath;
   private byte[] data;

   public File(String filepath, String data) {
      this.filepath = filepath;
      this.data = data.getBytes(StandardCharsets.UTF_8);
   }

   public String getFilepath() {
      return this.filepath;
   }

   public void save() {
      int MAX_RETRIES = 5;
      String localStoragePath = Gdx.files.getLocalStoragePath();
      Path destination = FileSystems.getDefault().getPath(localStoragePath + this.filepath);
      Path backup = FileSystems.getDefault().getPath(localStoragePath + this.filepath + ".backUp");
      Path parent = destination.getParent();
      logger.debug("Attempting to save file=" + destination);
      if (Files.exists(parent)) {
         if (Files.exists(destination)) {
            copyAndValidate(destination, backup, 5);
            deleteFile(destination);
         }
      } else {
         try {
            Files.createDirectories(parent);
         } catch (IOException var7) {
            logger.info("Failed to create directory", (Throwable)var7);
         }
      }

      boolean success = writeAndValidate(destination, this.data, 5);
      if (success) {
         logger.debug("Successfully saved file=" + destination.toString());
      }
   }

   private static void copyAndValidate(Path source, Path target, int retry) {
      byte[] sourceData = new byte[0];

      try {
         sourceData = Files.readAllBytes(source);
         Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException var5) {
         if (retry <= 0) {
            logger.info("Failed to copy " + source.toString() + " to " + target.toString() + ", but the retry expired", (Throwable)var5);
            return;
         }

         logger.info("Failed to copy file=" + source.toString(), (Throwable)var5);
         sleep(300);
         copyAndValidate(source, target, retry - 1);
      }

      Exception err = validateWrite(target, sourceData);
      if (err != null) {
         if (retry <= 0) {
            logger.info("Failed to copy " + source.toString() + " to " + target.toString() + ", but the retry expired", (Throwable)err);
            return;
         }

         logger.info("Failed to copy file=" + source.toString(), (Throwable)err);
         sleep(300);
         copyAndValidate(source, target, retry - 1);
      }
   }

   private static void sleep(int milliseconds) {
      try {
         Thread.sleep(milliseconds);
      } catch (InterruptedException var2) {
         logger.info(var2);
      }
   }

   private static void deleteFile(Path filepath) {
      try {
         Files.delete(filepath);
      } catch (IOException var2) {
         logger.info("Failed to delete", (Throwable)var2);
      }
   }

   private static Exception validateWrite(Path filepath, byte[] inMemoryBytes) {
      byte[] writtenBytes;
      try {
         writtenBytes = Files.readAllBytes(filepath);
      } catch (IOException var4) {
         return var4;
      }

      boolean valid = Arrays.equals(writtenBytes, inMemoryBytes);
      return !valid
         ? new FileWriteValidationError("Not valid: written=" + Arrays.toString(writtenBytes) + " vs inMemory=" + Arrays.toString(inMemoryBytes))
         : null;
   }

   static boolean writeAndValidate(Path filepath, byte[] data, int retry) {
      try {
         Files.write(filepath, data, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
      } catch (Exception var4) {
         if (retry <= 0) {
            logger.info("Failed to write file " + filepath.toString() + ", but the retry expired.", (Throwable)var4);
            return false;
         }

         logger.info("Failed to validate source=" + filepath.toString() + ", retrying...", (Throwable)var4);
         sleep(300);
         return writeAndValidate(filepath, data, retry - 1);
      }

      Exception err = validateWrite(filepath, data);
      if (err != null) {
         if (retry <= 0) {
            logger.info("Failed to write file " + filepath.toString() + ", but the retry expired.", (Throwable)err);
            return false;
         } else {
            logger.info("Failed to validate source=" + filepath.toString() + ", retrying...", (Throwable)err);
            sleep(300);
            return writeAndValidate(filepath, data, retry - 1);
         }
      } else {
         return true;
      }
   }
}
