"""Bootstrap the hathizip cli."""

import sys

from hathizip import cli


def main() -> None:
    """Check if pytest arg is run else run main entry point."""
    if len(sys.argv) > 1 and sys.argv[1] == "--pytest":
        # pylint: disable=import-outside-toplevel
        import pytest  # type: ignore
        sys.exit(pytest.main(sys.argv[2:]))
    else:
        cli.main()


if __name__ == '__main__':
    main()
