from simlab.cli import main

# Guard the entry point: with the "spawn"/"forkserver" start methods the runner
# uses (see simlab.runner), worker processes re-import this module. Without this
# guard each worker would re-run the CLI and recursively spawn more sweeps.
if __name__ == "__main__":
    raise SystemExit(main())
