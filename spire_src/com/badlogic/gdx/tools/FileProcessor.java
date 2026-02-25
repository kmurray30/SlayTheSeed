package com.badlogic.gdx.tools;

import com.badlogic.gdx.utils.Array;
import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class FileProcessor {
   FilenameFilter inputFilter;
   Comparator<File> comparator = new Comparator<File>() {
      public int compare(File o1, File o2) {
         return o1.getName().compareTo(o2.getName());
      }
   };
   Array<Pattern> inputRegex = new Array<>();
   String outputSuffix;
   ArrayList<FileProcessor.Entry> outputFiles = new ArrayList<>();
   boolean recursive = true;
   boolean flattenOutput;
   Comparator<FileProcessor.Entry> entryComparator = new Comparator<FileProcessor.Entry>() {
      public int compare(FileProcessor.Entry o1, FileProcessor.Entry o2) {
         return FileProcessor.this.comparator.compare(o1.inputFile, o2.inputFile);
      }
   };

   public FileProcessor() {
   }

   public FileProcessor(FileProcessor processor) {
      this.inputFilter = processor.inputFilter;
      this.comparator = processor.comparator;
      this.inputRegex.addAll(processor.inputRegex);
      this.outputSuffix = processor.outputSuffix;
      this.recursive = processor.recursive;
      this.flattenOutput = processor.flattenOutput;
   }

   public FileProcessor setInputFilter(FilenameFilter inputFilter) {
      this.inputFilter = inputFilter;
      return this;
   }

   public FileProcessor setComparator(Comparator<File> comparator) {
      this.comparator = comparator;
      return this;
   }

   public FileProcessor addInputSuffix(String... suffixes) {
      for (String suffix : suffixes) {
         this.addInputRegex("(?i).*" + Pattern.quote(suffix));
      }

      return this;
   }

   public FileProcessor addInputRegex(String... regexes) {
      for (String regex : regexes) {
         this.inputRegex.add(Pattern.compile(regex));
      }

      return this;
   }

   public FileProcessor setOutputSuffix(String outputSuffix) {
      this.outputSuffix = outputSuffix;
      return this;
   }

   public FileProcessor setFlattenOutput(boolean flattenOutput) {
      this.flattenOutput = flattenOutput;
      return this;
   }

   public FileProcessor setRecursive(boolean recursive) {
      this.recursive = recursive;
      return this;
   }

   public ArrayList<FileProcessor.Entry> process(String inputFileOrDir, String outputRoot) throws Exception {
      return this.process(new File(inputFileOrDir), outputRoot == null ? null : new File(outputRoot));
   }

   public ArrayList<FileProcessor.Entry> process(File inputFileOrDir, File outputRoot) throws Exception {
      if (!inputFileOrDir.exists()) {
         throw new IllegalArgumentException("Input file does not exist: " + inputFileOrDir.getAbsolutePath());
      } else {
         return inputFileOrDir.isFile() ? this.process(new File[]{inputFileOrDir}, outputRoot) : this.process(inputFileOrDir.listFiles(), outputRoot);
      }
   }

   public ArrayList<FileProcessor.Entry> process(File[] files, File outputRoot) throws Exception {
      if (outputRoot == null) {
         outputRoot = new File("");
      }

      this.outputFiles.clear();
      LinkedHashMap<File, ArrayList<FileProcessor.Entry>> dirToEntries = new LinkedHashMap<>();
      this.process(files, outputRoot, outputRoot, dirToEntries, 0);
      ArrayList<FileProcessor.Entry> allEntries = new ArrayList<>();

      for (Map.Entry<File, ArrayList<FileProcessor.Entry>> mapEntry : dirToEntries.entrySet()) {
         ArrayList<FileProcessor.Entry> dirEntries = mapEntry.getValue();
         if (this.comparator != null) {
            Collections.sort(dirEntries, this.entryComparator);
         }

         File inputDir = mapEntry.getKey();
         File newOutputDir = null;
         if (this.flattenOutput) {
            newOutputDir = outputRoot;
         } else if (!dirEntries.isEmpty()) {
            newOutputDir = dirEntries.get(0).outputDir;
         }

         String outputName = inputDir.getName();
         if (this.outputSuffix != null) {
            outputName = outputName.replaceAll("(.*)\\..*", "$1") + this.outputSuffix;
         }

         FileProcessor.Entry entry = new FileProcessor.Entry();
         entry.inputFile = mapEntry.getKey();
         entry.outputDir = newOutputDir;
         if (newOutputDir != null) {
            entry.outputFile = newOutputDir.length() == 0L ? new File(outputName) : new File(newOutputDir, outputName);
         }

         try {
            this.processDir(entry, dirEntries);
         } catch (Exception var14) {
            throw new Exception("Error processing directory: " + entry.inputFile.getAbsolutePath(), var14);
         }

         allEntries.addAll(dirEntries);
      }

      if (this.comparator != null) {
         Collections.sort(allEntries, this.entryComparator);
      }

      for (FileProcessor.Entry entry : allEntries) {
         try {
            this.processFile(entry);
         } catch (Exception var13) {
            throw new Exception("Error processing file: " + entry.inputFile.getAbsolutePath(), var13);
         }
      }

      return this.outputFiles;
   }

   private void process(File[] files, File outputRoot, File outputDir, LinkedHashMap<File, ArrayList<FileProcessor.Entry>> dirToEntries, int depth) {
      for (File file : files) {
         File dir = file.getParentFile();
         ArrayList<FileProcessor.Entry> entries = dirToEntries.get(dir);
         if (entries == null) {
            entries = new ArrayList<>();
            dirToEntries.put(dir, entries);
         }
      }

      for (File filex : files) {
         if (filex.isFile()) {
            if (this.inputRegex.size > 0) {
               boolean found = false;

               for (Pattern pattern : this.inputRegex) {
                  if (pattern.matcher(filex.getName()).matches()) {
                     found = true;
                  }
               }

               if (!found) {
                  continue;
               }
            }

            File dir = filex.getParentFile();
            if (this.inputFilter != null && !this.inputFilter.accept(dir, filex.getName())) {
               continue;
            }

            String outputName = filex.getName();
            if (this.outputSuffix != null) {
               outputName = outputName.replaceAll("(.*)\\..*", "$1") + this.outputSuffix;
            }

            FileProcessor.Entry entry = new FileProcessor.Entry();
            entry.depth = depth;
            entry.inputFile = filex;
            entry.outputDir = outputDir;
            if (this.flattenOutput) {
               entry.outputFile = new File(outputRoot, outputName);
            } else {
               entry.outputFile = new File(outputDir, outputName);
            }

            dirToEntries.get(dir).add(entry);
         }

         if (this.recursive && filex.isDirectory()) {
            File subdir = outputDir.getPath().length() == 0 ? new File(filex.getName()) : new File(outputDir, filex.getName());
            this.process(filex.listFiles(this.inputFilter), outputRoot, subdir, dirToEntries, depth + 1);
         }
      }
   }

   protected void processFile(FileProcessor.Entry entry) throws Exception {
   }

   protected void processDir(FileProcessor.Entry entryDir, ArrayList<FileProcessor.Entry> files) throws Exception {
   }

   protected void addProcessedFile(FileProcessor.Entry entry) {
      this.outputFiles.add(entry);
   }

   public static class Entry {
      public File inputFile;
      public File outputDir;
      public File outputFile;
      public int depth;

      public Entry() {
      }

      public Entry(File inputFile, File outputFile) {
         this.inputFile = inputFile;
         this.outputFile = outputFile;
      }

      @Override
      public String toString() {
         return this.inputFile.toString();
      }
   }
}
