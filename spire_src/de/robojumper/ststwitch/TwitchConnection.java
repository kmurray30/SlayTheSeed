package de.robojumper.ststwitch;

import com.gikk.twirk.Twirk;
import com.gikk.twirk.TwirkBuilder;
import com.gikk.twirk.events.TwirkListener;
import com.gikk.twirk.types.twitchMessage.TwitchMessage;
import com.gikk.twirk.types.users.TwitchUser;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TwitchConnection extends Thread {
   private final Twirk twirk;
   private static final Logger logger = LogManager.getLogger(TwitchConnection.class.getName());
   private volatile boolean wantsConnection = false;
   private volatile boolean shuttingDown = false;
   private int connectionAttempts = 0;
   private List<TwitchMessageListener> listeners = new ArrayList<>();
   private final TwitchConfig twitchConfig;
   private Queue<String> outMessages = new ArrayDeque<>();
   private Queue<TwitchConnection.MessageUserPair> inMessages = new ArrayDeque<>();
   private boolean hasPulse;
   private final Object gameLock = new Object();
   private static final Pattern QUOTE_PATTERN = Pattern.compile("(?i)\"(.*)\"(?:.*?)-?- ?joinrbs(?:[ \\.,].*)?");

   public TwitchConfig getTwitchConfig() {
      return this.twitchConfig;
   }

   public TwitchConnection(TwitchConfig twitchConfig) throws IOException {
      this.twitchConfig = twitchConfig;
      logger.info(
         "Connecting: username="
            + twitchConfig.getUsername()
            + ", token(truncated)="
            + truncateToken(twitchConfig.getToken())
            + ", channel="
            + twitchConfig.getChannel()
      );
      TwirkBuilder tb = new TwirkBuilder(twitchConfig.getChannel(), twitchConfig.getUsername(), twitchConfig.getToken());
      this.twirk = tb.setSSL(true).build();
      this.twirk.addIrcListener(getMessageListener(this.twirk, this.gameLock, this));
   }

   private static String truncateToken(String token) {
      return token.length() > 10 ? token.substring(0, 10) : token;
   }

   @Override
   public void run() {
      while (!this.shuttingDown) {
         if (this.wantsConnection && !this.twirk.isConnected()) {
            this.attemptConnect();
         } else if (!this.wantsConnection && this.twirk.isConnected()) {
            this.twirk.disconnect();
         }

         if (this.twirk.isConnected()) {
            String message;
            synchronized (this.gameLock) {
               while ((message = this.outMessages.poll()) != null) {
                  this.twirk.channelMessage(message);
               }
            }
         }

         try {
            Thread.sleep(1000L);
         } catch (InterruptedException var4) {
         }
      }

      this.twirk.close();
   }

   public void update() {
      synchronized (this.gameLock) {
         TwitchConnection.MessageUserPair message;
         while ((message = this.inMessages.poll()) != null) {
            for (TwitchMessageListener l : this.listeners) {
               l.onMessage(message.message, message.user);
            }
         }
      }
   }

   void registerMessageListener(TwitchMessageListener l) {
      synchronized (this.gameLock) {
         this.listeners.add(l);
      }
   }

   public void setStatus(boolean shouldBeRunning) {
      synchronized (this.twirk) {
         this.wantsConnection = shouldBeRunning;
      }
   }

   void toggleStatus() {
      synchronized (this.twirk) {
         this.setStatus(!this.wantsConnection);
      }
   }

   public void sendMessage(String msg) {
      synchronized (this.gameLock) {
         this.outMessages.add(msg);
         this.interrupt();
      }
   }

   TwitchConnection.ConnectionStatus getConnectionStatus() {
      if (this.wantsConnection) {
         return this.twirk.isConnected() ? TwitchConnection.ConnectionStatus.CONNECTED : TwitchConnection.ConnectionStatus.CONNECTING;
      } else {
         return this.twirk.isConnected() ? TwitchConnection.ConnectionStatus.DISCONNECTING : TwitchConnection.ConnectionStatus.DISCONNECTED;
      }
   }

   boolean popPulse() {
      synchronized (this.gameLock) {
         if (this.hasPulse) {
            this.hasPulse = false;
            return true;
         } else {
            return this.hasPulse;
         }
      }
   }

   public void dispose() {
      this.shuttingDown = true;
   }

   private void attemptConnect() {
      if (!this.twirk.isConnected() && !this.shuttingDown) {
         try {
            this.twirk.connect();
         } catch (InterruptedException | IOException var5) {
            this.connectionAttempts++;
            if (this.connectionAttempts > 3) {
               this.connectionAttempts = 0;
               synchronized (this.twirk) {
                  this.wantsConnection = false;
               }
            }
         }
      }
   }

   private static TwirkListener getMessageListener(Twirk twirk, final Object gameLock, final TwitchConnection conn) {
      return new TwirkListener() {
         @Override
         public void onDisconnect() {
         }

         @Override
         public void onPrivMsg(TwitchUser sender, TwitchMessage message) {
            String content = message.getContent().trim();
            String senderName = sender.getUserName();
            if (content.length() > 1) {
               synchronized (gameLock) {
                  Matcher m = TwitchConnection.QUOTE_PATTERN.matcher(content);
                  if (content.equals("#pulse")) {
                     conn.hasPulse = true;
                  }

                  conn.inMessages.add(new TwitchConnection.MessageUserPair(content, senderName));
               }
            }
         }
      };
   }

   public static enum ConnectionStatus {
      CONNECTED,
      CONNECTING,
      DISCONNECTING,
      DISCONNECTED;
   }

   private static class MessageUserPair {
      final String message;
      final String user;

      MessageUserPair(String m, String u) {
         this.message = m;
         this.user = u;
      }
   }
}
