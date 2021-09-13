from setuptools import setup

setup(
    packages=['hathizip'],
    test_suite="tests",
    setup_requires=['pytest-runner'],
    tests_require=['pytest'],
    install_requires=[
        'importlib_resources;python_version<"3.7"',
    ],
    package_data={"hathizip": ["py.typed"]},
    entry_points={
        "console_scripts": [
            "hathizip = hathizip.__main__:main"
        ]
    },
)
