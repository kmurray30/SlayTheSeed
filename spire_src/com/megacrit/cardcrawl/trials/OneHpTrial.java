package com.megacrit.cardcrawl.trials;

import com.megacrit.cardcrawl.characters.AbstractPlayer;

public class OneHpTrial extends AbstractTrial {
   @Override
   public AbstractPlayer setupPlayer(AbstractPlayer player) {
      player.currentHealth = 1;
      player.maxHealth = 1;
      return player;
   }
}
