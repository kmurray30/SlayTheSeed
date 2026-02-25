package com.gikk.twirk.types.cheer;

import com.gikk.twirk.types.TagMap;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CheerParser {
   private static final Pattern PATTERN = Pattern.compile("([a-zA-Z]+([1-9][0-9]+\\b(?<=\\w)))");

   public static List<Cheer> parseCheer(TagMap tagMap, String content) {
      List<Cheer> list = new ArrayList<>();
      int bits = tagMap.getAsInt("bits");
      if (bits == -1) {
         return list;
      } else {
         Matcher matcher = PATTERN.matcher(content);
         int bitsFound = 0;

         while (matcher.find() && bitsFound < bits) {
            int bit = Integer.parseInt(matcher.group(2));
            bitsFound += bit;
            Cheer cheer = new CheerImpl(bit, matcher.group(1));
            list.add(cheer);
         }

         return list;
      }
   }
}
