package org.apache.commons.net.ftp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import org.apache.commons.net.util.Charsets;

public class FTPListParseEngine {
   private List<String> entries = new LinkedList<>();
   private ListIterator<String> _internalIterator = this.entries.listIterator();
   private final FTPFileEntryParser parser;
   private final boolean saveUnparseableEntries;

   public FTPListParseEngine(FTPFileEntryParser parser) {
      this(parser, null);
   }

   FTPListParseEngine(FTPFileEntryParser parser, FTPClientConfig configuration) {
      this.parser = parser;
      if (configuration != null) {
         this.saveUnparseableEntries = configuration.getUnparseableEntries();
      } else {
         this.saveUnparseableEntries = false;
      }
   }

   public void readServerList(InputStream stream, String encoding) throws IOException {
      this.entries = new LinkedList<>();
      this.readStream(stream, encoding);
      this.parser.preParse(this.entries);
      this.resetIterator();
   }

   private void readStream(InputStream stream, String encoding) throws IOException {
      BufferedReader reader = new BufferedReader(new InputStreamReader(stream, Charsets.toCharset(encoding)));

      for (String line = this.parser.readNextEntry(reader); line != null; line = this.parser.readNextEntry(reader)) {
         this.entries.add(line);
      }

      reader.close();
   }

   public FTPFile[] getNext(int quantityRequested) {
      List<FTPFile> tmpResults = new LinkedList<>();

      for (int count = quantityRequested; count > 0 && this._internalIterator.hasNext(); count--) {
         String entry = this._internalIterator.next();
         FTPFile temp = this.parser.parseFTPEntry(entry);
         if (temp == null && this.saveUnparseableEntries) {
            temp = new FTPFile(entry);
         }

         tmpResults.add(temp);
      }

      return tmpResults.toArray(new FTPFile[tmpResults.size()]);
   }

   public FTPFile[] getPrevious(int quantityRequested) {
      List<FTPFile> tmpResults = new LinkedList<>();

      for (int count = quantityRequested; count > 0 && this._internalIterator.hasPrevious(); count--) {
         String entry = this._internalIterator.previous();
         FTPFile temp = this.parser.parseFTPEntry(entry);
         if (temp == null && this.saveUnparseableEntries) {
            temp = new FTPFile(entry);
         }

         tmpResults.add(0, temp);
      }

      return tmpResults.toArray(new FTPFile[tmpResults.size()]);
   }

   public FTPFile[] getFiles() throws IOException {
      return this.getFiles(FTPFileFilters.NON_NULL);
   }

   public FTPFile[] getFiles(FTPFileFilter filter) throws IOException {
      List<FTPFile> tmpResults = new ArrayList<>();

      for (String entry : this.entries) {
         FTPFile temp = this.parser.parseFTPEntry(entry);
         if (temp == null && this.saveUnparseableEntries) {
            temp = new FTPFile(entry);
         }

         if (filter.accept(temp)) {
            tmpResults.add(temp);
         }
      }

      return tmpResults.toArray(new FTPFile[tmpResults.size()]);
   }

   public boolean hasNext() {
      return this._internalIterator.hasNext();
   }

   public boolean hasPrevious() {
      return this._internalIterator.hasPrevious();
   }

   public void resetIterator() {
      this._internalIterator = this.entries.listIterator();
   }

   @Deprecated
   public void readServerList(InputStream stream) throws IOException {
      this.readServerList(stream, null);
   }
}
