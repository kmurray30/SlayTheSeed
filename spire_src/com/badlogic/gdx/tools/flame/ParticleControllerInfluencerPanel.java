package com.badlogic.gdx.tools.flame;

import com.badlogic.gdx.graphics.g3d.particles.ParticleController;
import com.badlogic.gdx.graphics.g3d.particles.ParticleEffect;
import com.badlogic.gdx.graphics.g3d.particles.influencers.ParticleControllerInfluencer;
import com.badlogic.gdx.utils.Array;
import java.awt.GridBagConstraints;
import java.awt.Insets;

public class ParticleControllerInfluencerPanel
   extends InfluencerPanel<ParticleControllerInfluencer>
   implements TemplatePickerPanel.Listener<ParticleController>,
   LoaderButton.Listener<ParticleEffect>,
   EventManager.Listener {
   TemplatePickerPanel<ParticleController> controllerPicker;

   public ParticleControllerInfluencerPanel(FlameMain editor, ParticleControllerInfluencer influencer, boolean single, String name, String desc) {
      super(editor, influencer, name, desc, true, false);
      this.controllerPicker.setMultipleSelectionAllowed(!single);
      EventManager.get().attach(0, this);
   }

   public void setValue(ParticleControllerInfluencer value) {
      super.setValue(value);
      if (value != null) {
         this.controllerPicker.setValue(value.templates);
      }
   }

   @Override
   protected void initializeComponents() {
      super.initializeComponents();
      this.controllerPicker = new TemplatePickerPanel<ParticleController>(this.editor, null, this, ParticleController.class) {
         protected String getTemplateName(ParticleController template, int index) {
            return template.name;
         }
      };
      this.reloadControllers();
      this.controllerPicker.setIsAlwayShown(true);
      this.contentPanel
         .add(new LoaderButton.ParticleEffectLoaderButton(this.editor, this), new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, 10, 1, new Insets(0, 0, 0, 6), 0, 0));
      this.contentPanel.add(this.controllerPicker, new GridBagConstraints(0, 1, 1, 1, 1.0, 1.0, 10, 1, new Insets(0, 0, 0, 6), 0, 0));
   }

   public void onTemplateChecked(ParticleController model, boolean isChecked) {
      this.editor.restart();
   }

   public void onResourceLoaded(ParticleEffect resource) {
      this.reloadControllers();
   }

   private void reloadControllers() {
      Array<ParticleEffect> effects = new Array<>();
      Array<ParticleController> controllers = new Array<>();
      this.editor.assetManager.getAll(ParticleEffect.class, effects);

      for (ParticleEffect effect : effects) {
         controllers.addAll(effect.getControllers());
      }

      this.controllerPicker.setLoadedTemplates(controllers);
   }

   @Override
   public void handle(int aEventType, Object aEventData) {
      if (aEventType == 0) {
         Object[] data = (Object[])aEventData;
         if (data[0] instanceof ParticleEffect) {
            ParticleEffect oldEffect = (ParticleEffect)data[0];
            int currentCount = this.value.templates.size;
            this.value.templates.removeAll(oldEffect.getControllers(), true);
            if (this.value.templates.size != currentCount) {
               int diff = currentCount - this.value.templates.size;
               if (diff > 0) {
                  ParticleEffect newEffect = (ParticleEffect)data[1];
                  Array<ParticleController> newControllers = newEffect.getControllers();
                  if (newControllers.size > 0) {
                     int i = 0;

                     for (int c = Math.min(diff, newControllers.size); i < c; i++) {
                        this.value.templates.add(newControllers.get(i));
                     }
                  }
               } else {
                  this.value.templates.addAll(this.editor.assetManager.<ParticleEffect>get("pre_particle.png").getControllers());
               }

               this.controllerPicker.reloadTemplates();
               this.controllerPicker.setValue(this.value.templates);
               this.editor.restart();
            }
         }
      }
   }
}
