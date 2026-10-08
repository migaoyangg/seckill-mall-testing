"""Capture exact pytest selectors, including parameterized cases, in JUnit XML."""


import os


def pytest_collection_modifyitems(items):
    for item in items:
        selector = os.path.relpath(str(item.path), os.getcwd()) + "::" + item.nodeid.split("::", 1)[1]
        item.user_properties.append(("testflow_nodeid", selector))
