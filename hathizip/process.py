"""Tools for doing things to the files."""

import zipfile
import tempfile
import os
import shutil
import logging
import typing
from collections import namedtuple

PackageFile = namedtuple("PackageFile", ("absolute_path", "archive_path"))


# TODO: create get_files testing
def get_files(path: str) -> typing.Iterator[PackageFile]:
    """Find files relative to a given path.

    Args:
        path: Root to search for files

    Yields: PackageFile containing the absolute path, and the archive path

    """
    starting_point = \
        os.path.sep.join(os.path.normcase(path).split(os.path.sep)[:-1])

    for root, _, files in os.walk(path):
        for _file in files:
            relative_root = os.path.relpath(root, starting_point)
            yield PackageFile(
                absolute_path=os.path.join(root, _file),
                archive_path=os.path.join(relative_root, _file)
            )


def compress_folder(path: str, dst: str) -> None:
    """Compress the contents of a path.

    Args:
        path: Root of the package
        dst: Path where the zipped package should be saved


    """
    logger = logging.getLogger(__name__)
    logger.debug("Taking care of {}".format(path))

    last_path = os.path.normcase(path).split(os.path.sep)[-1]
    zipname = "{}.zip".format(last_path)

    with tempfile.TemporaryDirectory() as temp_dir:
        tmp_zip = os.path.join(temp_dir, zipname)
        logger.debug("Creating temp zip file {}".format(tmp_zip))
        with zipfile.ZipFile(tmp_zip, "w") as zipped_package:

            for file, archive_name in get_files(path):
                logger.debug(
                    "Writing {} as {} to {}".format(
                        file, archive_name, tmp_zip
                    )
                )
                zipped_package.write(file, arcname=archive_name)
                logger.info("Zipped {}".format(file))
        final_zip = os.path.join(dst, zipname)
        shutil.move(tmp_zip, final_zip)
        logger.info("Generated {}".format(final_zip))


def compress_folder_inplace(path: str, dst: str) -> None:
    """Compress the contents of a path without using a temp directory.

    Args:
        path: Root of the package
        dst: Path where the zipped package should be saved


    """
    logger = logging.getLogger(__name__)
    logger.debug("Taking care of {}".format(path))

    last_path = os.path.normcase(path).split(os.path.sep)[-1]
    zipname = "{}.zip".format(last_path)

    temp_zipname = os.path.join(dst, "processing.dat")

    # with tempfile.TemporaryDirectory() as tf:
    # logger.debug("Creating temp zip file {}".format(tmp_zip))
    final_zip = os.path.join(dst, zipname)
    with zipfile.ZipFile(temp_zipname, "w") as zipped_package:

        for file, archive_name in get_files(path):

            logger.debug("Writing {} as {} to {}".format(
                file, archive_name, temp_zipname))
            zipped_package.write(file, arcname=archive_name)

            logger.info("Zipped {}".format(file))

    logger.debug("Renaming {} to {}".format(temp_zipname, final_zip))

    shutil.move(temp_zipname, final_zip)
    logger.info("Generated {}".format(final_zip))
