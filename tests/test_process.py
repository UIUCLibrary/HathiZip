import os

import itertools
import zipfile
from unittest.mock import Mock

from hathizip import process
import pytest

expected_files = [
    (os.path.join("hathigood", "2693684", "00000001.jp2"), os.path.join("2693684", "00000001.jp2")),
    (os.path.join("hathigood", "2693684", "00000001.txt"), os.path.join("2693684", "00000001.txt")),
    (os.path.join("hathigood", "2693684", "00000002.jp2"), os.path.join("2693684", "00000002.jp2")),
    (os.path.join("hathigood", "2693684", "00000002.txt"), os.path.join("2693684", "00000002.txt")),
    (os.path.join("hathigood", "2693684", "00000003.jp2"), os.path.join("2693684", "00000003.jp2")),
    (os.path.join("hathigood", "2693684", "00000003.txt"), os.path.join("2693684", "00000003.txt")),
    (os.path.join("hathigood", "2693684", "00000004.jp2"), os.path.join("2693684", "00000004.jp2")),
    (os.path.join("hathigood", "2693684", "00000004.txt"), os.path.join("2693684", "00000004.txt")),
    (os.path.join("hathigood", "2693684", "00000005.jp2"), os.path.join("2693684", "00000005.jp2")),
    (os.path.join("hathigood", "2693684", "00000005.txt"), os.path.join("2693684", "00000005.txt")),
    (os.path.join("hathigood", "2693684", "00000006.jp2"), os.path.join("2693684", "00000006.jp2")),
    (os.path.join("hathigood", "2693684", "00000006.txt"), os.path.join("2693684", "00000006.txt")),
    (os.path.join("hathigood", "2693684", "00000007.jp2"), os.path.join("2693684", "00000007.jp2")),
    (os.path.join("hathigood", "2693684", "00000007.txt"), os.path.join("2693684", "00000007.txt")),
    (os.path.join("hathigood", "2693684", "00000008.jp2"), os.path.join("2693684", "00000008.jp2")),
    (os.path.join("hathigood", "2693684", "00000008.txt"), os.path.join("2693684", "00000008.txt")),
    (os.path.join("hathigood", "2693684", "00000009.jp2"), os.path.join("2693684", "00000009.jp2")),
    (os.path.join("hathigood", "2693684", "00000009.txt"), os.path.join("2693684", "00000009.txt")),
    (os.path.join("hathigood", "2693684", "00000010.jp2"), os.path.join("2693684", "00000010.jp2")),
    (os.path.join("hathigood", "2693684", "00000010.txt"), os.path.join("2693684", "00000010.txt")),
    (os.path.join("hathigood", "2693684", "00000011.jp2"), os.path.join("2693684", "00000011.jp2")),
    (os.path.join("hathigood", "2693684", "00000011.txt"), os.path.join("2693684", "00000011.txt")),
    (os.path.join("hathigood", "2693684", "00000012.jp2"), os.path.join("2693684", "00000012.jp2")),
    (os.path.join("hathigood", "2693684", "00000012.txt"), os.path.join("2693684", "00000012.txt")),
    (os.path.join("hathigood", "2693684", "00000013.jp2"), os.path.join("2693684", "00000013.jp2")),
    (os.path.join("hathigood", "2693684", "00000013.txt"), os.path.join("2693684", "00000013.txt")),
    (os.path.join("hathigood", "2693684", "00000014.jp2"), os.path.join("2693684", "00000014.jp2")),
    (os.path.join("hathigood", "2693684", "00000014.txt"), os.path.join("2693684", "00000014.txt")),
    (os.path.join("hathigood", "2693684", "00000015.jp2"), os.path.join("2693684", "00000015.jp2")),
    (os.path.join("hathigood", "2693684", "00000015.txt"), os.path.join("2693684", "00000015.txt")),
    (os.path.join("hathigood", "2693684", "00000016.jp2"), os.path.join("2693684", "00000016.jp2")),
    (os.path.join("hathigood", "2693684", "00000016.txt"), os.path.join("2693684", "00000016.txt")),
    (os.path.join("hathigood", "2693684", "00000017.jp2"), os.path.join("2693684", "00000017.jp2")),
    (os.path.join("hathigood", "2693684", "00000017.txt"), os.path.join("2693684", "00000017.txt")),
    (os.path.join("hathigood", "2693684", "00000018.jp2"), os.path.join("2693684", "00000018.jp2")),
    (os.path.join("hathigood", "2693684", "00000018.txt"), os.path.join("2693684", "00000018.txt")),
    (os.path.join("hathigood", "2693684", "00000019.jp2"), os.path.join("2693684", "00000019.jp2")),
    (os.path.join("hathigood", "2693684", "00000019.txt"), os.path.join("2693684", "00000019.txt")),
    (os.path.join("hathigood", "2693684", "00000020.jp2"), os.path.join("2693684", "00000020.jp2")),
    (os.path.join("hathigood", "2693684", "00000020.txt"), os.path.join("2693684", "00000020.txt")),
    (os.path.join("hathigood", "2693684", "00000021.jp2"), os.path.join("2693684", "00000021.jp2")),
    (os.path.join("hathigood", "2693684", "00000021.txt"), os.path.join("2693684", "00000021.txt")),
    (os.path.join("hathigood", "2693684", "00000022.jp2"), os.path.join("2693684", "00000022.jp2")),
    (os.path.join("hathigood", "2693684", "00000022.txt"), os.path.join("2693684", "00000022.txt")),
    (os.path.join("hathigood", "2693684", "00000023.jp2"), os.path.join("2693684", "00000023.jp2")),
    (os.path.join("hathigood", "2693684", "00000023.txt"), os.path.join("2693684", "00000023.txt")),
    (os.path.join("hathigood", "2693684", "00000024.jp2"), os.path.join("2693684", "00000024.jp2")),
    (os.path.join("hathigood", "2693684", "00000024.txt"), os.path.join("2693684", "00000024.txt")),
    (os.path.join("hathigood", "2693684", "00000025.jp2"), os.path.join("2693684", "00000025.jp2")),
    (os.path.join("hathigood", "2693684", "00000025.txt"), os.path.join("2693684", "00000025.txt")),
    (os.path.join("hathigood", "2693684", "00000026.jp2"), os.path.join("2693684", "00000026.jp2")),
    (os.path.join("hathigood", "2693684", "00000026.txt"), os.path.join("2693684", "00000026.txt")),
    (os.path.join("hathigood", "2693684", "00000027.jp2"), os.path.join("2693684", "00000027.jp2")),
    (os.path.join("hathigood", "2693684", "00000027.txt"), os.path.join("2693684", "00000027.txt")),
    (os.path.join("hathigood", "2693684", "00000028.jp2"), os.path.join("2693684", "00000028.jp2")),
    (os.path.join("hathigood", "2693684", "00000028.txt"), os.path.join("2693684", "00000028.txt")),
    (os.path.join("hathigood", "2693684", "00000029.jp2"), os.path.join("2693684", "00000029.jp2")),
    (os.path.join("hathigood", "2693684", "00000029.txt"), os.path.join("2693684", "00000029.txt")),
    (os.path.join("hathigood", "2693684", "00000030.jp2"), os.path.join("2693684", "00000030.jp2")),
    (os.path.join("hathigood", "2693684", "00000030.txt"), os.path.join("2693684", "00000030.txt")),
    (os.path.join("hathigood", "2693684", "00000031.jp2"), os.path.join("2693684", "00000031.jp2")),
    (os.path.join("hathigood", "2693684", "00000031.txt"), os.path.join("2693684", "00000031.txt")),
    (os.path.join("hathigood", "2693684", "00000032.jp2"), os.path.join("2693684", "00000032.jp2")),
    (os.path.join("hathigood", "2693684", "00000032.txt"), os.path.join("2693684", "00000032.txt")),
    (os.path.join("hathigood", "2693684", "00000033.jp2"), os.path.join("2693684", "00000033.jp2")),
    (os.path.join("hathigood", "2693684", "00000033.txt"), os.path.join("2693684", "00000033.txt")),
    (os.path.join("hathigood", "2693684", "00000034.jp2"), os.path.join("2693684", "00000034.jp2")),
    (os.path.join("hathigood", "2693684", "00000034.txt"), os.path.join("2693684", "00000034.txt")),
    (os.path.join("hathigood", "2693684", "00000035.jp2"), os.path.join("2693684", "00000035.jp2")),
    (os.path.join("hathigood", "2693684", "00000035.txt"), os.path.join("2693684", "00000035.txt")),
    (os.path.join("hathigood", "2693684", "00000036.jp2"), os.path.join("2693684", "00000036.jp2")),
    (os.path.join("hathigood", "2693684", "00000036.txt"), os.path.join("2693684", "00000036.txt")),
    (os.path.join("hathigood", "2693684", "00000037.jp2"), os.path.join("2693684", "00000037.jp2")),
    (os.path.join("hathigood", "2693684", "00000037.txt"), os.path.join("2693684", "00000037.txt")),
    (os.path.join("hathigood", "2693684", "00000038.jp2"), os.path.join("2693684", "00000038.jp2")),
    (os.path.join("hathigood", "2693684", "00000038.txt"), os.path.join("2693684", "00000038.txt")),
    (os.path.join("hathigood", "2693684", "00000039.jp2"), os.path.join("2693684", "00000039.jp2")),
    (os.path.join("hathigood", "2693684", "00000039.txt"), os.path.join("2693684", "00000039.txt")),
    (os.path.join("hathigood", "2693684", "00000040.jp2"), os.path.join("2693684", "00000040.jp2")),
    (os.path.join("hathigood", "2693684", "00000040.txt"), os.path.join("2693684", "00000040.txt")),
    (os.path.join("hathigood", "2693684", "00000041.jp2"), os.path.join("2693684", "00000041.jp2")),
    (os.path.join("hathigood", "2693684", "00000041.txt"), os.path.join("2693684", "00000041.txt")),
    (os.path.join("hathigood", "2693684", "00000042.jp2"), os.path.join("2693684", "00000042.jp2")),
    (os.path.join("hathigood", "2693684", "00000042.txt"), os.path.join("2693684", "00000042.txt")),
    (os.path.join("hathigood", "2693684", "00000043.jp2"), os.path.join("2693684", "00000043.jp2")),
    (os.path.join("hathigood", "2693684", "00000043.txt"), os.path.join("2693684", "00000043.txt")),
    (os.path.join("hathigood", "2693684", "00000044.jp2"), os.path.join("2693684", "00000044.jp2")),
    (os.path.join("hathigood", "2693684", "00000044.txt"), os.path.join("2693684", "00000044.txt")),
    (os.path.join("hathigood", "2693684", "00000045.jp2"), os.path.join("2693684", "00000045.jp2")),
    (os.path.join("hathigood", "2693684", "00000045.txt"), os.path.join("2693684", "00000045.txt")),
    (os.path.join("hathigood", "2693684", "00000046.jp2"), os.path.join("2693684", "00000046.jp2")),
    (os.path.join("hathigood", "2693684", "00000046.txt"), os.path.join("2693684", "00000046.txt")),
    (os.path.join("hathigood", "2693684", "00000047.jp2"), os.path.join("2693684", "00000047.jp2")),
    (os.path.join("hathigood", "2693684", "00000047.txt"), os.path.join("2693684", "00000047.txt")),
    (os.path.join("hathigood", "2693684", "00000048.jp2"), os.path.join("2693684", "00000048.jp2")),
    (os.path.join("hathigood", "2693684", "00000048.txt"), os.path.join("2693684", "00000048.txt")),
    (os.path.join("hathigood", "2693684", "00000049.jp2"), os.path.join("2693684", "00000049.jp2")),
    (os.path.join("hathigood", "2693684", "00000049.txt"), os.path.join("2693684", "00000049.txt")),
    (os.path.join("hathigood", "2693684", "00000050.jp2"), os.path.join("2693684", "00000050.jp2")),
    (os.path.join("hathigood", "2693684", "00000050.txt"), os.path.join("2693684", "00000050.txt")),
    (os.path.join("hathigood", "2693684", "00000051.jp2"), os.path.join("2693684", "00000051.jp2")),
    (os.path.join("hathigood", "2693684", "00000051.txt"), os.path.join("2693684", "00000051.txt")),
    (os.path.join("hathigood", "2693684", "00000052.jp2"), os.path.join("2693684", "00000052.jp2")),
    (os.path.join("hathigood", "2693684", "00000052.txt"), os.path.join("2693684", "00000052.txt")),
    (os.path.join("hathigood", "2693684", "00000053.jp2"), os.path.join("2693684", "00000053.jp2")),
    (os.path.join("hathigood", "2693684", "00000053.txt"), os.path.join("2693684", "00000053.txt")),
    (os.path.join("hathigood", "2693684", "00000054.jp2"), os.path.join("2693684", "00000054.jp2")),
    (os.path.join("hathigood", "2693684", "00000054.txt"), os.path.join("2693684", "00000054.txt")),
    (os.path.join("hathigood", "2693684", "00000055.jp2"), os.path.join("2693684", "00000055.jp2")),
    (os.path.join("hathigood", "2693684", "00000055.txt"), os.path.join("2693684", "00000055.txt")),
    (os.path.join("hathigood", "2693684", "00000056.jp2"), os.path.join("2693684", "00000056.jp2")),
    (os.path.join("hathigood", "2693684", "00000056.txt"), os.path.join("2693684", "00000056.txt")),
    (os.path.join("hathigood", "2693684", "checksum.md5"), os.path.join("2693684", "checksum.md5")),
    (os.path.join("hathigood", "2693684", "marc.xml"), os.path.join("2693684", "marc.xml")),
    (os.path.join("hathigood", "2693684", "meta.yml"), os.path.join("2693684", "meta.yml")),
]


def test_get_files(monkeypatch):
    def fake(path):
        yield os.path.join("hathigood", "2693684"), [], ['00000001.jp2', '00000001.txt', '00000002.jp2', '00000002.txt',
                                                         '00000003.jp2', '00000003.txt', '00000004.jp2', '00000004.txt',
                                                         '00000005.jp2', '00000005.txt', '00000006.jp2', '00000006.txt',
                                                         '00000007.jp2', '00000007.txt', '00000008.jp2', '00000008.txt',
                                                         '00000009.jp2', '00000009.txt', '00000010.jp2', '00000010.txt',
                                                         '00000011.jp2', '00000011.txt', '00000012.jp2', '00000012.txt',
                                                         '00000013.jp2', '00000013.txt', '00000014.jp2', '00000014.txt',
                                                         '00000015.jp2', '00000015.txt', '00000016.jp2', '00000016.txt',
                                                         '00000017.jp2', '00000017.txt', '00000018.jp2', '00000018.txt',
                                                         '00000019.jp2', '00000019.txt', '00000020.jp2', '00000020.txt',
                                                         '00000021.jp2', '00000021.txt', '00000022.jp2', '00000022.txt',
                                                         '00000023.jp2', '00000023.txt', '00000024.jp2', '00000024.txt',
                                                         '00000025.jp2', '00000025.txt', '00000026.jp2', '00000026.txt',
                                                         '00000027.jp2', '00000027.txt', '00000028.jp2', '00000028.txt',
                                                         '00000029.jp2', '00000029.txt', '00000030.jp2', '00000030.txt',
                                                         '00000031.jp2', '00000031.txt', '00000032.jp2', '00000032.txt',
                                                         '00000033.jp2', '00000033.txt', '00000034.jp2', '00000034.txt',
                                                         '00000035.jp2', '00000035.txt', '00000036.jp2', '00000036.txt',
                                                         '00000037.jp2', '00000037.txt', '00000038.jp2', '00000038.txt',
                                                         '00000039.jp2', '00000039.txt', '00000040.jp2', '00000040.txt',
                                                         '00000041.jp2', '00000041.txt', '00000042.jp2', '00000042.txt',
                                                         '00000043.jp2', '00000043.txt', '00000044.jp2', '00000044.txt',
                                                         '00000045.jp2', '00000045.txt', '00000046.jp2', '00000046.txt',
                                                         '00000047.jp2', '00000047.txt', '00000048.jp2', '00000048.txt',
                                                         '00000049.jp2', '00000049.txt', '00000050.jp2', '00000050.txt',
                                                         '00000051.jp2', '00000051.txt', '00000052.jp2', '00000052.txt',
                                                         '00000053.jp2', '00000053.txt', '00000054.jp2', '00000054.txt',
                                                         '00000055.jp2', '00000055.txt', '00000056.jp2', '00000056.txt',
                                                         'checksum.md5', 'marc.xml', 'meta.yml']

    monkeypatch.setattr(os, "walk", fake)
    for i, (file_path, archive_name) in enumerate(process.get_files(os.path.join("hathigood", "2693684"))):
        assert os.path.normpath(file_path) == os.path.normpath(expected_files[i][0]), "got {}, expected {}".format(
            file_path, expected_files[i][0])
        assert os.path.normpath(archive_name) == os.path.normpath(expected_files[i][1]), "got {}, expected {}".format(
            file_path, expected_files[i][1])


def test_compress_folder_writes(tmpdir, monkeypatch):
    src = tmpdir / "src"
    src.ensure_dir()

    dist = tmpdir / "dst"
    dist.ensure_dir()

    def mock_get_files(*args):
        yield process.PackageFile("dummy", "/dummy")

    with monkeypatch.context() as e:
        mock_writer = Mock()
        e.setattr(zipfile.ZipFile, "write", mock_writer)
        e.setattr(process, "get_files", mock_get_files)
        process.compress_folder(src.strpath, dst=dist.strpath)
        mock_writer.assert_called()


def test_compress_folder_inplace_writes(tmpdir, monkeypatch):
    src = tmpdir / "src"
    src.ensure_dir()

    dist = tmpdir / "dst"
    dist.ensure_dir()

    def mock_get_files(*args):
        yield process.PackageFile("dummy", "/dummy")

    with monkeypatch.context() as e:
        mock_writer = Mock()
        e.setattr(zipfile.ZipFile, "write", mock_writer)
        e.setattr(process, "get_files", mock_get_files)
        process.compress_folder_inplace(src.strpath, dst=dist.strpath)
        mock_writer.assert_called()
