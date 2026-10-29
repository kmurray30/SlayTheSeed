//! Prints sansb/sts2-sim's (MinInitialHp, MaxInitialHp) per monster kind at a
//! given ascension (default 0), one JSON object. NOT part of sansb/sts2-sim:
//! a spire-mentor cross-engine content-diff helper.
//! Usage: xsim_hp_bands [ASCENSION]
use serde_json::{Map, Value, json};
use sts_sim::encounters::hp_band;
use sts_sim::ids::MonsterKind;

fn main() {
    let asc: u8 = std::env::args()
        .nth(1)
        .and_then(|a| a.parse().ok())
        .unwrap_or(0);
    let mut out = Map::new();
    for name in MonsterKind::NAMES {
        let kind = MonsterKind::from_str(name).expect("a listed name parses");
        let v = match hp_band(kind, asc) {
            Ok((lo, hi)) => json!([lo, hi]),
            Err(e) => json!(format!("{e:?}")),
        };
        out.insert(name.to_string(), v);
    }
    println!(
        "{}",
        json!({"ascension": asc, "hp_bands": Value::Object(out)})
    );
}
