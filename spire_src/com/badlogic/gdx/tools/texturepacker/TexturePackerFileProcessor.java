package com.badlogic.gdx.tools.texturepacker;

import com.badlogic.gdx.tools.FileProcessor;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.ObjectMap;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TexturePackerFileProcessor extends FileProcessor {
   private final TexturePacker.Settings defaultSettings;
   private ObjectMap<File, TexturePacker.Settings> dirToSettings = new ObjectMap<>();
   private Json json = new Json();
   private String packFileName;
   private File root;
   ArrayList<File> ignoreDirs = new ArrayList<>();

   public TexturePackerFileProcessor() {
      this(new TexturePacker.Settings(), "pack.atlas");
   }

   public TexturePackerFileProcessor(TexturePacker.Settings defaultSettings, String packFileName) {
      this.defaultSettings = defaultSettings;
      if (packFileName.toLowerCase().endsWith(defaultSettings.atlasExtension.toLowerCase())) {
         packFileName = packFileName.substring(0, packFileName.length() - defaultSettings.atlasExtension.length());
      }

      this.packFileName = packFileName;
      this.setFlattenOutput(true);
      this.addInputSuffix(".png", ".jpg", ".jpeg");
   }

   @Override
   public ArrayList<FileProcessor.Entry> process(File inputFile, File outputRoot) throws Exception {
      this.root = inputFile;
      final ArrayList<File> settingsFiles = new ArrayList<>();
      FileProcessor settingsProcessor = new FileProcessor() {
         @Override
         protected void processFile(FileProcessor.Entry inputFile) throws Exception {
            settingsFiles.add(inputFile.inputFile);
         }
      };
      settingsProcessor.addInputRegex("pack\\.json");
      settingsProcessor.process(inputFile, null);
      Collections.sort(settingsFiles, new Comparator<File>() {
         public int compare(File file1, File file2) {
            return file1.toString().length() - file2.toString().length();
         }
      });

      for (File settingsFile : settingsFiles) {
         TexturePacker.Settings settings = null;
         File parent = settingsFile.getParentFile();

         while (!parent.equals(this.root)) {
            parent = parent.getParentFile();
            settings = this.dirToSettings.get(parent);
            if (settings != null) {
               settings = new TexturePacker.Settings(settings);
               break;
            }
         }

         if (settings == null) {
            settings = new TexturePacker.Settings(this.defaultSettings);
         }

         this.merge(settings, settingsFile);
         this.dirToSettings.put(settingsFile.getParentFile(), settings);
      }

      return super.process(inputFile, outputRoot);
   }

   private void merge(TexturePacker.Settings settings, File settingsFile) {
      try {
         this.json.readFields(settings, new JsonReader().parse(new FileReader(settingsFile)));
      } catch (Exception var4) {
         throw new GdxRuntimeException("Error reading settings file: " + settingsFile, var4);
      }
   }

   @Override
   public ArrayList<FileProcessor.Entry> process(File[] files, File outputRoot) throws Exception {
      if (outputRoot.exists()) {
         File settingsFile = new File(this.root, "pack.json");
         TexturePacker.Settings rootSettings = this.defaultSettings;
         if (settingsFile.exists()) {
            rootSettings = new TexturePacker.Settings(rootSettings);
            this.merge(rootSettings, settingsFile);
         }

         String atlasExtension = rootSettings.atlasExtension == null ? "" : rootSettings.atlasExtension;
         atlasExtension = Pattern.quote(atlasExtension);
         int i = 0;

         for (int n = rootSettings.scale.length; i < n; i++) {
            FileProcessor deleteProcessor = new FileProcessor() {
               @Override
               protected void processFile(FileProcessor.Entry inputFile) throws Exception {
                  inputFile.inputFile.delete();
               }
            };
            deleteProcessor.setRecursive(false);
            String scaledPackFileName = rootSettings.getScaledPackFileName(this.packFileName, i);
            File packFile = new File(scaledPackFileName);
            String prefix = packFile.getName();
            int dotIndex = prefix.lastIndexOf(46);
            if (dotIndex != -1) {
               prefix = prefix.substring(0, dotIndex);
            }

            deleteProcessor.addInputRegex("(?i)" + prefix + "\\d*\\.(png|jpg|jpeg)");
            deleteProcessor.addInputRegex("(?i)" + prefix + atlasExtension);
            String dir = packFile.getParent();
            if (dir == null) {
               deleteProcessor.process(outputRoot, null);
            } else if (new File(outputRoot + "/" + dir).exists()) {
               deleteProcessor.process(outputRoot + "/" + dir, null);
            }
         }
      }

      return super.process(files, outputRoot);
   }

   @Override
   protected void processDir(FileProcessor.Entry inputDir, ArrayList<FileProcessor.Entry> files) throws Exception {
      if (!this.ignoreDirs.contains(inputDir.inputFile)) {
         TexturePacker.Settings settings = null;
         File parent = inputDir.inputFile;

         while (true) {
            settings = this.dirToSettings.get(parent);
            if (settings != null || parent == null || parent.equals(this.root)) {
               if (settings == null) {
                  settings = this.defaultSettings;
               }

               if (settings.ignore) {
                  return;
               } else {
                  if (settings.combineSubdirectories) {
                     files = (new FileProcessor(this) {
                        @Override
                        protected void processDir(FileProcessor.Entry entryDir, ArrayList<FileProcessor.Entry> files) {
                           TexturePackerFileProcessor.this.ignoreDirs.add(entryDir.inputFile);
                        }

                        @Override
                        protected void processFile(FileProcessor.Entry entry) {
                           this.addProcessedFile(entry);
                        }
                     }).process(inputDir.inputFile, null);
                  }

                  if (files.isEmpty()) {
                     return;
                  } else {
                     Collections.sort(files, new Comparator<FileProcessor.Entry>() {
                        final Pattern digitSuffix = Pattern.compile("(.*?)(\\d+)$");

                        public int compare(FileProcessor.Entry entry1, FileProcessor.Entry entry2) {
                           String full1 = entry1.inputFile.getName();
                           int dotIndex = full1.lastIndexOf(46);
                           if (dotIndex != -1) {
                              full1 = full1.substring(0, dotIndex);
                           }

                           String full2 = entry2.inputFile.getName();
                           dotIndex = full2.lastIndexOf(46);
                           if (dotIndex != -1) {
                              full2 = full2.substring(0, dotIndex);
                           }

                           String name1 = full1;
                           String name2 = full2;
                           int num1 = 0;
                           int num2 = 0;
                           Matcher matcher = this.digitSuffix.matcher(full1);
                           if (matcher.matches()) {
                              try {
                                 num1 = Integer.parseInt(matcher.group(2));
                                 name1 = matcher.group(1);
                              } catch (Exception var13) {
                              }
                           }

                           matcher = this.digitSuffix.matcher(full2);
                           if (matcher.matches()) {
                              try {
                                 num2 = Integer.parseInt(matcher.group(2));
                                 name2 = matcher.group(1);
                              } catch (Exception var12) {
                              }
                           }

                           int compare = name1.compareTo(name2);
                           return compare == 0 && num1 != num2 ? num1 - num2 : compare;
                        }
                     });
                     if (!settings.silent) {
                        System.out.println(inputDir.inputFile.getName());
                     }

                     TexturePacker packer = new TexturePacker(this.root, settings);

                     for (FileProcessor.Entry file : files) {
                        packer.addImage(file.inputFile);
                     }

                     packer.pack(inputDir.outputDir, this.packFileName);
                     return;
                  }
               }
            }

            parent = parent.getParentFile();
         }
      }
   }
}
