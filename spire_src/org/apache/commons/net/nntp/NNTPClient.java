package org.apache.commons.net.nntp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Vector;
import org.apache.commons.net.MalformedServerReplyException;
import org.apache.commons.net.io.DotTerminatedMessageReader;
import org.apache.commons.net.io.DotTerminatedMessageWriter;
import org.apache.commons.net.io.Util;

public class NNTPClient extends NNTP {
   private void __parseArticlePointer(String reply, ArticleInfo pointer) throws MalformedServerReplyException {
      String[] tokens = reply.split(" ");
      if (tokens.length >= 3) {
         int i = 1;

         try {
            pointer.articleNumber = Long.parseLong(tokens[i++]);
            pointer.articleId = tokens[i++];
            return;
         } catch (NumberFormatException var6) {
         }
      }

      throw new MalformedServerReplyException("Could not parse article pointer.\nServer reply: " + reply);
   }

   private static void __parseGroupReply(String reply, NewsgroupInfo info) throws MalformedServerReplyException {
      String[] tokens = reply.split(" ");
      if (tokens.length >= 5) {
         int i = 1;

         try {
            info._setArticleCount(Long.parseLong(tokens[i++]));
            info._setFirstArticle(Long.parseLong(tokens[i++]));
            info._setLastArticle(Long.parseLong(tokens[i++]));
            info._setNewsgroup(tokens[i++]);
            info._setPostingPermission(0);
            return;
         } catch (NumberFormatException var5) {
         }
      }

      throw new MalformedServerReplyException("Could not parse newsgroup info.\nServer reply: " + reply);
   }

   static NewsgroupInfo __parseNewsgroupListEntry(String entry) {
      String[] tokens = entry.split(" ");
      if (tokens.length < 4) {
         return null;
      } else {
         NewsgroupInfo result = new NewsgroupInfo();
         int i = 0;
         result._setNewsgroup(tokens[i++]);

         try {
            long lastNum = Long.parseLong(tokens[i++]);
            long firstNum = Long.parseLong(tokens[i++]);
            result._setFirstArticle(firstNum);
            result._setLastArticle(lastNum);
            if (firstNum == 0L && lastNum == 0L) {
               result._setArticleCount(0L);
            } else {
               result._setArticleCount(lastNum - firstNum + 1L);
            }
         } catch (NumberFormatException var8) {
            return null;
         }

         switch (tokens[i++].charAt(0)) {
            case 'M':
            case 'm':
               result._setPostingPermission(1);
               break;
            case 'N':
            case 'n':
               result._setPostingPermission(3);
               break;
            case 'Y':
            case 'y':
               result._setPostingPermission(2);
               break;
            default:
               result._setPostingPermission(0);
         }

         return result;
      }
   }

   static Article __parseArticleEntry(String line) {
      Article article = new Article();
      article.setSubject(line);
      String[] parts = line.split("\t");
      if (parts.length > 6) {
         int i = 0;

         try {
            article.setArticleNumber(Long.parseLong(parts[i++]));
            article.setSubject(parts[i++]);
            article.setFrom(parts[i++]);
            article.setDate(parts[i++]);
            article.setArticleId(parts[i++]);
            article.addReference(parts[i++]);
         } catch (NumberFormatException var5) {
         }
      }

      return article;
   }

   private NewsgroupInfo[] __readNewsgroupListing() throws IOException {
      BufferedReader reader = new DotTerminatedMessageReader(this._reader_);
      Vector<NewsgroupInfo> list = new Vector<>(2048);

      String line;
      try {
         while ((line = reader.readLine()) != null) {
            NewsgroupInfo tmp = __parseNewsgroupListEntry(line);
            if (tmp == null) {
               throw new MalformedServerReplyException(line);
            }

            list.addElement(tmp);
         }
      } finally {
         reader.close();
      }

      int var8;
      if ((var8 = list.size()) < 1) {
         return new NewsgroupInfo[0];
      } else {
         NewsgroupInfo[] info = new NewsgroupInfo[var8];
         list.copyInto(info);
         return info;
      }
   }

   private BufferedReader __retrieve(int command, String articleId, ArticleInfo pointer) throws IOException {
      if (articleId != null) {
         if (!NNTPReply.isPositiveCompletion(this.sendCommand(command, articleId))) {
            return null;
         }
      } else if (!NNTPReply.isPositiveCompletion(this.sendCommand(command))) {
         return null;
      }

      if (pointer != null) {
         this.__parseArticlePointer(this.getReplyString(), pointer);
      }

      return new DotTerminatedMessageReader(this._reader_);
   }

   private BufferedReader __retrieve(int command, long articleNumber, ArticleInfo pointer) throws IOException {
      if (!NNTPReply.isPositiveCompletion(this.sendCommand(command, Long.toString(articleNumber)))) {
         return null;
      } else {
         if (pointer != null) {
            this.__parseArticlePointer(this.getReplyString(), pointer);
         }

         return new DotTerminatedMessageReader(this._reader_);
      }
   }

   public BufferedReader retrieveArticle(String articleId, ArticleInfo pointer) throws IOException {
      return this.__retrieve(0, articleId, pointer);
   }

   public Reader retrieveArticle(String articleId) throws IOException {
      return this.retrieveArticle(articleId, (ArticleInfo)null);
   }

   public Reader retrieveArticle() throws IOException {
      return this.retrieveArticle((String)null);
   }

   public BufferedReader retrieveArticle(long articleNumber, ArticleInfo pointer) throws IOException {
      return this.__retrieve(0, articleNumber, pointer);
   }

   public BufferedReader retrieveArticle(long articleNumber) throws IOException {
      return this.retrieveArticle(articleNumber, null);
   }

   public BufferedReader retrieveArticleHeader(String articleId, ArticleInfo pointer) throws IOException {
      return this.__retrieve(3, articleId, pointer);
   }

   public Reader retrieveArticleHeader(String articleId) throws IOException {
      return this.retrieveArticleHeader(articleId, (ArticleInfo)null);
   }

   public Reader retrieveArticleHeader() throws IOException {
      return this.retrieveArticleHeader((String)null);
   }

   public BufferedReader retrieveArticleHeader(long articleNumber, ArticleInfo pointer) throws IOException {
      return this.__retrieve(3, articleNumber, pointer);
   }

   public BufferedReader retrieveArticleHeader(long articleNumber) throws IOException {
      return this.retrieveArticleHeader(articleNumber, null);
   }

   public BufferedReader retrieveArticleBody(String articleId, ArticleInfo pointer) throws IOException {
      return this.__retrieve(1, articleId, pointer);
   }

   public Reader retrieveArticleBody(String articleId) throws IOException {
      return this.retrieveArticleBody(articleId, (ArticleInfo)null);
   }

   public Reader retrieveArticleBody() throws IOException {
      return this.retrieveArticleBody(null);
   }

   public BufferedReader retrieveArticleBody(long articleNumber, ArticleInfo pointer) throws IOException {
      return this.__retrieve(1, articleNumber, pointer);
   }

   public BufferedReader retrieveArticleBody(long articleNumber) throws IOException {
      return this.retrieveArticleBody(articleNumber, null);
   }

   public boolean selectNewsgroup(String newsgroup, NewsgroupInfo info) throws IOException {
      if (!NNTPReply.isPositiveCompletion(this.group(newsgroup))) {
         return false;
      } else {
         if (info != null) {
            __parseGroupReply(this.getReplyString(), info);
         }

         return true;
      }
   }

   public boolean selectNewsgroup(String newsgroup) throws IOException {
      return this.selectNewsgroup(newsgroup, null);
   }

   public String listHelp() throws IOException {
      if (!NNTPReply.isInformational(this.help())) {
         return null;
      } else {
         StringWriter help = new StringWriter();
         BufferedReader reader = new DotTerminatedMessageReader(this._reader_);
         Util.copyReader(reader, help);
         reader.close();
         help.close();
         return help.toString();
      }
   }

   public String[] listOverviewFmt() throws IOException {
      if (!NNTPReply.isPositiveCompletion(this.sendCommand("LIST", "OVERVIEW.FMT"))) {
         return null;
      } else {
         BufferedReader reader = new DotTerminatedMessageReader(this._reader_);
         ArrayList<String> list = new ArrayList<>();

         String line;
         while ((line = reader.readLine()) != null) {
            list.add(line);
         }

         reader.close();
         return list.toArray(new String[list.size()]);
      }
   }

   public boolean selectArticle(String articleId, ArticleInfo pointer) throws IOException {
      if (articleId != null) {
         if (!NNTPReply.isPositiveCompletion(this.stat(articleId))) {
            return false;
         }
      } else if (!NNTPReply.isPositiveCompletion(this.stat())) {
         return false;
      }

      if (pointer != null) {
         this.__parseArticlePointer(this.getReplyString(), pointer);
      }

      return true;
   }

   public boolean selectArticle(String articleId) throws IOException {
      return this.selectArticle(articleId, (ArticleInfo)null);
   }

   public boolean selectArticle(ArticleInfo pointer) throws IOException {
      return this.selectArticle(null, pointer);
   }

   public boolean selectArticle(long articleNumber, ArticleInfo pointer) throws IOException {
      if (!NNTPReply.isPositiveCompletion(this.stat(articleNumber))) {
         return false;
      } else {
         if (pointer != null) {
            this.__parseArticlePointer(this.getReplyString(), pointer);
         }

         return true;
      }
   }

   public boolean selectArticle(long articleNumber) throws IOException {
      return this.selectArticle(articleNumber, null);
   }

   public boolean selectPreviousArticle(ArticleInfo pointer) throws IOException {
      if (!NNTPReply.isPositiveCompletion(this.last())) {
         return false;
      } else {
         if (pointer != null) {
            this.__parseArticlePointer(this.getReplyString(), pointer);
         }

         return true;
      }
   }

   public boolean selectPreviousArticle() throws IOException {
      return this.selectPreviousArticle((ArticleInfo)null);
   }

   public boolean selectNextArticle(ArticleInfo pointer) throws IOException {
      if (!NNTPReply.isPositiveCompletion(this.next())) {
         return false;
      } else {
         if (pointer != null) {
            this.__parseArticlePointer(this.getReplyString(), pointer);
         }

         return true;
      }
   }

   public boolean selectNextArticle() throws IOException {
      return this.selectNextArticle((ArticleInfo)null);
   }

   public NewsgroupInfo[] listNewsgroups() throws IOException {
      return !NNTPReply.isPositiveCompletion(this.list()) ? null : this.__readNewsgroupListing();
   }

   public Iterable<String> iterateNewsgroupListing() throws IOException {
      if (NNTPReply.isPositiveCompletion(this.list())) {
         return new ReplyIterator(this._reader_);
      } else {
         throw new IOException("LIST command failed: " + this.getReplyString());
      }
   }

   public Iterable<NewsgroupInfo> iterateNewsgroups() throws IOException {
      return new NewsgroupIterator(this.iterateNewsgroupListing());
   }

   public NewsgroupInfo[] listNewsgroups(String wildmat) throws IOException {
      return !NNTPReply.isPositiveCompletion(this.listActive(wildmat)) ? null : this.__readNewsgroupListing();
   }

   public Iterable<String> iterateNewsgroupListing(String wildmat) throws IOException {
      if (NNTPReply.isPositiveCompletion(this.listActive(wildmat))) {
         return new ReplyIterator(this._reader_);
      } else {
         throw new IOException("LIST ACTIVE " + wildmat + " command failed: " + this.getReplyString());
      }
   }

   public Iterable<NewsgroupInfo> iterateNewsgroups(String wildmat) throws IOException {
      return new NewsgroupIterator(this.iterateNewsgroupListing(wildmat));
   }

   public NewsgroupInfo[] listNewNewsgroups(NewGroupsOrNewsQuery query) throws IOException {
      return !NNTPReply.isPositiveCompletion(this.newgroups(query.getDate(), query.getTime(), query.isGMT(), query.getDistributions()))
         ? null
         : this.__readNewsgroupListing();
   }

   public Iterable<String> iterateNewNewsgroupListing(NewGroupsOrNewsQuery query) throws IOException {
      if (NNTPReply.isPositiveCompletion(this.newgroups(query.getDate(), query.getTime(), query.isGMT(), query.getDistributions()))) {
         return new ReplyIterator(this._reader_);
      } else {
         throw new IOException("NEWGROUPS command failed: " + this.getReplyString());
      }
   }

   public Iterable<NewsgroupInfo> iterateNewNewsgroups(NewGroupsOrNewsQuery query) throws IOException {
      return new NewsgroupIterator(this.iterateNewNewsgroupListing(query));
   }

   public String[] listNewNews(NewGroupsOrNewsQuery query) throws IOException {
      if (!NNTPReply.isPositiveCompletion(this.newnews(query.getNewsgroups(), query.getDate(), query.getTime(), query.isGMT(), query.getDistributions()))) {
         return null;
      } else {
         Vector<String> list = new Vector<>();
         BufferedReader reader = new DotTerminatedMessageReader(this._reader_);

         String line;
         try {
            while ((line = reader.readLine()) != null) {
               list.addElement(line);
            }
         } finally {
            reader.close();
         }

         int size = list.size();
         if (size < 1) {
            return new String[0];
         } else {
            String[] result = new String[size];
            list.copyInto(result);
            return result;
         }
      }
   }

   public Iterable<String> iterateNewNews(NewGroupsOrNewsQuery query) throws IOException {
      if (NNTPReply.isPositiveCompletion(this.newnews(query.getNewsgroups(), query.getDate(), query.getTime(), query.isGMT(), query.getDistributions()))) {
         return new ReplyIterator(this._reader_);
      } else {
         throw new IOException("NEWNEWS command failed: " + this.getReplyString());
      }
   }

   public boolean completePendingCommand() throws IOException {
      return NNTPReply.isPositiveCompletion(this.getReply());
   }

   public Writer postArticle() throws IOException {
      return !NNTPReply.isPositiveIntermediate(this.post()) ? null : new DotTerminatedMessageWriter(this._writer_);
   }

   public Writer forwardArticle(String articleId) throws IOException {
      return !NNTPReply.isPositiveIntermediate(this.ihave(articleId)) ? null : new DotTerminatedMessageWriter(this._writer_);
   }

   public boolean logout() throws IOException {
      return NNTPReply.isPositiveCompletion(this.quit());
   }

   public boolean authenticate(String username, String password) throws IOException {
      int replyCode = this.authinfoUser(username);
      if (replyCode == 381) {
         replyCode = this.authinfoPass(password);
         if (replyCode == 281) {
            this._isAllowedToPost = true;
            return true;
         }
      }

      return false;
   }

   private BufferedReader __retrieveArticleInfo(String articleRange) throws IOException {
      return !NNTPReply.isPositiveCompletion(this.xover(articleRange)) ? null : new DotTerminatedMessageReader(this._reader_);
   }

   public BufferedReader retrieveArticleInfo(long articleNumber) throws IOException {
      return this.__retrieveArticleInfo(Long.toString(articleNumber));
   }

   public BufferedReader retrieveArticleInfo(long lowArticleNumber, long highArticleNumber) throws IOException {
      return this.__retrieveArticleInfo(lowArticleNumber + "-" + highArticleNumber);
   }

   public Iterable<Article> iterateArticleInfo(long lowArticleNumber, long highArticleNumber) throws IOException {
      BufferedReader info = this.retrieveArticleInfo(lowArticleNumber, highArticleNumber);
      if (info == null) {
         throw new IOException("XOVER command failed: " + this.getReplyString());
      } else {
         return new ArticleIterator(new ReplyIterator(info, false));
      }
   }

   private BufferedReader __retrieveHeader(String header, String articleRange) throws IOException {
      return !NNTPReply.isPositiveCompletion(this.xhdr(header, articleRange)) ? null : new DotTerminatedMessageReader(this._reader_);
   }

   public BufferedReader retrieveHeader(String header, long articleNumber) throws IOException {
      return this.__retrieveHeader(header, Long.toString(articleNumber));
   }

   public BufferedReader retrieveHeader(String header, long lowArticleNumber, long highArticleNumber) throws IOException {
      return this.__retrieveHeader(header, lowArticleNumber + "-" + highArticleNumber);
   }

   @Deprecated
   public Reader retrieveHeader(String header, int lowArticleNumber, int highArticleNumber) throws IOException {
      return this.retrieveHeader(header, (long)lowArticleNumber, (long)highArticleNumber);
   }

   @Deprecated
   public Reader retrieveArticleInfo(int lowArticleNumber, int highArticleNumber) throws IOException {
      return this.retrieveArticleInfo((long)lowArticleNumber, (long)highArticleNumber);
   }

   @Deprecated
   public Reader retrieveHeader(String a, int b) throws IOException {
      return this.retrieveHeader(a, (long)b);
   }

   @Deprecated
   public boolean selectArticle(int a, ArticlePointer ap) throws IOException {
      ArticleInfo ai = this.__ap2ai(ap);
      boolean b = this.selectArticle((long)a, ai);
      this.__ai2ap(ai, ap);
      return b;
   }

   @Deprecated
   public Reader retrieveArticleInfo(int lowArticleNumber) throws IOException {
      return this.retrieveArticleInfo((long)lowArticleNumber);
   }

   @Deprecated
   public boolean selectArticle(int a) throws IOException {
      return this.selectArticle((long)a);
   }

   @Deprecated
   public Reader retrieveArticleHeader(int a) throws IOException {
      return this.retrieveArticleHeader((long)a);
   }

   @Deprecated
   public Reader retrieveArticleHeader(int a, ArticlePointer ap) throws IOException {
      ArticleInfo ai = this.__ap2ai(ap);
      Reader rdr = this.retrieveArticleHeader((long)a, ai);
      this.__ai2ap(ai, ap);
      return rdr;
   }

   @Deprecated
   public Reader retrieveArticleBody(int a) throws IOException {
      return this.retrieveArticleBody((long)a);
   }

   @Deprecated
   public Reader retrieveArticle(int articleNumber, ArticlePointer pointer) throws IOException {
      ArticleInfo ai = this.__ap2ai(pointer);
      Reader rdr = this.retrieveArticle((long)articleNumber, ai);
      this.__ai2ap(ai, pointer);
      return rdr;
   }

   @Deprecated
   public Reader retrieveArticle(int articleNumber) throws IOException {
      return this.retrieveArticle((long)articleNumber);
   }

   @Deprecated
   public Reader retrieveArticleBody(int a, ArticlePointer ap) throws IOException {
      ArticleInfo ai = this.__ap2ai(ap);
      Reader rdr = this.retrieveArticleBody((long)a, ai);
      this.__ai2ap(ai, ap);
      return rdr;
   }

   @Deprecated
   public Reader retrieveArticle(String articleId, ArticlePointer pointer) throws IOException {
      ArticleInfo ai = this.__ap2ai(pointer);
      Reader rdr = this.retrieveArticle(articleId, ai);
      this.__ai2ap(ai, pointer);
      return rdr;
   }

   @Deprecated
   public Reader retrieveArticleBody(String articleId, ArticlePointer pointer) throws IOException {
      ArticleInfo ai = this.__ap2ai(pointer);
      Reader rdr = this.retrieveArticleBody(articleId, ai);
      this.__ai2ap(ai, pointer);
      return rdr;
   }

   @Deprecated
   public Reader retrieveArticleHeader(String articleId, ArticlePointer pointer) throws IOException {
      ArticleInfo ai = this.__ap2ai(pointer);
      Reader rdr = this.retrieveArticleHeader(articleId, ai);
      this.__ai2ap(ai, pointer);
      return rdr;
   }

   @Deprecated
   public boolean selectArticle(String articleId, ArticlePointer pointer) throws IOException {
      ArticleInfo ai = this.__ap2ai(pointer);
      boolean b = this.selectArticle(articleId, ai);
      this.__ai2ap(ai, pointer);
      return b;
   }

   @Deprecated
   public boolean selectArticle(ArticlePointer pointer) throws IOException {
      ArticleInfo ai = this.__ap2ai(pointer);
      boolean b = this.selectArticle(ai);
      this.__ai2ap(ai, pointer);
      return b;
   }

   @Deprecated
   public boolean selectNextArticle(ArticlePointer pointer) throws IOException {
      ArticleInfo ai = this.__ap2ai(pointer);
      boolean b = this.selectNextArticle(ai);
      this.__ai2ap(ai, pointer);
      return b;
   }

   @Deprecated
   public boolean selectPreviousArticle(ArticlePointer pointer) throws IOException {
      ArticleInfo ai = this.__ap2ai(pointer);
      boolean b = this.selectPreviousArticle(ai);
      this.__ai2ap(ai, pointer);
      return b;
   }

   private ArticleInfo __ap2ai(ArticlePointer ap) {
      return ap == null ? null : new ArticleInfo();
   }

   private void __ai2ap(ArticleInfo ai, ArticlePointer ap) {
      if (ap != null) {
         ap.articleId = ai.articleId;
         ap.articleNumber = (int)ai.articleNumber;
      }
   }
}
