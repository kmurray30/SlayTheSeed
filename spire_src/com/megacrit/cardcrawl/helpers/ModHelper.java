package com.megacrit.cardcrawl.helpers;

import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.daily.mods.AbstractDailyMod;
import com.megacrit.cardcrawl.daily.mods.Allstar;
import com.megacrit.cardcrawl.daily.mods.BigGameHunter;
import com.megacrit.cardcrawl.daily.mods.Binary;
import com.megacrit.cardcrawl.daily.mods.BlueCards;
import com.megacrit.cardcrawl.daily.mods.Brewmaster;
import com.megacrit.cardcrawl.daily.mods.CertainFuture;
import com.megacrit.cardcrawl.daily.mods.Chimera;
import com.megacrit.cardcrawl.daily.mods.ColorlessCards;
import com.megacrit.cardcrawl.daily.mods.Colossus;
import com.megacrit.cardcrawl.daily.mods.ControlledChaos;
import com.megacrit.cardcrawl.daily.mods.CursedRun;
import com.megacrit.cardcrawl.daily.mods.DeadlyEvents;
import com.megacrit.cardcrawl.daily.mods.Diverse;
import com.megacrit.cardcrawl.daily.mods.Draft;
import com.megacrit.cardcrawl.daily.mods.Flight;
import com.megacrit.cardcrawl.daily.mods.GreenCards;
import com.megacrit.cardcrawl.daily.mods.Heirloom;
import com.megacrit.cardcrawl.daily.mods.Hoarder;
import com.megacrit.cardcrawl.daily.mods.Insanity;
import com.megacrit.cardcrawl.daily.mods.Lethality;
import com.megacrit.cardcrawl.daily.mods.Midas;
import com.megacrit.cardcrawl.daily.mods.NightTerrors;
import com.megacrit.cardcrawl.daily.mods.PurpleCards;
import com.megacrit.cardcrawl.daily.mods.RedCards;
import com.megacrit.cardcrawl.daily.mods.SealedDeck;
import com.megacrit.cardcrawl.daily.mods.Shiny;
import com.megacrit.cardcrawl.daily.mods.Specialized;
import com.megacrit.cardcrawl.daily.mods.Terminal;
import com.megacrit.cardcrawl.daily.mods.TimeDilation;
import com.megacrit.cardcrawl.daily.mods.Vintage;
import com.megacrit.cardcrawl.metrics.BotDataUploader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Map.Entry;

public class ModHelper {
   private static HashMap<String, AbstractDailyMod> starterMods = new HashMap<>();
   private static HashMap<String, AbstractDailyMod> genericMods = new HashMap<>();
   private static HashMap<String, AbstractDailyMod> difficultyMods = new HashMap<>();
   private static HashMap<String, AbstractDailyMod> legacyMods = new HashMap<>();
   public static ArrayList<AbstractDailyMod> enabledMods = new ArrayList<>();

   public static void initialize() {
      addStarterMod(new Shiny());
      addStarterMod(new Allstar());
      addStarterMod(new Draft());
      addStarterMod(new SealedDeck());
      addStarterMod(new Insanity());
      addStarterMod(new Heirloom());
      addStarterMod(new Specialized());
      addStarterMod(new Chimera());
      addStarterMod(new CursedRun());
      addGenericMod(new Diverse());
      addGenericMod(new RedCards());
      addGenericMod(new GreenCards());
      addGenericMod(new BlueCards());
      addGenericMod(new PurpleCards());
      addGenericMod(new ColorlessCards());
      addGenericMod(new TimeDilation());
      addGenericMod(new Vintage());
      addGenericMod(new Hoarder());
      addGenericMod(new Flight());
      addGenericMod(new CertainFuture());
      addGenericMod(new ControlledChaos());
      addDifficultyMod(new BigGameHunter());
      addDifficultyMod(new Lethality());
      addDifficultyMod(new NightTerrors());
      addDifficultyMod(new Binary());
      addDifficultyMod(new Midas());
      addDifficultyMod(new Terminal());
      addDifficultyMod(new DeadlyEvents());
      addLegacyMod(new Brewmaster());
      addLegacyMod(new Colossus());
   }

   private static void addStarterMod(AbstractDailyMod mod) {
      starterMods.put(mod.modID, mod);
   }

   private static void addGenericMod(AbstractDailyMod mod) {
      genericMods.put(mod.modID, mod);
   }

   private static void addDifficultyMod(AbstractDailyMod mod) {
      difficultyMods.put(mod.modID, mod);
   }

   private static void addLegacyMod(AbstractDailyMod mod) {
      legacyMods.put(mod.modID, mod);
   }

   public static void setMods(List<String> modIDs) {
      setModsFalse();

      for (String m : modIDs) {
         if (!m.equals("Endless")) {
            enabledMods.add(getMod(m));
         }
      }
   }

   public static AbstractDailyMod getMod(String key) {
      AbstractDailyMod mod = starterMods.get(key);
      if (mod == null) {
         mod = genericMods.get(key);
      }

      if (mod == null) {
         mod = difficultyMods.get(key);
      }

      if (mod == null) {
         mod = legacyMods.get(key);
      }

      return mod;
   }

   public static ArrayList<String> getEnabledModIDs() {
      ArrayList<String> enabled = new ArrayList<>();

      for (AbstractDailyMod m : enabledMods) {
         if (m != null) {
            enabled.add(m.modID);
         }
      }

      return enabled;
   }

   private static void setTheMods(HashMap<String, AbstractDailyMod> modMap, long daysSince1970, AbstractPlayer.PlayerClass characterClass) {
      ArrayList<AbstractDailyMod> shuffledList = new ArrayList<>();

      for (Entry<String, AbstractDailyMod> m : modMap.entrySet()) {
         if (m.getValue().classToExclude != characterClass) {
            shuffledList.add(m.getValue());
         }
      }

      int rotationConstant = 5;
      int modSelectionIndex = (int)(daysSince1970 % rotationConstant);
      if (modSelectionIndex < 0) {
         modSelectionIndex += rotationConstant;
      }

      int shuffleInterval = (int)(daysSince1970 / rotationConstant);
      Collections.shuffle(shuffledList, new Random(shuffleInterval));
      enabledMods.add(shuffledList.get(modSelectionIndex));
   }

   public static void setTodaysMods(long daysSince1970, AbstractPlayer.PlayerClass chosenClass) {
      setModsFalse();
      setTheMods(starterMods, daysSince1970, chosenClass);
      setTheMods(genericMods, daysSince1970, chosenClass);
      setTheMods(difficultyMods, daysSince1970, chosenClass);
   }

   public static boolean isModEnabled(String modID) {
      for (AbstractDailyMod m : enabledMods) {
         if (m != null && m.modID != null && m.modID.equals(modID)) {
            return true;
         }
      }

      return false;
   }

   public static void setModsFalse() {
      enabledMods.clear();
   }

   public static void uploadModData() {
      ArrayList<String> data = new ArrayList<>();

      for (Entry<String, AbstractDailyMod> m : starterMods.entrySet()) {
         data.add(m.getValue().gameDataUploadData());
      }

      for (Entry<String, AbstractDailyMod> m : genericMods.entrySet()) {
         data.add(m.getValue().gameDataUploadData());
      }

      for (Entry<String, AbstractDailyMod> m : difficultyMods.entrySet()) {
         data.add(m.getValue().gameDataUploadData());
      }

      BotDataUploader.uploadDataAsync(BotDataUploader.GameDataType.DAILY_MOD_DATA, AbstractDailyMod.gameDataUploadHeader(), data);
   }

   public static void clearNulls() {
      Iterator<AbstractDailyMod> m = enabledMods.iterator();

      while (m.hasNext()) {
         AbstractDailyMod derp = m.next();
         if (derp == null) {
            m.remove();
         }
      }
   }
}
