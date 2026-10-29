"""STS2 Workbench — web backend for visualizing AI play over a run.

Additive tooling: serves recorded runs (JSONL) and game art to a browser
SPA. Does not touch the C# headless path, play.py, or the MCP server.
"""

from workbench.seed import canonicalize_seed

__version__ = "0.1.0"

__all__ = ["canonicalize_seed"]
