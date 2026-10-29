"""sts2-pycli replay harness — bead `sts2-rs-p0jzo` (W7).

Per-floor verification scaffolding that lets the W8 corpus driver
(``act1_replay.py``) drive the Rust ``RunManager`` engine through
scraped ``.run`` files and assert per-floor parity.

Modules:

- :mod:`scraped` — parse ``sts2_run_scraper/data/files/*.run`` into a
  typed ``Run`` / ``Floor`` view (one floor per ``map_point_history``
  entry).
- :mod:`driver` — phase-dispatched action loop scaffold; takes a
  ``Floor`` and a Rust ``RunManager`` and emits the actions implied by
  the scraped choices.
- :mod:`checkpoints` — per-floor assertion helpers; produces a typed
  ``Divergence`` list (don't fail-fast — log + continue) so the
  W8 driver can produce a full per-run diagnostic.
"""

from .checkpoints import Divergence, DivergenceKind, check_floor
from .driver import apply_floor
from .scraped import Floor, Run, parse_run
