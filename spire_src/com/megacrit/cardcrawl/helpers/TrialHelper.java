package com.megacrit.cardcrawl.helpers;

import com.megacrit.cardcrawl.trials.AbstractTrial;
import com.megacrit.cardcrawl.trials.AnyColorDraftTrial;
import com.megacrit.cardcrawl.trials.CursedTrial;
import com.megacrit.cardcrawl.trials.DraftTrial;
import com.megacrit.cardcrawl.trials.HoarderTrial;
import com.megacrit.cardcrawl.trials.InceptionTrial;
import com.megacrit.cardcrawl.trials.LoseMaxHpTrial;
import com.megacrit.cardcrawl.trials.MyTrueFormTrial;
import com.megacrit.cardcrawl.trials.OneHpTrial;
import com.megacrit.cardcrawl.trials.RandomModsTrial;
import com.megacrit.cardcrawl.trials.SlowpokeTrial;
import com.megacrit.cardcrawl.trials.SneckoTrial;
import com.megacrit.cardcrawl.trials.StarterDeckTrial;
import java.util.HashMap;

public class TrialHelper {
   private static HashMap<String, TrialHelper.TRIAL> trialKeysMap;

   private static void initialize() {
      if (trialKeysMap == null) {
         trialKeysMap = new HashMap<>();
         trialKeysMap.put(formatKey("RandomMods"), TrialHelper.TRIAL.RANDOM_MODS);
         trialKeysMap.put(formatKey("DailyMods"), TrialHelper.TRIAL.RANDOM_MODS);
         trialKeysMap.put(formatKey("StarterDeck"), TrialHelper.TRIAL.NO_CARD_DROPS);
         trialKeysMap.put(formatKey("Inception"), TrialHelper.TRIAL.UNCEASING_TOP);
         trialKeysMap.put(formatKey("FadeAway"), TrialHelper.TRIAL.LOSE_MAX_HP);
         trialKeysMap.put(formatKey("PraiseSnecko"), TrialHelper.TRIAL.SNECKO);
         trialKeysMap.put(formatKey("YoureTooSlow"), TrialHelper.TRIAL.SLOW);
         trialKeysMap.put(formatKey("MyTrueForm"), TrialHelper.TRIAL.FORMS);
         trialKeysMap.put(formatKey("Draft"), TrialHelper.TRIAL.DRAFT);
         trialKeysMap.put(formatKey("MegaDraft"), TrialHelper.TRIAL.MEGA_DRAFT);
         trialKeysMap.put(formatKey("1HitWonder"), TrialHelper.TRIAL.ONE_HP);
         trialKeysMap.put(formatKey("MoreCards"), TrialHelper.TRIAL.MORE_CARDS);
         trialKeysMap.put(formatKey("Cursed"), TrialHelper.TRIAL.CURSED);
      }
   }

   private static String formatKey(String key) {
      return SeedHelper.sterilizeString(key);
   }

   public static boolean isTrialSeed(String seed) {
      initialize();
      return trialKeysMap.containsKey(seed);
   }

   public static AbstractTrial getTrialForSeed(String seed) {
      initialize();
      if (seed == null) {
         return null;
      } else {
         TrialHelper.TRIAL picked = trialKeysMap.get(seed);
         if (picked == null) {
            return null;
         } else {
            switch (picked) {
               case RANDOM_MODS:
                  return new RandomModsTrial();
               case NO_CARD_DROPS:
                  return new StarterDeckTrial();
               case UNCEASING_TOP:
                  return new InceptionTrial();
               case LOSE_MAX_HP:
                  return new LoseMaxHpTrial();
               case SNECKO:
                  return new SneckoTrial();
               case SLOW:
                  return new SlowpokeTrial();
               case FORMS:
                  return new MyTrueFormTrial();
               case DRAFT:
                  return new DraftTrial();
               case MEGA_DRAFT:
                  return new AnyColorDraftTrial();
               case ONE_HP:
                  return new OneHpTrial();
               case MORE_CARDS:
                  return new HoarderTrial();
               case CURSED:
                  return new CursedTrial();
               default:
                  return null;
            }
         }
      }
   }

   private static enum TRIAL {
      RANDOM_MODS,
      NO_CARD_DROPS,
      UNCEASING_TOP,
      LOSE_MAX_HP,
      SNECKO,
      SLOW,
      FORMS,
      DRAFT,
      MEGA_DRAFT,
      ONE_HP,
      MORE_CARDS,
      CURSED;
   }
}
