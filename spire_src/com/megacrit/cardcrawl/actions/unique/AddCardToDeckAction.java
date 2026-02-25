package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndObtainEffect;

public class AddCardToDeckAction extends AbstractGameAction {
   AbstractCard cardToObtain;

   public AddCardToDeckAction(AbstractCard card) {
      this.cardToObtain = card;
      this.duration = 0.5F;
   }

   @Override
   public void update() {
      AbstractDungeon.effectList.add(new ShowCardAndObtainEffect(this.cardToObtain, Settings.WIDTH / 2.0F, Settings.HEIGHT / 2.0F));
      this.isDone = true;
   }
}
