import os
from unittest.mock import Mock

from hathizip import utils


def test_has_subdirs_false(monkeypatch):
    def scandir(path):
        res = []
        return res
    requested_path = os.path.join("some", "path")
    monkeypatch.setattr(utils.os, "scandir", scandir)
    assert utils.has_subdirs(requested_path) is False


def test_has_subdirs_true(monkeypatch):
    def scandir(path):
        mock_dir = Mock(path=os.path.join(path, "something"))
        mock_dir.is_dir = Mock(return_value=True)
        return [
            mock_dir
        ]

    requested_path = os.path.join("some", "path")
    monkeypatch.setattr(utils.os, "scandir", scandir)
    assert utils.has_subdirs(requested_path) is True
