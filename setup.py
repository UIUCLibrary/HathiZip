from setuptools import setup

setup(
    packages=['hathizip'],
    test_suite="tests",
    tests_require=['pytest'],
    package_data={"hathizip": ["py.typed"]},
    entry_points={
        "console_scripts": [
            "hathizip = hathizip.__main__:main"
        ]
    },
)
