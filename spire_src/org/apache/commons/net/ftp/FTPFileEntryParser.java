package org.apache.commons.net.ftp;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;

public interface FTPFileEntryParser {
   FTPFile parseFTPEntry(String var1);

   String readNextEntry(BufferedReader var1) throws IOException;

   List<String> preParse(List<String> var1);
}
