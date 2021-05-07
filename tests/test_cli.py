import os

from hathizip import cli, process
from unittest.mock import Mock
import argparse
import pytest


def test_version_exits_after_being_called(monkeypatch):

    parser = cli.get_parser()
    version_exit_mock = Mock()

    with monkeypatch.context() as m:
        m.setattr(argparse.ArgumentParser, "exit", version_exit_mock)
        parser.parse_args(["--version"])

    version_exit_mock.assert_called()


def test_main_cli_args_calls_compress_folder(monkeypatch, tmpdir):
    src = tmpdir / "src"
    src.ensure_dir()

    dst = tmpdir / "dst"
    dst.ensure_dir()

    def mock_parse(*args, **kwargs):
        m = argparse.Namespace(
            path=src.strpath,
            dest=dst.strpath,
            debug=False,
            remove=False,
            log_debug=None
        )
        return m

    def mock_scan_dir(*args, **kwargs):
        scan_mock = Mock()
        scan_mock.is_dir = Mock(return_value=True)
        yield scan_mock

    with monkeypatch.context() as m:
        m.setattr(os, "scandir", mock_scan_dir)
        m.setattr(argparse.ArgumentParser, "parse_args", mock_parse)
        mock_compress_folder = Mock()
        m.setattr(process, "compress_folder", mock_compress_folder)
        cli.main()
        mock_compress_folder.assert_called()


class TestDestinationPath:
    def test_valid(self, monkeypatch):
        sample_path = os.path.join("sample", "path")

        monkeypatch.setattr(
            cli.os.path, "exists", lambda path: path == sample_path
        )

        monkeypatch.setattr(
            cli.os.path, "isdir", lambda path: path == sample_path
        )

        cli.destination_path(sample_path)

    def test_non_existent_fails(self, monkeypatch):
        sample_path = os.path.join("invalid", "path", "does", "not", "exists")

        monkeypatch.setattr(
            cli.os.path, "exists", lambda path: False
        )

        monkeypatch.setattr(
            cli.os.path, "isdir", lambda path: path == sample_path
        )
        with pytest.raises(ValueError):
            cli.destination_path(sample_path)

    def test_use_files_fails(self, monkeypatch):
        sample_path = os.path.join("sample", "path.txt")

        monkeypatch.setattr(
            cli.os.path, "exists", lambda path: path == sample_path
        )

        monkeypatch.setattr(
            cli.os.path, "isdir", lambda path: False
        )
        with pytest.raises(ValueError):
            cli.destination_path(sample_path)


class TestMain:
    def test_valid(self, monkeypatch):
        source = os.path.join("some", "valid", "source")
        monkeypatch.setattr(
            cli.os.path, "exists", lambda path: path == source
        )

        monkeypatch.setattr(
            cli.os.path, "isdir", lambda path: path == source
        )
        monkeypatch.setattr(cli, "has_subdirs", lambda path: path == source)

        def mock_scan_dir(path):
            x = Mock(path=os.path.join(path, "dummy.txt"))
            x.name = "dummy.txt"
            res = [
                x
            ]
            return res

        monkeypatch.setattr(
            cli.os, "scandir", mock_scan_dir
        )
        compress_folder = Mock()
        monkeypatch.setattr(cli.process, "compress_folder", compress_folder)
        cli.main(argv=[source])
        assert compress_folder.called is True

    def test_valid_removed(self, monkeypatch):
        source = os.path.join("some", "valid", "source")
        monkeypatch.setattr(
            cli.os.path, "exists", lambda path: path == source
        )

        monkeypatch.setattr(
            cli.os.path, "isdir", lambda path: path == source
        )
        monkeypatch.setattr(cli, "has_subdirs", lambda path: path == source)

        def mock_scan_dir(path):
            x = Mock(path=os.path.join(path, "dummy.txt"))
            x.name = "dummy.txt"
            res = [
                x
            ]
            return res

        monkeypatch.setattr(
            cli.os, "scandir", mock_scan_dir
        )
        compress_folder = Mock()
        monkeypatch.setattr(cli.process, "compress_folder", compress_folder)

        rmtree = Mock()
        monkeypatch.setattr(cli.shutil, "rmtree", rmtree)
        cli.main(argv=[source, "--remove"])

        assert compress_folder.called is True and \
               rmtree.called is True
