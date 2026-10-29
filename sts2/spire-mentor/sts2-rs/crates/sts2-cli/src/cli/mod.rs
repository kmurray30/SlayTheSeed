//! `sts2-cli` argument parsing — subcommand-shaped surface.
//!
//! The binary takes one subcommand:
//!
//! * `combat` — drive a single combat (REPL or random agent). Carries
//!   every flag that used to live on the bare `Cli` struct verbatim.
//! * `list <kind>` — inspect static content (encounters, cards, relics,
//!   potions, monsters). Filters STS1-name Phase-4 scaffolding stubs by
//!   default; `--all` opts back into them.

pub mod combat;
pub mod fuzz;
pub mod list;
pub mod seedfind;

use clap::{Parser, Subcommand};

pub use combat::CombatArgs;
pub use fuzz::FuzzArgs;
pub use list::ListArgs;
pub use seedfind::SeedfindArgs;

#[derive(Debug, Parser)]
#[command(
    name = "sts2-cli",
    version,
    about = "Interactive REPL + scripted-replay driver for sts2-core."
)]
pub struct Cli {
    #[command(subcommand)]
    pub command: Command,

    /// Game build to simulate (`v0.107.1` or `v0.111.0`; the bare `0.111.0`
    /// form works too). Applies to every subcommand that builds a run or a
    /// combat. GH #787.
    #[arg(long, global = true, default_value_t = sts2_core::GameVersion::V0_107_1)]
    pub game_version: sts2_core::GameVersion,
}

#[derive(Debug, Subcommand)]
pub enum Command {
    /// Drive a single combat (REPL or random agent).
    Combat(CombatArgs),
    /// Inspect static content surfaces (encounters, cards, relics,
    /// potions, monsters).
    List(ListArgs),
    /// Seeded full-run crash/livelock sweep (the AutoSlay-style fuzzer).
    Fuzz(FuzzArgs),
    /// Search seeds for a specific run-content combination (GH #297).
    Seedfind(SeedfindArgs),
}

#[cfg(test)]
mod tests {
    use super::*;
    use sts2_core::GameVersion;

    #[test]
    fn game_version_defaults_to_v0107() {
        let cli = Cli::try_parse_from(["sts2-cli", "fuzz"]).expect("parses");
        assert_eq!(cli.game_version, GameVersion::V0_107_1);
    }

    #[test]
    fn game_version_is_global_and_parses_both_forms() {
        let before = Cli::try_parse_from(["sts2-cli", "--game-version", "v0.111.0", "fuzz"])
            .expect("before the subcommand");
        assert_eq!(before.game_version, GameVersion::V0_111_0);
        let after = Cli::try_parse_from(["sts2-cli", "fuzz", "--game-version", "0.111.0"])
            .expect("after the subcommand");
        assert_eq!(after.game_version, GameVersion::V0_111_0);
        assert!(Cli::try_parse_from(["sts2-cli", "--game-version", "0.1", "fuzz"]).is_err());
    }
}
