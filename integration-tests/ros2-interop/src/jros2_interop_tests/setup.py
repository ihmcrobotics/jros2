import os
from glob import glob
from setuptools import setup

package_name = 'jros2_interop_tests'

setup(
    name=package_name,
    version='1.0.0',
    packages=[package_name],
    data_files=[
        ('share/ament_index/resource_index/packages',
            ['resource/' + package_name]),
        ('share/' + package_name, ['package.xml']),
        (os.path.join('share', package_name, 'launch'), glob('launch/*.launch.py')),
        # Install Java wrapper scripts as executables
        (os.path.join('lib', package_name), glob('scripts/*')),
    ],
    install_requires=['setuptools'],
    zip_safe=True,
    maintainer='IHMC Test',
    maintainer_email='test@example.com',
    description='Java and Python integration tests for jros2 interoperability',
    license='Apache-2.0',
    tests_require=['pytest'],
    entry_points={
        'console_scripts': [
            'test_publisher = jros2_interop_tests.test_publisher:main',
            'test_subscriber = jros2_interop_tests.test_subscriber:main',
            'test_service_server = jros2_interop_tests.test_service_server:main',
            'test_service_client = jros2_interop_tests.test_service_client:main',
            'test_action_server = jros2_interop_tests.test_action_server:main',
            'test_action_client = jros2_interop_tests.test_action_client:main',
            'test_parameter_client = jros2_interop_tests.test_parameter_client:main',
        ],
    },
)
