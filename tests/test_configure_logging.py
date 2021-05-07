import logging
from unittest.mock import mock_open, patch


def test_configure_logging_returns_a_logger():
    from hathizip import configure_logging
    logger = configure_logging.configure_logger()
    assert isinstance(logger, logging.Logger)
    for h in logger.handlers:
        logger.removeHandler(h)


def test_configure_logging_with_debug_mode():
    from hathizip import configure_logging
    logger = configure_logging.configure_logger(debug_mode=True)
    assert logger.handlers[0].level == logging.DEBUG
    for h in logger.handlers:
        logger.removeHandler(h)


def test_configure_logging_with_log_file():
    import os
    from hathizip import configure_logging
    sample_output_file = os.path.join("some", "output", "logging.log")

    m = mock_open()
    with patch('hathizip.configure_logging.logging.open', m):
        logger = configure_logging.configure_logger(log_file=sample_output_file)
    assert m.called is True

    for h in logger.handlers:
        logger.removeHandler(h)
