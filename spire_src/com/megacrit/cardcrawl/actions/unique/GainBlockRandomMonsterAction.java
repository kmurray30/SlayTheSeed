package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.vfx.combat.FlashAtkImgEffect;
import java.util.ArrayList;

public class GainBlockRandomMonsterAction extends AbstractGameAction {
   public GainBlockRandomMonsterAction(AbstractCreature source, int amount) {
      this.duration = 0.5F;
      this.source = source;
      this.amount = amount;
      this.actionType = AbstractGameAction.ActionType.BLOCK;
   }

   @Override
   public void update() {
      if (this.duration == 0.5F) {
         ArrayList<AbstractMonster> validMonsters = new ArrayList<>();

         for (AbstractMonster m : AbstractDungeon.getMonsters().monsters) {
            if (m != this.source && m.intent != AbstractMonster.Intent.ESCAPE && !m.isDying) {
               validMonsters.add(m);
            }
         }

         if (!validMonsters.isEmpty()) {
            this.target = validMonsters.get(AbstractDungeon.aiRng.random(validMonsters.size() - 1));
         } else {
            this.target = this.source;
         }

         if (this.target != null) {
            AbstractDungeon.effectList.add(new FlashAtkImgEffect(this.target.hb.cX, this.target.hb.cY, AbstractGameAction.AttackEffect.SHIELD));
            this.target.addBlock(this.amount);
         }
      }

      this.tickDuration();
   }
}
