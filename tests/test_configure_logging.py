import logging
from unittest.mock import mock_open, patch
from hathizip import configure_logging


def test_configure_logging_returns_a_logger(caplog):
    logger = configure_logging.configure_logger()
    try:
        assert isinstance(logger, logging.Logger)
    finally:
        logger.handlers.clear()


def test_configure_logging_with_debug_mode(caplog):

    logger = configure_logging.configure_logger(debug_mode=True)
    try:
        assert logger.handlers[0].level == logging.DEBUG
    finally:
        logger.handlers.clear()


def test_configure_logging_with_log_file():
    import os
    sample_output_file = os.path.join("some", "output", "logging.log")
    try:
        m = mock_open()
        with patch('hathizip.configure_logging.logging.open', m):
            logger = configure_logging.configure_logger(log_file=sample_output_file)
        assert m.called is True
    finally:
        logger.handlers.clear()
