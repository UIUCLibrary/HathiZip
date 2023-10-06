"""Logging configurations."""

import logging
import sys
from typing import Optional


def configure_logger(
        debug_mode: bool = False,
        log_file: Optional[str] = None
) -> logging.Logger:
    """Configure logger.

    Useful for passing logging information

    Args:
        debug_mode:
        log_file:

    Returns:
        configured logger

    """
    logger = logging.getLogger(__package__)
    logger.setLevel(logging.DEBUG)
    debug_formatter = \
        logging.Formatter(
            '%(asctime)s - %(name)s - %(levelname)s - %(message)s'
        )

    std_handler = logging.StreamHandler(sys.stdout)
    if log_file:
        file_handler = logging.FileHandler(filename=log_file)
        file_handler.setLevel(logging.DEBUG)
        file_handler.setFormatter(debug_formatter)
        logger.addHandler(file_handler)

    if debug_mode:
        print("Debug mode")
        std_handler.setLevel(logging.DEBUG)
        std_handler.setFormatter(debug_formatter)
    else:
        std_handler.setLevel(logging.INFO)
        logger.setLevel(logging.INFO)

    # std_handler.setFormatter(debug_formatter)

    logger.addHandler(std_handler)
    return logger
