/** Whether a cue carries information or is texture. `reduced` mode drops chatter. */
export type CueClass = "informative" | "chatter";

/** One sound to play, named by its FMOD event path. */
export interface SoundCue {
  event: string;
  cls: CueClass;
  /** 0..1 multiplier applied on top of the user's volume. Defaults to 1. */
  gain?: number;
}
