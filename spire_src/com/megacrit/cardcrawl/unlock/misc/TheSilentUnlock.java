package com.megacrit.cardcrawl.unlock.misc;

import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.unlock.AbstractUnlock;

public class TheSilentUnlock extends AbstractUnlock {
   public static final String KEY = "The Silent";

   public TheSilentUnlock() {
      this.type = AbstractUnlock.UnlockType.CHARACTER;
      this.key = "The Silent";
      this.title = "The Silent";
   }

   @Override
   public void onUnlockScreenOpen() {
      this.player = CardCrawlGame.characterManager.getCharacter(AbstractPlayer.PlayerClass.THE_SILENT);
      this.player.drawX = Settings.WIDTH / 2.0F - 20.0F * Settings.scale;
      this.player.drawY = Settings.HEIGHT / 2.0F - 118.0F * Settings.scale;
   }
}
