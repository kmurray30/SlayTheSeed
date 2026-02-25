package org.apache.commons.codec.language;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.Map.Entry;
import org.apache.commons.codec.EncoderException;
import org.apache.commons.codec.StringEncoder;

public class DaitchMokotoffSoundex implements StringEncoder {
   private static final String COMMENT = "//";
   private static final String DOUBLE_QUOTE = "\"";
   private static final String MULTILINE_COMMENT_END = "*/";
   private static final String MULTILINE_COMMENT_START = "/*";
   private static final String RESOURCE_FILE = "org/apache/commons/codec/language/dmrules.txt";
   private static final int MAX_LENGTH = 6;
   private static final Map<Character, List<DaitchMokotoffSoundex.Rule>> RULES = new HashMap<>();
   private static final Map<Character, Character> FOLDINGS = new HashMap<>();
   private final boolean folding;

   private static void parseRules(
      Scanner scanner, String location, Map<Character, List<DaitchMokotoffSoundex.Rule>> ruleMapping, Map<Character, Character> asciiFoldings
   ) {
      int currentLine = 0;
      boolean inMultilineComment = false;

      while (scanner.hasNextLine()) {
         currentLine++;
         String rawLine = scanner.nextLine();
         String line = rawLine;
         if (!inMultilineComment) {
            if (!rawLine.startsWith("/*")) {
               int cmtI = rawLine.indexOf("//");
               if (cmtI >= 0) {
                  line = rawLine.substring(0, cmtI);
               }

               line = line.trim();
               if (line.length() != 0) {
                  if (line.contains("=")) {
                     String[] parts = line.split("=");
                     if (parts.length != 2) {
                        throw new IllegalArgumentException("Malformed folding statement split into " + parts.length + " parts: " + rawLine + " in " + location);
                     }

                     String leftCharacter = parts[0];
                     String rightCharacter = parts[1];
                     if (leftCharacter.length() != 1 || rightCharacter.length() != 1) {
                        throw new IllegalArgumentException("Malformed folding statement - patterns are not single characters: " + rawLine + " in " + location);
                     }

                     asciiFoldings.put(leftCharacter.charAt(0), rightCharacter.charAt(0));
                  } else {
                     String[] partsx = line.split("\\s+");
                     if (partsx.length != 4) {
                        throw new IllegalArgumentException("Malformed rule statement split into " + partsx.length + " parts: " + rawLine + " in " + location);
                     }

                     try {
                        String pattern = stripQuotes(partsx[0]);
                        String replacement1 = stripQuotes(partsx[1]);
                        String replacement2 = stripQuotes(partsx[2]);
                        String replacement3 = stripQuotes(partsx[3]);
                        DaitchMokotoffSoundex.Rule r = new DaitchMokotoffSoundex.Rule(pattern, replacement1, replacement2, replacement3);
                        char patternKey = r.pattern.charAt(0);
                        List<DaitchMokotoffSoundex.Rule> rules = ruleMapping.get(patternKey);
                        if (rules == null) {
                           rules = new ArrayList<>();
                           ruleMapping.put(patternKey, rules);
                        }

                        rules.add(r);
                     } catch (IllegalArgumentException var17) {
                        throw new IllegalStateException("Problem parsing line '" + currentLine + "' in " + location, var17);
                     }
                  }
               }
            } else {
               inMultilineComment = true;
            }
         } else if (rawLine.endsWith("*/")) {
            inMultilineComment = false;
         }
      }
   }

   private static String stripQuotes(String str) {
      if (str.startsWith("\"")) {
         str = str.substring(1);
      }

      if (str.endsWith("\"")) {
         str = str.substring(0, str.length() - 1);
      }

      return str;
   }

   public DaitchMokotoffSoundex() {
      this(true);
   }

   public DaitchMokotoffSoundex(boolean folding) {
      this.folding = folding;
   }

   private String cleanup(String input) {
      StringBuilder sb = new StringBuilder();

      for (char ch : input.toCharArray()) {
         if (!Character.isWhitespace(ch)) {
            ch = Character.toLowerCase(ch);
            if (this.folding && FOLDINGS.containsKey(ch)) {
               ch = FOLDINGS.get(ch);
            }

            sb.append(ch);
         }
      }

      return sb.toString();
   }

   @Override
   public Object encode(Object obj) throws EncoderException {
      if (!(obj instanceof String)) {
         throw new EncoderException("Parameter supplied to DaitchMokotoffSoundex encode is not of type java.lang.String");
      } else {
         return this.encode((String)obj);
      }
   }

   @Override
   public String encode(String source) {
      return source == null ? null : this.soundex(source, false)[0];
   }

   public String soundex(String source) {
      String[] branches = this.soundex(source, true);
      StringBuilder sb = new StringBuilder();
      int index = 0;

      for (String branch : branches) {
         sb.append(branch);
         if (++index < branches.length) {
            sb.append('|');
         }
      }

      return sb.toString();
   }

   private String[] soundex(String source, boolean branching) {
      if (source == null) {
         return null;
      } else {
         String input = this.cleanup(source);
         Set<DaitchMokotoffSoundex.Branch> currentBranches = new LinkedHashSet<>();
         currentBranches.add(new DaitchMokotoffSoundex.Branch());
         char lastChar = 0;

         for (int index = 0; index < input.length(); index++) {
            char ch = input.charAt(index);
            if (!Character.isWhitespace(ch)) {
               String inputContext = input.substring(index);
               List<DaitchMokotoffSoundex.Rule> rules = RULES.get(ch);
               if (rules != null) {
                  List<DaitchMokotoffSoundex.Branch> nextBranches = (List<DaitchMokotoffSoundex.Branch>)(branching ? new ArrayList<>() : Collections.EMPTY_LIST);

                  for (DaitchMokotoffSoundex.Rule rule : rules) {
                     if (rule.matches(inputContext)) {
                        if (branching) {
                           nextBranches.clear();
                        }

                        String[] replacements = rule.getReplacements(inputContext, lastChar == 0);
                        boolean branchingRequired = replacements.length > 1 && branching;

                        for (DaitchMokotoffSoundex.Branch branch : currentBranches) {
                           for (String nextReplacement : replacements) {
                              DaitchMokotoffSoundex.Branch nextBranch = branchingRequired ? branch.createBranch() : branch;
                              boolean force = lastChar == 'm' && ch == 'n' || lastChar == 'n' && ch == 'm';
                              nextBranch.processNextReplacement(nextReplacement, force);
                              if (!branching) {
                                 break;
                              }

                              nextBranches.add(nextBranch);
                           }
                        }

                        if (branching) {
                           currentBranches.clear();
                           currentBranches.addAll(nextBranches);
                        }

                        index += rule.getPatternLength() - 1;
                        break;
                     }
                  }

                  lastChar = ch;
               }
            }
         }

         String[] result = new String[currentBranches.size()];
         int indexx = 0;

         for (DaitchMokotoffSoundex.Branch branch : currentBranches) {
            branch.finish();
            result[indexx++] = branch.toString();
         }

         return result;
      }
   }

   static {
      InputStream rulesIS = DaitchMokotoffSoundex.class.getClassLoader().getResourceAsStream("org/apache/commons/codec/language/dmrules.txt");
      if (rulesIS == null) {
         throw new IllegalArgumentException("Unable to load resource: org/apache/commons/codec/language/dmrules.txt");
      } else {
         Scanner scanner = new Scanner(rulesIS, "UTF-8");
         parseRules(scanner, "org/apache/commons/codec/language/dmrules.txt", RULES, FOLDINGS);
         scanner.close();

         for (Entry<Character, List<DaitchMokotoffSoundex.Rule>> rule : RULES.entrySet()) {
            List<DaitchMokotoffSoundex.Rule> ruleList = rule.getValue();
            Collections.sort(ruleList, new Comparator<DaitchMokotoffSoundex.Rule>() {
               public int compare(DaitchMokotoffSoundex.Rule rule1, DaitchMokotoffSoundex.Rule rule2) {
                  return rule2.getPatternLength() - rule1.getPatternLength();
               }
            });
         }
      }
   }

   private static final class Branch {
      private final StringBuilder builder = new StringBuilder();
      private String cachedString;
      private String lastReplacement = null;

      private Branch() {
         this.cachedString = null;
      }

      public DaitchMokotoffSoundex.Branch createBranch() {
         DaitchMokotoffSoundex.Branch branch = new DaitchMokotoffSoundex.Branch();
         branch.builder.append(this.toString());
         branch.lastReplacement = this.lastReplacement;
         return branch;
      }

      @Override
      public boolean equals(Object other) {
         if (this == other) {
            return true;
         } else {
            return !(other instanceof DaitchMokotoffSoundex.Branch) ? false : this.toString().equals(((DaitchMokotoffSoundex.Branch)other).toString());
         }
      }

      public void finish() {
         while (this.builder.length() < 6) {
            this.builder.append('0');
            this.cachedString = null;
         }
      }

      @Override
      public int hashCode() {
         return this.toString().hashCode();
      }

      public void processNextReplacement(String replacement, boolean forceAppend) {
         boolean append = this.lastReplacement == null || !this.lastReplacement.endsWith(replacement) || forceAppend;
         if (append && this.builder.length() < 6) {
            this.builder.append(replacement);
            if (this.builder.length() > 6) {
               this.builder.delete(6, this.builder.length());
            }

            this.cachedString = null;
         }

         this.lastReplacement = replacement;
      }

      @Override
      public String toString() {
         if (this.cachedString == null) {
            this.cachedString = this.builder.toString();
         }

         return this.cachedString;
      }
   }

   private static final class Rule {
      private final String pattern;
      private final String[] replacementAtStart;
      private final String[] replacementBeforeVowel;
      private final String[] replacementDefault;

      protected Rule(String pattern, String replacementAtStart, String replacementBeforeVowel, String replacementDefault) {
         this.pattern = pattern;
         this.replacementAtStart = replacementAtStart.split("\\|");
         this.replacementBeforeVowel = replacementBeforeVowel.split("\\|");
         this.replacementDefault = replacementDefault.split("\\|");
      }

      public int getPatternLength() {
         return this.pattern.length();
      }

      public String[] getReplacements(String context, boolean atStart) {
         if (atStart) {
            return this.replacementAtStart;
         } else {
            int nextIndex = this.getPatternLength();
            boolean nextCharIsVowel = nextIndex < context.length() ? this.isVowel(context.charAt(nextIndex)) : false;
            return nextCharIsVowel ? this.replacementBeforeVowel : this.replacementDefault;
         }
      }

      private boolean isVowel(char ch) {
         return ch == 'a' || ch == 'e' || ch == 'i' || ch == 'o' || ch == 'u';
      }

      public boolean matches(String context) {
         return context.startsWith(this.pattern);
      }

      @Override
      public String toString() {
         return String.format(
            "%s=(%s,%s,%s)",
            this.pattern,
            Arrays.asList(this.replacementAtStart),
            Arrays.asList(this.replacementBeforeVowel),
            Arrays.asList(this.replacementDefault)
         );
      }
   }
}
