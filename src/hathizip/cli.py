"""Simple interface for zipping from the commandline."""

import argparse
import os
import shutil
import sys
from typing import Optional, List

from hathizip import process, configure_logging
from hathizip.utils import has_subdirs


try:
    from importlib import metadata
except ImportError:
    import importlib_metadata as metadata  # type: ignore


def destination_path(path: str) -> str:
    """Validate the entry point for the cli args.

    Args:
        path: input string data to validate

    Returns:
        Returns full file path when input path is valid

    """
    if not os.path.exists(path):
        raise ValueError(f"{path} is an invalid path")

    if not os.path.isdir(path):
        raise ValueError(f"{path} is not a path")

    return os.path.abspath(path)


def get_parser() -> argparse.ArgumentParser:
    """Get cli argument parser.

    Returns:
        Argument parser

    """
    parser = argparse.ArgumentParser(
        description="Creates .zip file packages for HathiTrust.")

    try:
        version = metadata.version(__package__)
    except metadata.PackageNotFoundError:
        version = "dev"
    parser.add_argument(
        '--version',
        action='version',
        version=version
    )

    parser.add_argument(
        "path",
        help="Path to the HathiTrust folders to be zipped"
    )
    parser.add_argument(
        "--dest",
        type=destination_path,
        help="Alternative path to save the newly created HathiTrust zipped "
             "package for submission"
    )
    parser.add_argument(
        "--remove",
        action="store_true",
        help="Remove original files after successfully zipped"
    )

    debug_group = parser.add_argument_group("Debug")

    debug_group.add_argument(
        '--debug',
        action="store_true",
        help="Run script in debug mode")

    debug_group.add_argument(
        "--log-debug",
        dest="log_debug",
        help="Save debug information to a file"
    )

    return parser


def main(argv: Optional[List[str]] = None) -> None:
    """Run main entry point for the program."""
    argv = argv or sys.argv[1:]

    parser = get_parser()
    args = parser.parse_args(argv)

    if args.dest:
        # If an alternative destination path for the zip files is asked for,
        # use that.
        _destination_path = args.dest
    else:
        # Otherwise just put the newly created zip files in the same path
        _destination_path = args.path

    logger = configure_logging.configure_logger(
        debug_mode=args.debug,
        log_file=args.log_debug
    )

    if not has_subdirs(args.path):
        logger.error("No directories found at %s", args.path)
    for folder in filter(lambda x: x.is_dir(), os.scandir(args.path)):
        process.compress_folder(folder.path, dst=_destination_path)
        if args.remove:
            shutil.rmtree(folder.path)
            logger.info("Removing %s.", folder.path)


if __name__ == '__main__':
    main()
