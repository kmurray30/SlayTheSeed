package com.badlogic.gdx.tools.flame;

import com.badlogic.gdx.graphics.g3d.particles.emitters.RegularEmitter;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JCheckBox;

public class RegularEmitterPanel extends EditorPanel<RegularEmitter> {
   CountPanel countPanel;
   RangedNumericPanel delayPanel;
   RangedNumericPanel durationPanel;
   ScaledNumericPanel emissionPanel;
   ScaledNumericPanel lifePanel;
   ScaledNumericPanel lifeOffsetPanel;
   JCheckBox continuousCheckbox;

   public RegularEmitterPanel(FlameMain particleEditor3D, RegularEmitter emitter) {
      super(particleEditor3D, "Regular Emitter", "This is a generic emitter used to generate particles regularly.");
      this.initializeComponents(emitter);
      this.setValue(null);
   }

   private void initializeComponents(RegularEmitter emitter) {
      this.continuousCheckbox = new JCheckBox("Continuous");
      this.continuousCheckbox.setSelected(emitter.isContinuous());
      this.continuousCheckbox.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            RegularEmitter emitterx = (RegularEmitter)RegularEmitterPanel.this.editor.getEmitter().emitter;
            emitterx.setContinuous(RegularEmitterPanel.this.continuousCheckbox.isSelected());
         }
      });
      this.continuousCheckbox.setHorizontalTextPosition(2);
      int i = 0;
      this.addContent(i++, 0, this.continuousCheckbox, 17, 0);
      this.addContent(
         i++,
         0,
         this.countPanel = new CountPanel(
            this.editor, "Count", "Min number of particles at all times, max number of particles allowed.", emitter.minParticleCount, emitter.maxParticleCount
         )
      );
      this.addContent(
         i++,
         0,
         this.delayPanel = new RangedNumericPanel(
            this.editor, emitter.getDelay(), "Delay", "Time from beginning of effect to emission start, in milliseconds.", false
         )
      );
      this.addContent(
         i++,
         0,
         this.durationPanel = new RangedNumericPanel(this.editor, emitter.getDuration(), "Duration", "Time particles will be emitted, in milliseconds.")
      );
      this.addContent(
         i++,
         0,
         this.emissionPanel = new ScaledNumericPanel(this.editor, emitter.getEmission(), "Duration", "Emission", "Number of particles emitted per second.")
      );
      this.addContent(
         i++, 0, this.lifePanel = new ScaledNumericPanel(this.editor, emitter.getLife(), "Duration", "Life", "Time particles will live, in milliseconds.")
      );
      this.addContent(
         i++,
         0,
         this.lifeOffsetPanel = new ScaledNumericPanel(
            this.editor, emitter.getLifeOffset(), "Duration", "Life Offset", "Particle starting life consumed, in milliseconds.", false
         )
      );
   }
}
