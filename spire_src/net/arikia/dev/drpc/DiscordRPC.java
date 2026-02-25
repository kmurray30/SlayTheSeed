package net.arikia.dev.drpc;

import com.sun.jna.Library;
import com.sun.jna.Native;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class DiscordRPC {
   private static final String DLL_VERSION = "3.3.0";
   private static boolean isInitialized = false;

   public static void discordInitialize(String applicationId, DiscordEventHandlers handlers, boolean autoRegister) {
      discordInitialize(applicationId, handlers, autoRegister, null, null);
   }

   public static void discordInitialize(String applicationId, DiscordEventHandlers handlers, boolean autoRegister, String steamId) {
      discordInitialize(applicationId, handlers, autoRegister, steamId, null);
   }

   public static void discordInitialize(String applicationId, DiscordEventHandlers handlers, boolean autoRegister, String steamId, File dllUnpackPath) {
      if (dllUnpackPath == null) {
         isInitialized = loadDLL();
      } else {
         isInitialized = loadDLL(dllUnpackPath);
      }

      DiscordRPC.DLL.INSTANCE.Discord_Initialize(applicationId, handlers, autoRegister ? 1 : 0, steamId);
   }

   public static void discordRegister(String applicationId, String command) {
      DiscordRPC.DLL.INSTANCE.Discord_Register(applicationId, command);
   }

   public static void discordRegisterSteam(String applicationId, String steamId) {
      DiscordRPC.DLL.INSTANCE.Discord_RegisterSteamGame(applicationId, steamId);
   }

   public static void discordUpdateEventHandlers(DiscordEventHandlers handlers) {
      DiscordRPC.DLL.INSTANCE.Discord_UpdateHandlers(handlers);
   }

   public static void discordShutdown() {
      DiscordRPC.DLL.INSTANCE.Discord_Shutdown();
   }

   public static void discordRunCallbacks() {
      DiscordRPC.DLL.INSTANCE.Discord_RunCallbacks();
   }

   public static void discordUpdatePresence(DiscordRichPresence presence) {
      DiscordRPC.DLL.INSTANCE.Discord_UpdatePresence(presence);
   }

   public static void discordClearPresence() {
      DiscordRPC.DLL.INSTANCE.Discord_ClearPresence();
   }

   public static void discordRespond(String userId, DiscordRPC.DiscordReply reply) {
      DiscordRPC.DLL.INSTANCE.Discord_Respond(userId, reply.reply);
   }

   private static boolean loadDLL() {
      String finalPath;
      String tempPath;
      if (OSUtil.isMac()) {
         String name = System.mapLibraryName("discord-rpc");
         File homeDir = new File(System.getProperty("user.home") + "/Library/Application Support/");
         finalPath = "/darwin/" + name;
         tempPath = homeDir + "/discord-rpc/" + name;
      } else if (OSUtil.isWindows()) {
         String name = System.mapLibraryName("discord-rpc");
         File homeDir = new File(System.getenv("TEMP"));
         boolean is64bit = System.getProperty("sun.arch.data.model").equals("64");
         finalPath = is64bit ? "/win-x64/" + name : "win-x86/" + name;
         tempPath = homeDir + "\\discord-rpc\\" + name;
      } else {
         String name = System.mapLibraryName("discord-rpc");
         File homeDir = new File(System.getProperty("user.home"), ".discord-rpc");
         finalPath = "/linux/" + name;
         tempPath = homeDir + "/" + name;
      }

      return loadLib(finalPath, new File(tempPath));
   }

   private static boolean loadDLL(File tempPath) {
      String name;
      String finalPath;
      if (OSUtil.isMac()) {
         name = System.mapLibraryName("discord-rpc");
         finalPath = "/darwin/" + name;
      } else if (OSUtil.isWindows()) {
         name = System.mapLibraryName("discord-rpc");
         boolean is64bit = System.getProperty("sun.arch.data.model").equals("64");
         finalPath = is64bit ? "/win-x64/" + name : "win-x86/" + name;
      } else {
         name = System.mapLibraryName("discord-rpc");
         finalPath = "/linux/" + name;
      }

      return loadLib(finalPath, new File(tempPath, name));
   }

   private static boolean loadLib(String source, File target) {
      try (
         InputStream in = DiscordRPC.class.getResourceAsStream(source);
         OutputStream out = openOutputStream(target);
      ) {
         copyFile(in, out);
         target.deleteOnExit();
      } catch (IOException var34) {
         var34.printStackTrace();
      }

      System.load(target.getAbsolutePath());
      return true;
   }

   private static void copyFile(InputStream input, OutputStream output) throws IOException {
      byte[] buffer = new byte[4096];

      int n;
      while (-1 != (n = input.read(buffer))) {
         output.write(buffer, 0, n);
      }
   }

   private static FileOutputStream openOutputStream(File file) throws IOException {
      if (file.exists()) {
         if (file.isDirectory()) {
            throw new IOException("File '" + file + "' exists but is a directory");
         }

         if (!file.canWrite()) {
            throw new IOException("File '" + file + "' cannot be written to");
         }
      } else {
         File parent = file.getParentFile();
         if (parent != null && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Directory '" + parent + "' could not be created");
         }
      }

      return new FileOutputStream(file);
   }

   private interface DLL extends Library {
      DiscordRPC.DLL INSTANCE = Native.loadLibrary("discord-rpc", DiscordRPC.DLL.class);

      void Discord_Initialize(String var1, DiscordEventHandlers var2, int var3, String var4);

      void Discord_Register(String var1, String var2);

      void Discord_RegisterSteamGame(String var1, String var2);

      void Discord_UpdateHandlers(DiscordEventHandlers var1);

      void Discord_Shutdown();

      void Discord_RunCallbacks();

      void Discord_UpdatePresence(DiscordRichPresence var1);

      void Discord_ClearPresence();

      void Discord_Respond(String var1, int var2);
   }

   public static enum DiscordReply {
      NO(0),
      YES(1),
      IGNORE(2);

      public final int reply;

      private DiscordReply(int reply) {
         this.reply = reply;
      }
   }
}
